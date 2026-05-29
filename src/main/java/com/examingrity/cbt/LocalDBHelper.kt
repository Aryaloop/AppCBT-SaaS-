package com.examingrity.cbt

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalDBHelper(context: Context) : SQLiteOpenHelper(context, "examingrity_cbt.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        // 🚀 UPGRADE: Tabel sekarang mendukung kolom file_path
        db.execSQL("""
            CREATE TABLE jawaban_lokal (
                participant_id INTEGER, 
                soal_id INTEGER, 
                jawaban TEXT, 
                file_path TEXT, 
                PRIMARY KEY(participant_id, soal_id)
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS jawaban_lokal")
        onCreate(db)
    }

    // ==============================================================
    // 🛡️ FUNGSI LAMA (DIPERTAHANKAN AGAR ExamViewModel.kt TIDAK ERROR)
    // ==============================================================

    // Fungsi ini dipanggil oleh soal PG biasa
    fun simpanJawaban(participantId: Int, soalId: Int, jawaban: String) {
        // Alihkan ke fungsi baru, isi path foto dengan 'null' karena PG tidak butuh foto
        simpanJawabanLengkap(participantId, soalId, jawaban, null)
    }

    // Fungsi ini dipanggil saat Recovery awal
    fun getSemuaJawaban(participantId: Int): MutableMap<Int, String> {
        return getSemuaJawabanTeks(participantId)
    }

    // ==============================================================
    // 🚀 FUNGSI BARU UNTUK MENDUKUNG ESAI & FOTO JAWABAN
    // ==============================================================

    // Fungsi menyimpan teks jawaban sekaligus path foto lokal
    fun simpanJawabanLengkap(participantId: Int, soalId: Int, jawaban: String, filePath: String?) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("participant_id", participantId)
            put("soal_id", soalId)
            put("jawaban", jawaban)
            put("file_path", filePath)
        }
        db.insertWithOnConflict("jawaban_lokal", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // Mengambil seluruh data teks esai/PG
    fun getSemuaJawabanTeks(participantId: Int): MutableMap<Int, String> {
        val db = this.readableDatabase
        val map = mutableMapOf<Int, String>()
        val cursor = db.rawQuery("SELECT soal_id, jawaban FROM jawaban_lokal WHERE participant_id = ?", arrayOf(participantId.toString()))
        if (cursor.moveToFirst()) {
            do {
                map[cursor.getInt(0)] = cursor.getString(1) ?: ""
            } while (cursor.moveToNext())
        }
        cursor.close()
        return map
    }

    // Mengambil seluruh data path foto lokal untuk dipulihkan ke UI preview
    fun getSemuaPathFoto(participantId: Int): MutableMap<Int, String> {
        val db = this.readableDatabase
        val map = mutableMapOf<Int, String>()
        val cursor = db.rawQuery("SELECT soal_id, file_path FROM jawaban_lokal WHERE participant_id = ? AND file_path IS NOT NULL", arrayOf(participantId.toString()))
        if (cursor.moveToFirst()) {
            do {
                map[cursor.getInt(0)] = cursor.getString(1) ?: ""
            } while (cursor.moveToNext())
        }
        cursor.close()
        return map
    }

    // Membersihkan memori HP setelah ujian sukses dikumpulkan
    fun hapusSesi(participantId: Int) {
        val db = this.writableDatabase
        db.delete("jawaban_lokal", "participant_id = ?", arrayOf(participantId.toString()))
    }
}