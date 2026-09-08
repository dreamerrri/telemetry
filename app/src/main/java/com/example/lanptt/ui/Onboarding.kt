package com.example.lanptt.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lanptt.ui.talknet.TalkPrimaryButton
import com.example.lanptt.ui.theme.TalkBg
import com.example.lanptt.ui.theme.TalkBorder
import com.example.lanptt.ui.theme.TalkCard
import com.example.lanptt.ui.theme.TalkMint
import com.example.lanptt.ui.theme.TalkMuted
import com.example.lanptt.ui.theme.TalkText
import com.example.lanptt.ui.theme.TalkTextDim

/** Steps of the first-launch onboarding flow. */
enum class OnboardingStep { Welcome, Permissions, Battery, Done }

private data class OnboardingPerm(
    val title: String,
    val reason: String
)

/**
 * One-time onboarding shown before the main app on the very first launch. Guides the
 * user through the permissions the app needs (explaining WHY before the system dialog),
 * then the battery-optimization exemption for background / screen-off operation.
 *
 * The host activity owns the actual system prompts (`requestPermissions(...)` and the
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent); this composable only wires
 * buttons to those callbacks and renders the progression.
 */
@Composable
fun OnboardingFlow(
    step: OnboardingStep,
    neededMic: Boolean,
    neededNotifications: Boolean,
    batteryExempt: Boolean,
    onStart: () -> Unit,
    onPermContinue: () -> Unit,
    onEnableBattery: () -> Unit,
    onSkipBattery: () -> Unit,
    onFinish: () -> Unit,
    onSkipAll: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TalkBg)
            .statusBarsPadding()
    ) {
        val total = 4
        val current = step.ordinal + 1
        // Top bar: brand + progress dots + step label.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Telemetry",
                color = TalkText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.weight(1f))
            (0 until total).forEach { i ->
                Box(
                    Modifier
                        .size(if (i == step.ordinal) 10.dp else 7.dp)
                        .background(if (i <= step.ordinal) TalkMint else TalkMuted, CircleShape)
                )
                Spacer(Modifier.width(5.dp))
            }
            Spacer(Modifier.width(2.dp))
            Text("$current / $total", color = TalkTextDim, fontSize = 12.sp)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(TalkBorder))

        // Scrollable body.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (step) {
                OnboardingStep.Welcome -> WelcomeBody()
                OnboardingStep.Permissions -> PermissionsBody(
                    neededMic = neededMic,
                    neededNotifications = neededNotifications
                )
                OnboardingStep.Battery -> BatteryBody(batteryExempt = batteryExempt)
                OnboardingStep.Done -> DoneBody()
            }
        }
// Bottom action button.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            when (step) {
                OnboardingStep.Welcome ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TalkPrimaryButton(
                            text = "Continue",
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Skip setup for now →",
                            color = TalkTextDim,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 4.dp)
                                .clickable(onClick = onSkipAll)
                        )
                    }
                OnboardingStep.Permissions -> TalkPrimaryButton(
                    text = "Continue",
                    onClick = onPermContinue,
                    modifier = Modifier.fillMaxWidth()
                )
                OnboardingStep.Battery ->
                    if (batteryExempt) {
                        TalkPrimaryButton(
                            text = "Continue",
                            onClick = onSkipBattery,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            TalkPrimaryButton(
                                text = "Allow in background",
                                onClick = onEnableBattery,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "or skip for now →",
                                color = TalkTextDim,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 4.dp)
                                    .clickable(onClick = onSkipBattery)
                            )
                        }
                    }
                OnboardingStep.Done -> TalkPrimaryButton(
                    text = "Get started",
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun Title(text: String, subtitle: String? = null) {
    Text(text, color = TalkText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) {
        Text(subtitle, color = TalkTextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PermRow(perm: OnboardingPerm) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TalkCard)
            .border(1.dp, TalkBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(TalkMint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("i", color = TalkBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(perm.title, color = TalkText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(perm.reason, color = TalkTextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun WelcomeBody() {
    Title(
        "Welcome to Telemetry",
        "A push-to-talk radio for your team — over WiFi / LAN between nearby phones, or over " +
            "the internet through a channel."
    )
    Text(
        "To work properly it needs a couple of permissions. We'll explain each one and only ask " +
            "for what's required — this takes less than a minute.",
        color = TalkTextDim,
        fontSize = 14.sp
    )
}

@Composable
private fun PermissionsBody(neededMic: Boolean, neededNotifications: Boolean) {
    Title("Permissions", "Here's why we need access — tap Continue when you're ready.")
    if (!neededMic && !neededNotifications) {
        Text(
            "You've already granted everything we need. Nothing more to do here.",
            color = TalkTextDim,
            fontSize = 14.sp
        )
    } else {
        if (neededMic) {
            PermRow(
                OnboardingPerm(
                    "Microphone",
                    "We need the Microphone to send your voice on the radio. " +
                        "Audio only goes to the channels you join."
                )
            )
        }
        if (neededNotifications) {
            PermRow(
                OnboardingPerm(
                    "Notifications",
                    "We need Notifications so incoming calls can still ring you " +
                        "while the app is in the background."
                )
            )
        }
        Text(
            "Tapping Continue opens the system prompt(s) for the permissions shown above.",
            color = TalkTextDim,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun BatteryBody(batteryExempt: Boolean) {
    Title("Keep talking with the screen off", "One more small thing.")
    if (batteryExempt) {
        Text(
            "Battery access is already granted — you're good to go!",
            color = TalkTextDim,
            fontSize = 14.sp
        )
    } else {
        Text(
            "Android may suspend power-hungry apps when the screen is off, which would cut off " +
                "incoming calls. We need to run in the background so you keep hearing the radio " +
                "even with the display off.",
            color = TalkTextDim,
            fontSize = 14.sp
        )
        Text(
            "Tap \u201cAllow in background\u201d to open the system battery-settings exemption for " +
                "Telemetry.",
            color = TalkTextDim,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun DoneBody() {
    Title("You're all set", "Everything's ready.")
    Text(
        "Mic, notifications and background access handled. Open the radio, tap a channel, and " +
            "hold to talk.",
        color = TalkTextDim,
        fontSize = 14.sp
    )
}