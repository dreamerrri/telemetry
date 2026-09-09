package com.example.lanptt.ui.talknet

import android.view.MotionEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.lanptt.ui.theme.TalkBg
import com.example.lanptt.ui.theme.TalkBorder
import com.example.lanptt.ui.theme.TalkCard
import com.example.lanptt.ui.theme.TalkCard2
import com.example.lanptt.ui.theme.TalkMint
import com.example.lanptt.ui.theme.TalkMintBright
import com.example.lanptt.ui.theme.TalkMuted
import com.example.lanptt.ui.theme.TalkText
import com.example.lanptt.ui.theme.TalkTextDim
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* ─── Data ─────────────────────────────────────────────── */

data class ChatPeer(
    val id: String,
    val name: String,
    val initials: String,
    val color: Color
)

private val PeerPalette = listOf(
    Color(0xFF6C63FF), Color(0xFFFF6584), Color(0xFF43BCCD), Color(0xFFF9844A),
    Color(0xFFA8DADC), Color(0xFFE9C46A), Color(0xFF264653)
)

fun peerColorFor(identity: String): Color =
    PeerPalette[abs(identity.hashCode()) % PeerPalette.size]

fun peerColorForName(name: String): Color = peerColorFor(name)

fun initialsFor(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    return if (parts.size == 1) parts[0].take(2).uppercase()
    else (parts[0].take(1) + parts[1].take(1)).uppercase()
}

val MePeer = ChatPeer("me", "You", "YO", TalkMint)

/* ─── Click without the ripple / "static" press flash ────── */

/**
 * [Modifier.clickable] without an [Indication] (ripple). The default Material
 * indication renders as a grainy "static" flash on tap on this dark theme, so
 * interactive surfaces here opt out of it — the press is still dispatched and
 * keyboard/screen-reader semantics are preserved.
 */
fun Modifier.quietClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = null,
    indication = null,
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick
)

/* ─── Primary filled button: M3 Expressive ─────────────── */

/**
 * Primary CTA: M3 Expressive FilledTonalButton in TalkNet mint. The component's
 * built-in press morph + state layer come from the theme's expressive
 * motionScheme (no custom ripple workarounds). Same contract as before
 * (text / onClick / enabled / modifier), TalkNet brand colors kept.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TalkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(28.dp),
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        shape = shape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = TalkMint,
            contentColor = TalkBg,
            disabledContainerColor = TalkCard2,
            disabledContentColor = TalkMuted
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

/**
 * Secondary action: M3 Expressive OutlinedButton mapped to the TalkNet palette.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TalkSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = TalkText,
            disabledContentColor = TalkMuted
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, TalkBorder)
    ) {
        Text(
            text.uppercase(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
    }
}

/* ─── Mono label (JetBrains Mono stand-in) ─────────────── */

@Composable
fun MonoLabel(
    text: String,
    color: Color = TalkMuted,
    fontSize: Int = 11,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.2.sp
    )
}

/* ─── Waveform bars ────────────────────────────────────── */

@Composable
fun WaveformBars(
    active: Boolean,
    color: Color = TalkMint,
    modifier: Modifier = Modifier
) {
    val heights = listOf(6, 10, 14, 10, 6)
    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            val t = rememberInfiniteTransition(label = "wave")
            val s by t.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "bar"
            )
            heights.forEach { h ->
                Box(
                    Modifier
                        .width(2.dp)
                        .height(h.dp)
                        .graphicsLayer { scaleY = s }
                        .background(color, RoundedCornerShape(1.dp))
                )
            }
        } else {
            heights.forEach { h ->
                Box(
                    Modifier
                        .width(2.dp)
                        .height((h * 0.3f).dp)
                        .background(color, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

/* ─── Pulsing dot ──────────────────────────────────────── */

@Composable
fun PulsingDot(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val ringScale by t.animateFloat(
        1f, 1.6f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "ringS"
    )
    val ringAlpha by t.animateFloat(
        0.7f, 0f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "ringA"
    )
    val dotAlpha by t.animateFloat(
        1f, 0.4f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "dotA"
    )
    Box(modifier = modifier.size(10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer { scaleX = ringScale; scaleY = ringScale }
                .alpha(ringAlpha)
                .background(TalkMint, CircleShape)
        )
        Box(
            Modifier
                .size(8.dp)
                .alpha(dotAlpha)
                .background(TalkMint, CircleShape)
        )
    }
}

/* ─── Avatar ───────────────────────────────────────────── */

@Composable
fun Avatar(
    peer: ChatPeer,
    size: Dp,
    speaking: Boolean = false,
    dot: Color? = null,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(peer.color, CircleShape)
            .then(if (speaking) Modifier.border(2.5.dp, TalkMint, CircleShape) else Modifier)
    ) {
        Text(
            text = peer.initials,
            color = Color.White,
            fontSize = (size.value * 0.33f).sp,
            fontWeight = FontWeight.SemiBold
        )
        if (dot != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.24f).dp)
                    .background(dot, CircleShape)
                    .border(1.5.dp, TalkBg, CircleShape)
            )
        }
    }
}

/* ─── Quick texts ──────────────────────────────────────── */

/**
 * Quick-text presets as M3 Expressive AssistChips in TalkNet colors.
 * Same contract (presets / onSend); chips bring the themed press
 * indication + accessible role for free.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickTextRow(
    presets: List<String>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(presets, key = { it }) { p ->
            AssistChip(
                onClick = { onSend(p) },
                label = { Text(p, fontSize = 13.sp) },
                shape = RoundedCornerShape(50.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = TalkCard2,
                    labelColor = TalkText
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, TalkBorder)
            )
        }
    }
}

/**
 * Single themed text field for the whole app (M3 Expressive shape + TalkNet
 * palette). Replaces the raw OutlinedTextFields scattered across settings /
 * direct / join / chat so focus, error and placeholder states match M3.
 */
@Composable
fun TalkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder?.let { { Text(it, color = TalkMuted) } },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TalkText,
            unfocusedTextColor = TalkText,
            focusedContainerColor = TalkCard,
            unfocusedContainerColor = TalkCard,
            focusedBorderColor = TalkMint,
            unfocusedBorderColor = TalkBorder,
            cursorColor = TalkMint,
            focusedPlaceholderColor = TalkMuted,
            unfocusedPlaceholderColor = TalkMuted
        ),
        modifier = modifier
    )
}

/**
 * Small "CHANGE" entry: M3 OutlinedButton with mono label in TalkNet mint.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TalkChangeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = TalkCard,
            contentColor = TalkMint
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, TalkBorder)
    ) {
        Text(
            "CHANGE",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Chat send action: M3 Expressive FilledIconButton with Material Send icon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TalkSendButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
            containerColor = TalkMint,
            contentColor = TalkBg,
            disabledContainerColor = TalkCard2,
            disabledContentColor = TalkMuted
        )
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send",
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun IncomingTexts(
    msgs: List<com.example.lanptt.service.TextMsg>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        msgs.takeLast(3).forEach { m ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TalkCard)
                    .border(1.dp, TalkBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                MonoLabel(m.sender.uppercase().take(24), color = TalkMint, fontSize = 10)
                Text(m.text, color = TalkText, fontSize = 13.sp)
            }
        }
    }
}

/* ─── Overlapping avatar stack ─────────────────────────── */

@Composable
fun AvatarStack(
    peers: List<ChatPeer>,
    max: Int = 3,
    avatarSize: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val shown = peers.take(max)
    val rest = peers.size - max
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { i, p ->
            Avatar(
                peer = p,
                size = avatarSize,
                modifier = Modifier
                    .zIndex((shown.size - i).toFloat())
                    .then(if (i == 0) Modifier else Modifier.offset(x = (-10).dp))
                    .border(2.dp, TalkBg, CircleShape)
            )
        }
        if (rest > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = (-10).dp)
                    .size(avatarSize)
                    .background(TalkBorder, CircleShape)
                    .border(2.dp, TalkBg, CircleShape)
            ) {
                Text(text = "+$rest", color = TalkTextDim, fontSize = 11.sp)
            }
        }
    }
}

/* ─── Back button: M3 Expressive ───────────────────────── */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TalkBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedIconButton(
        onClick = onBack,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        colors = androidx.compose.material3.IconButtonDefaults.outlinedIconButtonColors(
            containerColor = TalkCard,
            contentColor = TalkText
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, TalkBorder)
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(20.dp)
        )
    }
}

/* ─── Big round PTT button ─────────────────────────────── */

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundPttButton(
    transmitting: Boolean,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    // PTT must stay a custom press-and-hold surface (ACTION_DOWN/UP timing),
    // but the visuals now ride the M3 Expressive motionScheme: emphasized spring
    // scale morph on transmit, expressive-shaped glow, morphing container.
    val targetScale = if (transmitting) 1.18f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "ptt-scale"
    )
    val targetGlow = if (transmitting) 28.dp else 14.dp
    val glow by animateDpAsState(
        targetValue = targetGlow,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "ptt-glow"
    )
    val targetAlpha = if (transmitting) 0.55f else 0.3f
    val haloAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "ptt-halo"
    )
    val bg by animateColorAsState(
        targetValue = if (transmitting) TalkMintBright else TalkMint,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "ptt-bg"
    )
    Box(modifier = modifier.size(190.dp), contentAlignment = Alignment.Center) {
        // Soft expressive halo behind the button (always on, brighter when live).
        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .alpha(haloAlpha)
                .background(TalkMint.copy(alpha = 0.35f), CircleShape)
        )
        if (transmitting) {
            // Expanding transmit wave using the expressive spatial spring.
            val t = rememberInfiniteTransition(label = "ptt-wave")
            val s by t.animateFloat(
                1f, 1.6f,
                infiniteRepeatable(
                    tween(1200),
                    RepeatMode.Restart
                ),
                label = "s"
            )
            val a by t.animateFloat(
                0.5f, 0f,
                infiniteRepeatable(
                    tween(1200),
                    RepeatMode.Restart
                ),
                label = "a"
            )
            Box(
                Modifier
                    .size(128.dp)
                    .graphicsLayer { scaleX = s; scaleY = s }
                    .alpha(a)
                    .background(TalkMint.copy(alpha = 0.4f), CircleShape)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .size(128.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(glow, CircleShape, ambientColor = TalkMint.copy(alpha = 0.5f))
                .background(bg, CircleShape)
                .pointerInteropFilter { event ->
                    if (!enabled) return@pointerInteropFilter false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            onDown()
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            onUp()
                            true
                        }
                        else -> false
                    }
                }
                .semantics { role = Role.Button },
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = TalkBg,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = if (transmitting) "SENDING" else "HOLD",
                color = TalkBg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
/* ─── Settings button (real, accessible) ─────────────────── */

/**
 * M3 Expressive settings entry: FilledTonalIconButton with a Material Settings
 * icon in TalkNet colors. Keeps Role.Button semantics (built into IconButton)
 * and the caller's onClick contract.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = TalkCard2,
            contentColor = TalkText
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
            modifier = Modifier.size(22.dp)
        )
    }
}

/* ─── Expressive loading + status ────────────────────────── */

/**
 * M3 Expressive indeterminate LoadingIndicator mapped to TalkNet mint.
 * Used for joining/connecting states (replaces hand-rolled spinners).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TalkLoadingIndicator(modifier: Modifier = Modifier) {
    LoadingIndicator(
        modifier = modifier.size(72.dp),
        color = TalkMint
    )
}

/**
 * Compact contained variant for inline rows (peer list refresh, roster sync).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TalkInlineLoadingIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    ContainedLoadingIndicator(
        progress = progress,
        modifier = modifier.size(56.dp),
        containerColor = TalkCard2,
        indicatorColor = TalkMint,
    )
}

/* ─── Expressive chat toggle (ToggleButton, morphs with dock) ── */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TalkChatToggle(
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    unread: Int,
    modifier: Modifier = Modifier
) {
    // M3 Expressive ToggleButton (filled-tonal, medium): checked morph + icon,
    // TalkNet mint when the dock is open / unread present.
    androidx.compose.material3.FilledTonalToggleButton(
        checked = expanded,
        onCheckedChange = onToggle,
        modifier = modifier,
        buttonSize = ToggleButtonSize.Medium,
        colors = androidx.compose.material3.FilledTonalToggleButtonDefaults.colors(
            containerColor = TalkCard2,
            contentColor = TalkText,
            disabledContainerColor = TalkCard2,
            disabledContentColor = TalkMuted,
            checkedContainerColor = TalkMint,
            checkedContentColor = TalkBg
        ),
        shapes = androidx.compose.material3.ToggleButtonShapes(
            shape = RoundedCornerShape(20.dp),
            pressedShape = RoundedCornerShape(12.dp),
            checkedShape = RoundedCornerShape(20.dp)
        )
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
            contentDescription = if (expanded) "Hide chat" else "Show chat",
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (unread > 0 && !expanded) "CHAT · $unread" else "CHAT",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}
