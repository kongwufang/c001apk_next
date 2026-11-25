package com.example.c001apk.util

import android.app.Application
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.logic.network.LenientGson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NotificationV18KitTest {
    private fun page(json: String) = LenientGson.gson.fromJson(json, MessageResponse::class.java)

    @Test fun likesWithNullIdUseEntityIdAndKeepReplyTarget() {
        val received = page("""{"data":[
            {"entityType":"notificationV18","entityId":"feed-42-15","id":null,
             "note_type":"reply_like","from_uid":"15","from_username":"Someone",
             "url":"/feed/42?rid=7","target_id":7}
        ]}""")
        val result = NotificationV18Kit.normalizePage(NotificationV18Kit.LIKE_URL, received)
        assertEquals(1, result.rawPageSize)
        assertEquals("feed-42-15", result.rawLastItem)
        val item = result.data!!.single()
        assertEquals("feed-42-15", item.id)
        assertEquals("15", item.uid)
        assertEquals("Someone", item.likeUsername)
        assertEquals("42", item.fid)
        assertEquals(7L, item.rid)
    }

    @Test fun cursorUsesRawLastRowEvenWhenVisibleRepliesAreFiltered() {
        val received = page("""{"data":[
            {"entityType":"notificationV18","entityId":"reply-1","id":null,"note_type":"feed_reply"},
            {"entityType":"notificationV18","entityId":"last-system","id":null,"note_type":"notify_system"}
        ]}""")
        val result = NotificationV18Kit.normalizePage(NotificationV18Kit.URL, received)
        assertEquals(2, result.rawPageSize)
        assertEquals("last-system", result.rawLastItem)
        assertEquals("reply-1", result.data!!.single().id)
    }

    @Test fun emptyPagesAndMissingCursorsDoNotThrowAndOldIdsAreKept() {
        val old = page("""{"data":[{"id":"legacy","entityId":"alternate"}]}""")
        val result = NotificationV18Kit.normalizePage("/v6/notification/atMeList", old)
        assertEquals("legacy", result.rawLastItem)
        assertEquals(old.data, result.data)
        val missing = NotificationV18Kit.normalizePage(NotificationV18Kit.LIKE_URL,
            page("""{"data":[{"id":null,"entityId":" "}]}"""))
        assertNull(missing.rawLastItem)
        assertEquals("", missing.data!!.single().id)
        val empty = NotificationV18Kit.normalizePage(NotificationV18Kit.LIKE_URL, page("""{"data":[]}"""))
        assertEquals(0, empty.rawPageSize)
        assertNull(empty.rawLastItem)
        assertTrue(empty.data!!.isEmpty())
    }
}
