package com.example.lanptt

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : Activity() {

    companion object {
        const val PORT = 50005
        const val SAMPLE_RATE = 16000
        const val REQ_AUDIO = 1001
    }

    private lateinit var ownIpText: TextView
    private lateinit var peerIpInput: EditText
    private lateinit var statusText: TextView
    private lateinit var pttButton: Button

    @Volatile private var receiving = false
    @Volatile private var transmitting = false
    private var rxSocket: DatagramSocket? = null
    private var rxThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ownIpText = findViewById(R.id.ownIpText)
        peerIpInput = findViewById(R.id.peerIpInput)
        statusText = findViewById(R.id.statusText)
        pttButton = findViewById(R.id.pttButton)

        ownIpText.text = "My IP: ${getOwnIp()}"

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
        }

        volumeControlStream = AudioManager.STREAM_MUSIC

        pttButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startTalking()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopTalking()
                    true
                }
                else -> false
            }
        }

        startReceiver()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setStatus("Ready. Enter peer IP, hold to talk.")
            } else {
                setStatus("Mic permission denied - cannot talk.")
            }
        }
    }

    private fun getOwnIp(): String {
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

    private fun setStatus(msg: String) {
        runOnUiThread { statusText.text = "Status: $msg" }
    }

    private fun startReceiver() {
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
                runOnUiThread { statusText.text = "Status: Ready. Enter peer IP, hold to talk." }
                val buf = ByteArray(2048)
                while (receiving) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        rxSocket?.receive(pkt)
                        if (pkt.length > 0) {
                            track.write(pkt.data, 0, pkt.length)
                            setStatus("Receiving ${pkt.length}B from ${pkt.address.hostAddress}...")
                        }
                    } catch (e: Exception) {
                        if (!receiving) break
                    }
                }
                track.stop()
                track.release()
            } catch (e: Exception) {
                setStatus("Listen failed: ${e.message}")
            }
        }, "ptt-rx")
        rxThread?.start()
    }

    private fun startTalking() {
        if (transmitting) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        val peerIp = peerIpInput.text.toString().trim()
        if (peerIp.isEmpty()) {
            setStatus("Enter peer IP first.")
            return
        }
        val peer: InetAddress
        try {
            peer = InetAddress.getByName(peerIp)
        } catch (_: Exception) {
            setStatus("Bad peer IP.")
            return
        }
        transmitting = true
        setStatus("Talking -> $peerIp...")
        pttButton.text = "TALKING... (release)"

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
                setStatus("Talk failed: ${e.message}")
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
        pttButton.text = "HOLD TO TALK"
        setStatus("Ready. Enter peer IP, hold to talk.")
    }

    override fun onDestroy() {
        receiving = false
        transmitting = false
        try { rxSocket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
