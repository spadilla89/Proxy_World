package com.proxyworld.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.proxyworld.R
import com.proxyworld.model.ProxyEntry

class ProxyAdapter(
    private val items: MutableList<ProxyEntry>,
    private val onDelete: (ProxyEntry) -> Unit
) : RecyclerView.Adapter<ProxyAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tMain: TextView = view.findViewById(R.id.tMain)
        val tMeta: TextView = view.findViewById(R.id.tMeta)
        val bDel: ImageButton = view.findViewById(R.id.bDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_proxy, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.tMain.text = p.toString()
        holder.tMeta.text = "${'$'}{p.protocol.uppercase()} • ${'$'}{p.country ?: "-"} • ${'$'}{p.latencyMs ?: "--"}ms • ${'$'}{p.status}"
        holder.bDel.setOnClickListener {
            onDelete(p)
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun getItemCount(): Int = items.size
}
