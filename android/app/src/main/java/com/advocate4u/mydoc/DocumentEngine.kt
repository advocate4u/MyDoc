package com.advocate4u.mydoc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

object DocumentEngine {
    fun readText(bytes: ByteArray, extension: String): String = when (extension.lowercase()) {
        "docx" -> readDocx(bytes)
        "txt", "csv" -> bytes.toString(Charsets.UTF_8)
        else -> ""
    }

    private fun readDocx(bytes: ByteArray): String {
        val zis = ZipInputStream(bytes.inputStream())
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xml = zis.readBytes().toString(Charsets.UTF_8)
                return xml.replace(Regex("</w:p>"), "\n")
                    .replace(Regex("<w:tab[^>]*/>"), "\t")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&apos;", "'")
            }
            entry = zis.nextEntry
        }
        return ""
    }

    fun writeDocx(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            add(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            add(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            val paragraphs = text.split('\n').joinToString("") { p -> "<w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(p)}</w:t></w:r></w:p>" }
            add(zip, "word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr/></w:body></w:document>""")
        }
        return out.toByteArray()
    }

    fun writePdf(context: Context, text: String, output: File) {
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f }
        val lines = text.split('\n')
        var pageNo = 1
        var index = 0
        while (index < lines.size || pageNo == 1) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            val canvas: Canvas = page.canvas
            var y = 50f
            while (index < lines.size && y < 800f) {
                canvas.drawText(lines[index].take(90), 40f, y, paint)
                y += 20f
                index++
            }
            pdf.finishPage(page)
            pageNo++
            if (index >= lines.size) break
        }
        FileOutputStream(output).use { pdf.writeTo(it) }
        pdf.close()
    }

    private fun add(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
