package com.example.c001apk.ui.feed.reply.emoji

import com.example.c001apk.util.emoji.EmojiPackStore

/** Page layout of the emoji panel: 27 slots per page plus the backspace cell. */
internal object EmojiPanels {
    const val PER_PAGE = 27

    fun pages(codes: List<String>): List<List<String>> =
        if (codes.isEmpty()) listOf(emptyList()) else codes.chunked(PER_PAGE)

    fun titles(): List<String> {
        val groups = EmojiPackStore.groups()
        return listOf("最近") + if (groups.isEmpty()) listOf("表情包") else groups.map { it.title }
    }

    fun tabs(recent: List<String>): List<List<List<String>>> = buildList {
        add(pages(recent.filter(EmojiPackStore::contains)))
        val groups = EmojiPackStore.groups()
        if (groups.isEmpty()) add(pages(emptyList())) else groups.forEach { add(pages(it.codes)) }
    }
}
