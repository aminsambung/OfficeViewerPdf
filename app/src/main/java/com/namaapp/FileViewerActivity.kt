package com.namaapp

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileViewerActivity : Activity() {
    companion object {
        const val EXTRA_URI = "file_uri"
        const val EXTRA_NAME = "file_name"
        const val EXTRA_CONVERT = "convert_pdf"
    }

    private lateinit var documentText: String
    private lateinit var fileName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_viewer)

        fileName = intent.getStringExtra(EXTRA_NAME) ?: "dokumen"
        val uriString = intent.getStringExtra(EXTRA_URI)
        val uri = uriString?.let { Uri.parse(it) }
        findViewById<TextView>(R.id.txtFileName).text = fileName
        val output = findViewById<TextView>(R.id.txtDocument)
        val saveButton = findViewById<Button>(R.id.btnSavePdf)

        documentText = try {
            if (uri == null) "File tidak ditemukan." else
                ConversionHelper.extractText(contentResolver, uri, fileName)
        } catch (e: Exception) {
            "Tidak dapat membaca dokumen ini. Pastikan file berformat DOCX, XLSX, atau PPTX.\n\nDetail: ${e.message ?: "format tidak didukung"}"
        }
        output.text = documentText.ifBlank { "(Dokumen tidak memiliki teks yang dapat dibaca.)" }

        saveButton.setOnClickListener { savePdf() }
        if (intent.getBooleanExtra(EXTRA_CONVERT, false)) {
            saveButton.post { savePdf() }
        }
    }

    private fun savePdf() {
        if (!::documentText.isInitialized) return
        try {
            val pdfBytes = ConversionHelper.createPdf(this, fileName, documentText)
            val baseName = fileName.substringBeforeLast('.', fileName).ifBlank { "dokumen" }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = contentResolver
            val outputUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Tidak bisa membuat file PDF")
            resolver.openOutputStream(outputUri)?.use { it.write(pdfBytes) }
                ?: throw IllegalStateException("Tidak bisa menulis file PDF")
            AlertDialog.Builder(this)
                .setTitle("PDF berhasil dibuat")
                .setMessage("File disimpan di folder Download sebagai $baseName.pdf")
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuat PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
