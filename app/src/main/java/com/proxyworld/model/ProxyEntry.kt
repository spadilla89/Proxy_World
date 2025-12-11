package com.proxyworld.model

data class ProxyEntry(
    val ip: String,
    val port: Int,
    val protocol: String, // http, https, socks4, socks5
    val country: String? = null,
    var latencyMs: Long? = null,
    var status: Status = Status.UNKNOWN
){
    override fun toString(): String = "${'$'}ip:${'$'}port"
}

enum class Status { UNKNOWN, VALID, INVALID, SLOW }
