package com.example.c001apk.util

import android.app.Application
import com.example.c001apk.MyApplication
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.ui.message.MessageCenterAdapter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MessageCenterCacheStoreTest {
    @Before fun setup() {
        MyApplication.context = RuntimeEnvironment.getApplication()
        MyApplication.context.getSharedPreferences("settings", 0).edit().clear().commit()
        MyApplication.context.filesDir.listFiles()?.filter { it.name.startsWith("message_center") }
            ?.forEach { it.delete() }
    }
    private fun login(uid: String): MessageAccount {
        PrefManager.isLogin = true
        PrefManager.uid = uid
        return requireNotNull(MessageAccount.current())
    }
    private fun items(id: String) = listOf(MessageCenterAdapter.Item("commentMe", MessageResponse.Data(id = id)))

    @Test fun cacheIsIsolatedAndOwnCacheSurvivesRoundTrip() {
        val a = login("100")
        MessageCenterCacheStore.save(a, items("A"))
        val b = login("200")
        assertTrue(MessageCenterCacheStore.load(b).isEmpty())
        MessageCenterCacheStore.save(b, items("B"))
        assertEquals("A", MessageCenterCacheStore.load(login("100")).single().data.id)
        assertEquals("B", MessageCenterCacheStore.load(login("200")).single().data.id)
    }
    @Test fun emptySuccessReplacesOldCache() {
        val a = login("100")
        MessageCenterCacheStore.save(a, items("old"))
        MessageCenterCacheStore.save(a, emptyList())
        assertTrue(MessageCenterCacheStore.load(a).isEmpty())
    }
    @Test fun staleRequestCannotWriteAfterSwitchOrLogoutAndBackToSameAccount() {
        val old = login("100")
        MessageCenterCacheStore.save(old, items("good"))
        login("200")
        MessageCenterCacheStore.save(old, items("stale"))
        val current = login("100")
        MessageCenterCacheStore.save(old, items("stale"))
        assertEquals("good", MessageCenterCacheStore.load(current).single().data.id)
        PrefManager.isLogin = false
        val returned = login("100")
        assertFalse(current.isCurrent())
        MessageCenterCacheStore.save(current, items("stale"))
        assertEquals("good", MessageCenterCacheStore.load(returned).single().data.id)
    }
    @Test fun legacyCacheIsNeverAssignedToCurrentAccountAndCorruptionIsSafe() {
        val a = login("100")
        val legacy = File(MyApplication.context.filesDir, "message_center_cache.json")
        legacy.writeText("[{\"category\":\"commentMe\",\"data\":{\"id\":\"unowned\"}}]")
        assertTrue(MessageCenterCacheStore.load(a).isEmpty())
        assertFalse(legacy.exists())
        MessageCenterCacheStore.save(a, items("ok"))
        MyApplication.context.filesDir.listFiles()!!.single { it.name.startsWith("message_center_") }
            .writeText("not json")
        assertTrue(MessageCenterCacheStore.load(a).isEmpty())
    }
    @Test fun tokenRenewalDoesNotInvalidateAccount() {
        val a = login("100")
        PrefManager.isLogin = true
        PrefManager.uid = "100"
        PrefManager.token = "renewed"
        assertTrue(a.isCurrent())
    }
}
