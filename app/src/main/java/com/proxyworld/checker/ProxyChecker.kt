package com.proxyworld.checker

import com.proxyworld.model.ProxyEntry
import com.proxyworld.model.Status
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.Semaphore
import kotlin.system.measureTimeMillis

class ProxyChecker(
    private val timeoutMs: Long = 6000,
    private val concurrent: Int = 20
) {
    private val semaphore = Semaphore(concurrent)

    suspend fun checkAll(
        list: List<ProxyEntry>,
        targetValidCount: Int? = null,
        onUpdate: (checked: Int, total: Int, valid: Int, entry: ProxyEntry?) -> Unit
    ): List<ProxyEntry> = coroutineScope {
        val valid = mutableListOf<ProxyEntry>()
        val total = list.size
        var checked = 0
        val jobs = mutableListOf<Job>()
        val scope = this
        val canceled = CompletableDeferred<Unit>()

        for (entry in list) {
            if (canceled.isCompleted) break
            semaphore.acquire()
            val job = scope.launch(Dispatchers.IO) {
                try {
                    val success = checkSingle(entry)
                    synchronized(valid) {
                        if (success) valid.add(entry)
                    }
                    checked++
                    onUpdate(checked, total, valid.size, entry)
                    if (targetValidCount != null && valid.size >= targetValidCount) {
                        canceled.complete(Unit)
                    }
                } finally {
                    semaphore.release()
                }
            }
            jobs.add(job)
            // if canceled, break loop
            if (canceled.isCompleted) break
        }
        // wait until all tasks complete or canceled
        try {
            if (targetValidCount != null) {
                // wait until either canceled or all jobs
                select<Unit> {
                    canceled.onAwait { }
                    launch { jobs.joinAll() }.apply { onJoin { } }
                }
            } else {
                jobs.joinAll()
            }
        } catch (_: CancellationException) {
        }

        // Cancel any remaining jobs if we hit goal
        if (canceled.isCompleted) jobs.forEach { if (it.isActive) it.cancel() }

        valid
    }

    private fun checkSingle(entry: ProxyEntry): Boolean {
        val proto = entry.protocol.lowercase()
        return try {
            val time = measureTimeMillis {
                when {
                    proto.startsWith("http") || proto == "https" -> checkHttp(entry)
                    proto.startsWith("socks") -> checkSocks(entry)
                    else -> checkHttp(entry)
                }
            }
            entry.latencyMs = time
            if (time > timeoutMs) {
                entry.status = Status.SLOW
                false
            } else {
                entry.status = Status.VALID
                true
            }
        } catch (e: Exception) {
            entry.status = Status.INVALID
            false
        }
    }

    private fun checkHttp(entry: ProxyEntry): Boolean {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(entry.ip, entry.port))
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        val req = Request.Builder().url("https://httpbin.org/ip").get().build()
        client.newCall(req).execute().use { resp ->
            return resp.isSuccessful
        }
    }

    private fun checkSocks(entry: ProxyEntry): Boolean {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(entry.ip, entry.port))
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        val req = Request.Builder().url("https://httpbin.org/ip").get().build()
        client.newCall(req).execute().use { resp ->
            return resp.isSuccessful
        }
    }
}
