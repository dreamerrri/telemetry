package com.example.lanptt.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address

data class LanPeer(val name: String, val ip: String, val lastSeen: Long)

/**
 * LAN presence: UDP broadcast beacons (name + IP) + listener.
 * Foreground-only by contract: activities call start() in onResume
 * and stop() in onPause. Ref-counted so overlapping activities share it.
 */
object LanDiscovery {
    const val PORT = 50006
    const val BEACON_MS = 2000L
    const val EXPIRE_MS = 10_000L
    private const val PREFIX = "TLM1|"

    private val _peers = MutableStateFlow<Map<String, LanPeer>>(emptyMap())
    val peers: StateFlow<Map<String, LanPeer>> = _peers

    private var refs = 0
    @Volatile private var running = false
    private var beaconSock: DatagramSocket? = null
    private var listenSock: DatagramSocket? = null

    @Synchronized
    fun start(displayName: String) {
        refs++
        if (running) return
        running = true
        val name = displayName.trim().take(32).ifEmpty { android.os.Build.MODEL ?: "Android" }
        Thread({ beaconLoop(name) }, "lan-beacon").apply { isDaemon = true; start() }
        Thread({ listenLoop() }, "lan-listen").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        refs = maxOf(0, refs - 1)
        if (refs > 0) return
        running = false
        try { beaconSock?.close() } catch (_: Exception) {}
        try { listenSock?.close() } catch (_: Exception) {}
        beaconSock = null
        listenSock = null
    }

    fun sorted(): List<LanPeer> = _peers.value.values.sortedBy { it.name.lowercase() }

    fun detectOwnIp(): String {
        try {
            val ifs = java.net.NetworkInterface.getNetworkInterfaces()
            for (nic in ifs) {
                if (!nic.isUp || nic.isLoopback) continue
                for (addr in nic.inetAddresses) {
                    if (addr.isLoopbackAddress) continue
                    if (addr is Inet4Address) return addr.hostAddress ?: "?"
                }
            }
        } catch (_: Exception) { }
        return "?"
    }

    private fun beaconLoop(name: String) {
        try {
            val sock = DatagramSocket().apply { broadcast = true }
            beaconSock = sock
            val dest = InetAddress.getByName("255.255.255.255")
            val payload = (PREFIX + name).toByteArray()
            while (running) {
                try {
                    sock.send(DatagramPacket(payload, payload.size, dest, PORT))
                } catch (_: Exception) { }
                prune()
                try {
                    Thread.sleep(BEACON_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
            try { sock.close() } catch (_: Exception) {}
        } catch (_: Exception) { }
    }

    private fun listenLoop() {
        val sock: DatagramSocket
        try {
            sock = DatagramSocket(PORT).apply { broadcast = true }
            listenSock = sock
        } catch (_: Exception) {
            // Another instance already listens; shared flow still updates.
            return
        }
        val own = detectOwnIp()
        val buf = ByteArray(256)
        while (running) {
            try {
                val pkt = DatagramPacket(buf, buf.size)
                sock.receive(pkt)
                val ip = pkt.address?.hostAddress ?: continue
                if (ip == own) continue
                val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                if (!msg.startsWith(PREFIX)) continue
                val peerName = msg.removePrefix(PREFIX).trim().take(48).ifEmpty { continue }
                val now = System.currentTimeMillis()
                val cur = _peers.value.toMutableMap()
                cur[ip] = LanPeer(peerName, ip, now)
                _peers.value = cur
            } catch (_: Exception) {
                if (!running) break
            }
        }
        try { sock.close() } catch (_: Exception) {}
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - EXPIRE_MS
        val cur = _peers.value
        if (cur.values.any { it.lastSeen < cutoff }) {
            _peers.value = cur.filterValues { it.lastSeen >= cutoff }
        }
    }
}
