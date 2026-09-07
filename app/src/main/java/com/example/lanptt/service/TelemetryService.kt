package com.example.lanptt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.IBinder
import com.example.lanptt.LiveKitActivity
import com.example.lanptt.R
import com.example.lanptt.lan.LanDiscovery
import com.example.lanptt.ui.talknet.ChatPeer
import com.example.lanptt.ui.talknet.MePeer
import com.example.lanptt.ui.talknet.initialsFor
import com.example.lanptt.ui.talknet.peerColorFor
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.ConnectionQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Phase 1: all audio + network lives here, surviving screen-off.
 *
 * - LAN UDP rx (always on) + tx (while held), port 50005, 16 kHz PCM.
 * - LAN discovery beacons while alive.
 * - LiveKit room join/publish for Cloud mode.
 * - Foreground notification (required) with a Stop action.
 *
 * Activities send command intents and collect [SessionState]; they own no sockets.
 * Lifetime: started on first app launch, stopped via the notification Stop action.
 */
class TelemetryService : Service() {

    companion object {
        const val ACTION_START = "com.example.lanptt.START"
        const val ACTION_STOP = "com.example.lanptt.STOP"
        const val ACTION_LAN_DOWN = "com.example.lanptt.LAN_DOWN"
        const val ACTION_LAN_UP = "com.example.lanptt.LAN_UP"
        const val ACTION_LAN_ROOM = "com.example.lanptt.LAN_ROOM"
        const val ACTION_CLOUD_JOIN = "com.example.lanptt.CLOUD_JOIN"
        const val ACTION_CLOUD_LEAVE = "com.example.lanptt.CLOUD_LEAVE"
        const val ACTION_CLOUD_TALK = "com.example.lanptt.CLOUD_TALK"
        const val ACTION_CLOUD_TEXT = "com.example.lanptt.CLOUD_TEXT"
        const val ACTION_LAN_TEXT = "com.example.lanptt.LAN_TEXT"
        const val ACTION_SET_NAME = "com.example.lanptt.SET_NAME"

        const val CH_ID = "telemetry_fg"
        const val NOTIF_ID = 1

        const val LAN_PORT = 50005
        const val SAMPLE_RATE = 16000

        fun cmd(ctx: android.content.Context, action: String): Intent =
            Intent(ctx, TelemetryService::class.java).setAction(action)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // LAN
    @Volatile private var receiving = false
    @Volatile private var lanTx = false
    private var rxSocket: DatagramSocket? = null
    private var lanName = "Android"

    // Cloud
    private var room: Room? = null
    private var myIdentity = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                intent.getStringExtra("name")?.takeIf { it.isNotBlank() }?.let { lanName = it }
                startLanRx()
                LanDiscovery.start(lanName, { batteryPct() }, { SessionState.lanRoom.value })
                refreshNotif()
            }
            ACTION_SET_NAME -> {
                intent.getStringExtra("name")?.let {
                    lanName = it.ifBlank { "Android" }
                    LanDiscovery.stop()
                    LanDiscovery.start(lanName, { batteryPct() }, { SessionState.lanRoom.value })
                }
            }
            ACTION_LAN_ROOM -> {
                SessionState.lanRoom.value = intent.getStringExtra("room").orEmpty().trim().take(64)
                SessionState.lanStatus.value = if (SessionState.lanRoom.value.isEmpty()) {
                    "Ready. Enter peer IP, hold to talk."
                } else {
                    "Room '${SessionState.lanRoom.value}'. Hold to talk."
                }
                refreshNotif()
            }
            ACTION_LAN_DOWN -> {
                startLanTalk(
                    intent.getStringExtra("peer").orEmpty(),
                    intent.getStringExtra("room").orEmpty()
                )
            }
            ACTION_LAN_UP -> stopLanTalk()
            ACTION_LAN_TEXT -> {
                sendLanText(
                    intent.getStringExtra("peer").orEmpty(),
                    intent.getStringExtra("room").orEmpty(),
                    intent.getStringExtra("text").orEmpty()
                )
            }
            ACTION_CLOUD_TEXT -> {
                sendCloudText(intent.getStringExtra("text").orEmpty())
            }
            ACTION_CLOUD_JOIN -> {
                cloudJoin(
                    intent.getStringExtra("url").orEmpty(),
                    intent.getStringExtra("worker").orEmpty(),
                    intent.getStringExtra("room").orEmpty(),
                    intent.getStringExtra("name").orEmpty()
                )
            }
            ACTION_CLOUD_LEAVE -> cloudLeave()
            ACTION_CLOUD_TALK -> setMicTalking(intent.getBooleanExtra("talk", false))
            ACTION_STOP -> {
                cloudLeave(silent = true)
                stopLanTalk()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        receiving = false
        lanTx = false
        try { rxSocket?.close() } catch (_: Exception) {}
        LanDiscovery.stop()
        scope.launch {
            try { room?.disconnect() } catch (_: Exception) {}
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun batteryPct(): Int {
        return try {
            getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Exception) { -1 }
    }

    /* ─── LAN ──────────────────────────────────────────── */

    private fun startLanRx() {
        if (receiving) return
        receiving = true
        Thread({
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 4)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()

                rxSocket = DatagramSocket(LAN_PORT).apply { broadcast = true }
                SessionState.lanStatus.value = "Ready. Enter peer IP, hold to talk."
                val buf = ByteArray(2048)
                while (receiving) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        rxSocket?.receive(pkt)
                        if (pkt.length > 0) {
                            // Sidecar text message?
                            val head = String(pkt.data, 0, minOf(pkt.length, 4), Charsets.UTF_8)
                            if ((head == "TXT|") && tryLanText(pkt)) continue
                            // Room-tagged voice? ("VOX|<room>|" + PCM)
                            val tagged = parseRoomVoice(pkt)
                            if (tagged != null) {
                                val (room, offset) = tagged
                                if (room == SessionState.lanRoom.value && room.isNotEmpty()) {
                                    track.write(pkt.data, offset, pkt.length - offset)
                                    SessionState.lanStatus.value =
                                        "Room '$room' · ${pkt.address.hostAddress}..."
                                }
                                continue
                            }
                            // Legacy untagged: direct dial.
                            track.write(pkt.data, 0, pkt.length)
                            SessionState.lanStatus.value =
                                "Receiving ${pkt.length}B from ${pkt.address.hostAddress}..."
                        }
                    } catch (e: Exception) {
                        if (!receiving) break
                    }
                }
                track.stop()
                track.release()
            } catch (e: Exception) {
                SessionState.lanStatus.value = "Listen failed: ${e.message}"
            }
        }, "ptt-rx").start()
    }

    /** Parse "VOX|<room>|" header. Returns (room, audioOffset) or null if untagged. */
    private fun parseRoomVoice(pkt: DatagramPacket): Pair<String, Int>? {
        return try {
            if (pkt.length < 6) return null
            val head = String(pkt.data, 0, minOf(pkt.length, 80), Charsets.UTF_8)
            if (!head.startsWith("VOX|")) return null
            val end = head.indexOf('|', 4)
            if (end < 0) return null
            val room = head.substring(4, end).take(64)
            if (room.isEmpty()) return null
            room to (end + 1)
        } catch (_: Exception) {
            null
        }
    }

    private fun startLanTalk(peerIp: String, room: String) {
        if (lanTx) return
        val roomMode = room.trim().take(64).isNotEmpty()
        val peer: InetAddress? = if (roomMode) {
            null
        } else {
            try {
                InetAddress.getByName(peerIp.trim())
            } catch (_: Exception) {
                SessionState.lanStatus.value = "Bad peer IP."
                return
            }
        }
        if (!roomMode && peer == null) {
            SessionState.lanStatus.value = "Bad peer IP."
            return
        }
        lanTx = true
        SessionState.lanTransmitting.value = true
        SessionState.lanStatus.value = if (roomMode) {
            "Talking -> room '${room.trim()}'..."
        } else {
            "Talking -> ${peerIp.trim()}..."
        }
        refreshNotif()

        Thread({
            var rec: AudioRecord? = null
            var sock: DatagramSocket? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
                sock = DatagramSocket().apply { broadcast = true }
                rec.startRecording()
                val buf = ByteArray(1024)
                val tag = if (roomMode) "VOX|${room.trim()}|".toByteArray() else null
                val dests = if (roomMode) LanDiscovery.broadcastAddrs() else emptyList()
                while (lanTx) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        if (roomMode && tag != null) {
                            val out = tag + buf.copyOf(n)
                            for (d in dests) {
                                try {
                                    sock.send(DatagramPacket(out, out.size, d, LAN_PORT))
                                } catch (_: Exception) { }
                            }
                        } else if (peer != null) {
                            sock.send(DatagramPacket(buf, n, peer, LAN_PORT))
                        }
                    }
                }
            } catch (e: Exception) {
                SessionState.lanStatus.value = "Talk failed: ${e.message}"
            } finally {
                try { rec?.stop() } catch (_: Exception) {}
                try { rec?.release() } catch (_: Exception) {}
                try { sock?.close() } catch (_: Exception) {}
            }
        }, "ptt-tx").start()
    }

    private fun stopLanTalk() {
        if (!lanTx) return
        lanTx = false
        SessionState.lanTransmitting.value = false
        SessionState.lanStatus.value = "Ready. Enter peer IP, hold to talk."
        refreshNotif()
    }

    /** Returns true if the packet was a text message (not voice). */
    private fun tryLanText(pkt: DatagramPacket): Boolean {
        return try {
            val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
            if (!msg.startsWith("TXT|")) return false
            val parts = msg.split("|", limit = 4)
            // New: TXT|<room>|<name>|<text>. Legacy: TXT|<name>|<text>.
            val (room, sender, text) = when {
                parts.size >= 4 -> Triple(parts[1], parts[2], parts[3])
                parts.size == 3 -> Triple("", parts[1], parts[2])
                else -> return false
            }
            val from = sender.ifBlank { pkt.address.hostAddress ?: "?" }
            val clean = text.trim().take(280)
            if (clean.isEmpty()) return true
            SessionState.lanTexts.value =
                (SessionState.lanTexts.value + TextMsg(from, clean, room = room)).takeLast(5)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sendLanText(peerIp: String, room: String, text: String) {
        val clean = text.trim().take(280)
        if (clean.isEmpty()) return
        val roomMode = room.trim().take(64).isNotEmpty()
        if (!roomMode && peerIp.isBlank()) return
        Thread({
            try {
                val payload =
                    "TXT|${room.trim().take(64)}|${lanName.replace("|", "/")}|$clean".toByteArray()
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    if (roomMode) {
                        for (d in LanDiscovery.broadcastAddrs()) {
                            try {
                                sock.send(DatagramPacket(payload, payload.size, d, LAN_PORT))
                            } catch (_: Exception) { }
                        }
                    } else {
                        val peer = InetAddress.getByName(peerIp.trim())
                        sock.send(DatagramPacket(payload, payload.size, peer, LAN_PORT))
                    }
                }
                SessionState.lanTexts.value =
                    (SessionState.lanTexts.value + TextMsg("You", clean, room = room.trim())).takeLast(5)
            } catch (e: Exception) {
                SessionState.lanStatus.value = "Text failed: ${e.message}"
            }
        }, "ptt-txt").start()
    }

    /* ─── Cloud ────────────────────────────────────────── */

    private fun cloudJoin(url: String, worker: String, roomName: String, name: String) {
        if (SessionState.cloudConnected.value) return
        SessionState.cloudError.value = null
        scope.launch(Dispatchers.IO) {
            try {
                val (token, serverUrl) = fetchToken(worker, roomName, name)
                val finalUrl = serverUrl?.let { normalizeUrl(it) } ?: normalizeUrl(url)
                myIdentity = name
                val r = LiveKit.create(applicationContext)
                room = r
                launch { observeEvents(r) }
                r.connect(finalUrl, token)
                r.localParticipant?.setMicrophoneEnabled(false)
                SessionState.cloudConnected.value = true
                SessionState.cloudRoom.value = roomName
                setPeerList()
                refreshNotif()
            } catch (e: Exception) {
                SessionState.cloudError.value = "Join failed: ${e.message}"
            }
        }
    }

    private fun setPeerList() {
        val r = room ?: run {
            SessionState.cloudPeers.value = emptyList()
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
        SessionState.cloudPeers.value = list
    }

    private suspend fun observeEvents(r: Room) {
        r.events.events.collect { event ->
            when (event) {
                is RoomEvent.ParticipantConnected,
                is RoomEvent.ParticipantDisconnected -> setPeerList()
                is RoomEvent.ActiveSpeakersChanged -> {
                    SessionState.cloudSpeakers.value = event.speakers
                        .mapNotNull { sp ->
                            val id = sp.identity?.value ?: return@mapNotNull null
                            if (id == myIdentity) "me" else id
                        }
                        .filter { id ->
                            id == "me" || SessionState.cloudPeers.value.any { it.id == id }
                        }
                        .toSet()
                }
                is RoomEvent.ConnectionQualityChanged -> {
                    val id = event.participant.identity?.value ?: return@collect
                    val key = if (id == myIdentity) "me" else id
                    val cur = SessionState.cloudQuality.value.toMutableMap()
                    cur[key] = event.quality.name
                    SessionState.cloudQuality.value = cur
                }
                is RoomEvent.DataReceived -> {
                    val text = String(event.data, Charsets.UTF_8).removePrefix("CHAT|").trim().take(280)
                    if (text.isNotEmpty()) {
                        val id = event.participant?.identity?.value ?: "?"
                        val sender = SessionState.cloudPeers.value.firstOrNull { it.id == id }?.name ?: id
                        SessionState.cloudTexts.value =
                            (SessionState.cloudTexts.value + TextMsg(sender, text)).takeLast(5)
                    }
                }
                is RoomEvent.Disconnected -> {
                    SessionState.cloudConnected.value = false
                    SessionState.cloudTalking.value = false
                    SessionState.cloudSpeakers.value = emptySet()
                    SessionState.cloudPeers.value = emptyList()
                    SessionState.cloudQuality.value = emptyMap()
                    SessionState.cloudTexts.value = emptyList()
                    SessionState.cloudRoom.value = ""
                    SessionState.cloudGeneration.value += 1
                    room = null
                    refreshNotif()
                }
                else -> {}
            }
        }
    }

    private fun sendCloudText(text: String) {
        val clean = text.trim().take(280)
        val r = room ?: return
        if (!SessionState.cloudConnected.value || clean.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                r.localParticipant?.publishData("CHAT|$clean".toByteArray())
                SessionState.cloudTexts.value =
                    (SessionState.cloudTexts.value + TextMsg("You", clean)).takeLast(5)
            } catch (e: Exception) {
                SessionState.cloudError.value = "Text failed: ${e.message}"
            }
        }
    }

    private fun setMicTalking(want: Boolean) {        val r = room ?: return
        if (!SessionState.cloudConnected.value) return
        scope.launch {
            try {
                r.localParticipant?.setMicrophoneEnabled(want)
                SessionState.cloudTalking.value = want
                refreshNotif()
            } catch (e: Exception) {
                SessionState.cloudError.value = "Mic error: ${e.message}"
            }
        }
    }

    private fun cloudLeave(silent: Boolean = false) {
        if (!SessionState.cloudConnected.value && room == null) return
        SessionState.cloudConnected.value = false
        SessionState.cloudTalking.value = false
        SessionState.cloudSpeakers.value = emptySet()
        SessionState.cloudPeers.value = emptyList()
        SessionState.cloudQuality.value = emptyMap()
        SessionState.cloudTexts.value = emptyList()
        SessionState.cloudRoom.value = ""
        if (!silent) SessionState.cloudGeneration.value += 1
        scope.launch {
            try { room?.disconnect() } catch (_: Exception) {}
            room = null
        }
        refreshNotif()
    }

    private fun normalizeUrl(raw: String): String {
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

    /* ─── Notification ─────────────────────────────────── */

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CH_ID, "Telemetry voice", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotif(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, LiveKitActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            cmd(this, ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val line = when {
            SessionState.cloudTalking.value -> "● Talking · ${SessionState.cloudRoom.value}"
            SessionState.cloudConnected.value -> "Cloud · ${SessionState.cloudRoom.value}"
            SessionState.lanTransmitting.value -> "● Talking · LAN"
            else -> "LAN · Listening"
        }
        SessionState.notifLine.value = line
        return Notification.Builder(this, CH_ID)
            .setContentTitle("Telemetry")
            .setContentText(line)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }

    private fun refreshNotif() {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif())
        } catch (_: Exception) { }
    }
}
