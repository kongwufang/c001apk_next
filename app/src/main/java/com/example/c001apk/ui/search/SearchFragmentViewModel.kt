package com.example.c001apk.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.SearchHotResponse
import com.example.c001apk.logic.model.SearchSuggestResponse
import com.example.c001apk.logic.model.StringEntity
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.logic.repository.SearchHistoryRepo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragmentViewModel @AssistedInject constructor(
    @Assisted("pageType") var pageType: String,
    @Assisted("pageParam") val pageParam: String,
    @Assisted("title") var title: String,
    private val historyRepo: SearchHistoryRepo,
    private val networkRepo: NetworkRepo,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("pageType") pageType: String,
            @Assisted("pageParam") pageParam: String,
            @Assisted("title") title: String,
        ): SearchFragmentViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            assistedFactory: Factory, pageType: String, pageParam: String, title: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(pageType, pageParam, title) as T
            }
        }
    }

    var type: String? = null

    val blackListLiveData: LiveData<List<StringEntity>> = historyRepo.loadAllListLive()

    fun insertData(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            with(StringEntity(data)) {
                if (historyRepo.checkHistory(data)) {
                    historyRepo.updateHistory(data, System.currentTimeMillis())
                } else
                    historyRepo.insertHistory(this)
            }

        }
    }

    fun deleteData(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.deleteHistory(data)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.deleteAllHistory()
        }
    }

    // ---------------- 搜索页默认态：热门搜索 + 热搜榜 ----------------

    private val _hotSearch = MutableLiveData<SearchHotResponse?>()
    val hotSearch: LiveData<SearchHotResponse?> = _hotSearch

    /** refresh = 1 时服务端会重新拉取热门搜索（对应标题栏右侧的刷新按钮） */
    fun fetchHotSearch(refresh: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.getSearchHot(refresh)
                .collect { result ->
                    val response = result.getOrNull()
                    if (response?.data != null)
                        _hotSearch.postValue(response)
                    else
                        result.exceptionOrNull()?.printStackTrace()
                }
        }
    }

    // ---------------- 输入过程中的搜索联想 ----------------

    private val _suggest = MutableLiveData<List<SearchSuggestResponse.Data>?>()
    val suggest: LiveData<List<SearchSuggestResponse.Data>?> = _suggest

    private var suggestJob: Job? = null

    /** 每次输入都取消上一次请求，避免旧响应把新结果覆盖掉 */
    fun fetchSuggest(keyWord: String) {
        suggestJob?.cancel()
        val key = keyWord.trim()
        if (key.isEmpty()) {
            _suggest.postValue(null)
            return
        }
        suggestJob = viewModelScope.launch(Dispatchers.IO) {
            // 输入很快，稍等一下再请求，避免每敲一个字都发一次
            delay(250)
            networkRepo.getSuggestSearchWords(key)
                .collect { result ->
                    val response = result.getOrNull()
                    if (response != null)
                        _suggest.postValue(response.data)
                    else if (result.exceptionOrNull() !is CancellationException)
                        result.exceptionOrNull()?.printStackTrace()
                }
        }
    }

    /** 离开搜索页时清掉联想列表，避免下次进来闪现旧数据 */
    fun clearSuggest() {
        suggestJob?.cancel()
        _suggest.postValue(null)
    }

}