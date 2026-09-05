package com.advocate4u.mydoc.core

import android.content.ContentResolver
import android.net.Uri
import com.advocate4u.mydoc.RecentDocument

/** Pure recent-document helpers kept outside the UI state layer. */
object RecentDocumentStore {
    const val MAX_ITEMS = 10

    fun normalize(items: List<RecentDocument>): List<RecentDocument> =
        items.asSequence()
            .filter { it.name.isNotBlank() && it.uri.isNotBlank() }
            .distinctBy { it.uri }
            .take(MAX_ITEMS)
            .toList()

    fun push(items: List<RecentDocument>, name: String, uri: String): List<RecentDocument> =
        normalize(listOf(RecentDocument(name.trim(), uri)) + items.filterNot { it.uri == uri })

    fun isAccessible(resolver: ContentResolver, item: RecentDocument): Boolean =
        runCatching {
            resolver.openAssetFileDescriptor(Uri.parse(item.uri), "r")?.use { true } ?: false
        }.getOrDefault(false)

    fun removeInaccessible(resolver: ContentResolver, items: List<RecentDocument>): List<RecentDocument> =
        normalize(items.filter { isAccessible(resolver, it) })

    fun filter(items: List<RecentDocument>, query: String): List<RecentDocument> {
        val q = query.trim()
        if (q.isEmpty()) return normalize(items)
        return normalize(items.filter { it.name.contains(q, ignoreCase = true) })
    }

    fun sort(items: List<RecentDocument>, mode: RecentSortMode): List<RecentDocument> =
        when (mode) {
            RecentSortMode.RECENT -> items
            RecentSortMode.NAME_ASC -> items.sortedBy { it.name.lowercase() }
            RecentSortMode.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
        }.let(::normalize)

    fun toggleFavorite(items: List<RecentDocument>, uri: String): List<RecentDocument> =
        items.map { if (it.uri == uri) it.copy(favorite = !it.favorite) else it }

    fun favoritesFirst(items: List<RecentDocument>): List<RecentDocument> =
        items.sortedWith(compareByDescending<RecentDocument> { it.favorite })
            .let(::normalize)
}

enum class RecentSortMode { RECENT, NAME_ASC, NAME_DESC }
