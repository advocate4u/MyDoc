package com.advocate4u.mydoc.core

/** Centralized extension and MIME mapping used by Android document pickers and sharing. */
enum class FileFormat(val extension: String, val mime: String, val label: String) {
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word document"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel workbook"),
    XLS("xls", "application/vnd.ms-excel", "Excel workbook"),
    XLSM("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12", "Excel macro workbook"),
    CSV("csv", "text/csv", "CSV file"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint presentation"),
    PDF("pdf", "application/pdf", "PDF document"),
    TXT("txt", "text/plain", "Text file");

    companion object {
        fun fromExtension(extension: String): FileFormat = values().firstOrNull { it.extension == extension.lowercase().removePrefix(".") } ?: TXT
        fun mimeForExtension(extension: String): String = fromExtension(extension).mime
    }
}
