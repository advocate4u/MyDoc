package com.advocate4u.mydoc

import com.advocate4u.mydoc.core.EditorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorUtilsTest {
    @Test fun replaceAllReturnsCount() {
        val result = EditorUtils.replace("one one one", "one", "two", true)
        assertEquals("two two two", result.first)
        assertEquals(3, result.second)
    }

    @Test fun evaluatesArithmeticFormula() {
        assertEquals("7", EditorUtils.evaluateSimpleFormula("=3+4", emptyList()))
    }

    @Test fun evaluatesSumRange() {
        val cells = listOf(listOf("2", "3"), listOf("4", "5"))
        assertEquals("14", EditorUtils.evaluateSimpleFormula("=SUM(A1:B2)", cells))
    }

    @Test fun nonFormulaReturnsNull() {
        assertNull(EditorUtils.evaluateSimpleFormula("hello", emptyList()))
    }
}
