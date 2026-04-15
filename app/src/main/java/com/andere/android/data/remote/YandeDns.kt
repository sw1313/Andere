package com.andere.android.data.remote

import okhttp3.Dns
import java.net.InetAddress

class YandeDns : Dns {
    @Volatile
    var enabled: Boolean = false

    private val hostMap = mapOf(
        "yande.re" to "198.251.89.183",
        "files.yande.re" to "198.251.89.183",
        "assets.yande.re" to "198.251.89.183",
    )

    override fun lookup(hostname: String): List<InetAddress> {
        if (enabled) {
            hostMap[hostname]?.let { ip ->
                return listOf(InetAddress.getByName(ip))
            }
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}
