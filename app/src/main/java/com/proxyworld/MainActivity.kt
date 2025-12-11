package com.proxyworld

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.proxyworld.api.ProxyFetcher
import com.proxyworld.checker.ProxyChecker
import com.proxyworld.model.ProxyEntry
import com.proxyworld.ui.ProxyAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val list = mutableListOf<ProxyEntry>()
    private lateinit var adapter: ProxyAdapter
    private var checkerJob: Job? = null
    private val progressFlow = MutableStateFlow(Triple(0,0,0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rView = findViewById<RecyclerView>(R.id.recycler)
        adapter = ProxyAdapter(list) { /* handled in adapter */ }
        rView.layoutManager = LinearLayoutManager(this)
        rView.adapter = adapter

        val eCountries = findViewById<EditText>(R.id.eCountries)
        val eProtocols = findViewById<EditText>(R.id.eProtocols)
        val bFetch = findViewById<Button>(R.id.bFetch)
        val bStart = findViewById<Button>(R.id.bStart)
        val bStop = findViewById<Button>(R.id.bStop)
        val eLimit = findViewById<EditText>(R.id.eLimit)
        val tStatus = findViewById<TextView>(R.id.tStatus)
        val pBar = findViewById<ProgressBar>(R.id.pBar)
        val bExport = findViewById<Button>(R.id.bExport)

        bFetch.setOnClickListener {
            val countries = eCountries.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val protocols = eProtocols.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val limit = eLimit.text.toString().toIntOrNull() ?: 50
            CoroutineScope(Dispatchers.Main).launch {
                tStatus.text = "Obteniendo proxies..."
                val fetched = ProxyFetcher.fetch(countries, protocols, limit)
                list.clear()
                list.addAll(fetched)
                adapter.notifyDataSetChanged()
                tStatus.text = "Obtenidos ${'$'}{list.size} proxies"
            }
        }

        bStart.setOnClickListener {
            if (list.isEmpty()) return@setOnClickListener
            val targetStr = EditText(this)
            targetStr.hint = "Número objetivo (vacío = todos)"
            AlertDialog.Builder(this)
                .setTitle("Configurar objetivo")
                .setView(targetStr)
                .setPositiveButton("Iniciar") { _, _ ->
                    val target = targetStr.text.toString().toIntOrNull()
                    startChecking(target)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        bStop.setOnClickListener {
            stopChecking()
        }

        bExport.setOnClickListener {
            val valids = list.filter { it.status.name == "VALID" }
            val text = valids.joinToString("\n") { "${'$'}{it.ip}:${'$'}{it.port}" }
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(intent, "Exportar proxies"))
        }

        CoroutineScope(Dispatchers.Main).launch {
            progressFlow.collectLatest { triple ->
                val (checked, total, valid) = triple
                pBar.max = if (total==0) 1 else total
                pBar.progress = checked
                tStatus.text = "Checked: ${'$'}checked / ${'$'}total — Valid: ${'$'}valid"
            }
        }
    }

    private fun startChecking(target: Int?) {
        stopChecking()
        val checker = ProxyChecker(timeoutMs = 6000, concurrent = 15)
        checkerJob = CoroutineScope(Dispatchers.Main).launch {
            val result = checker.checkAll(list, target) { checked, total, valid, entry ->
                progressFlow.value = Triple(checked, total, valid)
                adapter.notifyDataSetChanged()
            }
            // update UI after finished
            adapter.notifyDataSetChanged()
        }
    }

    private fun stopChecking() {
        checkerJob?.cancel()
        checkerJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopChecking()
    }
}
