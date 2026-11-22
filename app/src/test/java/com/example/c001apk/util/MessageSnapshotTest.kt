package com.example.c001apk.util

import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.ui.message.MessageCenterAdapter
import org.junit.Assert.*
import org.junit.Test

class MessageSnapshotTest {
    private fun item(id: String, time: Long, category: String = "commentMe", text: String = id) =
        MessageCenterAdapter.Item(category, MessageResponse.Data(id = id, dateline = time, note = text))

    @Test fun successfulRefreshDeletesMissingAndInsertsInMiddleAndUpdatesContent() {
        val old = listOf(item("a", 30), item("deleted", 20), item("c", 10))
        val fresh = listOf(item("a", 30, text = "edited"), item("middle", 25), item("c", 10))
        val result = MessageSnapshot.replaceCategories(old, mapOf("commentMe" to fresh))
        assertEquals(listOf("a", "middle", "c"), result.map { it.data.id })
        assertEquals("edited", result.first().data.note)
    }

    @Test fun realEmptyClearsItsCategoryButFailedCategoryIsPreserved() {
        val old = listOf(item("a", 30), item("like", 20, "feedLike"))
        assertEquals(listOf("like"), MessageSnapshot.replaceCategories(old,
            mapOf("commentMe" to emptyList())).map { it.data.id })
        assertEquals(MessageSnapshot.normalize(old), MessageSnapshot.replaceCategories(old, emptyMap()))
    }

    @Test fun deduplicatesByCategoryAndIdNotIdAlone() {
        val row = item("same", 10)
        assertEquals(2, MessageSnapshot.normalize(listOf(row, row, item("same", 20, "feedLike"))).size)
    }

    @Test fun diffDetectsTextAvatarAndTargetChangesWithoutChangingIdentity() {
        val old = item("a", 10)
        val changed = old.copy(data = old.data.copy(note = "new", fromUserAvatar = "avatar", url = "/feed/2"))
        val diff = MessageCenterAdapter.DiffCallback()
        assertTrue(diff.areItemsTheSame(old, changed))
        assertFalse(diff.areContentsTheSame(old, changed))
        assertTrue(diff.areContentsTheSame(old, old.copy()))
    }
}
