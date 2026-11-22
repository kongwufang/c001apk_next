package com.example.c001apk.util

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import com.example.c001apk.MyApplication
import com.example.c001apk.logic.database.FollowUserDatabase
import com.example.c001apk.logic.dao.FollowUserDao
import com.example.c001apk.logic.model.FollowUserEntity
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
        // PrefManager caches its SharedPreferences instance across tests in this sandbox.
        PrefManager.isLogin = false
        PrefManager.uid = ""
        PrefManager.saveFollowToCloud = false
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
        val db = Room.inMemoryDatabaseBuilder(MyApplication.context, FollowUserDatabase::class.java).build()
        try {
            val follow = FollowRepo(db.followUserDao())
            val api = mock(ApiService::class.java)
            val network = NetworkRepo(api, api, api, follow, mock(LocalCollectionRepo::class.java))
            PrefManager.saveFollowToCloud = true // stale preference from a signed-in session
            network.postFollowUnFollow("/v6/user/follow", "314159").first().getOrThrow()
            network.postFollowUnFollow("/v6/user/follow", "314159").first().getOrThrow()
            assertEquals(listOf("314159"), follow.loadAll().map { it.uid })
            network.postFollowUnFollow("/v6/user/unfollow", "314159").first().getOrThrow()
            assertTrue(follow.loadAll().isEmpty())
            assertTrue(network.postFollowUnFollow("/v6/user/follow", " ").first().isFailure)
            verifyNoInteractions(api)
        } finally {
            db.close()
        }
    }

    @Test fun databaseFailureIsNotReportedAsSuccess() = runBlocking {
        val api = mock(ApiService::class.java)
        val dao = object : FakeFollowDao() {
            override suspend fun insert(entity: FollowUserEntity) { error("simulated write failure") }
        }
        val network = NetworkRepo(api, api, api, FollowRepo(dao), mock(LocalCollectionRepo::class.java))
        assertEquals("simulated write failure", network.postFollowUnFollow("/v6/user/follow", "42")
            .first().exceptionOrNull()?.message)
        verifyNoInteractions(api)
    }

    private open class FakeFollowDao : FollowUserDao {
        private val rows = linkedMapOf<String, FollowUserEntity>()
        override suspend fun insert(entity: FollowUserEntity) { rows[entity.uid] = entity }
        override suspend fun insertList(list: List<FollowUserEntity>) { list.forEach { insert(it) } }
        override suspend fun loadAll() = rows.values.toList()
        override fun loadAllLive(): LiveData<List<FollowUserEntity>> = MutableLiveData(rows.values.toList())
        override fun observeFollowed(uid: String): LiveData<Boolean> = MutableLiveData(uid in rows)
        override suspend fun delete(uid: String) { rows.remove(uid) }
        override suspend fun deleteAll() { rows.clear() }
    }
}
