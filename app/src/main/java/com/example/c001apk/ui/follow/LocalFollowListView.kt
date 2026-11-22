package com.example.c001apk.ui.follow

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.logic.model.FollowUserEntity
import com.example.c001apk.logic.repository.FollowRepo
import com.example.c001apk.ui.user.UserActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.dp

/** This list only reads local rows. User profiles are fetched when a row is opened. */
internal class LocalFollowListView(context: Context) : FrameLayout(context) {
    private val rows = FollowAdapter()
    private val empty = TextView(context).apply {
        text = "还没有本机关注\n打开用户主页，点「关注」即可添加"
        gravity = Gravity.CENTER
        isVisible = false
        setPadding(24.dp, 0, 24.dp, 0)
    }

    init {
        addView(RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = rows
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun observe(repo: FollowRepo, owner: LifecycleOwner) {
        repo.loadAllLive().observe(owner) {
            rows.submitList(it)
            empty.isVisible = it.isEmpty()
        }
    }

    private class FollowAdapter : ListAdapter<FollowUserEntity, FollowAdapter.Holder>(
        object : DiffUtil.ItemCallback<FollowUserEntity>() {
            override fun areItemsTheSame(old: FollowUserEntity, new: FollowUserEntity) = old.uid == new.uid
            override fun areContentsTheSame(old: FollowUserEntity, new: FollowUserEntity) = old == new
        }
    ) {
        class Holder(val label: TextView) : RecyclerView.ViewHolder(label)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24.dp, 16.dp, 24.dp, 16.dp)
            })

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = getItem(position)
            holder.label.text = if (item.username.isBlank()) "uid: ${item.uid}" else "${item.username}（${item.uid}）"
            holder.label.setOnClickListener {
                IntentUtil.startActivity<UserActivity>(it.context) { putExtra("id", item.uid) }
            }
        }
    }
}
