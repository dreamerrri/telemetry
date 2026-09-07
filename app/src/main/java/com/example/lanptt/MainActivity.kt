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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lanptt.lan.LanDiscovery
import com.example.lanptt.lan.LanPeer
import com.example.lanptt.service.SessionState
import com.example.lanptt.service.TelemetryService
import com.example.lanptt.ui.talknet.Avatar
import com.example.lanptt.ui.talknet.ChatPeer
import com.example.lanptt.ui.talknet.IncomingTexts
import com.example.lanptt.ui.talknet.MonoLabel
import com.example.lanptt.ui.talknet.QuickTextRow
import com.example.lanptt.ui.talknet.RoundPttButton
import com.example.lanptt.ui.talknet.peerColorForName
import com.example.lanptt.ui.theme.LanPttTheme
import com.example.lanptt.ui.theme.TalkBg
import com.example.lanptt.ui.theme.TalkBorder
import com.example.lanptt.ui.theme.TalkCard
import com.example.lanptt.ui.theme.TalkMint
import com.example.lanptt.ui.theme.TalkMuted
import com.example.lanptt.ui.theme.TalkText
import com.example.lanptt.ui.theme.TalkTextDim

class MainActivity : ComponentActivity() {

    companion object {
        const val REQ_AUDIO = 1001
    }

    private var ownIp by mutableStateOf("...")
    private var lanPeer by mutableStateOf("")
    private var quickTexts by mutableStateOf(listOf("OK", "On my way", "Loud and clear"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ownIp = LanDiscovery.detectOwnIp()
        lanPeer = intent.getStringExtra("peerIp").orEmpty()
        quickTexts = loadQuickTexts()

        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), REQ_AUDIO)

        volumeControlStream = AudioManager.STREAM_MUSIC
        maybeAskBattery()

        // Audio lives in the foreground service (survives screen-off).
        startForegroundService(
            TelemetryService.cmd(this, TelemetryService.ACTION_START)
                .putExtra("name", lanName())
        )

        setContent {
            LanPttTheme(darkTheme = true) {
                val status by SessionState.lanStatus.collectAsState()
                val transmitting by SessionState.lanTransmitting.collectAsState()
                val nearby by LanDiscovery.peers.collectAsState()
                val texts by SessionState.lanTexts.collectAsState()
                LanScreen(
                    ownIp = ownIp,
                    status = status,
                    transmitting = transmitting,
                    initialPeerIp = lanPeer,
                    nearby = nearby.values.sortedBy { it.name.lowercase() },
                    onPeerTalk = { peerIp -> startTalking(peerIp) },
                    onStopTalk = { stopTalking() },
                    onPeerIpChange = { lanPeer = it },
                    presets = quickTexts,
                    onSendText = { sendText(it) },
                    texts = texts,
                    onOpenCloud = { startActivity(Intent(this, LiveKitActivity::class.java)) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ownIp = LanDiscovery.detectOwnIp()
        quickTexts = loadQuickTexts()
    }

    private fun loadQuickTexts(): List<String> {
        val raw = getSharedPreferences("lk", MODE_PRIVATE)
            .getString("quickTexts", "OK\nOn my way\nLoud and clear\nStand by\nYes\nNo").orEmpty()
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(8)
            .ifEmpty { listOf("OK") }
    }

    private fun lanName(): String =
        getSharedPreferences("lk", MODE_PRIVATE)
            .getString("lanName", Build.MODEL ?: "Android") ?: "Android"

    private fun volPttEnabled(): Boolean =
        getSharedPreferences("lk", MODE_PRIVATE).getBoolean("volPtt", true)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volPttEnabled() && lanPeer.isNotBlank()) {
            startTalking(lanPeer)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volPttEnabled()) {
            stopTalking()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun maybeAskBattery() {
        val prefs = getSharedPreferences("lk", MODE_PRIVATE)
        if (prefs.getBoolean("battAsked", false)) return
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        } catch (_: Exception) { }
        prefs.edit().putBoolean("battAsked", true).apply()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO &&
            permissions.contains(Manifest.permission.RECORD_AUDIO) &&
            (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)
        ) {
            SessionState.lanStatus.value = "Mic permission denied - cannot talk."
        }
    }

    private fun startTalking(peerIp: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        if (peerIp.isBlank()) {
            SessionState.lanStatus.value = "Enter peer IP first."
            return
        }
        startService(
            TelemetryService.cmd(this, TelemetryService.ACTION_LAN_DOWN)
                .putExtra("peer", peerIp.trim())
        )
    }

    private fun stopTalking() {
        startService(TelemetryService.cmd(this, TelemetryService.ACTION_LAN_UP))
    }

    private fun sendText(text: String) {
        if (lanPeer.isBlank()) {
            SessionState.lanStatus.value = "Pick a peer first."
            return
        }
        startService(
            TelemetryService.cmd(this, TelemetryService.ACTION_LAN_TEXT)
                .putExtra("peer", lanPeer.trim())
                .putExtra("text", text)
        )
    }
}

@Composable
fun LanScreen(
    ownIp: String,
    status: String,
    transmitting: Boolean,
    onPeerTalk: (String) -> Unit,
    onStopTalk: () -> Unit,
    onOpenCloud: () -> Unit,
    initialPeerIp: String = "",
    nearby: List<LanPeer> = emptyList(),
    onPeerIpChange: (String) -> Unit = {},
    presets: List<String> = emptyList(),
    onSendText: (String) -> Unit = {},
    texts: List<com.example.lanptt.service.TextMsg> = emptyList()
) {
    var peerIp by rememberSaveable(initialPeerIp) { mutableStateOf(initialPeerIp) }
    var liveMode by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TalkBg)
            .statusBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                MonoLabel("TELEMETRY", color = TalkMint, fontSize = 12)
                Text("LAN Direct", color = TalkText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            MonoLabel("My IP: $ownIp", color = TalkTextDim)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TalkBorder)
        )
        Spacer(Modifier.height(20.dp))
        MonoLabel("PEER IP (SAME WIFI)", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = peerIp,
            onValueChange = { peerIp = it; onPeerIpChange(it) },
            placeholder = { Text("e.g. 192.168.1.42", color = TalkMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        MonoLabel("Status: $status", color = TalkTextDim, modifier = Modifier.fillMaxWidth())
        if (texts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            IncomingTexts(msgs = texts, modifier = Modifier.fillMaxWidth())
        }
        if (nearby.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MonoLabel("NEARBY (${nearby.size})", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val dupes = nearby.groupBy { it.name }.filterValues { it.size > 1 }.keys
            nearby.forEach { peer ->
                val label = if (peer.name in dupes) {
                    "${peer.name} • ${peer.ip.substringAfterLast('.', peer.ip)}"
                } else peer.name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (peerIp == peer.ip) TalkMint.copy(alpha = 0.12f) else TalkCard
                        )
                        .border(
                            1.dp,
                            if (peerIp == peer.ip) TalkMint else TalkBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { peerIp = peer.ip; onPeerIpChange(peer.ip) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        peer = ChatPeer(
                            peer.ip, peer.name,
                            peer.name.split(" ").let {
                                if (it.size == 1) it[0].take(2).uppercase()
                                else (it[0].take(1) + it[1].take(1)).uppercase()
                            },
                            peerColorForName(peer.name)
                        ),
                        size = 36.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, color = TalkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val ageS = ((System.currentTimeMillis() - peer.lastSeen) / 1000).toInt()
                        val sub = buildString {
                            append(peer.ip)
                            if (peer.battery >= 0) {
                                append(" · ${peer.battery}%")
                            }
                            if (ageS > 3) append(" · ${ageS}s")
                        }
                        MonoLabel(
                            sub,
                            color = if (peer.battery in 0..19) androidx.compose.ui.graphics.Color(0xFFF87171) else TalkTextDim
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        if (presets.isNotEmpty()) {
            QuickTextRow(
                presets = presets,
                onSend = onSendText,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonoLabel("LIVE MONITOR")
            Switch(
                checked = liveMode,
                onCheckedChange = {
                    liveMode = it
                    if (it) onPeerTalk(peerIp) else onStopTalk()
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = TalkMint,
                    checkedThumbColor = TalkBg
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        RoundPttButton(
            transmitting = transmitting,
            enabled = true,
            onDown = { onPeerTalk(peerIp) },
            onUp = { if (!liveMode) onStopTalk() }
        )
        Spacer(Modifier.height(8.dp))
        MonoLabel(if (transmitting) "Transmitting..." else "Hold to talk · works screen-off", color = if (transmitting) TalkMint else TalkMuted)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onOpenCloud,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TalkMint, contentColor = TalkBg)
        ) {
            Text(
                "CLOUD CHANNELS",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F13)
@Composable
fun LanScreenPreview() {
    LanPttTheme(darkTheme = true) {
        LanScreen(
            ownIp = "192.168.1.10",
            status = "Ready. Enter peer IP, hold to talk.",
            transmitting = false,
            onPeerTalk = {},
            onStopTalk = {},
            onOpenCloud = {}
        )
    }
}
