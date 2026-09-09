package com.example.lanptt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.lanptt.lan.LanDiscovery
import com.example.lanptt.service.SessionState
import com.example.lanptt.service.TelemetryService
import com.example.lanptt.ui.talknet.BottomNav
import com.example.lanptt.ui.talknet.ChatPeer
import com.example.lanptt.ui.talknet.DefaultChannels
import com.example.lanptt.ui.talknet.DirectDisabled
import com.example.lanptt.ui.talknet.DirectPage
import com.example.lanptt.ui.talknet.HomeScreen
import com.example.lanptt.ui.talknet.SettingsButton
import com.example.lanptt.ui.talknet.JoinScreen
import com.example.lanptt.ui.talknet.MainTab
import com.example.lanptt.ui.talknet.MePeer
import com.example.lanptt.ui.talknet.MonoLabel
import com.example.lanptt.ui.talknet.RoomListContent
import com.example.lanptt.ui.talknet.SettingsPage
import com.example.lanptt.ui.talknet.TalkChannel
import com.example.lanptt.ui.talknet.TalkState
import com.example.lanptt.ui.talknet.TalkSurface
import com.example.lanptt.ui.talknet.Transport
import com.example.lanptt.ui.talknet.TransportToggle
import com.example.lanptt.ui.talknet.initialsFor
import com.example.lanptt.ui.talknet.peerColorFor
import com.example.lanptt.ui.talknet.peerColorForName
import com.example.lanptt.ui.OnboardingFlow
import com.example.lanptt.ui.OnboardingStep
import com.example.lanptt.ui.theme.LanPttTheme
import com.example.lanptt.ui.theme.TalkBorder
import com.example.lanptt.ui.theme.TalkMint
import com.example.lanptt.ui.theme.TalkText
import kotlinx.coroutines.launch

/**
 * Single home host: Direct (P2P) + Rooms (multidevice) pages,
 * LAN/CLOUD transport toggle. All audio lives in [TelemetryService].
 */
class LiveKitActivity : ComponentActivity() {

    private enum class CloudScreen { Home, Join, Active }

    private var tab by mutableStateOf(MainTab.Direct)
    private var transport by mutableStateOf(Transport.LAN)

    // Cloud join flow
    private var cloudScreen by mutableStateOf(CloudScreen.Home)
    private var prefsUrl by mutableStateOf("wss://REPLACE.livekit.cloud")
    private var prefsWorker by mutableStateOf("https://REPLACE.workers.dev")
    private var prefsName by mutableStateOf("")
    private var selectedChannel by mutableStateOf<String?>(null)
    private var freeWord by mutableStateOf("")
    private var freeRoom by mutableStateOf("")
    private var cloudRecents by mutableStateOf<List<String>>(emptyList())
    private var lanRecents by mutableStateOf<List<String>>(emptyList())
    private var lanFreeWord by mutableStateOf("")
    private var joinError by mutableStateOf<String?>(null)
    private var joining by mutableStateOf(false)
    private var volPtt by mutableStateOf(true)
    private var cloudLive by mutableStateOf(false)
    private var presetsText by mutableStateOf("OK\nOn my way\nLoud and clear\nStand by\nYes\nNo")

    // LAN state
    private var ownIp by mutableStateOf("...")
    private var lanPeerIp by mutableStateOf("")
    private var lanName by mutableStateOf("")
    private var directPhase by mutableStateOf(TalkState.Setup)
    private var live by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)

    // First-launch onboarding
    private var onboardingComplete by mutableStateOf(false)
    private var onboardingStep by mutableStateOf(OnboardingStep.Welcome)

    // Cached OS probe results (filled in onResume / before actions, never in composition).
    private var micGranted by mutableStateOf(true)
    private var notifGranted by mutableStateOf(true)
    private var battExempt by mutableStateOf(false)

    /** Last-known cloud rosters per room, shown on Home cards. */
    private val rosterCache = mutableStateMapOf<String, List<ChatPeer>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("lk", MODE_PRIVATE)
        prefsUrl = prefs.getString("url", "wss://REPLACE.livekit.cloud") ?: prefsUrl
        prefsWorker = prefs.getString("worker", "https://REPLACE.workers.dev") ?: prefsWorker
        prefsName = prefs.getString("name", "") ?: ""
        selectedChannel = prefs.getString("room", null)
        cloudRecents = prefs.getString("recentRooms", "").orEmpty()
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(8)
        lanRecents = prefs.getString("lanRecentRooms", "").orEmpty()
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(8)
        lanPeerIp = prefs.getString("lanPeer", "") ?: ""
        lanName = prefs.getString("lanName", Build.MODEL ?: "Android") ?: ""
        volPtt = prefs.getBoolean("volPtt", true)
        transport = if (prefs.getString("transport", "LAN") == "CLOUD") Transport.CLOUD else Transport.LAN
        presetsText = prefs.getString(
            "quickTexts", "OK\nOn my way\nLoud and clear\nStand by\nYes\nNo"
        ) ?: ""
        ownIp = LanDiscovery.detectOwnIp()
        onboardingComplete = prefs.getBoolean("onboarding_complete", false)

        volumeControlStream = AudioManager.STREAM_MUSIC

        // Don't start the foreground service on a fresh install: on Android 14+
        // a microphone-type FGS start without RECORD_AUDIO throws SecurityException
        // and kills the process before onboarding can even run. The service starts
        // (as playback-type, no permission needed) once onboarding completes —
        // see completeOnboarding()/onResume() — or immediately for existing users.
        if (onboardingComplete) {
            startTelemetryService()
        }

        lifecycleScope.launch {
            SessionState.cloudPeers.collect { list ->
                selectedChannel?.let { rosterCache[it] = list }
            }
        }

        setContent {
            LanPttTheme(darkTheme = true) {
                val nearby by LanDiscovery.peers.collectAsState()
                val lanRoom by SessionState.lanRoom.collectAsState()
                val lanStatus by SessionState.lanStatus.collectAsState()
                val lanTx by SessionState.lanTransmitting.collectAsState()
                val lanTexts by SessionState.lanTexts.collectAsState()
                val peers by SessionState.cloudPeers.collectAsState()
                val speakingIds by SessionState.cloudSpeakers.collectAsState()
                val connected by SessionState.cloudConnected.collectAsState()
                val talking by SessionState.cloudTalking.collectAsState()
                val cloudError by SessionState.cloudError.collectAsState()
                val generation by SessionState.cloudGeneration.collectAsState()
                val cloudTexts by SessionState.cloudTexts.collectAsState()

                LaunchedEffect(connected) {
                    if (connected && joining) {
                        joining = false
                        joinError = null
                        selectedChannel?.let { pushCloudRecent(it) }
                        cloudScreen = CloudScreen.Active
                    }
                }
                LaunchedEffect(cloudError) {
                    cloudError?.let {
                        joinError = it
                        joining = false
                    }
                }
                LaunchedEffect(generation) {
                    if (generation > 0 && cloudScreen == CloudScreen.Active && !connected) {
                        cloudLive = false
                        joinError = "Disconnected."
                        cloudScreen = CloudScreen.Home
                    }
                }

                if (onboardingComplete) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(com.example.lanptt.ui.theme.TalkBg)
                            .statusBarsPadding()
                    ) {
                    // Header: brand + transport toggle.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MonoLabel("TELEMETRY", color = TalkMint, fontSize = 12)
                            Text(
                                if (tab == MainTab.Direct) "Direct" else "Rooms",
                                color = TalkText, fontSize = 20.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        TransportToggle(transport = transport, onTransport = { switchTransport(it) })
                        Spacer(Modifier.width(8.dp))
                        SettingsButton(onClick = { settingsOpen = !settingsOpen })
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TalkBorder)
                    )

                    // Pages: swipeable Direct ↔ Rooms; BottomNav button still switches.
                    Box(modifier = Modifier.weight(1f)) {
                        if (settingsOpen) {
                            SettingsPage(
                                name = prefsName,
                                onName = { onSettingsName(it) },
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
                                onUrl = {
                                    prefsUrl = it
                                    prefs.edit().putString("url", it).apply()
                                },
                                worker = prefsWorker,
                                onWorker = {
                                    prefsWorker = it
                                    prefs.edit().putString("worker", it).apply()
                                },
                                ownIp = ownIp,
                                onBack = { settingsOpen = false }
                            )
                        } else {
                            // Swipeable pages: Direct (0) ↔ Rooms (1); BottomNav button still switches.
                            val pagerState = rememberPagerState(pageCount = { 2 })
                            LaunchedEffect(tab) {
                                if (pagerState.currentPage != tab.ordinal && !pagerState.isScrollInProgress) {
                                    pagerState.animateScrollToPage(tab.ordinal)
                                }
                            }
                            LaunchedEffect(pagerState.currentPage) {
                                switchTab(MainTab.entries[pagerState.currentPage])
                            }
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                when (MainTab.entries[page]) {
                                    MainTab.Direct -> {
                                if (transport == Transport.LAN) {
                                    if (directPhase == TalkState.Talk) {
                                        val directPeer = nearby.values.firstOrNull { it.ip == lanPeerIp }
                                        TalkSurface(
                                            identity = lanPeerIp,
                                            subtitle = "P2P · $lanPeerIp",
                                            peers = listOf(MePeer) + listOf(
                                                directPeer?.let {
                                                    ChatPeer(it.ip, it.name, initialsFor(it.name), peerColorForName(it.name))
                                                } ?: ChatPeer(lanPeerIp, "Peer", initialsFor(lanPeerIp), peerColorFor(lanPeerIp))
                                            ),
                                            isSpeaking = { it.id == "me" && lanTx },
                                            speaker = if (lanTx) MePeer else null,
                                            transmitting = lanTx,
                                            liveMode = live,
                                            onToggleLive = { live = it; if (it) lanDown(lanPeerIp, "") else lanUp() },
                                            presets = parsePresets(presetsText),
                                            onSendText = { sendLanText(lanPeerIp, "", it) },
                                            texts = lanTexts.filter { it.room.isEmpty() },
                                            transportLine = if (live) lanStatus else "LAN · Direct",
                                            onBack = { live = false; lanUp(); directPhase = TalkState.Setup },
                                            onChange = { live = false; lanUp(); directPhase = TalkState.Setup },
                                            onDown = { lanDown(lanPeerIp, "") },
                                            onUp = { if (!live) lanUp() }
                                        )
                                    } else {
                                        DirectPage(
                                            ownIp = ownIp,
                                            lanName = lanName,
                                            onLanName = { onSettingsName(it) },
                                            peerIp = lanPeerIp,
                                            onPeerIp = { setLanPeer(it) },
                                            nearby = nearby.values.sortedBy { it.name.lowercase() },
                                            onTalk = {
                                                if (lanPeerIp.isNotBlank()) {
                                                    live = false
                                                    lanUp()
                                                    directPhase = TalkState.Talk
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    DirectDisabled(onSwitchToLan = { switchTransport(Transport.LAN) })
                                }
                            }
                            MainTab.Rooms -> {
                                if (transport == Transport.LAN) {
                                    if (lanRoom.isNotEmpty()) {
                                        LanRoomDetail(
                                            room = lanRoom,
                                            nearby = nearby.values.sortedBy { it.name.lowercase() },
                                            transmitting = lanTx,
                                            texts = lanTexts.filter { it.room == lanRoom },
                                            presets = parsePresets(presetsText),
                                            lanStatus = lanStatus,
                                            onDown = { lanDown("", lanRoom) },
                                            onUp = { lanUp() },
                                            onSendText = { sendLanText("", lanRoom, it) },
                                            live = live,
                                            onToggleLive = {
                                                live = it
                                                if (it) lanDown("", lanRoom) else lanUp()
                                            },
                                            onLeave = { setLanRoom("") }
                                        )
                                    } else {
                                        LanRoomsList(
                                            nearby = nearby.values.sortedBy { it.name.lowercase() },
                                            recents = lanRecents,
                                            freeWord = lanFreeWord,
                                            onFreeWord = { lanFreeWord = it },
                                            onJoinWord = { joinLanRoom(lanFreeWord.trim()) },
                                            onTapRoom = { joinLanRoom(it) }
                                        )
                                    }
                                } else {
                                    when (cloudScreen) {
                                        CloudScreen.Home -> {
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
                                                recents = cloudRecentChannels(),
                                                freeWord = freeWord,
                                                onFreeWord = { freeWord = it },
                                                onJoinWord = { joinCloudWord(freeWord.trim()) },
                                                onTapChannel = { ch ->
                                                    if (connected) cloudLeave()
                                                    selectedChannel = ch.id
                                                    freeRoom = ""
                                                    joinError = null
                                                    cloudScreen = CloudScreen.Join
                                                }
                                            )
                                        }
                                        CloudScreen.Join -> {
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
                                                } + cloudRecentChannels()).distinctBy { it.id },
                                                selectedId = selectedChannel,
                                                onSelect = { selectedChannel = it; freeRoom = "" },
                                                freeRoom = freeRoom,
                                                onFreeRoom = { freeRoom = it },
                                                
                                                error = joinError ?: if (joining) "Getting token..." else null,
                                                onJoin = { join() },
                                                onBack = { cloudScreen = CloudScreen.Home }
                                            )
                                        }
                                        CloudScreen.Active -> {
                                            val chId = selectedChannel
                                            val isSpeaking: (ChatPeer) -> Boolean = {
                                                if (it.id == "me") talking else speakingIds.contains(it.id)
                                            }
                                            val speaker = peers.firstOrNull(isSpeaking)
                                            TalkSurface(
                                                identity = DefaultChannels.firstOrNull { it.id == chId }?.name
                                                    ?: chId ?: "Channel",
                                                subtitle = "Connected · Cloud",
                                                peers = peers,
                                                isSpeaking = isSpeaking,
                                                speaker = speaker,
                                                transmitting = talking,
                                                liveMode = cloudLive,
                                                onToggleLive = {
                                                    cloudLive = it
                                                    setMicTalking(it)
                                                },
                                                presets = parsePresets(presetsText),
                                                onSendText = { sendText(it) },
                                                texts = cloudTexts,
                                                transportLine = cloudError ?: if (connected) "Connected · LiveKit" else "Connecting...",
                                                onBack = {
                                                    cloudLive = false
                                                    cloudLeave()
                                                    cloudScreen = CloudScreen.Home
                                                },
                                                onChange = {
                                                    cloudLive = false
                                                    cloudLeave()
                                                    cloudScreen = CloudScreen.Join
                                                },
                                                onDown = { setMicTalking(true) },
                                                onUp = { if (!cloudLive) setMicTalking(false) }
                                            )
                                        }
                                    }
                                }
                            }
                                }
                            }
                        }
                    }

                    BottomNav(tab = tab, onTab = { switchTab(it) })
                    }
                } else {
                    OnboardingFlow(
                        step = onboardingStep,
                        neededMic = !micGranted,
                        neededNotifications = !notifGranted,
                        batteryExempt = battExempt,
                        onStart = { onboardingStep = OnboardingStep.Permissions },
                        onPermContinue = {
                            refreshPermissionState()
                            requestPendingPermissions()
                            onboardingStep = OnboardingStep.Battery
                        },
                        onEnableBattery = {
                            requestBatteryExemption()
                            onboardingStep = OnboardingStep.Done
                        },
                        onSkipBattery = {
                            refreshPermissionState()
                            onboardingStep = OnboardingStep.Done
                        },
                        onFinish = { completeOnboarding() },
                        onSkipAll = { completeOnboarding() }
                    )
                }
            }
        }
    }

    /* ─── Transport + tabs ─────────────────────────────── */

    private fun switchTransport(t: Transport) {
        if (t == transport) return
        // Leave everything before switching transports.
        startService(TelemetryService.cmd(this, TelemetryService.ACTION_LAN_UP))
        cloudLive = false
        live = false
        directPhase = TalkState.Setup
        if (SessionState.cloudConnected.value) cloudLeave()
        setLanRoom("")
        transport = t
        getSharedPreferences("lk", MODE_PRIVATE).edit().putString("transport", t.name).apply()
    }

    private fun switchTab(t: MainTab) {
        if (t == tab) return
        live = false
        directPhase = TalkState.Setup
        lanUp()
        if (transport == Transport.CLOUD && cloudScreen == CloudScreen.Active && !cloudLive) {
            setMicTalking(false)
        }
        tab = t
    }

    /* ─── LAN helpers ──────────────────────────────────── */

    private fun updateLanName(name: String) {
        lanName = name
        getSharedPreferences("lk", MODE_PRIVATE).edit().putString("lanName", name).apply()
        startService(
            TelemetryService.cmd(this, TelemetryService.ACTION_SET_NAME).putExtra("name", name)
        )
    }

    private fun onSettingsName(n: String) {
        prefsName = n
        updateLanName(n)
        getSharedPreferences("lk", MODE_PRIVATE).edit().putString("name", n).apply()
    }

    private fun setLanPeer(ip: String) {
        lanPeerIp = ip
        getSharedPreferences("lk", MODE_PRIVATE).edit().putString("lanPeer", ip).apply()
    }

    private fun setLanRoom(room: String) {
        startService(
            TelemetryService.cmd(this, TelemetryService.ACTION_LAN_ROOM).putExtra("room", room)
        )
    }

    private fun joinLanRoom(word: String) {
        if (!roomWordRe.matches(word)) {
            lanFreeWord = word
            return
        }
        setLanRoom(word)
        lanFreeWord = ""
        pushLanRecent(word)
    }

    private fun pushLanRecent(room: String) {
        lanRecents = (listOf(room) + lanRecents.filter { it != room }).take(8)
        getSharedPreferences("lk", MODE_PRIVATE).edit()
            .putString("lanRecentRooms", lanRecents.joinToString("\n")).apply()
    }

    private fun lanDown(peerIp: String, room: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
            return
        }
        if (room.isEmpty() && peerIp.isBlank()) {
            SessionState.lanStatus.value = "Pick a peer first."
            return
        }
        startService(
            TelemetryService.cmd(this, TelemetryService.ACTION_LAN_DOWN)
                .putExtra("peer", peerIp.trim())
                .putExtra("room", room.trim())
        )
    }

    private fun lanUp() {
        startService(TelemetryService.cmd(this, TelemetryService.ACTION_LAN_UP))
    }

    private fun sendLanText(peerIp: String, room: String, text: String) {
        if (room.isEmpty() && peerIp.isBlank()) {
            SessionState.lanStatus.value = "Pick a peer first."
            return
        }
        startService(
            TelemetryService.cmd(this, TelemetryService.ACTION_LAN_TEXT)
                .putExtra("peer", peerIp.trim())
                .putExtra("room", room.trim())
                .putExtra("text", text)
        )
    }

    /* ─── Cloud helpers ────────────────────────────────── */

    private val roomWordRe = Regex("^[a-zA-Z0-9 _-]{1,64}$")

    private fun pushCloudRecent(room: String) {
        cloudRecents = (listOf(room) + cloudRecents.filter { it != room }).take(8)
        getSharedPreferences("lk", MODE_PRIVATE).edit()
            .putString("recentRooms", cloudRecents.joinToString("\n")).apply()
    }

    private fun cloudRecentChannels(): List<TalkChannel> =
        cloudRecents.map { id ->
            TalkChannel(
                id = id,
                name = DefaultChannels.firstOrNull { it.id == id }?.name ?: id,
                knownPeers = rosterCache[id] ?: emptyList(),
                live = (rosterCache[id]?.size ?: 0) > 1
            )
        }

    private fun joinCloudWord(word: String) {
        if (!roomWordRe.matches(word)) {
            joinError = "Room: letters, numbers, space, _ or -, max 64 chars."
            freeWord = ""
            freeRoom = ""
            cloudScreen = CloudScreen.Join
        } else {
            selectedChannel = word
            freeWord = ""
            freeRoom = ""
            joinError = null
            cloudScreen = CloudScreen.Join
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

    private fun cloudLeave() {
        startService(TelemetryService.cmd(this, TelemetryService.ACTION_CLOUD_LEAVE))
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

    /* ─── Keys / permissions ───────────────────────────── */

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volPttEnabled()) {
            when {
                transport == Transport.LAN && tab == MainTab.Direct && lanPeerIp.isNotBlank() -> {
                    lanDown(lanPeerIp, "")
                    return true
                }
                transport == Transport.LAN && tab == MainTab.Rooms &&
                    SessionState.lanRoom.value.isNotEmpty() -> {
                    lanDown("", SessionState.lanRoom.value)
                    return true
                }
                transport == Transport.CLOUD && tab == MainTab.Rooms &&
                    cloudScreen == CloudScreen.Active && SessionState.cloudConnected.value -> {
                    setMicTalking(true)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volPttEnabled()) {
            when {
                transport == Transport.LAN -> {
                    lanUp()
                    return true
                }
                transport == Transport.CLOUD && tab == MainTab.Rooms &&
                    cloudScreen == CloudScreen.Active && SessionState.cloudConnected.value -> {
                    if (!cloudLive) setMicTalking(false)
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun volPttEnabled(): Boolean =
        getSharedPreferences("lk", MODE_PRIVATE).getBoolean("volPtt", true)

    override fun onResume() {
        super.onResume()
        ownIp = LanDiscovery.detectOwnIp()
        refreshPermissionState()
        // Existing installs / returning users: make sure the service is up even if
        // this process was recreated. Fresh installs skip this until onboarding
        // completes (completeOnboarding starts it).
        if (onboardingComplete) {
            startTelemetryService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && permissions.contains(Manifest.permission.RECORD_AUDIO) &&
            (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)
        ) {
            joinError = "Mic permission denied - cannot talk."
        }
    }

    // ── First-launch onboarding helpers ──────────────────────

    /**
     * Re-read the OS permission / battery state into the cached fields. Guarded with
     * catch(Throwable) so no probe (especially PowerManager, absent on some devices /
     * emulators) can throw out of composition and crash the activity. Safe defaults:
     * permissions assumed granted, battery assumed not exempt. Called from onResume and
     * from tap handlers — never from inside setContent.
     */
    private fun refreshPermissionState() {
        micGranted = try {
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { true }
        notifGranted = try {
            Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { true }
        val exempt = try {
            val pm = getSystemService(PowerManager::class.java)
            pm.isIgnoringBatteryOptimizations(packageName)
        } catch (_: Throwable) { false }
        battExempt = exempt == true
    }

    /** Ask for whatever required permissions the user hasn't granted yet (mic first). */
    private fun requestPendingPermissions() {
        refreshPermissionState()
        val need = buildList {
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
            if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), 1002)
        }
    }

    /**
     * Open the battery-optimization exemption for this package. Uses the in-app native
     * "Allow to run in background?" dialog when available (keeps this activity alive),
     * otherwise falls back to the battery-optimization list in Settings.
     */
    private fun requestBatteryExemption() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) return
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Throwable) { }
        }
    }

    private fun completeOnboarding() {
        onboardingComplete = true
        getSharedPreferences("lk", MODE_PRIVATE)
            .edit().putBoolean("onboarding_complete", true).apply()
        // Safe now: the service idles as a playback-type FGS (no mic permission
        // needed); it only touches the microphone type while transmitting.
        startTelemetryService()
    }

    /**
     * Idempotent service start. ACTION_START re-entry is already guarded inside
     * the service (startLanRx / Discovery.start no-op when running). Guarded
     * against background-start restrictions (API 31+) so it can never crash.
     */
    private fun startTelemetryService() {
        try {
            startForegroundService(
                TelemetryService.cmd(this, TelemetryService.ACTION_START)
                    .putExtra("name", lanName)
            )
        } catch (_: Throwable) { }
    }
}

/* ─── LAN rooms list + detail (same file: needs activity scope) ─────────── */

@Composable
private fun LiveKitActivity.LanRoomsList(
    nearby: List<com.example.lanptt.lan.LanPeer>,
    recents: List<String>,
    freeWord: String,
    onFreeWord: (String) -> Unit,
    onJoinWord: () -> Unit,
    onTapRoom: (String) -> Unit
) {
    val rooms = nearby.filter { it.room.isNotEmpty() }.groupBy { it.room }
    RoomListContent(
        channels = rooms.map { (id, members) ->
            TalkChannel(
                id = id,
                name = id,
                knownPeers = members.map { m ->
                    ChatPeer(
                        m.ip, m.name,
                        m.name.split(" ").let {
                            if (it.size == 1) it[0].take(2).uppercase()
                            else (it[0].take(1) + it[1].take(1)).uppercase()
                        },
                        com.example.lanptt.ui.talknet.peerColorForName(m.name)
                    )
                },
                live = false
            )
        }.sortedBy { it.id },
        recents = recents.map { TalkChannel(it, it) },
        freeWord = freeWord,
        onFreeWord = onFreeWord,
        onJoinWord = onJoinWord,
        onTapChannel = { onTapRoom(it.id) },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun LiveKitActivity.LanRoomDetail(
    room: String,
    nearby: List<com.example.lanptt.lan.LanPeer>,
    transmitting: Boolean,
    texts: List<com.example.lanptt.service.TextMsg>,
    presets: List<String>,
    lanStatus: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onSendText: (String) -> Unit,
    live: Boolean,
    onToggleLive: (Boolean) -> Unit,
    onLeave: () -> Unit
) {
    val members = nearby.filter { it.room == room }
    val all = listOf(
        MePeer
    ) + members.map { m ->
        ChatPeer(
            m.ip, m.name,
            m.name.split(" ").let {
                if (it.size == 1) it[0].take(2).uppercase()
                else (it[0].take(1) + it[1].take(1)).uppercase()
            },
            peerColorForName(m.name)
        )
    }
    TalkSurface(
        identity = room,
        subtitle = "${all.size} online · LAN",
        peers = all,
        isSpeaking = { it.id == "me" && transmitting },
        speaker = if (transmitting) MePeer else null,
        transmitting = transmitting,
        liveMode = live,
        onToggleLive = onToggleLive,
        presets = presets,
        onSendText = onSendText,
        texts = texts,
        transportLine = if (live) lanStatus else "LAN · $room",
        onBack = {
            onToggleLive(false)
            onLeave()
        },
        onChange = {
            onToggleLive(false)
            onLeave()
        },
        onDown = onDown,
        onUp = { if (!live) onUp() }
    )
}
