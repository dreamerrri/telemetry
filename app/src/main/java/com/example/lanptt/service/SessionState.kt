package com.example.lanptt.service

import com.example.lanptt.ui.talknet.ChatPeer
import kotlinx.coroutines.flow.MutableStateFlow

/** One received quick text. room = "" means LAN direct (no room). */
data class TextMsg(val sender: String, val text: String, val at: Long = System.currentTimeMillis(), val room: String = "")

/**
 * Shared session state. The [TelemetryService] owns all audio/network and
 * writes these flows; Activities only collect them. Thread-safe by design.
 */
object SessionState {
    // LAN
    val lanStatus = MutableStateFlow("starting...")
    val lanTransmitting = MutableStateFlow(false)
    val lanTexts = MutableStateFlow<List<TextMsg>>(emptyList())
    /** My current LAN room ("": none — direct dial only). */
    val lanRoom = MutableStateFlow("")

    // Cloud
    val cloudConnected = MutableStateFlow(false)
    val cloudTalking = MutableStateFlow(false)
    val cloudPeers = MutableStateFlow<List<ChatPeer>>(emptyList())
    val cloudSpeakers = MutableStateFlow<Set<String>>(emptySet())
    val cloudQuality = MutableStateFlow<Map<String, String>>(emptyMap())
    val cloudTexts = MutableStateFlow<List<TextMsg>>(emptyList())
    val cloudError = MutableStateFlow<String?>(null)

    /** Bumped on every hard disconnect so UI can navigate home. */
    val cloudGeneration = MutableStateFlow(0)

    // Service meta
    val cloudRoom = MutableStateFlow("")
    val notifLine = MutableStateFlow("LAN · Listening")
}
