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
        val bytes = DocumentEngine.writeDocx("Hello")
        assertTrue(bytes.isNotEmpty())
        assertEquals("Hello", DocumentEngine.readText(bytes, "docx").trim())
    }
}
