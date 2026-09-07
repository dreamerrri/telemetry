package com.example.lanptt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.lanptt.lan.LanDiscovery
import com.example.lanptt.ui.talknet.ActiveScreen
import com.example.lanptt.ui.talknet.ChatPeer
import com.example.lanptt.ui.talknet.DefaultChannels
import com.example.lanptt.ui.talknet.HomeMode
import com.example.lanptt.ui.talknet.HomeScreen
import com.example.lanptt.ui.talknet.JoinScreen
import com.example.lanptt.ui.talknet.MePeer
import com.example.lanptt.ui.talknet.TalkChannel
import com.example.lanptt.ui.talknet.initialsFor
import com.example.lanptt.ui.talknet.peerColorFor
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
 * Cloud mode (TalkNet UI): channel list -> join -> active PTT.
 * Mic starts MUTED; holding the button publishes, releasing mutes.
 * Join token is fetched from our Cloudflare Worker so the API secret
 * never ships in the app.
 */
class LiveKitActivity : ComponentActivity() {

    private enum class Screen { Home, Join, Active }

    private var screen by mutableStateOf(Screen.Home)
    private var homeMode by mutableStateOf(HomeMode.CLOUD)
    private var ownIp by mutableStateOf("...")
    private var lanPeerIp by mutableStateOf("")
    private var lanName by mutableStateOf("")
    private var prefsUrl by mutableStateOf("wss://REPLACE.livekit.cloud")
    private var prefsWorker by mutableStateOf("https://REPLACE.workers.dev")
    private var prefsName by mutableStateOf("")
    private var selectedChannel by mutableStateOf<String?>(null)
    private var serverOpen by mutableStateOf(false)
    private var joinError by mutableStateOf<String?>(null)

    private var peers by mutableStateOf<List<ChatPeer>>(emptyList())
    private var speakingIds by mutableStateOf<Set<String>>(emptySet())
    private var connected by mutableStateOf(false)
    private var talking by mutableStateOf(false)

    /** Last-known rosters per channel, shown on Home cards. */
    private val rosterCache = mutableStateMapOf<String, List<ChatPeer>>()
    private var myIdentity: String = ""

    private var room: Room? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("lk", MODE_PRIVATE)
        prefsUrl = prefs.getString("url", "wss://REPLACE.livekit.cloud") ?: prefsUrl
        prefsWorker = prefs.getString("worker", "https://REPLACE.workers.dev") ?: prefsWorker
        prefsName = prefs.getString("name", "") ?: ""
        selectedChannel = prefs.getString("room", null)
        lanPeerIp = prefs.getString("lanPeer", "") ?: ""
        lanName = prefs.getString("lanName", android.os.Build.MODEL ?: "Android") ?: ""
        ownIp = detectOwnIp()

        volumeControlStream = AudioManager.STREAM_MUSIC

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
        }

        setContent {
            LanPttTheme(darkTheme = true) {
                val nearby by LanDiscovery.peers.collectAsState()
                when (screen) {
                    Screen.Home -> {
                        val channels = DefaultChannels.map { def ->
                            TalkChannel(
                                id = def.id,
                                name = def.name,
                                knownPeers = rosterCache[def.id] ?: emptyList(),
                                live = (rosterCache[def.id]?.size ?: 0) > 1
                            )
                        }
                        HomeScreen(
                            channels = channels,
                            mode = homeMode,
                            onMode = { homeMode = it },
                            ownIp = ownIp,
                            peerIp = lanPeerIp,
                            onPeerIp = {
                                lanPeerIp = it
                                getSharedPreferences("lk", MODE_PRIVATE).edit()
                                    .putString("lanPeer", it).apply()
                            },
                            onOpenLanTalk = {
                                startActivity(
                                    Intent(this, MainActivity::class.java)
                                        .putExtra("peerIp", lanPeerIp.trim())
                                )
                            },
                            lanName = lanName,
                            onLanName = {
                                lanName = it
                                getSharedPreferences("lk", MODE_PRIVATE).edit()
                                    .putString("lanName", it).apply()
                                // Re-beacon under the new name.
                                LanDiscovery.stop()
                                LanDiscovery.start(it.ifBlank { android.os.Build.MODEL ?: "Android" })
                            },
                            nearby = nearby.values.sortedBy { it.name.lowercase() },
                            onPickPeer = {
                                lanPeerIp = it
                                getSharedPreferences("lk", MODE_PRIVATE).edit()
                                    .putString("lanPeer", it).apply()
                            },
                            onJoin = { screen = Screen.Join },
                            onTapChannel = { ch ->
                                if (connected) leave()
                                selectedChannel = ch.id
                                joinError = null
                                screen = Screen.Join
                            }
                        )
                    }
                    Screen.Join -> {
                        JoinScreen(
                            name = prefsName,
                            onName = { prefsName = it },
                            channels = DefaultChannels.map { def ->
                                TalkChannel(
                                    id = def.id,
                                    name = def.name,
                                    knownPeers = rosterCache[def.id] ?: emptyList(),
                                    live = (rosterCache[def.id]?.size ?: 0) > 1
                                )
                            },
                            selectedId = selectedChannel,
                            onSelect = { selectedChannel = it },
                            url = prefsUrl,
                            onUrl = { prefsUrl = it },
                            worker = prefsWorker,
                            onWorker = { prefsWorker = it },
                            serverOpen = serverOpen,
                            onToggleServer = { serverOpen = !serverOpen },
                            error = joinError,
                            onJoin = { join() },
                            onBack = { screen = Screen.Home }
                        )
                    }
                    Screen.Active -> {
                        val chId = selectedChannel
                        val speaker = peers.firstOrNull { isSpeaking(it) }
                        ActiveScreen(
                            channelName = DefaultChannels.firstOrNull { it.id == chId }?.name
                                ?: chId ?: "Channel",
                            peers = peers,
                            isSpeaking = ::isSpeaking,
                            speaker = speaker,
                            transmitting = talking,
                            onBack = {
                                leave()
                                screen = Screen.Home
                            },
                            onDown = { setMicTalking(true) },
                            onUp = { setMicTalking(false) }
                        )
                    }
                }
            }
        }
    }

    private fun isSpeaking(p: ChatPeer): Boolean =
        if (p.id == "me") talking else speakingIds.contains(p.id)

    override fun onResume() {
        super.onResume()
        ownIp = detectOwnIp()
        LanDiscovery.start(lanName.ifBlank { android.os.Build.MODEL ?: "Android" })
    }

    override fun onPause() {
        LanDiscovery.stop()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            joinError = "Mic permission denied - cannot talk."
        }
    }

    private fun setPeerList() {
        val r = room
        if (r == null) {
            peers = emptyList()
            return
        }
        val list = mutableListOf(MePeer)
        r.remoteParticipants.values.forEach { p ->
            val idStr = p.identity?.value ?: return@forEach
            val display = p.name?.takeIf { n -> n.isNotEmpty() } ?: idStr
            list += ChatPeer(
                id = idStr,
                name = display,
                initials = initialsFor(display),
                color = peerColorFor(idStr)
            )
        }
        peers = list
        selectedChannel?.let { rosterCache[it] = list }
    }

    private fun join() {
        if (connected) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
            return
        }
        val url = prefsUrl.trim()
        val worker = prefsWorker.trim().trimEnd('/')
        val roomName = selectedChannel?.trim().orEmpty()
        val name = prefsName.trim()
        if (url.contains("REPLACE") || worker.contains("REPLACE")) {
            joinError = "Paste your LiveKit URL + worker URL first (see token-server/README)."
            return
        }
        if (roomName.isEmpty() || name.isEmpty()) {
            joinError = "Name + channel are required."
            return
        }
        getSharedPreferences("lk", MODE_PRIVATE).edit()
            .putString("url", url).putString("worker", worker)
            .putString("room", roomName).putString("name", name).apply()

        joinError = null
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (token, serverUrl) = fetchToken(worker, roomName, name)
                val cleanEntered = normalizeLiveKitUrl(url)
                val cleanServer = serverUrl?.let { normalizeLiveKitUrl(it) }
                val finalUrl = cleanServer ?: cleanEntered
                myIdentity = name
                val r = LiveKit.create(applicationContext)
                room = r
                launch { observeEvents(r) }
                r.connect(finalUrl, token)
                // Start muted: this is push-to-talk, not an open mic.
                r.localParticipant?.setMicrophoneEnabled(false)
                connected = true
                withContext(Dispatchers.Main) { screen = Screen.Active }
                setPeerList()
            } catch (e: Exception) {
                joinError = "Join failed: ${e.message}"
            }
        }
    }

    private suspend fun observeEvents(r: Room) {
        r.events.events.collect { event ->
            when (event) {
                is RoomEvent.ParticipantConnected,
                is RoomEvent.ParticipantDisconnected -> setPeerList()
                is RoomEvent.ActiveSpeakersChanged -> {
                    speakingIds = event.speakers
                        .mapNotNull { sp ->
                            val id = sp.identity?.value ?: return@mapNotNull null
                            if (id == myIdentity) "me" else id
                        }
                        .filter { id -> id == "me" || peers.any { it.id == id } }
                        .toSet()
                }
                is RoomEvent.Disconnected -> {
                    connected = false
                    talking = false
                    speakingIds = emptySet()
                    room = null
                    peers = emptyList()
                    joinError = "Disconnected."
                    screen = Screen.Home
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
                joinError = "Mic error: ${e.message}"
            }
        }
    }

    private fun leave() {
        if (!connected && room == null) return
        connected = false
        talking = false
        speakingIds = emptySet()
        lifecycleScope.launch {
            try { room?.disconnect() } catch (_: Exception) {}
            room = null
            peers = emptyList()
        }
    }

    private fun detectOwnIp(): String = LanDiscovery.detectOwnIp()

    private fun normalizeLiveKitUrl(raw: String): String {        var u = raw.trim().trimEnd('/')
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
