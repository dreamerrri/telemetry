package com.example.lanptt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lanptt.ui.theme.LanPttTheme
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : ComponentActivity() {

    companion object {
        const val PORT = 50005
        const val SAMPLE_RATE = 16000
        const val REQ_AUDIO = 1001
    }

    private var ownIp by mutableStateOf("...")
    private var status by mutableStateOf("starting...")
    private var transmitting by mutableStateOf(false)

    @Volatile private var receiving = false
    private var rxSocket: DatagramSocket? = null
    private var rxThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ownIp = detectOwnIp()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
        }

        volumeControlStream = AudioManager.STREAM_MUSIC
        startReceiver()

        setContent {
            LanPttTheme {
                LanScreen(
                    ownIp = ownIp,
                    status = status,
                    transmitting = transmitting,
                    onPeerTalk = { peerIp -> startTalking(peerIp) },
                    onStopTalk = { stopTalking() },
                    onOpenCloud = { startActivity(Intent(this, LiveKitActivity::class.java)) }
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            status = if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                "Ready. Enter peer IP, hold to talk."
            } else {
                "Mic permission denied - cannot talk."
            }
        }
    }

    private fun detectOwnIp(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (nic in interfaces) {
                if (!nic.isUp || nic.isLoopback) continue
                for (addr in nic.inetAddresses) {
                    if (addr.isLoopbackAddress) continue
                    if (addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "?"
                    }
                }
            }
        } catch (_: Exception) { }
        return "?"
    }

    private fun startReceiver() {
        if (receiving) return
        receiving = true
        rxThread = Thread({
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

                rxSocket = DatagramSocket(PORT).apply { broadcast = true }
                runOnUiThread { status = "Ready. Enter peer IP, hold to talk." }
                val buf = ByteArray(2048)
                while (receiving) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        rxSocket?.receive(pkt)
                        if (pkt.length > 0) {
                            track.write(pkt.data, 0, pkt.length)
                            val from = pkt.address.hostAddress
                            runOnUiThread { status = "Receiving ${pkt.length}B from $from..." }
                        }
                    } catch (e: Exception) {
                        if (!receiving) break
                    }
                }
                track.stop()
                track.release()
            } catch (e: Exception) {
                runOnUiThread { status = "Listen failed: ${e.message}" }
            }
        }, "ptt-rx")
        rxThread?.start()
    }

    private fun startTalking(peerIp: String) {
        if (transmitting) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        if (peerIp.isBlank()) {
            status = "Enter peer IP first."
            return
        }
        val peer: InetAddress
        try {
            peer = InetAddress.getByName(peerIp.trim())
        } catch (_: Exception) {
            status = "Bad peer IP."
            return
        }
        transmitting = true
        status = "Talking -> ${peerIp.trim()}..."

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
                while (transmitting) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        val pkt = DatagramPacket(buf, n, peer, PORT)
                        sock.send(pkt)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status = "Talk failed: ${e.message}" }
            } finally {
                try { rec?.stop() } catch (_: Exception) {}
                try { rec?.release() } catch (_: Exception) {}
                try { sock?.close() } catch (_: Exception) {}
            }
        }, "ptt-tx").start()
    }

    private fun stopTalking() {
        if (!transmitting) return
        transmitting = false
        status = "Ready. Enter peer IP, hold to talk."
    }

    override fun onDestroy() {
        receiving = false
        transmitting = false
        try { rxSocket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LanScreen(
    ownIp: String,
    status: String,
    transmitting: Boolean,
    onPeerTalk: (String) -> Unit,
    onStopTalk: () -> Unit,
    onOpenCloud: () -> Unit
) {
    var peerIp by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text("LanPTT") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp)
        ) {
            Text("LAN Walkie-Talkie", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text("My IP: $ownIp", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text("Peer IP (other phone, same WiFi):", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = peerIp,
                onValueChange = { peerIp = it },
                placeholder = { Text("e.g. 192.168.1.42") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Tip: use the other phone's IP. Port ${MainActivity.PORT}.")
            Spacer(Modifier.height(16.dp))
            Text("Status: $status", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            // Press-and-hold PTT button.
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .pointerInteropFilter { event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                onPeerTalk(peerIp)
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                onStopTalk()
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                Text(if (transmitting) "TALKING... (release)" else "HOLD TO TALK")
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenCloud, modifier = Modifier.fillMaxWidth()) {
                Text("CLOUD MODE (OFFICE + REMOTE)")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LanScreenPreview() {
    LanPttTheme {
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
