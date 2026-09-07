package com.example.lanptt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.lanptt.lan.LanDiscovery
import com.example.lanptt.service.SessionState
import com.example.lanptt.service.TelemetryService
import com.example.lanptt.ui.talknet.ActiveScreen
import com.example.lanptt.ui.talknet.ChatPeer
import com.example.lanptt.ui.talknet.DefaultChannels
import com.example.lanptt.ui.talknet.HomeMode
import com.example.lanptt.ui.talknet.HomeScreen
import com.example.lanptt.ui.talknet.JoinScreen
import com.example.lanptt.ui.talknet.TalkChannel
import com.example.lanptt.ui.theme.LanPttTheme
import kotlinx.coroutines.launch

/**
 * Cloud mode (TalkNet UI): channel list -> join -> active PTT.
 * All audio/network lives in [TelemetryService]; this screen only
 * collects [SessionState] and sends command intents.
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
    private var freeWord by mutableStateOf("")
    private var freeRoom by mutableStateOf("")
    private var recents by mutableStateOf<List<String>>(emptyList())
    private var serverOpen by mutableStateOf(false)
    private var joinError by mutableStateOf<String?>(null)
    private var joining by mutableStateOf(false)
    private var volPtt by mutableStateOf(true)
    private var liveMode by mutableStateOf(false)
    private var presetsText by mutableStateOf("OK\nOn my way\nLoud and clear\nStand by\nYes\nNo")

    /** Last-known rosters per channel, shown on Home cards. */
    private val rosterCache = mutableStateMapOf<String, List<ChatPeer>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("lk", MODE_PRIVATE)
        prefsUrl = prefs.getString("url", "wss://REPLACE.livekit.cloud") ?: prefsUrl
        prefsWorker = prefs.getString("worker", "https://REPLACE.workers.dev") ?: prefsWorker
        prefsName = prefs.getString("name", "") ?: ""
        selectedChannel = prefs.getString("room", null)
        recents = prefs.getString("recentRooms", "").orEmpty()
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(8)
        lanPeerIp = prefs.getString("lanPeer", "") ?: ""
        lanName = prefs.getString("lanName", android.os.Build.MODEL ?: "Android") ?: ""
        volPtt = prefs.getBoolean("volPtt", true)
        presetsText = prefs.getString(
            "quickTexts", "OK\nOn my way\nLoud and clear\nStand by\nYes\nNo"
        ) ?: ""
        ownIp = LanDiscovery.detectOwnIp()

        volumeControlStream = AudioManager.STREAM_MUSIC

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
        }

        startForegroundService(
            TelemetryService.cmd(this, TelemetryService.ACTION_START)
                .putExtra("name", lanName)
        )

        // Cache rosters for Home cards.
        lifecycleScope.launch {
            SessionState.cloudPeers.collect { list ->
                selectedChannel?.let { rosterCache[it] = list }
            }
        }

        setContent {
            LanPttTheme(darkTheme = true) {
                val nearby by LanDiscovery.peers.collectAsState()
                val peers by SessionState.cloudPeers.collectAsState()
                val speakingIds by SessionState.cloudSpeakers.collectAsState()
                val connected by SessionState.cloudConnected.collectAsState()
                val talking by SessionState.cloudTalking.collectAsState()
                val cloudError by SessionState.cloudError.collectAsState()
                val generation by SessionState.cloudGeneration.collectAsState()
                val quality by SessionState.cloudQuality.collectAsState()
                val texts by SessionState.cloudTexts.collectAsState()

                // Join succeeded -> Active.
                LaunchedEffect(connected) {
                    if (connected && joining) {
                        joining = false
                        joinError = null
                        selectedChannel?.let { pushRecent(it) }
                        screen = Screen.Active
                    }
                }
                // Surface service errors on Join.
                LaunchedEffect(cloudError) {
                    cloudError?.let {
                        joinError = it
                        joining = false
                    }
                }
                // Hard disconnect while active -> Home.
                LaunchedEffect(generation) {
                    if (generation > 0 && screen == Screen.Active && !connected) {
                        liveMode = false
                        joinError = "Disconnected."
                        screen = Screen.Home
                    }
                }

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
                            recents = recentChannels(),
                            freeWord = freeWord,
                            onFreeWord = { freeWord = it },
                            onJoinWord = {
                                val word = freeWord.trim()
                                if (!roomWordRe.matches(word)) {
                                    joinError = "Room: letters, numbers, space, _ or -, max 64 chars."
                                    freeWord = ""
                                    freeRoom = ""
                                    screen = Screen.Join
                                } else {
                                    selectedChannel = word
                                    freeWord = ""
                                    freeRoom = ""
                                    joinError = null
                                    screen = Screen.Join
                                }
                            },
                            mode = homeMode,
                            onMode = { homeMode = it },
                            ownIp = ownIp,
                            peerIp = lanPeerIp,
                            onPeerIp = {
                                lanPeerIp = it
                                prefs.edit().putString("lanPeer", it).apply()
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
                                prefs.edit().putString("lanName", it).apply()
                                startService(
                                    TelemetryService.cmd(this, TelemetryService.ACTION_SET_NAME)
                                        .putExtra("name", it)
                                )
                            },
                            nearby = nearby.values.sortedBy { it.name.lowercase() },
                            onPickPeer = {
                                lanPeerIp = it
                                prefs.edit().putString("lanPeer", it).apply()
                            },
                            onJoin = { screen = Screen.Join },
                            onTapChannel = { ch ->
                                if (connected) {
                                    startService(TelemetryService.cmd(this, TelemetryService.ACTION_CLOUD_LEAVE))
                                }
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
                            channels = (DefaultChannels.map { def ->
                                TalkChannel(
                                    id = def.id,
                                    name = def.name,
                                    knownPeers = rosterCache[def.id] ?: emptyList(),
                                    live = (rosterCache[def.id]?.size ?: 0) > 1
                                )
                            } + recentChannels()).distinctBy { it.id },
                            selectedId = selectedChannel,
                            onSelect = { selectedChannel = it; freeRoom = "" },
                            freeRoom = freeRoom,
                            onFreeRoom = { freeRoom = it },
                            volPtt = volPtt,
                            onVolPtt = {
                                volPtt = it
                                prefs.edit().putBoolean("volPtt", it).apply()
                            },
                            presetsText = presetsText,
                            onPresetsText = {
                                presetsText = it
                                prefs.edit().putString("quickTexts", it).apply()
                            },
                            url = prefsUrl,
                            onUrl = { prefsUrl = it },
                            worker = prefsWorker,
                            onWorker = { prefsWorker = it },
                            serverOpen = serverOpen,
                            onToggleServer = { serverOpen = !serverOpen },
                            error = joinError ?: if (joining) "Getting token..." else null,
                            onJoin = { join() },
                            onBack = { screen = Screen.Home }
                        )
                    }
                    Screen.Active -> {
                        val chId = selectedChannel
                        val isSpeaking: (ChatPeer) -> Boolean = {
                            if (it.id == "me") talking else speakingIds.contains(it.id)
                        }
                        val speaker = peers.firstOrNull(isSpeaking)
                        ActiveScreen(
                            channelName = DefaultChannels.firstOrNull { it.id == chId }?.name
                                ?: chId ?: "Channel",
                            peers = peers,
                            isSpeaking = isSpeaking,
                            speaker = speaker,
                            transmitting = talking,
                            liveMode = liveMode,
                            onToggleLive = {
                                liveMode = it
                                setMicTalking(it)
                            },
                            qualityDot = { p ->
                                com.example.lanptt.ui.talknet.qualityColor(quality[p.id])
                            },
                            presets = parsePresets(presetsText),
                            onSendText = { sendText(it) },
                            texts = texts,
                            onBack = {
                                liveMode = false
                                startService(TelemetryService.cmd(this, TelemetryService.ACTION_CLOUD_LEAVE))
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

    private val roomWordRe = Regex("^[a-zA-Z0-9 _-]{1,64}$")

    private fun saveRecents(list: List<String>) {
        recents = list
        getSharedPreferences("lk", MODE_PRIVATE).edit()
            .putString("recentRooms", list.joinToString("\n")).apply()
    }

    private fun pushRecent(room: String) {
        saveRecents((listOf(room) + recents.filter { it != room }).take(8))
    }

    private fun recentChannels(): List<TalkChannel> =
        recents.map { id ->
            TalkChannel(
                id = id,
                name = DefaultChannels.firstOrNull { it.id == id }?.name ?: id,
                knownPeers = rosterCache[id] ?: emptyList(),
                live = (rosterCache[id]?.size ?: 0) > 1
            )
        }

    override fun onResume() {
        super.onResume()
        ownIp = LanDiscovery.detectOwnIp()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volPtt &&
            screen == Screen.Active && SessionState.cloudConnected.value
        ) {
            setMicTalking(true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volPtt &&
            screen == Screen.Active && SessionState.cloudConnected.value
        ) {
            if (!liveMode) setMicTalking(false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            joinError = "Mic permission denied - cannot talk."
        }
    }

    private fun join() {
        if (SessionState.cloudConnected.value || joining) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
            return
        }
        val url = prefsUrl.trim()
        val worker = prefsWorker.trim().trimEnd('/')
        val roomName = freeRoom.trim().ifEmpty { selectedChannel?.trim().orEmpty() }
        val name = prefsName.trim()
        if (url.contains("REPLACE") || worker.contains("REPLACE")) {
            joinError = "Paste your LiveKit URL + worker URL first (see token-server/README)."
            return
        }
        if (roomName.isEmpty() || name.isEmpty()) {
            joinError = "Name + channel are required."
            return
        }
        if (!roomWordRe.matches(roomName)) {
            joinError = "Room: letters, numbers, space, _ or -, max 64 chars."
            return
        }
        selectedChannel = roomName
        freeRoom = ""
        getSharedPreferences("lk", MODE_PRIVATE).edit()
            .putString("url", url).putString("worker", worker)
            .putString("room", roomName).putString("name", name).apply()

        joining = true
        joinError = null
        startForegroundService(
            TelemetryService.cmd(this, TelemetryService.ACTION_CLOUD_JOIN)
                .putExtra("url", url)
                .putExtra("worker", worker)
                .putExtra("room", roomName)
                .putExtra("name", name)
        )
    }

    private fun setMicTalking(wantTalking: Boolean) {
        if (!SessionState.cloudConnected.value) return
        startService(
            TelemetryService.cmd(this, TelemetryService.ACTION_CLOUD_TALK)
                .putExtra("talk", wantTalking)
        )
    }

    private fun sendText(text: String) {
        if (!SessionState.cloudConnected.value) return
        startService(
            TelemetryService.cmd(this, TelemetryService.ACTION_CLOUD_TEXT)
                .putExtra("text", text)
        )
    }

    private fun parsePresets(raw: String): List<String> =
        raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(8)
}
