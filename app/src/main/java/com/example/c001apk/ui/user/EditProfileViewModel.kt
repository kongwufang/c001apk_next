package com.example.c001apk.ui.user

import com.example.c001apk.util.AppLog
import com.example.c001apk.util.LogModule
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.logic.model.OSSUploadPrepareResponse
import com.example.c001apk.logic.model.ProfileEditResponse
import com.example.c001apk.logic.model.UserProfileResponse
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.ui.article.ArticleUploadFile
import com.example.c001apk.util.Event
import com.example.c001apk.util.ImageUtil.toHex
import com.example.c001apk.util.PrefManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.inject.Inject

/**
 * 编辑资料（个人主页）。
 *
 * 所有改动都是「点一下存一下」——和官方一样，每项单独走一次接口：
 * - 头像 `POST /v6/account/changeAvatar`（multipart，字段名 imgFile，返回新头像地址）
 * - 背景图 先 OSS 上传（uploadBucket=avatar & uploadDir=cover）再 `POST /v6/account/changeAvatarCover`
 * - 性别 / 生日 / 地区 / 签名 `POST /v6/account/changeProfile`（key + value）
 * - 生日、地区设成保密 `POST /v6/account/resetProfile`（key=birth / location）
 *
 * 2026-09-24 抓包 + 实测全部核对通过：
 * - `changeProfile` / `resetProfile` / `changeAvatar` 与实现完全一致；
 * - 背景图 `ossUploadPrepare`（uploadBucket=avatar & uploadDir=cover）request body 246 字节
 *   与官方抓包**一模一样**，返回 prefix=`http://avatar.coolapk.com` + fileName=`cover/<uid>_<ts>.jpg`，
 *   PUT 到 `coolapk-oss-avatar.oss-cn-hangzhou.aliyuncs.com`；
 * - `changeAvatarCover` 的 `url` 就是 `$prefix/$fileName`（84 字节与官方抓包一致），返回 `{"data":"上传成功"}`。
 * 官方还有「预置背景图」（GET /v6/account/avatarCoverList）——本 App 不做该功能。
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val networkRepo: NetworkRepo
) : ViewModel() {

    val loadingState = MutableLiveData<LoadingState>()
    val profile = MutableLiveData<UserProfileResponse.Data>()
    val toastText = MutableLiveData<Event<String?>>()

    /** 保存成功（回到个人主页要重新拉一次资料） */
    val over = MutableLiveData<Event<Boolean>>()

    /** 背景图 OSS 上传凭证 */
    val uploadImage = MutableLiveData<Event<OSSUploadPrepareResponse.Data>>()

    /** 这次进来有没有改过东西（决定返回时刷不刷主页） */
    var changed = false
        private set

    private val ossUploadPrepareData: HashMap<String, String> = HashMap()

    fun fetchProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.getProfile(PrefManager.uid)
                .collect { result ->
                    val response = result.getOrNull()
                    val data = response?.data
                    if (data != null) {
                        publishProfile(data)
                        loadingState.postValue(LoadingState.LoadingDone)
                    } else {
                        response?.message?.let { toastText.postValue(Event(it)) }
                        loadingState.postValue(LoadingState.LoadingError("response is null"))
                    }
                }
        }
    }

    /** 抽一层非空入口：Lint NullSafeMutableLiveData 不允许把可空值直接塞进非空 LiveData */
    private fun publishProfile(data: UserProfileResponse.Data) = profile.postValue(data)

    // ---------------- 文本类字段 ----------------

    /** 性别：1 男 / 0 女 / -1 保密 */
    fun onPostGender(gender: Int) = postChangeProfile("gender", gender.toString())

    /** 生日：`value={"birthyear":..,"birthmonth":..,"birthday":..}`（key 为空） */
    fun onPostBirthday(year: Int, month: Int, day: Int) = postChangeProfile(
        "", Gson().toJson(mapOf("birthyear" to year, "birthmonth" to month, "birthday" to day))
    )

    /** 生日设为保密 */
    fun onResetBirthday() = postResetProfile("birth")

    /** 地区：`value={"province":..,"city":..}`（key 为空） */
    fun onPostCity(province: String, city: String) = postChangeProfile(
        "", Gson().toJson(mapOf("province" to province, "city" to city))
    )

    /** 地区设为保密 */
    fun onResetCity() = postResetProfile("location")

    /** 个人签名 */
    fun onPostBio(bio: String) = postChangeProfile("bio", bio)

    private fun postChangeProfile(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.changeProfile(key, value)
                .collect { result -> handleEditResponse(result.getOrNull()) }
        }
    }

    private fun postResetProfile(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.resetProfile(key)
                .collect { result -> handleEditResponse(result.getOrNull()) }
        }
    }

    /** `message` 为空就是成功（服务端失败时一定带 message） */
    private fun handleEditResponse(response: ProfileEditResponse?) {
        AppLog.i(LogModule.USER, "EditProfileViewModel", "changeProfile/resetProfile -> message=" + response?.message)
        if (response != null && response.message == null) {
            changed = true
            over.postValue(Event(true))
        } else {
            toastText.postValue(Event(response?.message ?: "修改失败"))
        }
    }

    // ---------------- 图片 ----------------

    /**
     * 换头像：multipart，服务端字段名固定 imgFile。
     * 抓包核对（2026-09-24）：官方发的 part 名 = imgFile、filename = 图片 md5（**不带扩展名**）、
     * Content-Type = image/jpeg，响应 `{"data":"http://avatar.coolapk.com/..."}`。
     */
    fun onPostAvatar(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            loadingState.postValue(LoadingState.Loading)
            val bytes = runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            // 注意：ByteArray 没有 isNullOrEmpty()（那是 Array/CharSequence/Collection 的扩展）
            if (bytes == null || bytes.isEmpty()) {
                loadingState.postValue(LoadingState.LoadingDone)
                toastText.postValue(Event("读取图片失败"))
                return@launch
            }
            val md5 = MessageDigest.getInstance("MD5").digest(bytes).toHex()
            val part = MultipartBody.Part.createFormData(
                "imgFile", md5, bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            networkRepo.changeAvatar(part)
                .collect { result ->
                    val response = result.getOrNull()
                    val newAvatar = response?.data
                    loadingState.postValue(LoadingState.LoadingDone)
                    if (newAvatar != null) {
                        changed = true
                        PrefManager.userAvatar = newAvatar
                        over.postValue(Event(true))
                    } else {
                        toastText.postValue(Event(response?.message ?: "头像修改失败"))
                    }
                }
        }
    }

    /** 背景图第一步：取 OSS 上传凭证（和官方一致：uploadBucket=avatar，uploadDir=cover） */
    fun onPostOSSUploadPrepare(uploadFileList: List<ArticleUploadFile>) {
        ossUploadPrepareData["uploadBucket"] = "avatar"
        ossUploadPrepareData["uploadDir"] = "cover"
        ossUploadPrepareData["is_anonymous"] = "0"
        ossUploadPrepareData["uploadFileList"] = Gson().toJson(uploadFileList)
        ossUploadPrepareData["toUid"] = ""

        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postOSSUploadPrepare(ossUploadPrepareData)
                .collect { result ->
                    val response = result.getOrNull()
                    AppLog.i(LogModule.USER, "EditProfileViewModel", "postOSSUploadPrepare -> message=" + response?.message)
                    if (response?.message != null) {
                        toastText.postValue(Event("uploadPrepare error: ${response.message}"))
                    } else if (response?.data != null) {
                        val info = response.data.uploadPrepareInfo
                        AppLog.i(LogModule.USER, "EditProfileViewModel", "uploadPrepare ok: bucket=" + info.bucket + " endPoint=" + info.endPoint +
                                " prefix=" + info.uploadImagePrefix + " callbackUrl=" + info.callbackUrl +
                                " fileName=" + response.data.fileInfo.firstOrNull()?.uploadFileName
                        )
                        uploadImage.postValue(Event(response.data))
                    } else {
                        AppLog.i(LogModule.USER, "EditProfileViewModel", "uploadPrepare: response is null, ex=" + result.exceptionOrNull()?.message)
                        toastText.postValue(Event("response is null"))
                    }
                }
        }
    }

    /** 背景图第二步：把 OSS 上的地址回传给服务端 */
    fun onPostAvatarCover(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.changeAvatarCover(url)
                .collect { result ->
                    val response = result.getOrNull()
                    AppLog.i(LogModule.USER, "EditProfileViewModel", "changeAvatarCover url=" + url + " -> message=" + response?.message +
                            " data=" + response?.data + " ex=" + result.exceptionOrNull()?.message
                    )
                    if (response?.data != null) {
                        changed = true
                        over.postValue(Event(true))
                    } else {
                        toastText.postValue(Event(response?.message ?: "背景图修改失败"))
                    }
                }
        }
    }
}
