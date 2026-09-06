package com.example.lanptt

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.events.RoomEvent
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
class LiveKitActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var workerInput: EditText
    private lateinit var roomInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var statusText: TextView
    private lateinit var peersText: TextView
    private lateinit var joinButton: Button
    private lateinit var leaveButton: Button
    private lateinit var pttButton: Button

    private var room: Room? = null
    private var connected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_livekit)

        urlInput = findViewById(R.id.lkUrlInput)
        workerInput = findViewById(R.id.lkWorkerInput)
        roomInput = findViewById(R.id.lkRoomInput)
        nameInput = findViewById(R.id.lkNameInput)
        statusText = findViewById(R.id.lkStatusText)
        peersText = findViewById(R.id.lkPeersText)
        joinButton = findViewById(R.id.lkJoinButton)
        leaveButton = findViewById(R.id.lkLeaveButton)
        pttButton = findViewById(R.id.lkPttButton)

        // Restore last-used values (see token-server/README.md for setup).
        val prefs = getSharedPreferences("lk", MODE_PRIVATE)
        urlInput.setText(prefs.getString("url", "wss://REPLACE.livekit.cloud"))
        workerInput.setText(prefs.getString("worker", "https://REPLACE.workers.dev"))
        roomInput.setText(prefs.getString("room", "office"))
        nameInput.setText(prefs.getString("name", ""))

        volumeControlStream = AudioManager.STREAM_MUSIC
        updateUi()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
        }

        joinButton.setOnClickListener { join() }
        leaveButton.setOnClickListener { leave() }

        pttButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    setTalking(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setTalking(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun setStatus(msg: String) {
        runOnUiThread { statusText.text = "Status: $msg" }
    }

    private fun updateUi() {
        runOnUiThread {
            joinButton.isEnabled = !connected
            leaveButton.isEnabled = connected
            pttButton.isEnabled = connected
            pttButton.text = if (connected) "HOLD TO TALK" else "JOIN FIRST"
            if (!connected) peersText.text = "In room: -"
        }
    }

    private fun updatePeers() {
        val r = room ?: return
        val names = listOf("you") + r.remoteParticipants.values.map {
            it.name?.takeIf { n -> n.isNotEmpty() } ?: it.identity
        }
        runOnUiThread { peersText.text = "In room (${names.size}): ${names.joinToString(", ")}" }
    }

    private fun join() {
        if (connected) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
            return
        }
        val url = urlInput.text.toString().trim()
        val worker = workerInput.text.toString().trim().trimEnd('/')
        val roomName = roomInput.text.toString().trim()
        val name = nameInput.text.toString().trim()
        if (url.contains("REPLACE") || worker.contains("REPLACE")) {
            setStatus("Paste your LiveKit URL + worker URL first (see token-server/README).")
            return
        }
        if (roomName.isEmpty() || name.isEmpty()) {
            setStatus("Room + name are required.")
            return
        }
        getSharedPreferences("lk", MODE_PRIVATE).edit()
            .putString("url", url).putString("worker", worker)
            .putString("room", roomName).putString("name", name).apply()

        setStatus("Getting token...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = fetchToken(worker, roomName, name)
                val r = LiveKit.create(applicationContext)
                room = r
                launch { observeEvents(r) }
                r.connect(url, token)
                // Start muted: this is push-to-talk, not an open mic.
                r.localParticipant?.setMicrophoneEnabled(false)
                connected = true
                withContext(Dispatchers.Main) { updateUi() }
                setStatus("Joined '$roomName'. Hold to talk.")
                updatePeers()
            } catch (e: Exception) {
                setStatus("Join failed: ${e.message}")
            }
        }
    }

    private suspend fun observeEvents(r: Room) {
        r.events.events.collect { event ->
            when (event) {
                is RoomEvent.ParticipantConnected,
                is RoomEvent.ParticipantDisconnected -> updatePeers()
                is RoomEvent.ActiveSpeakersChanged -> {
                    val talking = event.speakers.map {
                        it.name?.takeIf { n -> n.isNotEmpty() } ?: it.identity
                    }
                    if (talking.isNotEmpty()) setStatus("Talking: ${talking.joinToString(", ")}")
                }
                is RoomEvent.Disconnected -> {
                    connected = false
                    room = null
                    updateUi()
                    setStatus("Disconnected.")
                }
                else -> {}
            }
        }
    }

    private fun setTalking(talking: Boolean) {
        val r = room
        if (!connected || r == null) return
        lifecycleScope.launch {
            try {
                r.localParticipant?.setMicrophoneEnabled(talking)
                runOnUiThread { pttButton.text = if (talking) "TALKING... (release)" else "HOLD TO TALK" }
            } catch (e: Exception) {
                setStatus("Mic error: ${e.message}")
            }
        }
    }

    private fun leave() {
        if (!connected) return
        connected = false
        lifecycleScope.launch {
            try { room?.disconnect() } catch (_: Exception) {}
            room = null
            updateUi()
            setStatus("Left. Join again anytime.")
        }
    }

    private fun fetchToken(worker: String, roomName: String, name: String): String {
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
        return JSONObject(resp).getString("token")
    }

    override fun onDestroy() {
        lifecycleScope.launch {
            try { room?.disconnect() } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
