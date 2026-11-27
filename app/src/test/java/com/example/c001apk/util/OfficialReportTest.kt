package com.example.c001apk.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialReportTest {
    @Test fun restoresTheOriginalCoolapkRoutes() {
        assertEquals("https://m.coolapk.com/mp/do?c=feed&m=report&type=feed&id=42", OfficialReport.url("feed", "42"))
        assertEquals("https://m.coolapk.com/mp/do?c=feed&m=report&type=feed_reply&id=700", OfficialReport.url("feed_reply", "700"))
        assertEquals("https://m.coolapk.com/mp/do?c=user&m=report&id=3", OfficialReport.url("user", "3"))
    }

    @Test fun doesNotGuessUnsupportedRoutesOrPutUntrustedIdsInUrls() {
        assertNull(OfficialReport.url("topic", "42"))
        assertNull(OfficialReport.url("feed&m=other", "42"))
        for (id in listOf(null, "", "0", "000", "42&m=other", "42#x", "42/1", " 42", "１２")) {
            assertNull(OfficialReport.url("feed", id))
        }
    }
}
