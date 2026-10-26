package com.namaapp

import android.content.ContentResolver
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

object ConversionHelper {
    /**
     * Lightweight text extraction for Office Open XML files.
     * This intentionally does not preserve original page layout, charts, images or formatting.
     */
    fun extractText(resolver: ContentResolver, uri: Uri, fileName: String): String {
        val lower = fileName.lowercase()
        val input = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("File tidak dapat dibuka")
        val entries = mutableMapOf<String, ByteArray>()
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (
                        entry.name == "word/document.xml" ||
                        entry.name == "xl/sharedStrings.xml" ||
                        entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") ||
                        entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")
                    )) {
                        val out = ByteArrayOutputStream()
                        zip.copyToEntry(out)
                        entries[entry.name] = out.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        return when {
            lower.endsWith(".docx") -> parseXmlText(entries["word/document.xml"])
            lower.endsWith(".xlsx") -> extractSpreadsheet(entries)
            lower.endsWith(".pptx") -> entries.keys
                .filter { it.startsWith("ppt/slides/slide") }
                .sortedBy { it.substringAfter("slide").substringBefore(".").toIntOrNull() ?: 0 }
                .joinToString("\n\n") { parseXmlText(entries[it]).trim() }
            lower.endsWith(".doc") || lower.endsWith(".xls") || lower.endsWith(".ppt") ->
                throw IllegalArgumentException("Format Office lama (.doc/.xls/.ppt) belum didukung. Simpan ulang sebagai .docx/.xlsx/.pptx.")
            else -> throw IllegalArgumentException("Format file tidak didukung")
        }.trim()
    }

    private fun ZipInputStream.copyToEntry(out: ByteArrayOutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count <= 0) break
            out.write(buffer, 0, count)
        }
    }

    private fun parseXmlText(bytes: ByteArray?): String {
        if (bytes == null) return ""
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        val result = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "p", "tr", "row", "a:p" -> if (result.isNotEmpty()) result.append('\n')
                        "tab" -> result.append('\t')
                        "br" -> result.append('\n')
                    }
                }
                XmlPullParser.TEXT -> result.append(parser.text)
            }
            event = parser.next()
        }
        return result.toString().replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
    }


    private fun extractSharedStrings(bytes: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        val values = mutableListOf<String>()
        var inItem = false
        var current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") {
                    inItem = true
                    current = StringBuilder()
                }
                XmlPullParser.TEXT -> if (inItem) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    values.add(current.toString())
                    inItem = false
                }
            }
            event = parser.next()
        }
        return values
    }

    private fun extractSpreadsheet(entries: Map<String, ByteArray>): String {
        val shared = entries["xl/sharedStrings.xml"]?.let { parseXmlText(it).split('\n').filter { s -> s.isNotBlank() } }
            ?: emptyList()
        val sheets = entries.keys.filter {
            it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml")
        }.sortedBy { it.substringAfter("sheet").substringBefore(".").toIntOrNull() ?: 0 }

        return sheets.mapIndexed { index, key ->
            val parser = Xml.newPullParser()
            parser.setInput(entries[key]!!.inputStream(), "UTF-8")
            val rows = mutableListOf<String>()
            var row = mutableListOf<String>()
            var inCell = false
            var cellType = ""
            var value = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> row = mutableListOf()
                        "c" -> { inCell = true; cellType = parser.getAttributeValue(null, "t") ?: ""; value = StringBuilder() }
                        "v", "t" -> if (inCell) value = StringBuilder()
                    }
                    XmlPullParser.TEXT -> if (inCell) value.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "v", "t" -> {
                            if (inCell && value.isNotEmpty()) {
                                val raw = value.toString()
                                val resolved = if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1) ?: raw else raw
                                if (resolved.isNotBlank()) row.add(resolved)
                            }
                        }
                        "c" -> inCell = false
                        "row" -> if (row.isNotEmpty()) rows.add(row.joinToString("  |  "))
                    }
                }
                event = parser.next()
            }
            "Sheet ${index + 1}\n" + rows.joinToString("\n")
        }.joinToString("\n\n")
    }

    fun createPdf(context: Context, title: String, content: String): ByteArray {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val maxWidth = pageWidth - margin * 2
        val lines = mutableListOf<String>()
        content.lines().forEach { paragraph ->
            if (paragraph.isBlank()) {
                lines.add("")
            } else {
                var current = ""
                paragraph.split(Regex("\\s+")).forEach { word ->
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                        lines.add(current)
                        current = word
                    } else current = candidate
                }
                if (current.isNotEmpty()) lines.add(current)
            }
        }

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 48f
        canvas.drawText(title.take(70), margin, y, titlePaint)
        y += 28f
        lines.forEach { line ->
            if (y > pageHeight - margin) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
            }
            canvas.drawText(line.take(180), margin, y, paint)
            y += if (line.isEmpty()) 8f else 16f
        }
        pdf.finishPage(page)
        val out = ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }
}
