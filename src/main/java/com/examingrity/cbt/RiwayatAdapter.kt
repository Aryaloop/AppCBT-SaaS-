package com.examingrity.cbt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.examingrity.cbt.network.RiwayatItem

class RiwayatAdapter(private val listRiwayat: List<RiwayatItem>) : RecyclerView.Adapter<RiwayatAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvJudul: TextView = view.findViewById(R.id.tvJudulUjianRiwayat)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusRiwayat)
        val tvNilai: TextView = view.findViewById(R.id.tvNilaiRiwayat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_riwayat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val riwayat = listRiwayat[position]
        holder.tvJudul.text = riwayat.judul_ujian
        holder.tvStatus.text = riwayat.status.uppercase()
        holder.tvNilai.text = riwayat.nilai_akhir ?: "Menunggu"
    }

    override fun getItemCount(): Int = listRiwayat.size
}