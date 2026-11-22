package com.example.c001apk.ui.follow

import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.c001apk.R
import com.example.c001apk.logic.repository.FollowRepo
import com.example.c001apk.util.PrefManager
import com.google.android.material.color.MaterialColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FollowButtonEntryPoint {
    fun followRepo(): FollowRepo
}

/** Binds a user's action to Room in local mode; never displays the server's follow state there. */
internal object FollowButton {
    fun clear(view: TextView) {
        (view.getTag(R.id.local_follow_binding) as? LocalBinding)?.let {
            it.stop()
            view.removeOnAttachStateChangeListener(it)
        }
        view.setTag(R.id.local_follow_binding, null)
        view.setOnClickListener(null)
        view.isEnabled = true
    }

    fun bind(
        view: TextView,
        uid: String?,
        cloudFollowed: Boolean?,
        followedText: String = "已关注",
        tintText: Boolean = false,
        onCloudClick: (Boolean) -> Unit,
    ) {
        clear(view)
        val available = PrefManager.canFollowUser(uid)
        val cloud = PrefManager.saveFollowToCloud
        view.isVisible = false
        if (!available || uid == null) return
        if (cloud) {
            if (cloudFollowed == null) return
            label(view, cloudFollowed, followedText, tintText)
            view.setOnClickListener {
                if (PrefManager.saveFollowToCloud && PrefManager.canFollowUser(uid)) onCloudClick(cloudFollowed)
            }
        } else {
            val repo = EntryPointAccessors.fromApplication(
                view.context.applicationContext, FollowButtonEntryPoint::class.java
            ).followRepo()
            val binding = LocalBinding(view, repo, uid, followedText, tintText)
            view.setTag(R.id.local_follow_binding, binding)
            view.addOnAttachStateChangeListener(binding)
            if (view.isAttachedToWindow) binding.onViewAttachedToWindow(view)
        }
    }

    private fun label(view: TextView, followed: Boolean, followedText: String, tintText: Boolean) {
        view.text = if (followed) followedText else "关注"
        if (tintText) {
            view.setTextColor(if (followed) view.context.getColor(android.R.color.darker_gray)
            else MaterialColors.getColor(view.context, androidx.appcompat.R.attr.colorPrimary, view.currentTextColor))
        }
        view.isVisible = true
    }

    private class LocalBinding(
        private val view: TextView,
        private val repo: FollowRepo,
        private val uid: String,
        private val followedText: String,
        private val tintText: Boolean,
    ) : View.OnAttachStateChangeListener {
        private var source: LiveData<Boolean>? = null
        private var followed: Boolean? = null
        private var pending: Boolean? = null
        private val observer = Observer<Boolean> { value ->
            followed = value
            if (value == pending) pending = null
            render()
        }

        override fun onViewAttachedToWindow(v: View) {
            val owner = view.findViewTreeLifecycleOwner() ?: return
            source = repo.observeFollowed(uid).also { it.observe(owner, observer) }
            view.setOnClickListener {
                val current = followed ?: return@setOnClickListener
                if (pending != null || PrefManager.saveFollowToCloud || !PrefManager.canFollowUser(uid)) {
                    return@setOnClickListener
                }
                pending = !current
                render()
                owner.lifecycleScope.launch {
                    try {
                        repo.setFollowed(uid, !current)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        pending = null
                        if (source != null) {
                            render()
                            Toast.makeText(view.context, "本机关注未保存，请重试", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        override fun onViewDetachedFromWindow(v: View) = stop()

        fun stop() {
            source?.removeObserver(observer)
            source = null
            followed = null
            pending = null
            view.setOnClickListener(null)
            view.isVisible = false
        }

        private fun render() {
            val current = followed ?: return
            label(view, current, followedText, tintText)
            view.isEnabled = pending == null
        }
    }
}
