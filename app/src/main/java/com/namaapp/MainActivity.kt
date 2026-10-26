package com.namaapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private var selectedUri: Uri? = null
    private var selectedName: String = ""
    private lateinit var selectedText: TextView
    private lateinit var viewButton: Button
    private lateinit var convertButton: Button

    // Dideklarasikan dengan lateinit agar bisa dipanggil di onCreate
    private lateinit var pickFile: androidx.activity.result.ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectedText = findViewById(R.id.txtSelected)
        viewButton = findViewById(R.id.btnView)
        convertButton = findViewById(R.id.btnConvert)

        // Inisialisasi pickFile di dalam onCreate
        pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
                selectedUri = uri
                selectedName = getDisplayName(uri) ?: "dokumen"
                selectedText.text = selectedName
                viewButton.isEnabled = true
                convertButton.isEnabled = true
            }
        }

        findViewById<Button>(R.id.btnChoose).setOnClickListener {
            pickFile.launch(arrayOf(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/msword", "application/vnd.ms-excel",
                "application/vnd.ms-powerpoint", "application/octet-stream"
            ))
        }

        viewButton.setOnClickListener { openViewer(false) }
        convertButton.setOnClickListener { openViewer(true) }
    }

    private fun openViewer(convert: Boolean) {
        val uri = selectedUri ?: return
        val intent = Intent(this, FileViewerActivity::class.java).apply {
            putExtra(FileViewerActivity.EXTRA_URI, uri.toString())
            putExtra(FileViewerActivity.EXTRA_NAME, selectedName)
            putExtra(FileViewerActivity.EXTRA_CONVERT, convert)
        }
        startActivity(intent)
    }

    private fun getDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        return uri.lastPathSegment
    }
}
