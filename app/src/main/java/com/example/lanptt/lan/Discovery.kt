package com.example.lanptt.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address

data class LanPeer(val name: String, val ip: String, val lastSeen: Long, val battery: Int = -1)

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
    fun start(displayName: String, battery: () -> Int = { -1 }) {
        refs++
        if (running) return
        running = true
        val name = displayName.trim().take(32).ifEmpty { android.os.Build.MODEL ?: "Android" }
        Thread({ beaconLoop(name, battery) }, "lan-beacon").apply { isDaemon = true; start() }
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

    /** Last own-IP seen by the beacon loop; used to flush stale peers on network switch. */
    @Volatile var lastOwnIp: String = ""
        private set

    private fun scoreIface(name: String): Int = when {
        // Hotspot hosting / tether interfaces first.
        name.startsWith("ap") || name.startsWith("rndis") || name.startsWith("ncm") -> 0
        // Regular WiFi / ethernet next.
        name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("p2p") -> 1
        // Mobile data last — never prefer these.
        name.startsWith("rmnet") || name.startsWith("ccmni")
            || name.startsWith("qmap") || name.startsWith("dummy") -> 99
        else -> 50
    }

    /** Best own IPv4, preferring hotspot/WiFi interfaces over mobile data. */
    fun detectOwnIp(): String {
        try {
            var fallback: String? = null
            var bestScore = 100
            val ifs = java.net.NetworkInterface.getNetworkInterfaces()
            for (nic in ifs) {
                if (!nic.isUp || nic.isLoopback) continue
                for (addr in nic.inetAddresses) {
                    if (addr.isLoopbackAddress) continue
                    if (addr !is Inet4Address) continue
                    val ip = addr.hostAddress ?: continue
                    val score = scoreIface(nic.name)
                    if (score < bestScore) {
                        bestScore = score
                        fallback = ip
                        if (score == 0) return ip
                    }
                }
            }
            if (fallback != null) return fallback
        } catch (_: Exception) { }
        return "?"
    }

    /** Broadcast address per eligible interface (for multi-homed phones). */
    private fun broadcastAddrs(): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        try {
            val ifs = java.net.NetworkInterface.getNetworkInterfaces()
            for (nic in ifs) {
                if (!nic.isUp || nic.isLoopback) continue
                if (scoreIface(nic.name) >= 99) continue
                for (ia in nic.interfaceAddresses) {
                    val b = ia.broadcast ?: continue
                    if (b is Inet4Address && b !in out) out += b
                }
            }
        } catch (_: Exception) { }
        if (out.isEmpty()) {
            try {
                out += InetAddress.getByName("255.255.255.255")
            } catch (_: Exception) { }
        }
        return out
    }

    private fun beaconLoop(name: String, battery: () -> Int) {
        try {
            val sock = DatagramSocket().apply { broadcast = true }
            beaconSock = sock
            lastOwnIp = detectOwnIp()
            while (running) {
                try {
                    val clean = name.replace("|", "/")
                    val payload = "$PREFIX$clean|${battery()}".toByteArray()
                    for (dest in broadcastAddrs()) {
                        try {
                            sock.send(DatagramPacket(payload, payload.size, dest, PORT))
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
                // Network switched (WiFi <-> hotspot): drop stale peers.
                val nowOwn = detectOwnIp()
                if (nowOwn != lastOwnIp) {
                    lastOwnIp = nowOwn
                    _peers.value = emptyMap()
                }
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
        val buf = ByteArray(256)
        while (running) {
            try {
                val pkt = DatagramPacket(buf, buf.size)
                sock.receive(pkt)
                val ip = pkt.address?.hostAddress ?: continue
                if (ip == detectOwnIp()) continue
                val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                if (!msg.startsWith(PREFIX)) continue
                val parts = msg.removePrefix(PREFIX).split("|", limit = 3)
                val peerName = parts.getOrNull(0)?.trim()?.take(48)?.ifEmpty { continue } ?: continue
                val batt = parts.getOrNull(1)?.toIntOrNull() ?: -1
                val now = System.currentTimeMillis()
                val cur = _peers.value.toMutableMap()
                cur[ip] = LanPeer(peerName, ip, now, batt)
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
