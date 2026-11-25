package com.example.c001apk.util

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CoolapkLinksTest {
    @Test fun buildsOnlyOfficialLinksForValidIds() {
        assertEquals("https://www.coolapk.com/feed/123456", CoolapkLinks.feed("123456"))
        assertEquals("https://www.coolapk.com/u/789", CoolapkLinks.user("789"))
        for (id in listOf(null, "", "0", "000", "12/34", "12?s=promo", "123#4", "１２３")) {
            assertNull(CoolapkLinks.feed(id))
            assertNull(CoolapkLinks.user(id))
        }
    }

    @Test fun systemChooserReceivesPlainCanonicalLink() {
        val app = RuntimeEnvironment.getApplication()
        val url = requireNotNull(CoolapkLinks.feed("123456"))
        IntentUtil.shareText(app, url)
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val payload = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, payload.action)
        assertEquals("text/plain", payload.type)
        assertEquals(url, payload.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(payload.`package`)
    }
}
