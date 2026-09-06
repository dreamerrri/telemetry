package com.example.lanptt.ui.talknet

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lanptt.ui.theme.LanPttTheme
import com.example.lanptt.ui.theme.TalkBg
import com.example.lanptt.ui.theme.TalkBorder
import com.example.lanptt.ui.theme.TalkCard
import com.example.lanptt.ui.theme.TalkCard2
import com.example.lanptt.ui.theme.TalkMint
import com.example.lanptt.ui.theme.TalkMuted
import com.example.lanptt.ui.theme.TalkText
import com.example.lanptt.ui.theme.TalkTextDim

/* ─── Channel model ────────────────────────────────────── */

data class TalkChannel(
    val id: String,
    val name: String,
    val knownPeers: List<ChatPeer> = emptyList(),
    val live: Boolean = false
)

val DefaultChannels = listOf(
    TalkChannel("office", "Office"),
    TalkChannel("warehouse", "Warehouse"),
    TalkChannel("remote", "Remote Team"),
    TalkChannel("security", "Security")
)

/* ─── Radio glyph ──────────────────────────────────────── */

@Composable
fun RadioIcon(active: Boolean, modifier: Modifier = Modifier) {
    val c = if (active) TalkMint else TalkMuted
    Canvas(modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = c,
            topLeft = Offset(w * 0.3f, h * 0.15f),
            size = Size(w * 0.4f, h * 0.55f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 3f)
        )
        drawRect(color = c, topLeft = Offset(w * 0.425f, h * 0.05f), size = Size(w * 0.15f, h * 0.14f))
        drawCircle(color = c, radius = w * 0.075f, center = Offset(w * 0.5f, h * 0.5f))
        drawLine(c, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.9f), strokeWidth = 3f)
        drawLine(c, Offset(w * 0.4f, h * 0.9f), Offset(w * 0.6f, h * 0.9f), strokeWidth = 3f)
    }
}

/* ─── HOME ─────────────────────────────────────────────── */

@Composable
fun HomeScreen(
    channels: List<TalkChannel>,
    onJoin: () -> Unit,
    onTapChannel: (TalkChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TalkBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                MonoLabel("TALKNET", color = TalkMint, fontSize = 12)
                Text("Channels", color = TalkText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onJoin,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = TalkMint, contentColor = TalkBg)
            ) {
                Text("+ Join", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TalkBorder)
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(channels, key = { it.id }) { ch ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TalkCard)
                        .border(1.dp, TalkBorder, RoundedCornerShape(16.dp))
                        .clickable { onTapChannel(ch) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(TalkCard2, RoundedCornerShape(12.dp))
                            .border(1.dp, TalkBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        RadioIcon(active = ch.live)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.name, color = TalkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (ch.live) {
                                Spacer(Modifier.width(8.dp))
                                PulsingDot()
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row {
                            MonoLabel(
                                if (ch.knownPeers.isEmpty()) "Tap to join"
                                else "${ch.knownPeers.size} online",
                                color = TalkTextDim
                            )
                            if (ch.live) {
                                MonoLabel("  · live", color = TalkMint)
                            }
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (ch.knownPeers.isNotEmpty()) AvatarStack(ch.knownPeers)
                        if (ch.live) WaveformBars(active = true)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoLabel("Cloud · Connected", color = TalkMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(TalkMint, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("Online", color = TalkMint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/* ─── JOIN ─────────────────────────────────────────────── */

@Composable
fun JoinScreen(
    name: String,
    onName: (String) -> Unit,
    channels: List<TalkChannel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    url: String,
    onUrl: (String) -> Unit,
    worker: String,
    onWorker: (String) -> Unit,
    serverOpen: Boolean,
    onToggleServer: () -> Unit,
    error: String?,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canJoin = name.isNotBlank() && selectedId != null
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TalkBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TalkBackButton(onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Join a Channel", color = TalkText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                MonoLabel("Set your name & pick a channel")
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TalkBorder)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                MonoLabel("DISPLAY NAME")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    placeholder = { Text("e.g. Alex Torres", color = TalkMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column {
                MonoLabel("SELECT CHANNEL")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    channels.forEach { ch ->
                        val sel = selectedId == ch.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (sel) TalkMint.copy(alpha = 0.1f) else TalkCard
                                )
                                .border(
                                    1.dp,
                                    if (sel) TalkMint else TalkBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelect(ch.id) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .border(
                                        2.dp,
                                        if (sel) TalkMint else TalkMuted,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sel) Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(TalkMint, CircleShape)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    ch.name,
                                    color = if (sel) TalkText else TalkTextDim,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                MonoLabel(
                                    if (ch.knownPeers.isEmpty()) "Tap to preview"
                                    else "${ch.knownPeers.size} online" + if (ch.live) " · live" else ""
                                )
                            }
                            if (ch.knownPeers.isNotEmpty()) AvatarStack(ch.knownPeers, max = 2)
                        }
                    }
                }
            }
            Column {
                TextButton(onClick = onToggleServer) {
                    Text(
                        if (serverOpen) "Hide server settings" else "Server settings",
                        color = TalkTextDim,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                if (serverOpen) {
                    OutlinedTextField(
                        value = url, onValueChange = onUrl,
                        label = { Text("LiveKit URL", color = TalkMuted, fontSize = 12.sp) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = worker, onValueChange = onWorker,
                        label = { Text("Token server", color = TalkMuted, fontSize = 12.sp) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (error != null) {
                Text(error, color = Color(0xFFF87171), fontSize = 12.sp)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = onJoin,
                enabled = canJoin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TalkMint,
                    contentColor = TalkBg,
                    disabledContainerColor = TalkCard2,
                    disabledContentColor = TalkMuted
                )
            ) {
                Text(
                    "Join Channel",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

/* ─── ACTIVE ───────────────────────────────────────────── */

@Composable
fun ActiveScreen(
    channelName: String,
    peers: List<ChatPeer>,
    isSpeaking: (ChatPeer) -> Boolean,
    speaker: ChatPeer?,
    transmitting: Boolean,
    onBack: () -> Unit,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val anyoneSpeaking = peers.any(isSpeaking)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TalkBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TalkBackButton(onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channelName, color = TalkText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                MonoLabel("${peers.size} participants")
            }
            if (anyoneSpeaking) WaveformBars(active = true)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TalkBorder)
        )
        Column(modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)) {
            MonoLabel("IN CHANNEL")
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(peers, key = { it.id }) { p ->
                    val speaking = isSpeaking(p)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(58.dp)
                    ) {
                        Avatar(peer = p, size = 52.dp, speaking = speaking)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (p.id == "me") "You" else p.name.split(" ").firstOrNull() ?: p.name,
                            color = if (speaking) TalkMint else TalkTextDim,
                            fontSize = 12.sp,
                            fontWeight = if (speaking) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (speaker != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TalkCard)
                        .border(1.dp, TalkMint, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(peer = speaker, size = 48.dp, speaking = true)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(speaker.name, color = TalkText, fontWeight = FontWeight.SemiBold)
                        MonoLabel("Transmitting...", color = TalkMint)
                    }
                    WaveformBars(active = true)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF181B22))
                        .border(1.dp, TalkBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Channel is quiet — hold to talk",
                        color = TalkMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp)
        ) {
            RoundPttButton(
                transmitting = transmitting,
                enabled = true,
                onDown = onDown,
                onUp = onUp
            )
            Spacer(Modifier.height(16.dp))
            MonoLabel("Connected · Cloud")
        }
    }
}

/* ─── Previews ─────────────────────────────────────────── */

private val MockPeers = listOf(
    MePeer,
    ChatPeer("u1", "Marcus Chen", "MC", androidx.compose.ui.graphics.Color(0xFF6C63FF)),
    ChatPeer("u2", "Leila Hassan", "LH", androidx.compose.ui.graphics.Color(0xFFFF6584)),
    ChatPeer("u3", "Tom Okafor", "TO", androidx.compose.ui.graphics.Color(0xFF43BCCD))
)

@Preview(showBackground = true, backgroundColor = 0xFF0D0F13)
@Composable
fun HomePreview() {
    LanPttTheme(darkTheme = true) {
        HomeScreen(
            channels = listOf(
                TalkChannel("office", "Office", MockPeers.drop(1), live = true),
                TalkChannel("warehouse", "Warehouse")
            ),
            onJoin = {}, onTapChannel = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F13)
@Composable
fun JoinPreview() {
    LanPttTheme(darkTheme = true) {
        JoinScreen(
            name = "Alex", onName = {},
            channels = DefaultChannels, selectedId = "office", onSelect = {},
            url = "wss://demo.livekit.cloud", onUrl = {},
            worker = "https://demo.workers.dev", onWorker = {},
            serverOpen = false, onToggleServer = {},
            error = null, onJoin = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F13)
@Composable
fun ActivePreview() {
    LanPttTheme(darkTheme = true) {
        ActiveScreen(
            channelName = "Office",
            peers = MockPeers,
            isSpeaking = { it.id == "u2" },
            speaker = MockPeers[2],
            transmitting = false,
            onBack = {}, onDown = {}, onUp = {}
        )
    }
}
