package com.advocate4u.mydoc

import com.advocate4u.mydoc.core.EditorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorUtilsTest {
    private val cells = listOf(listOf("2", "3"), listOf("4", "5"))

    @Test fun replaceAllReturnsCount() {
        val result = EditorUtils.replace("one one one", "one", "two", true)
        assertEquals("two two two", result.first)
        assertEquals(3, result.second)
    }

    @Test fun evaluatesArithmeticFormula() {
        assertEquals("7", EditorUtils.evaluateSimpleFormula("=3+4", emptyList()))
        assertEquals("14", EditorUtils.evaluateSimpleFormula("=2+3*4", emptyList()))
    }

    @Test fun evaluatesCellReferenceFormula() {
        assertEquals("5", EditorUtils.evaluateSimpleFormula("=A1+B1", cells))
    }

    @Test fun evaluatesRangeFunctions() {
        assertEquals("14", EditorUtils.evaluateSimpleFormula("=SUM(A1:B2)", cells))
        assertEquals("3.5", EditorUtils.evaluateSimpleFormula("=AVERAGE(A1:B2)", cells))
        assertEquals("2", EditorUtils.evaluateSimpleFormula("=MIN(A1:B2)", cells))
        assertEquals("5", EditorUtils.evaluateSimpleFormula("=MAX(A1:B2)", cells))
        assertEquals("4", EditorUtils.evaluateSimpleFormula("=COUNT(A1:B2)", cells))
    }

    @Test fun evaluatesIfComparison() {
        assertEquals("yes", EditorUtils.evaluateSimpleFormula("=IF(A1<B1,yes,no)", cells))
        assertEquals("ok", EditorUtils.evaluateSimpleFormula("=IF(5>=5,ok,bad)", emptyList()))
    }

    @Test fun nonFormulaReturnsNull() {
        assertNull(EditorUtils.evaluateSimpleFormula("hello", emptyList()))
    }
}
