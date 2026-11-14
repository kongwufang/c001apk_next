package com.example.c001apk.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.constant.Constants.LOADING_EMPTY
import com.example.c001apk.constant.Constants.LOADING_END
import com.example.c001apk.constant.Constants.LOADING_FAILED
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.logic.repository.HistoryFavoriteRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.ui.base.BaseAppViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.c001apk.util.ApiError
import com.example.c001apk.util.Event
import com.google.gson.Gson

class SearchContentViewModel @AssistedInject constructor(
    @Assisted("keyWord") var keyWord: String,
    @Assisted("type") var type: String,
    @Assisted("pageType") val pageType: String,
    @Assisted("pageParam") var pageParam: String,
    blackListRepo: BlackListRepo,
    historyRepo: HistoryFavoriteRepo,
    networkRepo: NetworkRepo
) : BaseAppViewModel(blackListRepo, historyRepo, networkRepo) {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("keyWord") keyWord: String,
            @Assisted("type") type: String,
            @Assisted("pageType") pageType: String,
            @Assisted("pageParam") pageParam: String,
        ): SearchContentViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            assistedFactory: Factory,
            keyWord: String,
            type: String,
            pageType: String,
            pageParam: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(keyWord, type, pageType, pageParam) as T
            }
        }
    }

    var feedType: String = "all"
    var sort: String = "default" //hot // reply // dateline // none(精确)
    /** 「精确」排序：sort=none 且 isStrict=1（抓包实证） */
    var isStrict: Int = 0
    val searchTabEvent = MutableLiveData<Event<String>>()

    override fun onSearchTab(value: String) {
        if (value == "product" || value == "feedTopic") searchTabEvent.value = Event(value)
    }

    private var searchJob: Job? = null
    private var keepSearchCards = false

    private fun buildSearchSortCard(): HomeFeedResponse.Data = Gson().fromJson(
        """{"entityType":"card","entityTemplate":"sortSelectCard","entityId":"c001apk-search-sort","title":"动态"}""",
        HomeFeedResponse.Data::class.java
    ).copy(selectedTab = sort)

    override fun onSearchSort(value: String) {
        searchJob?.cancel()
        sort = value
        isStrict = if (value == "none") 1 else 0
        keepSearchCards = type == "all"
        dataList.value = if (keepSearchCards) {
            // 请求完成前保留旧动态占位，避免列表缩短导致排序条跳到屏幕底部。
            val current = dataList.value.orEmpty().map {
                if (it.entityId == "c001apk-search-sort") buildSearchSortCard() else it
            }
            if (current.any { it.entityId == "c001apk-search-sort" }) current
            else current + buildSearchSortCard()
        } else emptyList()
        footerState.value = FooterState.LoadingDone
        loadingState.value = LoadingState.Loading
    }

    override fun fetchData() {
        searchJob?.cancel()
        val previous = dataList.value.orEmpty()
        val keepCards = keepSearchCards || isLoadMore
        keepSearchCards = false
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            if (type == "all") {
                fetchCombinedData(previous, keepCards)
                return@launch
            }
            networkRepo.getSearch(
                type, feedType, sort, keyWord,
                pageType, pageParam, page, lastItem, isStrict
            )
                .onStart {
                    if (isLoadMore) {
                        if (listSize <= 0)
                            loadingState.postValue(LoadingState.Loading)
                        else
                            footerState.postValue(FooterState.Loading)
                    }
                }
                .collect { result ->
                    val searchList = dataList.value?.toMutableList() ?: ArrayList()
                    val search = result.getOrNull()
                    if (search != null) {
                        if (!search.message.isNullOrEmpty()) {
                            if (listSize <= 0)
                                loadingState.postValue(LoadingState.LoadingError(search.message))
                            else
                                footerState.postValue(FooterState.LoadingError(search.message))
                            return@collect
                        } else if (!search.data.isNullOrEmpty()) {
                            lastItem = search.data.last().id
                            if (isRefreshing)
                                searchList.clear()
                            // ★ 2026-11-13：首次进入（既非 refresh 也非 loadMore）也必须入列
                            //   原来只在 refresh/loadMore 时 add ⇒ 「综合」tab 永远空白 ✗
                            run {
                                if (type == "feed")
                                    search.data.forEach {
                                        if (it.entityType == "feed")
                                            if (!blackListRepo.checkUid(it.userInfo?.uid.toString())
                                                && !blackListRepo.checkTopic(
                                                    it.tags + it.ttitle + it.relationRows?.getOrNull(
                                                        0
                                                    )?.title
                                                )
                                            )
                                                searchList.add(it)
                                    }
                                else
                                    searchList.addAll(search.data)
                            }
                            page++
                            if (listSize <= 0)
                                loadingState.postValue(LoadingState.LoadingDone)
                            else
                                footerState.postValue(FooterState.LoadingDone)
                            dataList.postValue(searchList)

                        } else {
                            isEnd = true
                            if (listSize <= 0)
                                loadingState.postValue(LoadingState.LoadingFailed(LOADING_EMPTY))
                            else {
                                if (isRefreshing)
                                    dataList.postValue(emptyList())
                                footerState.postValue(FooterState.LoadingEnd(LOADING_END))
                            }
                        }
                    } else {
                        isEnd = true
                        if (listSize <= 0)
                            loadingState.postValue(LoadingState.LoadingFailed(ApiError.textOf(result.exceptionOrNull())))
                        else
                            footerState.postValue(FooterState.LoadingError(ApiError.textOf(result.exceptionOrNull())))
                        result.exceptionOrNull()?.printStackTrace()
                    }
                    isRefreshing = false
                    isLoadMore = false
                }
        }
    }

    /** 综合页的卡片段与动态段分别请求；排序和翻页只更新动态。 */
    private suspend fun fetchCombinedData(previous: List<HomeFeedResponse.Data>, keepCards: Boolean) {
        val requestPage = page
        val requestSort = sort
        val requestStrict = isStrict
        val loadingMore = isLoadMore
        val cards = previous.filterNot {
            it.entityType == "feed" || it.entityTemplate == "sortSelectCard"
        }.toMutableList()
        if (!keepCards) {
            val result = networkRepo.getSearch(
                "all", feedType, "default", keyWord, pageType, pageParam, 1, null, 0
            ).first()
            val response = result.getOrNull()
            if (response == null || !response.message.isNullOrEmpty()) {
                combinedError(response?.message ?: ApiError.textOf(result.exceptionOrNull()))
                return
            }
            cards.clear()
            cards.addAll(response.data.orEmpty().filterNot {
                it.entityType == "feed" || it.entityTemplate == "sortSelectCard"
            })
        }
        withContext(Dispatchers.Main) { footerState.value = FooterState.Loading }
        val result = networkRepo.getSearch(
            "feed", feedType, requestSort, keyWord, pageType, pageParam,
            requestPage, if (loadingMore) lastItem else null, requestStrict
        ).first()
        val response = result.getOrNull()
        if (response == null || !response.message.isNullOrEmpty()) {
            withContext(Dispatchers.Main) {
                if (!loadingMore) dataList.value = cards + buildSearchSortCard()
            }
            combinedError(response?.message ?: ApiError.textOf(result.exceptionOrNull()))
            return
        }
        val incoming = response.data.orEmpty()
        val feeds = if (loadingMore) previous.filter { it.entityType == "feed" }.toMutableList()
                    else mutableListOf()
        incoming.filter { item ->
            item.entityType == "feed" &&
                !blackListRepo.checkUid(item.userInfo?.uid.toString()) &&
                !blackListRepo.checkTopic(item.tags + item.ttitle + item.relationRows?.getOrNull(0)?.title)
        }.forEach { item ->
            if (feeds.none { it.id == item.id }) feeds.add(item)
        }
        withContext(Dispatchers.Main) {
            ensureActive()
            lastItem = incoming.lastOrNull()?.id
            page = requestPage + 1
            isEnd = incoming.isEmpty()
            dataList.value = cards + buildSearchSortCard() + feeds
            loadingState.value = LoadingState.LoadingDone
            footerState.value = if (isEnd) FooterState.LoadingEnd(LOADING_END) else FooterState.LoadingDone
            isRefreshing = false
            isLoadMore = false
        }
    }

    private suspend fun combinedError(message: String) = withContext(Dispatchers.Main) {
        if (dataList.value.isNullOrEmpty()) loadingState.value = LoadingState.LoadingFailed(message)
        else {
            loadingState.value = LoadingState.LoadingDone
            footerState.value = FooterState.LoadingError(message)
        }
        isRefreshing = false
        isLoadMore = false
    }
}
