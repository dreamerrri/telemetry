package com.example.lanptt.ui.talknet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lanptt.lan.LanPeer
import com.example.lanptt.service.TextMsg
import com.example.lanptt.ui.theme.LanPttTheme
import com.example.lanptt.ui.theme.TalkBg
import com.example.lanptt.ui.theme.TalkBorder
import com.example.lanptt.ui.theme.TalkCard
import com.example.lanptt.ui.theme.TalkCard2
import com.example.lanptt.ui.theme.TalkMint
import com.example.lanptt.ui.theme.TalkMuted
import com.example.lanptt.ui.theme.TalkText
import com.example.lanptt.ui.theme.TalkTextDim

/* ─── Talk states ───────────────────────────────────────── */

/** A talk surface can be in configure-first (Setup) or live-radio (Talk) state. */
enum class TalkState { Setup, Talk }

/* ─── Nav + transport ──────────────────────────────────── */

enum class MainTab { Direct, Rooms }
enum class Transport { LAN, CLOUD }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransportToggle(
    transport: Transport,
    onTransport: (Transport) -> Unit,
    modifier: Modifier = Modifier
) {
    // M3 Expressive segmented control: single-choice row, TalkNet mint active
    // state, expressive motion comes from the theme's motionScheme.
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        Transport.entries.forEachIndexed { i, t ->
            val sel = transport == t
            SegmentedButton(
                selected = sel,
                onClick = { onTransport(t) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = i,
                    count = Transport.entries.size
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = TalkMint,
                    activeContentColor = TalkBg,
                    inactiveContainerColor = TalkCard2,
                    inactiveContentColor = TalkTextDim
                ),
                border = SegmentedButtonDefaults.borderStroke(
                    color = TalkBorder
                )
            ) {
                Text(
                    t.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomNav(
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    // M3 Expressive NavigationBar: animated indicator pill + icon morph, mapped
    // onto the TalkNet palette (mint indicator, dim unselected, TalkNet surface).
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = TalkBg,
        contentColor = TalkText
    ) {
        MainTab.entries.forEach { t ->
            val sel = tab == t
            NavigationBarItem(
                selected = sel,
                onClick = { onTab(t) },
                icon = {
                    Icon(
                        imageVector = if (sel) {
                            if (t == MainTab.Direct) Icons.Filled.Home else Icons.Filled.People
                        } else {
                            if (t == MainTab.Direct) Icons.Outlined.Home else Icons.Outlined.People
                        },
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        t.name.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TalkBg,
                    selectedTextColor = TalkMint,
                    unselectedIconColor = TalkMuted,
                    unselectedTextColor = TalkMuted,
                    indicatorColor = TalkMint
                )
            )
        }
    }
}
/* ─── Channel model ─────────────────────────────────────── */

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

/* ─── Channel card (shared room row): M3 Expressive Card ─── */

@Composable
fun ChannelCard(
    ch: TalkChannel,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // M3 Expressive Card: built-in press indication + accessible click role,
    // TalkNet surface/border colors kept.
    Card(
        onClick = onTap,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = TalkCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, TalkBorder)
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(TalkCard2, RoundedCornerShape(16.dp))
                .border(1.dp, TalkBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = null,
                tint = if (ch.live) TalkMint else TalkMuted,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.name, color = TalkText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
/* ─── Shared room list (pinned + recents + word) ────────── */

@Composable
fun RoomListContent(
    channels: List<TalkChannel>,
    recents: List<TalkChannel>,
    freeWord: String,
    onFreeWord: (String) -> Unit,
    onJoinWord: () -> Unit,
    onTapChannel: (TalkChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(channels, key = { "pin-${it.id}" }) { ch ->
            ChannelCard(ch = ch, onTap = { onTapChannel(ch) })
        }
        if (recents.isNotEmpty()) {
            item(key = "recent-label") {
                MonoLabel("RECENT", modifier = Modifier.padding(top = 8.dp))
            }
            items(recents, key = { "recent-${it.id}" }) { ch ->
                ChannelCard(ch = ch, onTap = { onTapChannel(ch) })
            }
        }
        item(key = "free-word") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TalkCard)
                    .border(1.dp, TalkBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                MonoLabel("JOIN BY WORD")
                Spacer(Modifier.height(8.dp))
                TalkTextField(
                    value = freeWord,
                    onValueChange = onFreeWord,
                    placeholder = "e.g. night-shift",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                MonoLabel("Any word is a channel. Same word = same room.")
                Spacer(Modifier.height(8.dp))
                TalkPrimaryButton(
                    text = "JOIN",
                    onClick = onJoinWord,
                    enabled = freeWord.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

/* ─── HOME (cloud rooms content) ────────────────────────── */

@Composable
fun HomeScreen(
    channels: List<TalkChannel>,
    recents: List<TalkChannel>,
    freeWord: String,
    onFreeWord: (String) -> Unit,
    onJoinWord: () -> Unit,
    onTapChannel: (TalkChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    RoomListContent(
        channels = channels,
        recents = recents,
        freeWord = freeWord,
        onFreeWord = onFreeWord,
        onJoinWord = onJoinWord,
        onTapChannel = onTapChannel,
        modifier = modifier.fillMaxSize()
    )
}
/* ─── SETTINGS (configure-once) ─────────────────────────── */

@Composable
fun SettingsPage(
    name: String,
    onName: (String) -> Unit,
    volPtt: Boolean,
    onVolPtt: (Boolean) -> Unit,
    presetsText: String,
    onPresetsText: (String) -> Unit,
    url: String,
    onUrl: (String) -> Unit,
    worker: String,
    onWorker: (String) -> Unit,
    ownIp: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TalkBackButton(onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Settings", color = TalkText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                MonoLabel("Configure once · name, voice, quick texts, servers")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsGroup("PROFILE") {
                MonoLabel("DISPLAY NAME")
                Spacer(Modifier.height(6.dp))
                TalkTextField(
                    value = name,
                    onValueChange = onName,
                    placeholder = "e.g. Alex Torres",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                MonoLabel("Shown to peers & in rooms. Saved automatically.", color = TalkTextDim)
            }
            SettingsGroup("VOICE") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        MonoLabel("VOLUME-DOWN = PTT")
                        Text(
                            "Hold the volume-down key to transmit.",
                            color = TalkTextDim, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = volPtt,
                        onCheckedChange = onVolPtt,
                        thumbContent = if (volPtt) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) }
                        } else null,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = TalkMint,
                            checkedThumbColor = TalkBg,
                            checkedIconColor = TalkMint,
                            uncheckedTrackColor = TalkCard2,
                            uncheckedThumbColor = TalkMuted,
                            uncheckedBorderColor = TalkBorder
                        )
                    )
                }
            }
            SettingsGroup("QUICK TEXTS") {
                MonoLabel("ONE PER LINE · MAX 8")
                Spacer(Modifier.height(6.dp))
                TalkTextField(
                    value = presetsText,
                    onValueChange = onPresetsText,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SettingsGroup("CLOUD SERVER") {
                MonoLabel("LIVEKIT URL")
                Spacer(Modifier.height(6.dp))
                TalkTextField(
                    value = url,
                    onValueChange = onUrl,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                MonoLabel("TOKEN SERVER (WORKER)")
                Spacer(Modifier.height(6.dp))
                TalkTextField(
                    value = worker,
                    onValueChange = onWorker,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SettingsGroup("LAN") {
                MonoLabel("YOUR DEVICE IP")
                Spacer(Modifier.height(6.dp))
                MonoLabel(ownIp, color = TalkMint, fontSize = 14)
                Spacer(Modifier.height(4.dp))
                MonoLabel(
                    "Auto-detected. Peers on the same WiFi appear automatically.",
                    color = TalkTextDim
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TalkCard)
            .border(1.dp, TalkBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        MonoLabel(title, color = TalkMint)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
/* ─── DIRECT (LAN 1-to-1: setup, then TALK) ─────────────── */

@Composable
fun DirectDisabled(
    onSwitchToLan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MonoLabel("P2P NEEDS LAN")
        Spacer(Modifier.height(8.dp))
        Text(
            "Direct talk is device-to-device over WiFi. Switch transport up top to use it.",
            color = TalkTextDim,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        TalkPrimaryButton(
            text = "SWITCH TO LAN",
            onClick = onSwitchToLan,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun DirectPage(
    ownIp: String,
    lanName: String,
    onLanName: (String) -> Unit,
    peerIp: String,
    onPeerIp: (String) -> Unit,
    nearby: List<LanPeer>,
    onTalk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canTalk = peerIp.isNotBlank()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))
            MonoLabel("DISPLAY NAME")
            Spacer(Modifier.height(8.dp))
            TalkTextField(
                value = lanName,
                onValueChange = onLanName,
                placeholder = "e.g. Alex Carter",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoLabel("NEARBY")
                MonoLabel("YOU: $ownIp", color = TalkMint, fontSize = 10)
            }
            Spacer(Modifier.height(8.dp))
            if (nearby.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TalkCard)
                        .border(1.dp, TalkBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text("Searching for peers on your WiFi…", color = TalkTextDim, fontSize = 13.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    nearby.forEach { peer ->
                        val label = peer.name.ifBlank { peer.ip }
                        val selected = peerIp == peer.ip
                        Card(
                            onClick = { onPeerIp(peer.ip) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) TalkMint.copy(alpha = 0.1f) else TalkCard
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) TalkMint else TalkBorder
                            )
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
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
                                MonoLabel(
                                    peer.ip + (if (peer.battery >= 0) " · ${peer.battery}%" else ""),
                                    color = TalkTextDim
                                )
                            }
                            if (selected) PulsingDot()
                        }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            MonoLabel("PEER IP (MANUAL)")
            Spacer(Modifier.height(8.dp))
            TalkTextField(
                value = peerIp,
                onValueChange = onPeerIp,
                placeholder = "e.g. 192.168.1.42",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            MonoLabel(
                if (canTalk) "Radio opens with PTT pinned — works screen-off."
                else "Tap a nearby device or type an IP to open the radio.",
                color = if (canTalk) TalkMint else TalkTextDim
            )
            Spacer(Modifier.height(12.dp))
        }
        TalkPrimaryButton(
            text = if (canTalk) "Open Radio · $peerIp" else "Select a peer to talk",
            onClick = onTalk,
            enabled = canTalk,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
/* ─── JOIN (cloud — lean: name + room only) ─────────────── */

@Composable
fun JoinScreen(
    name: String,
    onName: (String) -> Unit,
    channels: List<TalkChannel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    freeRoom: String,
    onFreeRoom: (String) -> Unit,
    error: String?,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canJoin = name.isNotBlank() && (selectedId != null || freeRoom.isNotBlank())
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TalkBackButton(onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Join a Channel", color = TalkText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                MonoLabel("Name + pick a room · servers live in Settings")
            }
            Spacer(Modifier.weight(1f))
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
                TalkTextField(
                    value = name,
                    onValueChange = onName,
                    placeholder = "e.g. Alex Torres",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column {
                MonoLabel("OR TYPE A NEW ROOM")
                Spacer(Modifier.height(8.dp))
                TalkTextField(
                    value = freeRoom,
                    onValueChange = onFreeRoom,
                    placeholder = "e.g. night-shift",
                    modifier = Modifier.fillMaxWidth()
                )
                if (freeRoom.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    MonoLabel("Joining '${freeRoom.trim()}' — overrides selection above.")
                }
            }
            Column {
                MonoLabel("SELECT CHANNEL")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    channels.forEach { ch ->
                        val sel = selectedId == ch.id
                        Card(
                            onClick = { onSelect(ch.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (sel) TalkMint.copy(alpha = 0.1f) else TalkCard
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (sel) TalkMint else TalkBorder
                            )
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sel,
                                onClick = { onSelect(ch.id) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = TalkMint,
                                    unselectedColor = TalkMuted
                                )
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    ch.name,
                                    color = if (sel) TalkText else TalkTextDim,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                MonoLabel(
                                    if (ch.knownPeers.isEmpty()) "Tap to join"
                                    else "${ch.knownPeers.size} online" + if (ch.live) " · live" else ""
                                )
                            }
                            if (ch.knownPeers.isNotEmpty()) AvatarStack(ch.knownPeers, max = 2)
                        }
                        }
                    }
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
            TalkPrimaryButton(
                text = "Join Channel",
                onClick = onJoin,
                enabled = canJoin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                fontSize = 16.sp
            )
        }
    }
}
/* ─── TALK SURFACE (shared radio: Direct + Rooms) ───────── */
/* Header (identity + CHANGE) · calm stage · chat dock above a pinned PTT. */

@Composable
fun TalkSurface(
    identity: String,
    subtitle: String,
    peers: List<ChatPeer>,
    isSpeaking: (ChatPeer) -> Boolean,
    speaker: ChatPeer?,
    transmitting: Boolean,
    liveMode: Boolean,
    onToggleLive: (Boolean) -> Unit,
    presets: List<String>,
    onSendText: (String) -> Unit,
    texts: List<TextMsg>,
    transportLine: String = "Connected",
    onBack: () -> Unit,
    onChange: () -> Unit,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    // Small-screen fit: measure the window, reserve room for header + dock chrome +
    // PTT + status line, and give the chat feed only what's left (never pushing PTT offscreen).
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val feedMax = (maxHeight - 390.dp).coerceIn(96.dp, 220.dp)
        Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TalkBackButton(onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(identity, color = TalkText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                MonoLabel(subtitle)
            }
            TalkChangeButton(onClick = onChange)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TalkBorder)
        )

        // Calm stage — mostly an ear.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
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
                            MonoLabel("Transmitting…", color = TalkMint)
                        }
                        WaveformBars(active = true)
                    }
                } else {
                    Text(
                        text = if (transmitting) "Hold to talk…" else "Channel is quiet — hold to talk",
                        color = if (transmitting) TalkMint else TalkMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }

        ChatDock(
            expanded = sheetOpen,
            onToggle = { sheetOpen = it },
            texts = texts,
            presets = presets,
            onSendText = onSendText,
            feedMaxHeight = feedMax,
            liveMode = liveMode,
            onToggleLive = onToggleLive,
            peers = peers,
            isSpeaking = isSpeaking
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            RoundPttButton(transmitting = transmitting, enabled = true, onDown = onDown, onUp = onUp)
        }
        // M3 Expressive chat toggle: tonal toggle button morphing with the dock.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            com.example.lanptt.ui.talknet.TalkChatToggle(
                expanded = sheetOpen,
                onToggle = { sheetOpen = it },
                unread = texts.size
            )
        }
        MonoLabel(
            transportLine,
            color = if (transmitting) TalkMint else TalkMuted,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp, bottom = 8.dp)
        )
        }
    }
}
/* ─── Chat dock: peek + badge collapsed, feed docked above the PTT when open. ─── */

@Composable
private fun ChatDock(
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    texts: List<TextMsg>,
    presets: List<String>,
    onSendText: (String) -> Unit,
    feedMaxHeight: Dp = 220.dp,
    liveMode: Boolean,
    onToggleLive: (Boolean) -> Unit,
    peers: List<ChatPeer>,
    isSpeaking: (ChatPeer) -> Boolean
) {
    val listState = rememberLazyListState()
    var seen by rememberSaveable { mutableIntStateOf(0) }
    // at-bottom observed off the layout pass (docs: don't read layoutInfo directly in composition).
    val atBottom by produceState(initialValue = false, listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 &&
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) == info.totalItemsCount - 1
        }.collect { value = it }
    }
    val unread = (texts.size - seen).coerceAtLeast(0)

    // Opening the sheet always lands you on the latest message.
    LaunchedEffect(expanded) {
        if (expanded && texts.isNotEmpty()) listState.scrollToItem(texts.lastIndex)
    }
    // While open and already at the bottom, keep following new arrivals.
    LaunchedEffect(expanded, texts.size) {
        if (expanded && atBottom && texts.isNotEmpty()) listState.scrollToItem(texts.lastIndex)
    }
    // Reading = sheet open at the bottom; that is where the unread badge drains.
    LaunchedEffect(expanded, atBottom) {
        if (expanded && atBottom) seen = texts.size
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TalkCard)
            .border(1.dp, TalkBorder, RoundedCornerShape(16.dp))
            .animateContentSize()
    ) {
        // Handle row — always visible, tap toggles (M3 ripple + expand icon).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!expanded) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!expanded) {
                    if (texts.isNotEmpty()) {
                        val last = texts.last()
                        MonoLabel(last.sender.uppercase().take(24), color = TalkMint, fontSize = 10)
                        Text(last.text, color = TalkText, fontSize = 13.sp, maxLines = 1)
                    } else {
                        MonoLabel("CHAT", color = TalkTextDim)
                        Text("No messages yet — tap to open", color = TalkTextDim, fontSize = 12.sp)
                    }
                } else {
                    Text("Chat", color = TalkText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (unread > 0) {
                androidx.compose.material3.Badge(
                    containerColor = TalkMint,
                    contentColor = TalkBg
                ) { Text("$unread") }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = TalkMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TalkBorder)
            )
            if (peers.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(peers, key = { it.id }) { p ->
                        Column(
                            modifier = Modifier.width(52.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Avatar(peer = p, size = 34.dp, speaking = isSpeaking(p))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (p.id == "me") "You" else p.name.split(" ").firstOrNull() ?: p.name,
                                color = if (isSpeaking(p)) TalkMint else TalkTextDim,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TalkBorder)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = feedMaxHeight)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = feedMaxHeight),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(texts, key = { "${it.at}-${it.sender}-${it.text}" }) { m ->
                        val mine = m.sender == "You"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (mine) TalkMint.copy(alpha = 0.18f) else TalkCard2)
                                    .border(
                                        1.dp,
                                        if (mine) TalkMint.copy(alpha = 0.5f) else TalkBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                MonoLabel(m.sender.uppercase().take(24), color = TalkMint, fontSize = 10)
                                Text(m.text, color = TalkText, fontSize = 13.sp)
                            }
                        }
                    }
                }
                // "N new" pill: shown when scrolled up and messages arrive — tap to jump to latest.
                if (expanded && !atBottom && unread > 0) {
                    FilledTonalButton(
                        onClick = { listState.requestScrollToItem(texts.lastIndex) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(50.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = TalkMint,
                            contentColor = TalkBg
                        )
                    ) { Text("$unread new", fontWeight = FontWeight.Bold) }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TalkBorder)
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                // Free-text input — type anything, not just presets.
                var draft by rememberSaveable { mutableStateOf("") }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TalkTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = "Message…",
                        modifier = Modifier.weight(1f)
                    )
                    val canSend = draft.isNotBlank()
                    TalkSendButton(
                        enabled = canSend,
                        onClick = {
                            onSendText(draft.trim())
                            draft = ""
                        }
                    )
                }
                if (presets.isNotEmpty()) {
                    Box(Modifier.height(8.dp))
                    QuickTextRow(presets = presets, onSend = onSendText)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonoLabel("LIVE MONITOR")
                    Switch(
                        checked = liveMode,
                        onCheckedChange = onToggleLive,
                        thumbContent = if (liveMode) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) }
                        } else null,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = TalkMint,
                            checkedThumbColor = TalkBg,
                            checkedIconColor = TalkMint,
                            uncheckedTrackColor = TalkCard2,
                            uncheckedThumbColor = TalkMuted,
                            uncheckedBorderColor = TalkBorder
                        )
                    )
                }
            }
        }
    }
}
/* ─── Previews ──────────────────────────────────────────── */

private val MockPeers = listOf(
    MePeer,
    ChatPeer("u1", "Marcus Chen", "MC", Color(0xFF6C63FF)),
    ChatPeer("u2", "Leila Hassan", "LH", Color(0xFFFF6584)),
    ChatPeer("u3", "Tom Okafor", "TO", Color(0xFF43BCCD))
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E)
@Composable
fun HomePreview() {
    LanPttTheme(darkTheme = true) {
        HomeScreen(
            channels = listOf(
                TalkChannel("office", "Office", MockPeers.drop(1), live = true),
                TalkChannel("warehouse", "Warehouse")
            ),
            recents = listOf(TalkChannel("night-shift", "night-shift")),
            freeWord = "",
            onFreeWord = {},
            onJoinWord = {},
            onTapChannel = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E)
@Composable
fun JoinPreview() {
    LanPttTheme(darkTheme = true) {
        JoinScreen(
            name = "Alex", onName = {},
            channels = DefaultChannels, selectedId = "office", onSelect = {},
            freeRoom = "", onFreeRoom = {},
            error = null, onJoin = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E)
@Composable
fun DirectPreview() {
    LanPttTheme(darkTheme = true) {
        DirectPage(
            ownIp = "192.168.1.10",
            lanName = "Alex", onLanName = {},
            peerIp = "", onPeerIp = {},
            nearby = listOf(
                LanPeer("Ben", "192.168.1.42", System.currentTimeMillis(), 82, "")
            ),
            onTalk = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E)
@Composable
fun SettingsPreview() {
    LanPttTheme(darkTheme = true) {
        SettingsPage(
            name = "Alex", onName = {},
            volPtt = true, onVolPtt = {},
            presetsText = "OK\nOn my way", onPresetsText = {},
            url = "wss://demo.livekit.cloud", onUrl = {},
            worker = "https://demo.workers.dev", onWorker = {},
            ownIp = "192.168.1.10", onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E)
@Composable
fun TalkSurfacePreview() {
    LanPttTheme(darkTheme = true) {
        TalkSurface(
            identity = "Office",
            subtitle = "3 online · LAN",
            peers = MockPeers,
            isSpeaking = { it.id == "u2" },
            speaker = MockPeers[2],
            transmitting = false,
            liveMode = false,
            onToggleLive = {},
            presets = listOf("OK", "On my way"),
            onSendText = {},
            texts = listOf(
                TextMsg("Marcus", "Heading over now"),
                TextMsg("You", "Copy that")
            ),
            transportLine = "LAN · Office",
            onBack = {}, onChange = {}, onDown = {}, onUp = {}
        )
    }
}