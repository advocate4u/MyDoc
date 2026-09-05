package com.advocate4u.mydoc

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentEngineTest {
    @Test fun textRoundTripIsDeterministic() {
        val source = "Hello\nMyDoc"
        assertEquals(source, DocumentEngine.readText(source.toByteArray(), "txt"))
    }

    @Test fun docxWriterProducesNonEmptyPackage() {
        val bytes = DocumentEngine.writeDocx("Hello")
        assert(bytes.isNotEmpty())
        assertEquals("", DocumentEngine.readText(bytes, "docx").trim())
    }
}
