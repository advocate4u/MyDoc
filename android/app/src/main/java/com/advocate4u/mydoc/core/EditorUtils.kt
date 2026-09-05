package com.advocate4u.mydoc.core

import kotlin.math.max

object EditorUtils {
    fun replace(text: String, query: String, replacement: String, replaceAll: Boolean): Pair<String, Int> {
        if (query.isEmpty()) return text to 0
        if (replaceAll) {
            var count = 0
            var at = 0
            while (true) {
                val i = text.indexOf(query, at)
                if (i < 0) break
                count++
                at = i + max(1, query.length)
            }
            return text.replace(query, replacement) to count
        }
        val i = text.indexOf(query)
        return if (i < 0) text to 0 else text.substring(0, i) + replacement + text.substring(i + query.length) to 1
    }

    fun evaluateSimpleFormula(value: String, cells: List<List<String>>): String? {
        if (!value.startsWith("=")) return null
        val expr = value.drop(1).trim()
        val range = Regex("(?i)^(SUM|AVERAGE)\\(([A-Z]+)(\\d+):([A-Z]+)(\\d+)\\)$").matchEntire(expr)
        if (range != null) {
            val fn = range.groupValues[1].uppercase()
            val c1 = column(range.groupValues[2]); val r1 = range.groupValues[3].toIntOrNull()?.minus(1) ?: return null
            val c2 = column(range.groupValues[4]); val r2 = range.groupValues[5].toIntOrNull()?.minus(1) ?: return null
            val values = buildList { for (r in minOf(r1, r2)..maxOf(r1, r2)) for (c in minOf(c1, c2)..maxOf(c1, c2)) add(cells.getOrNull(r)?.getOrNull(c)?.toDoubleOrNull() ?: 0.0) }
            if (values.isEmpty()) return "0"
            return if (fn == "SUM") values.sum().format() else (values.sum() / values.size).format()
        }
        val tokens = Regex("\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*([+*/-])\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*").matchEntire(expr) ?: return null
        val a = tokens.groupValues[1].toDouble(); val op = tokens.groupValues[2]; val b = tokens.groupValues[3].toDouble()
        return when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> if (b == 0.0) Double.NaN else a / b; else -> Double.NaN }.format()
    }

    private fun column(value: String): Int = value.uppercase().fold(0) { acc, ch -> acc * 26 + ch.code - 'A'.code + 1 } - 1
    private fun Double.format(): String = if (isNaN() || isInfinite()) toString() else if (this % 1.0 == 0.0) toLong().toString() else toString()
}
