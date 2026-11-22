package com.example.c001apk.util

import android.app.Application
import androidx.room.Room
import com.example.c001apk.MyApplication
import com.example.c001apk.logic.database.FollowUserDatabase
import com.example.c001apk.logic.network.ApiService
import com.example.c001apk.logic.repository.FollowRepo
import com.example.c001apk.logic.repository.LocalCollectionRepo
import com.example.c001apk.logic.repository.NetworkRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LocalFollowTest {
    @Before fun reset() {
        MyApplication.context = RuntimeEnvironment.getApplication()
        MyApplication.context.getSharedPreferences("settings", 0).edit().clear().commit()
    }

    @Test fun guestCannotUseCloudEvenWithAStoredCloudPreference() {
        assertFalse(PrefManager.saveFollowToCloud)
        PrefManager.saveFollowToCloud = true
        assertFalse(PrefManager.saveFollowToCloud)
        assertTrue(PrefManager.canFollowUser("42"))
        assertFalse(PrefManager.canFollowUser(" "))
        PrefManager.uid = "42"
        PrefManager.isLogin = true
        assertTrue(PrefManager.saveFollowToCloud)
        assertFalse(PrefManager.canFollowUser("42"))
        PrefManager.saveFollowToCloud = false
        assertFalse(PrefManager.saveFollowToCloud)
    }

    @Test fun guestFollowPersistsOneRowAndUnfollowRemovesItWithoutRequests() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(MyApplication.context, FollowUserDatabase::class.java).build()
        try {
            val follow = FollowRepo(database.followUserDao())
            val api = mock(ApiService::class.java)
            val network = NetworkRepo(api, api, api, follow, mock(LocalCollectionRepo::class.java))
            PrefManager.saveFollowToCloud = true // stale preference from a signed-in session
            assertTrue(network.postFollowUnFollow("/v6/user/follow", "42").first().isSuccess)
            assertTrue(network.postFollowUnFollow("/v6/user/follow", "42").first().isSuccess)
            assertEquals(listOf("42"), follow.loadAll().map { it.uid })
            assertTrue(network.postFollowUnFollow("/v6/user/unfollow", "42").first().isSuccess)
            assertTrue(follow.loadAll().isEmpty())
            assertTrue(network.postFollowUnFollow("/v6/user/follow", " ").first().isFailure)
            verifyNoInteractions(api)
        } finally {
            database.close()
        }
    }
}
