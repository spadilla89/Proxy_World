package com.proxyworld.api

import com.proxyworld.model.ProxyEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object ProxyFetcher {
    private val client = OkHttpClient.Builder().build()

    // Fetch from Geonode public API. See: https://proxylist.geonode.com/api/proxy-list
    // countryCodes: list like ["US","CO"]
    suspend fun fetch(countryCodes: List<String>, protocols: List<String>, limit: Int = 100): List<ProxyEntry> =
        withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(countryCodes, protocols, limit)
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data") ?: return@withContext emptyList()
                    val list = mutableListOf<ProxyEntry>()
                    for (i in 0 until data.length()) {
                        val o = data.getJSONObject(i)
                        val ip = o.optString("ip")
                        val port = o.optInt("port", -1)
                        val protoArray = o.optJSONArray("protocols")
                        val proto = if (protoArray != null && protoArray.length()>0) protoArray.getString(0) else o.optString("protocol")
                        val country = o.optString("country")
                        if (ip.isNotEmpty() && port>0) {
                            list.add(ProxyEntry(ip, port, proto ?: "http", country))
                        }
                    }
                    list
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    private fun buildUrl(countryCodes: List<String>, protocols: List<String>, limit: Int): String {
        val base = "https://proxylist.geonode.com/api/proxy-list"
        val params = mutableListOf<String>()
        if (countryCodes.isNotEmpty()) params.add("country=" + countryCodes.joinToString(","))
        if (protocols.isNotEmpty()) params.add("protocols=" + protocols.joinToString(","))
        params.add("limit=$limit")
        params.add("page=1")
        return base + "?" + params.joinToString("&")
    }
}
