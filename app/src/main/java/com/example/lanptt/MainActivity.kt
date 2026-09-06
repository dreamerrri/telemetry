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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lanptt.ui.talknet.MonoLabel
import com.example.lanptt.ui.talknet.RoundPttButton
import com.example.lanptt.ui.theme.LanPttTheme
import com.example.lanptt.ui.theme.TalkBg
import com.example.lanptt.ui.theme.TalkBorder
import com.example.lanptt.ui.theme.TalkMint
import com.example.lanptt.ui.theme.TalkMuted
import com.example.lanptt.ui.theme.TalkText
import com.example.lanptt.ui.theme.TalkTextDim
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
                MonoLabel("TALKNET", color = TalkMint, fontSize = 12)
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
            onValueChange = { peerIp = it },
            placeholder = { Text("e.g. 192.168.1.42", color = TalkMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        MonoLabel("Status: $status", color = TalkTextDim, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        RoundPttButton(
            transmitting = transmitting,
            enabled = true,
            onDown = { onPeerTalk(peerIp) },
            onUp = { onStopTalk() }
        )
        Spacer(Modifier.height(8.dp))
        MonoLabel(if (transmitting) "Transmitting..." else "Hold to talk", color = if (transmitting) TalkMint else TalkMuted)
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
