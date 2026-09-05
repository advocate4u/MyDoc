package com.advocate4u.mydoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentEngineTest {
    @Test fun textRoundTripIsDeterministic() {
        val source = "Hello\nMyDoc"
        assertEquals(source, DocumentEngine.readText(source.toByteArray(), "txt"))
    }

    @Test fun docxWriterProducesReadablePackage() {
        val bytes = DocumentEngine.writeDocx("Hello", bold = true, italic = true, underline = true)
        assertTrue(bytes.isNotEmpty())
        assertEquals("Hello", DocumentEngine.readText(bytes, "docx").trim())
    }

    @Test fun xlsxWriterProducesReadableWorkbook() {
        val bytes = DocumentEngine.writeXlsx(listOf(listOf("A", "B"), listOf("1", "=SUM(1,2)")))
        assertTrue(bytes.isNotEmpty())
        val text = DocumentEngine.readText(bytes, "xlsx")
        assertTrue(text.contains("A"))
        assertTrue(text.contains("=SUM(1,2)"))
    }

    @Test fun pptxWriterProducesPackage() {
        val bytes = DocumentEngine.writePptx("MyDoc presentation")
        assertTrue(bytes.isNotEmpty())
    }
}
