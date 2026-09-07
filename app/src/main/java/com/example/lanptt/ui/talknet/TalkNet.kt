package com.example.lanptt.ui.talknet

import android.view.MotionEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.lanptt.ui.theme.TalkBg
import com.example.lanptt.ui.theme.TalkBorder
import com.example.lanptt.ui.theme.TalkCard
import com.example.lanptt.ui.theme.TalkMint
import com.example.lanptt.ui.theme.TalkMintBright
import com.example.lanptt.ui.theme.TalkMuted
import com.example.lanptt.ui.theme.TalkText
import com.example.lanptt.ui.theme.TalkTextDim
import kotlin.math.abs

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

/** ConnectionQuality name -> dot color. */
fun qualityColor(q: String?): Color? = when (q) {
    "EXCELLENT", "GOOD" -> TalkMint
    "POOR" -> Color(0xFFE9C46A)
    "LOST" -> Color(0xFFF87171)
    else -> null
}

/* ─── Quick texts ──────────────────────────────────────── */

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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFF252A3A))
                    .border(1.dp, Color(0xFF2A2E3D), RoundedCornerShape(50.dp))
                    .clickable { onSend(p) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(p, color = TalkText, fontSize = 13.sp)
            }
        }
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
                    .background(Color(0xFF2A2E3D), CircleShape)
                    .border(2.dp, TalkBg, CircleShape)
            ) {
                Text(text = "+$rest", color = TalkTextDim, fontSize = 11.sp)
            }
        }
    }
}

/* ─── Back button ──────────────────────────────────────── */

@Composable
fun TalkBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .background(Color(0xFF1E2230), CircleShape)
            .border(1.dp, Color(0xFF2A2E3D), CircleShape)
            .clickable(onClick = onBack)
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            drawLine(
                TalkText,
                Offset(w * 0.62f, h * 0.2f),
                Offset(w * 0.32f, h * 0.5f),
                strokeWidth = 4f, cap = StrokeCap.Round
            )
            drawLine(
                TalkText,
                Offset(w * 0.32f, h * 0.5f),
                Offset(w * 0.62f, h * 0.8f),
                strokeWidth = 4f, cap = StrokeCap.Round
            )
        }
    }
}

/* ─── Mic glyph ────────────────────────────────────────── */

@Composable
fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.32f, 0f),
            size = Size(w * 0.36f, h * 0.55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f, w * 0.18f)
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.3f),
            size = Size(w * 0.64f, h * 0.52f),
            style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
        )
        drawLine(
            color, Offset(w * 0.5f, h * 0.82f), Offset(w * 0.5f, h * 0.93f),
            strokeWidth = w * 0.07f, cap = StrokeCap.Round
        )
        drawLine(
            color, Offset(w * 0.38f, h * 0.93f), Offset(w * 0.62f, h * 0.93f),
            strokeWidth = w * 0.07f, cap = StrokeCap.Round
        )
    }
}

/* ─── Big round PTT button ─────────────────────────────── */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RoundPttButton(
    transmitting: Boolean,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(170.dp), contentAlignment = Alignment.Center) {
        if (transmitting) {
            val t = rememberInfiniteTransition(label = "ptt-wave")
            val s by t.animateFloat(
                1f, 1.6f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "s"
            )
            val a by t.animateFloat(
                0.5f, 0f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "a"
            )
            Box(
                Modifier
                    .size(112.dp)
                    .graphicsLayer { scaleX = s; scaleY = s }
                    .alpha(a)
                    .background(TalkMint.copy(alpha = 0.4f), CircleShape)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = if (transmitting) 0.95f else 1f
                    scaleY = if (transmitting) 0.95f else 1f
                }
                .shadow(20.dp, CircleShape, ambientColor = TalkMint.copy(alpha = 0.45f))
                .background(if (transmitting) TalkMintBright else TalkMint, CircleShape)
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
                },
            verticalArrangement = Arrangement.Center
        ) {
            MicGlyph(color = TalkBg)
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
