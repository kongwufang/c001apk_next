package com.example.c001apk.util

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.c001apk.MyApplication
import com.example.c001apk.logic.model.CheckCountResponse
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.logic.repository.HistoryFavoriteRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.ui.message.MessageCenterAdapter.Item
import com.example.c001apk.ui.message.MessageCenterViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MessageCenterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var network: NetworkRepo
    private lateinit var black: BlackListRepo
    private lateinit var history: HistoryFavoriteRepo
    private lateinit var vm: MessageCenterViewModel
    private val pages = ConcurrentHashMap<Pair<String, Int>, Result<MessageResponse>>()
    private val calls = ConcurrentHashMap.newKeySet<Pair<String, Int>>()
    private val atMe = "/v6/notification/atMeList"
    private val categories = listOf("atMe", "atCommentMe", "commentMe", "feedLike", "contactsFollow")
    private val urls get() = listOf(atMe, "/v6/notification/atCommentMeList", NotificationV18Kit.URL,
        NotificationV18Kit.LIKE_URL, "/v6/notification/contactsFollowList")

    @Before fun setup() = runBlocking {
        MyApplication.context = RuntimeEnvironment.getApplication()
        MyApplication.context.getSharedPreferences("settings", 0).edit().clear().commit()
        MyApplication.context.filesDir.listFiles()?.filter { it.name.startsWith("message_center") }?.forEach { it.delete() }
        PrefManager.isLogin = true; PrefManager.uid = "test-A"
        Dispatchers.setMain(dispatcher)
        network = mock(NetworkRepo::class.java)
        black = mock(BlackListRepo::class.java)
        history = mock(HistoryFavoriteRepo::class.java)
        `when`(black.checkUid(anyString())).thenReturn(false)
        `when`(network.checkCount()).thenReturn(flowOf(Result.success(CheckCountResponse(null, null, null, null))))
        `when`(network.getMessage(anyString(), anyInt(), nullable(String::class.java))).thenAnswer {
            val url = it.getArgument<String>(0); val page = it.getArgument<Int>(1)
            flow { calls.add(url to page); emit(pages[url to page] ?: Result.success(response(emptyList()))) }
        }
        vm = MessageCenterViewModel(black, history, network)
        onMain { vm.bindAccount() }
    }
    @After fun tearDown() { vm.viewModelScope.cancel(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }
    private fun onMain(block: () -> Unit) { block(); dispatcher.scheduler.runCurrent() }
    private fun data(id: String, time: Long = 1, text: String = id) = MessageResponse.Data(
        id = id, dateline = time, note = text, entityType = "notification", fromuid = "sender")
    private fun response(rows: List<MessageResponse.Data>, rawSize: Int = rows.size, cursor: String? = rows.lastOrNull()?.id) =
        MessageResponse(null, null, null, null, rows, rawSize, cursor)
    private fun waitDone() {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (vm.isRefreshing || vm.isLoadMore) {
            dispatcher.scheduler.runCurrent()
            if (System.nanoTime() > deadline) fail("Message refresh did not complete")
            Thread.yield()
        }
        runBlocking { vm.awaitCacheWrite() }
    }
    private fun refresh() { onMain { vm.refresh() }; waitDone() }

    @Test fun successfulRefreshReconcilesDeletionInsertionAndContentThenPersistsIt() {
        pages[atMe to 1] = Result.success(response(listOf(data("a", 30), data("deleted", 20), data("c", 10))))
        refresh()
        pages[atMe to 1] = Result.success(response(listOf(data("a", 30, "edited"), data("middle", 25), data("c", 10))))
        refresh()
        assertEquals(listOf("a", "middle", "c"), vm.messageData.value!!.map { it.data.id })
        assertEquals("edited", vm.messageData.value!!.first().data.note)
        assertEquals(vm.messageData.value, MessageCenterCacheStore.load(MessageAccount.current()!!))
        pages[atMe to 1] = Result.success(response(emptyList()))
        refresh()
        assertTrue(vm.messageData.value!!.isEmpty())
        assertTrue(MessageCenterCacheStore.load(MessageAccount.current()!!).isEmpty())
    }

    @Test fun failedCategoryKeepsItsCacheWhileSuccessfulCategoryClears() {
        MessageCenterCacheStore.save(MessageAccount.current()!!, listOf(
            Item("atMe", data("keep")), Item("feedLike", data("remove"))))
        onMain { vm.invalidateAccount(); vm.bindAccount() }
        pages[atMe to 1] = Result.failure(IllegalStateException("offline"))
        refresh()
        assertEquals(listOf("keep"), vm.messageData.value!!.map { it.data.id })
        assertEquals("keep", MessageCenterCacheStore.load(MessageAccount.current()!!).single().data.id)
    }

    @Test fun filteredEmptyV18PageUsesRawCursorAndContinues() {
        pages[NotificationV18Kit.URL to 1] = Result.success(response(emptyList(), 20, "filtered-cursor"))
        pages[NotificationV18Kit.URL to 2] = Result.success(response(listOf(data("reply"))))
        refresh()
        assertTrue(calls.contains(NotificationV18Kit.URL to 2))
        assertEquals("reply", vm.messageData.value!!.single().data.id)
    }

    @Test fun bufferedRowsRemainPageableAfterAllNetworkSourcesEnded() {
        urls.forEachIndexed { index, url -> pages[url to 1] = Result.success(response(
            (1..10).map { data("$index-$it", 100L-index*10-it) })) }
        refresh()
        assertEquals(20, vm.messageData.value!!.size)
        assertFalse(vm.isEnd)
        onMain { vm.loadMore() }; waitDone()
        assertEquals(40, vm.messageData.value!!.size)
        onMain { vm.loadMore() }; waitDone()
        assertEquals(50, vm.messageData.value!!.size)
        assertTrue(vm.isEnd)
    }

    @Test fun globalPagingFetchesNewerSourceBeforeShowingBufferedOldSource() {
        pages[atMe to 1] = Result.success(response((1..20).map { data("A$it", 1000L-it) }))
        pages[atMe to 2] = Result.success(response((21..40).map { data("A$it", 1000L-it) }))
        pages["/v6/notification/atCommentMeList" to 1] = Result.success(response((1..20).map { data("B$it", 100L-it) }))
        refresh()
        onMain { vm.loadMore() }; waitDone()
        assertEquals((1..40).map { "A$it" }, vm.messageData.value!!.map { it.data.id })
    }

    @Test fun lateOldAccountResponseCannotPublishOrOverwriteNewAccountCache() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        `when`(network.getMessage(anyString(), anyInt(), nullable(String::class.java))).thenAnswer {
            val url = it.getArgument<String>(0)
            flow {
                if (url == atMe) { waiting.complete(Unit); release.await() }
                emit(Result.success(response(if (url == atMe) listOf(data("old-A")) else emptyList())))
            }
        }
        onMain { vm.refresh() }
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!waiting.isCompleted) {
            dispatcher.scheduler.runCurrent()
            if (System.nanoTime() > deadline) fail("Request never reached network gate")
            Thread.yield()
        }
        PrefManager.uid = "test-B"
        val b = MessageAccount.current()!!
        MessageCenterCacheStore.save(b, listOf(Item("atMe", data("B"))))
        onMain { vm.bindAccount() }
        release.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals("B", vm.messageData.value!!.single().data.id)
        assertEquals("B", MessageCenterCacheStore.load(b).single().data.id)
    }
}
