package com.advocate4u.mydoc.core

import kotlin.math.max

/** Spreadsheet-safe helpers shared by Word and spreadsheet features. */
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

    /** Evaluates a safe spreadsheet subset: arithmetic, cell refs, ranges and common functions. */
    fun evaluateSimpleFormula(value: String, cells: List<List<String>>): String? {
        if (!value.startsWith("=")) return null
        val expr = value.drop(1).trim()
        if (expr.isEmpty()) return null

        val countIf = Regex("(?i)^COUNTIF\\(([^,]+),(.+)\\)$").matchEntire(expr)
        if (countIf != null) {
            val range = expandRawReference(countIf.groupValues[1].trim(), cells) ?: return null
            val criterion = countIf.groupValues[2].trim().trim('"')
            val comparator = Regex("^(>=|<=|<>|=|>|<)(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))$").matchEntire(criterion)
            if (comparator != null) {
                val target = comparator.groupValues[2].toDoubleOrNull() ?: return null
                val op = comparator.groupValues[1]
                return range.count { raw ->
                    val number = raw.toDoubleOrNull() ?: return false
                    when (op) {
                        ">" -> number > target
                        "<" -> number < target
                        ">=" -> number >= target
                        "<=" -> number <= target
                        "=" -> number == target
                        "<>" -> number != target
                        else -> false
                    }
                }.toString()
            }
            return range.count { it == criterion }.toString()
        }

        val function = Regex("(?i)^(SUM|AVERAGE|MIN|MAX|COUNT)\\(([^()]*)\\)$").matchEntire(expr)
        if (function != null) {
            val args = function.groupValues[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (args.isEmpty()) return "0"
            val values = mutableListOf<Double>()
            for (arg in args) {
                val expanded = expandReference(arg, cells)
                if (expanded != null) values.addAll(expanded) else values.add(arg.toDoubleOrNull() ?: return null)
            }
            return when (function.groupValues[1].uppercase()) {
                "SUM" -> values.sum().format()
                "AVERAGE" -> if (values.isEmpty()) "0" else (values.sum() / values.size).format()
                "MIN" -> values.minOrNull()?.format()
                "MAX" -> values.maxOrNull()?.format()
                "COUNT" -> values.count { it.isFinite() }.toString()
                else -> null
            }
        }

        // Small IF subset: =IF(condition, trueValue, falseValue), where condition is numeric comparison.
        val ifMatch = Regex("(?i)^IF\\(([^,]+),([^,]+),(.+)\\)$").matchEntire(expr)
        if (ifMatch != null) {
            val condition = ifMatch.groupValues[1].trim()
            val yes = ifMatch.groupValues[2].trim().trim('"')
            val no = ifMatch.groupValues[3].trim().trim('"')
            val comparison = Regex("^(.+?)(>=|<=|<>|=|>|<)(.+)$").matchEntire(condition) ?: return null
            val left = operandValue(comparison.groupValues[1].trim(), cells) ?: return null
            val right = operandValue(comparison.groupValues[3].trim(), cells) ?: return null
            val result = when (comparison.groupValues[2]) {
                ">" -> left > right
                "<" -> left < right
                ">=" -> left >= right
                "<=" -> left <= right
                "=" -> left == right
                "<>" -> left != right
                else -> false
            }
            return if (result) yes else no
        }
        return evaluateArithmetic(expr, cells)?.format()
    }

    private fun operandValue(value: String, cells: List<List<String>>): Double? =
        value.toDoubleOrNull() ?: expandReference(value, cells)?.singleOrNull()

    private fun evaluateArithmetic(expr: String, cells: List<List<String>>): Double? {
        val normalized = expr.replace(" ", "")
        if (normalized.isEmpty()) return null
        val tokens = Regex("(?i)([A-Z]+\\d+|(?:\\d+(?:\\.\\d*)?|\\.\\d+))|([+*/-])").findAll(normalized).toList()
        if (tokens.isEmpty()) return null
        var consumed = 0
        val values = mutableListOf<Double>()
        val operators = mutableListOf<Char>()
        for (match in tokens) {
            if (match.range.first != consumed) return null
            val operand = match.groupValues[1]
            if (operand.isNotEmpty()) {
                val v = if (Regex("(?i)^[A-Z]+\\d+$").matches(operand)) expandReference(operand, cells)?.singleOrNull() else operand.toDoubleOrNull()
                if (v == null) return null
                values.add(v)
            } else operators.add(match.groupValues[2][0])
            consumed = match.range.last + 1
        }
        if (consumed != normalized.length || values.size != operators.size + 1) return null
        val reducedValues = mutableListOf(values.first())
        val reducedOps = mutableListOf<Char>()
        for (i in operators.indices) {
            val op = operators[i]
            val next = values[i + 1]
            if (op == '*' || op == '/') {
                val current = reducedValues.removeLast()
                reducedValues.add(if (op == '*') current * next else if (next == 0.0) return Double.NaN else current / next)
            } else {
                reducedOps.add(op)
                reducedValues.add(next)
            }
        }
        var result = reducedValues.first()
        for (i in reducedOps.indices) result = if (reducedOps[i] == '+') result + reducedValues[i + 1] else result - reducedValues[i + 1]
        return result
    }

    private fun expandReference(reference: String, cells: List<List<String>>): List<Double>? =
        expandRawReference(reference, cells)?.map { it.toDoubleOrNull() ?: 0.0 }

    private fun expandRawReference(reference: String, cells: List<List<String>>): List<String>? {
        val single = Regex("(?i)^([A-Z]+)(\\d+)$").matchEntire(reference)
        if (single != null) {
            val c = column(single.groupValues[1]); val r = single.groupValues[2].toIntOrNull()?.minus(1) ?: return null
            if (r < 0 || c < 0) return null
            return listOf(cells.getOrNull(r)?.getOrNull(c) ?: "")
        }
        val range = Regex("(?i)^([A-Z]+)(\\d+):([A-Z]+)(\\d+)$").matchEntire(reference) ?: return null
        val c1 = column(range.groupValues[1]); val r1 = range.groupValues[2].toIntOrNull()?.minus(1) ?: return null
        val c2 = column(range.groupValues[3]); val r2 = range.groupValues[4].toIntOrNull()?.minus(1) ?: return null
        if (minOf(r1, r2) < 0 || minOf(c1, c2) < 0) return null
        return buildList { for (r in minOf(r1, r2)..maxOf(r1, r2)) for (c in minOf(c1, c2)..maxOf(c1, c2)) add(cells.getOrNull(r)?.getOrNull(c) ?: "") }
    }

    private fun column(value: String): Int = value.uppercase().fold(0) { acc, ch -> acc * 26 + ch.code - 'A'.code + 1 } - 1
    private fun Double.format(): String = if (isNaN() || isInfinite()) toString() else if (this % 1.0 == 0.0) toLong().toString() else toString()
}
