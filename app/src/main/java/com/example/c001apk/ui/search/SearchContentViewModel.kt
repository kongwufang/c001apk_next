package com.example.c001apk.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.constant.Constants.LOADING_EMPTY
import com.example.c001apk.constant.Constants.LOADING_END
import com.example.c001apk.constant.Constants.LOADING_FAILED
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.ui.base.BaseAppViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
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

    private fun buildSearchSortCard(): HomeFeedResponse.Data = Gson().fromJson(
        """{
          "entityType":"card",
          "entityTemplate":"sortSelectCard",
          "title":"动态",
          "entities":[
            {"title":"综合","url":"c001apk://search-sort/default"},
            {"title":"实时","url":"c001apk://search-sort/dateline"},
            {"title":"热度","url":"c001apk://search-sort/hot"},
            {"title":"评论","url":"c001apk://search-sort/reply"},
            {"title":"精确","url":"c001apk://search-sort/none"}
          ]
        }""",
        HomeFeedResponse.Data::class.java
    )

    override fun onSearchSort(value: String) {
        sort = value
        isStrict = if (value == "none") 1 else 0
        dataList.value = dataList.value.orEmpty()
            .filterNot { it.entityTemplate == "sortSelectCard" }
        footerState.value = FooterState.LoadingDone
        loadingState.value = LoadingState.Loading
    }
    override fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
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

                            // ★ 2026-11-14：官方「综合」= 卡片段 + 动态段 拼装
                            //   我们原来只请求 type=all ⇒ 只有卡片、下面空白 ✗
                            //   这里补一次 feed 请求，把动态接在卡片后面（用现有列表逻辑与样式）
                            if (type == "all" && !isLoadMore) {
                                networkRepo.getSearch(
                                    "feed", "all", "default", keyWord,
                                    pageType, pageParam, 1, null, 0
                                ).collect { r2 ->
                                    val extra = r2.getOrNull()?.data
                                    if (!extra.isNullOrEmpty()) {
                                        // postValue 异步发布，不能再从 value 读回尚未发布的卡片列表。
                                        val merged = searchList.toMutableList()
                                        val feedIndex = merged.indexOfFirst { it.entityType == "feed" }
                                         if (merged.none { it.entityTemplate == "sortSelectCard" }) {
                                             merged.add(
                                                 if (feedIndex >= 0) feedIndex else merged.size,
                                                 buildSearchSortCard()
                                             )
                                         }
                                         extra.filter { item ->
                                            item.entityType == "feed" &&
                                                !blackListRepo.checkUid(item.userInfo?.uid.toString()) &&
                                                !blackListRepo.checkTopic(item.tags + item.ttitle +
                                                    item.relationRows?.getOrNull(0)?.title)
                                        }.forEach { item ->
                                            if (merged.none { it.entityType == "feed" && it.id == item.id }) {
                                                merged.add(item)
                                            }
                                        }
                                        dataList.postValue(merged)
                                    }
                                }
                            }
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

}