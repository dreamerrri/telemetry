package com.example.lanptt

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.lanptt.ui.theme.LanPttTheme
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud mode: join a LiveKit room (channel) and push-to-talk.
 * Mic starts MUTED; holding the button publishes, releasing mutes.
 * Join token is fetched from our Cloudflare Worker so the API secret
 * never ships in the app.
 */
class LiveKitActivity : ComponentActivity() {

    private var prefsUrl by mutableStateOf("wss://REPLACE.livekit.cloud")
    private var prefsWorker by mutableStateOf("https://REPLACE.workers.dev")
    private var prefsRoom by mutableStateOf("office")
    private var prefsName by mutableStateOf("")

    private var status by mutableStateOf("enter details, hit Join.")
    private var peers by mutableStateOf("In room: -")
    private var connected by mutableStateOf(false)
    private var talking by mutableStateOf(false)

    private var room: Room? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("lk", MODE_PRIVATE)
        prefsUrl = prefs.getString("url", "wss://REPLACE.livekit.cloud") ?: prefsUrl
        prefsWorker = prefs.getString("worker", "https://REPLACE.workers.dev") ?: prefsWorker
        prefsRoom = prefs.getString("room", "office") ?: prefsRoom
        prefsName = prefs.getString("name", "") ?: ""

        volumeControlStream = AudioManager.STREAM_MUSIC

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
        }

        setContent {
            LanPttTheme {
                CloudScreen(
                    url = prefsUrl,
                    worker = prefsWorker,
                    roomName = prefsRoom,
                    userName = prefsName,
                    status = status,
                    peers = peers,
                    connected = connected,
                    talking = talking,
                    onUrlChange = { prefsUrl = it },
                    onWorkerChange = { prefsWorker = it },
                    onRoomChange = { prefsRoom = it },
                    onNameChange = { prefsName = it },
                    onJoin = { join() },
                    onLeave = { leave() },
                    onTalkStart = { setMicTalking(true) },
                    onTalkStop = { setMicTalking(false) }
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            status = "Mic permission denied - cannot talk."
        }
    }

    private fun setPeerList() {
        val r = room ?: run { peers = "In room: -"; return }
        val names = listOf("you") + r.remoteParticipants.values.map {
            it.name?.takeIf { n -> n.isNotEmpty() } ?: it.identity
        }
        peers = "In room (${names.size}): ${names.joinToString(", ")}"
    }

    private fun join() {
        if (connected) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
            return
        }
        val url = prefsUrl.trim()
        val worker = prefsWorker.trim().trimEnd('/')
        val roomName = prefsRoom.trim()
        val name = prefsName.trim()
        if (url.contains("REPLACE") || worker.contains("REPLACE")) {
            status = "Paste your LiveKit URL + worker URL first (see token-server/README)."
            return
        }
        if (roomName.isEmpty() || name.isEmpty()) {
            status = "Room + name are required."
            return
        }
        getSharedPreferences("lk", MODE_PRIVATE).edit()
            .putString("url", url).putString("worker", worker)
            .putString("room", roomName).putString("name", name).apply()

        status = "Getting token..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (token, serverUrl) = fetchToken(worker, roomName, name)
                // Prefer the worker's URL (source of truth) — fixes copy-paste
                // mistakes like https:// instead of wss:// or trailing slashes,
                // which otherwise fail with "could not fetch region settings".
                val cleanEntered = normalizeLiveKitUrl(url)
                val cleanServer = serverUrl?.let { normalizeLiveKitUrl(it) }
                if (cleanServer != null && cleanServer != cleanEntered) {
                    status = "Note: using server URL (you typed $cleanEntered)..."
                }
                val finalUrl = cleanServer ?: cleanEntered
                val r = LiveKit.create(applicationContext)
                room = r
                launch { observeEvents(r) }
                r.connect(finalUrl, token)
                // Start muted: this is push-to-talk, not an open mic.
                r.localParticipant?.setMicrophoneEnabled(false)
                connected = true
                withContext(Dispatchers.Main) { if (!connected) peers = "In room: -" }
                status = "Joined '$roomName'. Hold to talk."
                setPeerList()
            } catch (e: Exception) {
                status = "Join failed: ${e.message}"
            }
        }
    }

    private suspend fun observeEvents(r: Room) {
        r.events.events.collect { event ->
            when (event) {
                is RoomEvent.ParticipantConnected,
                is RoomEvent.ParticipantDisconnected -> setPeerList()
                is RoomEvent.ActiveSpeakersChanged -> {
                    val speakers = event.speakers.map {
                        it.name?.takeIf { n -> n.isNotEmpty() } ?: it.identity
                    }
                    if (speakers.isNotEmpty()) status = "Talking: ${speakers.joinToString(", ")}"
                }
                is RoomEvent.Disconnected -> {
                    connected = false
                    talking = false
                    room = null
                    peers = "In room: -"
                    status = "Disconnected."
                }
                else -> {}
            }
        }
    }

    private fun setMicTalking(wantTalking: Boolean) {
        val r = room
        if (!connected || r == null) return
        lifecycleScope.launch {
            try {
                r.localParticipant?.setMicrophoneEnabled(wantTalking)
                talking = wantTalking
            } catch (e: Exception) {
                status = "Mic error: ${e.message}"
            }
        }
    }

    private fun leave() {
        if (!connected) return
        connected = false
        talking = false
        lifecycleScope.launch {
            try { room?.disconnect() } catch (_: Exception) {}
            room = null
            peers = "In room: -"
            status = "Left. Join again anytime."
        }
    }

    private fun normalizeLiveKitUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.startsWith("https://")) u = "wss://" + u.removePrefix("https://")
        if (u.startsWith("http://")) u = "ws://" + u.removePrefix("http://")
        return u
    }

    private fun fetchToken(worker: String, roomName: String, name: String): Pair<String, String?> {
        val conn = (URL("$worker/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
        }
        val body = JSONObject().put("room", roomName).put("identity", name).toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        if (conn.responseCode != 200) throw Exception("token server HTTP ${conn.responseCode}")
        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        val url = if (json.has("url")) json.optString("url").takeIf { it.isNotEmpty() } else null
        return JSONObject(resp).getString("token") to url
    }

    override fun onDestroy() {
        lifecycleScope.launch {
            try { room?.disconnect() } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CloudScreen(
    url: String,
    worker: String,
    roomName: String,
    userName: String,
    status: String,
    peers: String,
    connected: Boolean,
    talking: Boolean,
    onUrlChange: (String) -> Unit,
    onWorkerChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Cloud PTT") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Cloud Walkie-Talkie (LiveKit)", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = url, onValueChange = onUrlChange, label = { Text("LiveKit URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = worker, onValueChange = onWorkerChange, label = { Text("Token server (worker)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = roomName, onValueChange = onRoomChange, label = { Text("Room (channel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = userName, onValueChange = onNameChange, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onJoin, enabled = !connected, modifier = Modifier.weight(1f)) { Text("JOIN") }
                OutlinedButton(onClick = onLeave, enabled = connected, modifier = Modifier.weight(1f)) { Text("LEAVE") }
            }
            Spacer(Modifier.height(12.dp))
            Text("Status: $status", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(peers, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {},
                enabled = connected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .pointerInteropFilter { event ->
                        if (!connected) return@pointerInteropFilter false
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                onTalkStart()
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                onTalkStop()
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                Text(if (!connected) "JOIN FIRST" else if (talking) "TALKING... (release)" else "HOLD TO TALK")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CloudScreenPreview() {
    LanPttTheme {
        CloudScreen(
            url = "wss://demo.livekit.cloud",
            worker = "https://demo.workers.dev",
            roomName = "office",
            userName = "andre",
            status = "Joined 'office'. Hold to talk.",
            peers = "In room (2): you, ben",
            connected = true,
            talking = false,
            onUrlChange = {}, onWorkerChange = {}, onRoomChange = {}, onNameChange = {},
            onJoin = {}, onLeave = {}, onTalkStart = {}, onTalkStop = {}
        )
    }
}
