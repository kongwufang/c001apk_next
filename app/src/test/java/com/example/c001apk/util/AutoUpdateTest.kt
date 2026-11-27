package com.example.c001apk.util

import com.example.c001apk.BuildConfig
import com.example.c001apk.MyApplication
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AutoUpdateTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun automaticMetadataUsesTheSameFirstPartyIdentityAsLegacyUpdate() {
        MyApplication.context = RuntimeEnvironment.getApplication()
        PrefManager.ssaid = "0123456789abcdef"
        PrefManager.userRandomId = "fedcba9876543210".repeat(2)
        val legacy = UpdateChecker.metadataRequest(UpdateChecker.BASE_URL).build()
        val automatic = UpdateChecker.metadataRequest(AutoUpdateInfo.ENDPOINT).build()
        assertEquals(legacy.headers, automatic.headers)
        assertEquals(ServerIdentity.API_VERSION.toString(), automatic.header(ServerIdentity.HEADER_API))
        assertEquals(BuildConfig.VERSION_CODE.toString(), automatic.header("X-Client-Code"))
        assertEquals(PrefManager.ssaid, automatic.header("X-Client-SSaid"))
        assertEquals(PrefManager.userRandomId, automatic.header("X-App-userrandomid"))
        assertTrue(automatic.header("User-Agent").orEmpty().contains("(#Build;"))
    }

    @Test fun publicDownloadsAndInsecureUrlsDoNotGetFirstPartyIdentity() {
        for (url in listOf(
            "https://github.com/project/repo/releases/download/V1/app.apk",
            "https://service.houlangs.cn.example.org/c001apk/antoupdate/",
            "http://service.houlangs.cn/c001apk/antoupdate/",
        )) {
            assertEquals(0, UpdateChecker.metadataRequest(url).build().headers.size)
        }
    }

    @Test fun supersedingReleaseKeepsOnlyTheNewMatchingArtifact() {
        val old = folder.newFile("10012-old.apk")
        val stalePartial = folder.newFile("10013-new.part.apk")
        val current = folder.newFile("10013-new.apk")
        pruneUpdateFiles(folder.root, keep = current.name)
        assertFalse(old.exists())
        assertFalse(stalePartial.exists())
        assertTrue(current.exists())
    }

    @Test fun skippingDeletesOnlyTheSelectedVersionsArtifacts() {
        val skipped = folder.newFile("10012-old.apk")
        val partial = folder.newFile("10012-old.part.apk")
        val following = folder.newFile("10013-new.apk")
        pruneUpdateFiles(folder.root, onlyVersion = 10012)
        assertFalse(skipped.exists())
        assertFalse(partial.exists())
        assertTrue(following.exists())
    }

    private fun release(): JSONObject = JSONObject().apply {
        put("code", 0)
        put("stable", JSONObject().apply {
            put("versionName", "V1.0.12")
            put("versionCode", 10012)
            put("changelog", "修复已知问题")
            put("downloadUrl", "https://example.org/android/10012.apk")
            put("sha256", "AB".repeat(32))
            put("sizeBytes", 4096)
        })
    }

    @Test fun parsesProductionManifestAndUsesArtifactIdentityInTheFileName() {
        val info = AutoUpdateInfo.parse(release().toString())!!
        assertEquals(10012L, info.update.versionCode)
        assertEquals("修复已知问题", info.update.changelog)
        assertEquals(4096L, info.sizeBytes)
        assertEquals("10012-${"ab".repeat(32)}.apk", info.fileName)
        assertTrue(info.update.lines.isEmpty())
    }

    @Test fun refusesDisabledOrUnsafeAutoUpdateMetadata() {
        assertNull(AutoUpdateInfo.parse(release().put("code", 403).toString()))
        assertNull(AutoUpdateInfo.parse("invalid"))
        for ((field, value) in listOf(
            "downloadUrl" to "http://example.org/insecure.apk",
            "downloadUrl" to "https://user:password@example.org/leak.apk",
            "downloadUrl" to "https://example.org/app.apk#fragment",
            "sha256" to "missing",
            "sizeBytes" to (AutoUpdateInfo.MAX_APK_BYTES + 1),
            "versionCode" to 0,
        )) {
            val broken = release().apply { getJSONObject("stable").put(field, value) }
            assertNull(field, AutoUpdateInfo.parse(broken.toString()))
        }
    }

    @Test fun stopsAfterOneTryAndFiveRetries() = runTest {
        var calls = 0
        val retries = mutableListOf<Int>()
        assertFalse(retryUpdateDownload({ retries.add(it) }) { calls++; throw IOException() })
        assertEquals(6, calls)
        assertEquals(listOf(1, 2, 3, 4, 5), retries)
    }

    @Test fun stopsImmediatelyAfterSuccess() = runTest {
        var calls = 0
        assertTrue(retryUpdateDownload({}) { if (++calls < 3) throw IOException() })
        assertEquals(3, calls)
    }

    @Test fun cancellationIsNotAFailureOrRetry() = runTest {
        var calls = 0
        try {
            retryUpdateDownload({ error("must not retry cancellation") }) {
                calls++
                throw CancellationException()
            }
            error("must propagate cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, calls)
        }
    }
}
