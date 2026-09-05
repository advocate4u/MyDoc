package com.advocate4u.mydoc.core

import android.content.Context
import java.io.File

/** Small crash-recovery journal kept entirely in app-private storage. */
class RecoveryManager(context: Context) {
    private val root = File(context.filesDir, "recovery")
    private val textFile get() = File(root, "document.txt")
    private val metaFile get() = File(root, "meta.properties")

    fun write(name: String, extension: String, text: String) {
        root.mkdirs()
        val tmpText = File(root, "document.tmp")
        val tmpMeta = File(root, "meta.tmp")
        tmpText.writeText(text, Charsets.UTF_8)
        tmpMeta.writeText("name=${encode(name)}\nextension=$extension\n", Charsets.UTF_8)
        replace(tmpText, textFile)
        replace(tmpMeta, metaFile)
    }

    fun exists(): Boolean = textFile.exists() && metaFile.exists() && textFile.length() <= 5_000_000

    fun read(): RecoverySnapshot? {
        if (!exists()) return null
        val props = metaFile.readLines().associate { line ->
            val i = line.indexOf('=')
            if (i > 0) line.substring(0, i) to line.substring(i + 1) else "" to ""
        }
        return RecoverySnapshot(decode(props["name"] ?: "Recovered document"), props["extension"] ?: "docx", textFile.readText(Charsets.UTF_8))
    }

    fun clear() {
        textFile.delete()
        metaFile.delete()
    }

    private fun replace(tmp: File, target: File) {
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun encode(value: String) = value.replace("%", "%25").replace("\n", "%0A")
    private fun decode(value: String) = value.replace("%0A", "\n").replace("%25", "%")
}

data class RecoverySnapshot(val name: String, val extension: String, val text: String)
