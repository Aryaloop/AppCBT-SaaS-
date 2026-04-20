package com.examingrity.cbt

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// 🛡️ FITUR UTAMA PENELITIAN: SQLite Database Helper untuk Fault Tolerance
class LocalDBHelper(context: Context) : SQLiteOpenHelper(context, "examingrity_cbt.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE jawaban_lokal (participant_id INTEGER, soal_id INTEGER, jawaban TEXT, PRIMARY KEY(participant_id, soal_id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS jawaban_lokal")
        onCreate(db)
    }

    // Fungsi: Simpan/Timpa jawaban saat siswa mengklik opsi
    fun simpanJawaban(participantId: Int, soalId: Int, jawaban: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("participant_id", participantId)
            put("soal_id", soalId)
            put("jawaban", jawaban)
        }
        db.insertWithOnConflict("jawaban_lokal", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // Fungsi: Tarik kembali semua jawaban saat aplikasi di-restart (Siswa masuk ulang)
    fun getSemuaJawaban(participantId: Int): MutableMap<Int, String> {
        val db = this.readableDatabase
        val map = mutableMapOf<Int, String>()
        val cursor = db.rawQuery("SELECT soal_id, jawaban FROM jawaban_lokal WHERE participant_id = ?", arrayOf(participantId.toString()))
        if (cursor.moveToFirst()) {
            do {
                map[cursor.getInt(0)] = cursor.getString(1)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return map
    }

    // Fungsi: Bersihkan memori HP setelah ujian sukses dikumpulkan
    fun hapusSesi(participantId: Int) {
        val db = this.writableDatabase
        db.delete("jawaban_lokal", "participant_id = ?", arrayOf(participantId.toString()))
    }
}