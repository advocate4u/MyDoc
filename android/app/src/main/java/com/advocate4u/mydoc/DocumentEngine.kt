package com.advocate4u.mydoc

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Format-aware, stateless document primitives. Heavy IO is dispatched by DocumentRepository. */
object DocumentEngine {
    private const val MAX_CACHE_ENTRIES = 8
    private const val MAX_TEXT_CACHE = 1_000_000
    private val textCache = object : LinkedHashMap<String, String>(MAX_CACHE_ENTRIES, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_CACHE_ENTRIES
    }

    @Synchronized private fun cached(key: String): String? = textCache[key]
    @Synchronized private fun cache(key: String, value: String) { textCache[key] = value }

    fun readText(input: InputStream, extension: String, cacheKey: String? = null): String {
        cacheKey?.let { cached(it)?.let { value -> return value } }
        val result = when (extension.lowercase()) {
            "docx" -> readDocx(input)
            "xlsx", "xlsm" -> readXlsx(input)
            "pptx" -> readPptx(input)
            "txt", "csv" -> input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            "pdf" -> "PDF opened. Text extraction/rendering is handled by the PDF module."
            else -> ""
        }
        if (cacheKey != null && result.length <= MAX_TEXT_CACHE) cache(cacheKey, result)
        return result
    }

    fun readText(bytes: ByteArray, extension: String): String = bytes.inputStream().use { readText(it, extension) }

    private fun readDocx(input: InputStream): String {
        ZipInputStream(input.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    return xml.replace(Regex("<w:tab[^>]*/>"), "\t")
                        .replace(Regex("</w:p>"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&quot;", "\"").replace("&apos;", "'")
                }
                entry = zis.nextEntry
            }
        }
        return ""
    }

    fun writeDocx(text: String, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            add(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            add(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            val rPr = buildString {
                if (bold) append("<w:b/>")
                if (italic) append("<w:i/>")
                if (underline) append("<w:u w:val=\"single\"/>")
            }
            val paragraphs = text.split('\n').joinToString("") { p -> "<w:p><w:r><w:rPr>$rPr</w:rPr><w:t xml:space=\"preserve\">${escapeXml(p)}</w:t></w:r></w:p>" }
            add(zip, "word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>""")
        }
        return out.toByteArray()
    }

    fun writeXlsx(cells: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            add(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            add(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            add(zip, "xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            add(zip, "xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            val rows = cells.mapIndexed { r, row ->
                val values = row.mapIndexed { c, value ->
                    val ref = "${('A'.code + c).toChar()}${r + 1}"
                    val escaped = escapeXml(value)
                    if (value.startsWith("=")) "<c r=\"$ref\"><f>${escapeXml(value.drop(1))}</f></c>" else "<c r=\"$ref\" t=\"inlineStr\"><is><t>$escaped</t></is></c>"
                }.joinToString("")
                "<row r=\"${r + 1}\">$values</row>"
            }.joinToString("")
            add(zip, "xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>""")
        }
        return out.toByteArray()
    }

    private fun readXlsx(input: InputStream): String {
        ZipInputStream(input.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                    val xml = zis.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val rows = Regex("<row[^>]*>(.*?)</row>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(xml)
                    return rows.joinToString("\n") { row ->
                        Regex("<c[^>]*?(?:t=\"inlineStr\")?[^>]*>(.*?)</c>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(row.groupValues[1])
                            .joinToString("\t") { cell -> Regex("<t>(.*?)</t>", setOf(RegexOption.DOT_MATCHES_ALL)).find(cell.groupValues[1])?.groupValues?.get(1) ?: Regex("<f>(.*?)</f>", setOf(RegexOption.DOT_MATCHES_ALL)).find(cell.groupValues[1])?.groupValues?.get(1)?.let { "=$it" } ?: "" }
                    }
                }
                entry = zis.nextEntry
            }
        }
        return ""
    }

    fun writePptx(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            add(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/></Types>""")
            add(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>""")
            add(zip, "ppt/_rels/presentation.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/></Relationships>""")
            add(zip, "ppt/presentation.xml", """<?xml version="1.0" encoding="UTF-8"?><p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst><p:sldMasterIdLst/><p:notesMasterIdLst/></p:presentation>""")
            add(zip, "ppt/slides/slide1.xml", """<?xml version="1.0" encoding="UTF-8"?><p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr/><p:grpSpPr/><p:sp><p:nvSpPr/><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US"/><a:t>${escapeXml(text)}</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>""")
        }
        return out.toByteArray()
    }

    fun writePdf(text: String, output: File) {
        output.parentFile?.mkdirs()
        val temp = File(output.parentFile, ".${output.name}.tmp")
        try {
            PdfDocument().use { pdf ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f }
                val lines = text.split('\n')
                var index = 0
                var pageNo = 1
                do {
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo++).create())
                    var y = 50f
                    while (index < lines.size && y < 800f) { page.canvas.drawText(lines[index].take(90), 40f, y, paint); y += 20f; index++ }
                    pdf.finishPage(page)
                } while (index < lines.size || lines.isEmpty())
                FileOutputStream(temp).use { pdf.writeTo(it) }
            }
            if (!temp.renameTo(output)) { temp.copyTo(output, overwrite = true); temp.delete() }
        } finally { if (temp.exists()) temp.delete() }
    }

    @Synchronized fun clearCache() = textCache.clear()

    private fun add(zip: ZipOutputStream, name: String, content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
    private fun escapeXml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
