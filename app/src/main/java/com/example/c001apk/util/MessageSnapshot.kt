package com.example.c001apk.util

import com.example.c001apk.ui.message.MessageCenterAdapter.Item

/** A successful category refresh replaces its old snapshot; failed categories keep theirs. */
object MessageSnapshot {
    fun normalize(items: List<Item>): List<Item> = items
        .filter { it.category.isNotBlank() && it.data.id.isNotBlank() }
        .distinctBy { it.category to it.data.id }
        .sortedWith(compareByDescending<Item> { it.data.dateline }
            .thenBy { it.category }.thenBy { it.data.id })

    fun replaceCategories(old: List<Item>, fresh: Map<String, List<Item>>): List<Item> =
        normalize(old.filterNot { it.category in fresh } + fresh.values.flatten())
}
