package com.example.c001apk.ui.message

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.constant.Constants.LOADING_EMPTY
import com.example.c001apk.constant.Constants.LOADING_END
import com.example.c001apk.constant.Constants.LOADING_FAILED
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.logic.repository.HistoryFavoriteRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Account-owned snapshots, replaced on successful refresh rather than appended forever. */
@HiltViewModel
class MessageCenterViewModel @Inject constructor(
    private val blackListRepo: BlackListRepo,
    private val historyRepo: HistoryFavoriteRepo,
    private val networkRepo: NetworkRepo
) : ViewModel() {
    private val pageSize = 20
    private val sources = linkedMapOf(
        "atMe" to "/v6/notification/atMeList",
        "atCommentMe" to "/v6/notification/atCommentMeList",
        "commentMe" to NotificationV18Kit.URL,
        "feedLike" to NotificationV18Kit.LIKE_URL,
        "contactsFollow" to "/v6/notification/contactsFollowList",
    )
    private data class SourceState(
        val items: List<MessageCenterAdapter.Item>,
        val page: Int,
        val lastItem: String?,
        val end: Boolean
    )
    var isRefreshing = false
        private set
    var isLoadMore = false
        private set
    var isEnd = false
    val messCountList = MutableLiveData<Boolean>()
    val footerState = MutableLiveData<FooterState>()
    val messageData = MutableLiveData<List<MessageCenterAdapter.Item>>()
    val toastText = MutableLiveData<Event<String>>()

    private var account: MessageAccount? = null
    private var generation = 0L
    private var job: Job? = null
    private var cacheWrite: Job? = null
    private var snapshot = emptyList<MessageCenterAdapter.Item>()
    private var shown = 0
    private val states = mutableMapOf<String, SourceState>()
    private val pendingSeen = mutableMapOf<String, MutableSet<String>>()

    /** Must run on main, before observing/restoring the list. */
    fun bindAccount() {
        val current = MessageAccount.current()
        if (account == current) return
        invalidateAccount()
        account = current
        snapshot = current?.let { MessageCenterCacheStore.load(it) }.orEmpty()
        shown = minOf(pageSize, snapshot.size)
        messageData.value = snapshot.take(shown)
    }

    fun invalidateAccount() {
        generation++
        job?.cancel()
        job = null
        account = null
        snapshot = emptyList()
        shown = 0
        states.clear()
        pendingSeen.clear()
        isRefreshing = false
        isLoadMore = false
        isEnd = false
        messageData.value = emptyList()
        footerState.value = FooterState.LoadingDone
    }

    private fun current(owner: MessageAccount, request: Long): Boolean =
        owner == account && owner.isCurrent() && request == generation

    fun refresh() {
        bindAccount()
        val owner = account ?: return
        if (isRefreshing) return
        // A refresh supersedes pagination; its cancelled reply must not publish later.
        job?.cancel()
        val request = ++generation
        val need = maxOf(shown, pageSize)
        isRefreshing = true
        isLoadMore = false
        isEnd = false
        job = viewModelScope.launch {
            try {
                fetchCounts(owner, request)
                val fetched = coroutineScope {
                    sources.keys.map { category -> async {
                        category to runCatching { fetchCategory(category, null, need) }
                            .also { ensureActive() }
                    } }.awaitAll()
                }
                if (!current(owner, request)) return@launch
                val successful = fetched.mapNotNull { (category, result) ->
                    result.getOrNull()?.let { category to it }
                }.toMap()
                if (successful.isNotEmpty()) {
                    // Only successful categories are authoritative. A failed source keeps its cache.
                    snapshot = MessageSnapshot.replaceCategories(snapshot,
                        successful.mapValues { it.value.items })
                    states.clear()
                    states.putAll(successful)
                    pendingSeen.forEach { (category, ids) ->
                        if (category in successful) ids.retainAll(snapshot.filter { it.category == category }.map { it.data.id }.toSet())
                    }
                    shown = minOf(need, snapshot.size)
                    publish(owner)
                }
                isEnd = successful.size == sources.size && states.values.all { it.end } && shown >= snapshot.size
                footerState.value = when {
                    successful.size < sources.size -> FooterState.LoadingError(LOADING_FAILED)
                    snapshot.isEmpty() -> FooterState.LoadingEnd(LOADING_EMPTY)
                    isEnd && shown >= snapshot.size -> FooterState.LoadingEnd(LOADING_END)
                    else -> FooterState.LoadingDone
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (current(owner, request)) footerState.value = FooterState.LoadingError(LOADING_FAILED)
            } finally {
                if (current(owner, request)) { isRefreshing = false; isLoadMore = false }
            }
        }
    }

    fun loadMore() {
        bindAccount()
        val owner = account ?: return
        if (isRefreshing || isLoadMore) return
        if (shown < snapshot.size && states.size == sources.size &&
            states.values.all { it.end || it.items.size >= shown + pageSize }) {
            shown = minOf(shown + pageSize, snapshot.size)
            isEnd = states.size == sources.size && states.values.all { it.end } && shown >= snapshot.size
            publish(owner)
            footerState.value = if (isEnd) FooterState.LoadingEnd(LOADING_END) else FooterState.LoadingDone
            return
        }
        if (isEnd) return
        // Failed categories have no cursor; retry a full refresh instead of inventing one.
        if (states.size < sources.size) { refresh(); return }
        val request = ++generation
        val need = shown + pageSize
        val previous = states.toMap()
        isLoadMore = true
        footerState.value = FooterState.Loading
        job = viewModelScope.launch {
            try {
                val fetched = coroutineScope {
                    sources.keys.map { category -> async {
                        category to runCatching { fetchCategory(category, previous[category], need) }
                            .also { ensureActive() }
                    } }.awaitAll()
                }
                if (!current(owner, request)) return@launch
                fetched.forEach { (category, result) -> result.getOrNull()?.let { states[category] = it } }
                snapshot = MessageSnapshot.replaceCategories(snapshot, states.mapValues { it.value.items })
                shown = minOf(need, snapshot.size)
                isEnd = states.values.all { it.end } && shown >= snapshot.size
                publish(owner)
                footerState.value = when {
                    fetched.any { it.second.isFailure } -> FooterState.LoadingError(LOADING_FAILED)
                    isEnd -> FooterState.LoadingEnd(LOADING_END)
                    else -> FooterState.LoadingDone
                }
            } catch (e: CancellationException) { throw e }
            finally { if (current(owner, request)) isLoadMore = false }
        }
    }

    /** Local state only: no shared pool is mutated by the five concurrent network requests. */
    private suspend fun fetchCategory(category: String, previous: SourceState?, need: Int): SourceState =
        withContext(Dispatchers.IO) {
            val rows = previous?.items.orEmpty().toMutableList()
            var page = previous?.page ?: 1
            var cursor = previous?.lastItem
            var end = previous?.end ?: false
            var rounds = 0
            while (!end && rows.size < need) {
                ensureActive()
                check(++rounds <= 100) { "Notification pagination did not converge" }
                val response = networkRepo.getMessage(sources.getValue(category), page, cursor)
                    .first().getOrThrow()
                check(response.message.isNullOrEmpty() && (response.error ?: 0) == 0) {
                    response.message ?: "Notification request failed"
                }
                val data = requireNotNull(response.data) { "Missing notification data" }
                val rawSize = response.rawPageSize ?: data.size
                check(rawSize >= 0) { "Missing raw notification data" }
                if (rawSize == 0) { end = true; break }
                val nextCursor = response.rawLastItem ?: data.lastOrNull()?.id
                check(!nextCursor.isNullOrBlank() && nextCursor != cursor) { "Repeated notification cursor" }
                for (item in data) {
                    if (isCandidate(category, item)) rows.add(MessageCenterAdapter.Item(category, item))
                }
                val unique = MessageSnapshot.normalize(rows)
                rows.clear(); rows.addAll(unique)
                cursor = nextCursor
                page++
            }
            SourceState(MessageSnapshot.normalize(rows), page, cursor, end)
        }

    private suspend fun isCandidate(category: String, data: MessageResponse.Data): Boolean {
        if (data.id.isBlank() || MessageKit.isSecretaryNotify(data)) return false
        val uid = when (category) {
            "feedLike" -> data.likeUid
            "atMe", "atCommentMe" -> data.uid
            else -> data.fromuid
        }
        return !blackListRepo.checkUid(uid)
    }

    /** Snapshot already reconciled/sorted; DiffUtil may insert/delete/move anywhere. */
    private fun publish(owner: MessageAccount) {
        if (!owner.isCurrent()) return
        val visible = snapshot.take(shown)
        visible.forEach { pendingSeen.getOrPut(it.category) { mutableSetOf() }.add(it.data.id) }
        messageData.value = visible
        val previousWrite = cacheWrite
        val saved = snapshot.toList()
        cacheWrite = viewModelScope.launch(Dispatchers.IO) {
            previousWrite?.join()
            MessageCenterCacheStore.save(owner, saved)
        }
        messCountList.value = true
    }

    internal suspend fun awaitCacheWrite() { cacheWrite?.join() }

    private fun stopListRequest() {
        generation++
        job?.cancel()
        isRefreshing = false
        isLoadMore = false
        isEnd = false
    }

    fun commitSeen() {
        val owner = account
        if (owner == null || !owner.isCurrent()) { pendingSeen.clear(); return }
        pendingSeen.forEach { (category, ids) ->
            val added = MessageCenterSeenStore.markSeen(category, ids.toList())
            MessageCenterSeenStore.addSeenCount(category, added)
        }
        pendingSeen.clear()
    }

    private suspend fun fetchCounts(owner: MessageAccount, request: Long) {
        val data = withContext(Dispatchers.IO) {
            networkRepo.checkCount().first().getOrNull()?.data
        } ?: return
        if (!current(owner, request)) return
        CookieUtil.atme = data.atme
        CookieUtil.atcommentme = data.atcommentme
        CookieUtil.commentme = data.commentme
        CookieUtil.feedlike = data.feedlike
        CookieUtil.contacts_follow = data.contactsFollow
        CookieUtil.badge = data.unreadBadge
        CookieUtil.notification = data.unreadNotification
        CookieUtil.message = data.message
        mapOf("atMe" to data.atme, "atCommentMe" to data.atcommentme,
            "commentMe" to data.commentme, "feedLike" to data.feedlike,
            "contactsFollow" to data.contactsFollow).forEach { (category, count) ->
            MessageCenterSeenStore.syncSeenCount(category, count)
        }
        messCountList.value = true
    }

    fun onCheckCount() {
        val owner = account ?: return
        val request = generation
        viewModelScope.launch { fetchCounts(owner, request) }
    }

    fun onPostDelete(position: Int, id: String) {
        val owner = account ?: return
        // Capture stable identity, not a position that may move while the request is in flight.
        val item = messageData.value?.getOrNull(position)?.takeIf { it.data.id == id } ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                networkRepo.postDelete("/v6/notification/delete", id).first()
            }
            if (account != owner || !owner.isCurrent()) return@launch
            val response = result.getOrNull() ?: return@launch
            if (response.data == "删除成功") {
                stopListRequest()
                snapshot = snapshot.filterNot { it.category == item.category && it.data.id == id }
                states.replaceAll { _, state -> state.copy(items = state.items.filterNot {
                    it.category == item.category && it.data.id == id
                }) }
                pendingSeen[item.category]?.remove(id)
                shown = minOf(shown, snapshot.size)
                publish(owner)
                toastText.value = Event(response.data)
            } else if (!response.message.isNullOrEmpty()) toastText.value = Event(response.message)
        }
    }

    fun blockUser(uid: String, position: Int) {
        val owner = account ?: return
        val item = messageData.value?.getOrNull(position) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { blackListRepo.saveUid(uid) }
            if (account != owner || !owner.isCurrent()) return@launch
            stopListRequest()
            fun blocked(row: MessageCenterAdapter.Item): Boolean = when (row.category) {
                "feedLike" -> row.data.likeUid
                "atMe", "atCommentMe" -> row.data.uid
                else -> row.data.fromuid
            } == uid
            val removed = snapshot.filter(::blocked)
            snapshot = snapshot.filterNot(::blocked)
            states.replaceAll { _, state -> state.copy(items = state.items.filterNot(::blocked)) }
            removed.forEach { pendingSeen[it.category]?.remove(it.data.id) }
            shown = minOf(shown, snapshot.size)
            publish(owner)
        }
    }

    fun saveHistory(id: String, uid: String, username: String, userAvatar: String,
        deviceTitle: String, message: String, dateline: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.saveHistory(id, uid, username, userAvatar, deviceTitle, message, dateline)
        }
    }
}
