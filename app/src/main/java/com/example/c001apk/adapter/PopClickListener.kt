package com.example.c001apk.adapter

import android.content.Context
import android.view.MenuItem
import androidx.appcompat.widget.PopupMenu
import com.example.c001apk.R
import com.example.c001apk.util.OfficialReport

class PopClickListener(
    val listener: ItemListener,
    val context: Context,
    val entityType: String,
    val id: String,
    val uid: String,
    val position: Int,
    // 该条动态当前是否已在自己主页置顶（只有 feed 卡片会传，回复卡片用默认值）
    val isStickTop: Boolean = false
) :
    PopupMenu.OnMenuItemClickListener {
    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.block -> {
                listener.onBlockUser(id, uid, position)
            }

            R.id.report -> {
                OfficialReport.open(context, entityType, if (entityType == "user") uid else id)
            }

            R.id.delete -> {
                listener.onDeleteClicked(entityType, id, position)
            }

            R.id.publishStatus -> {
                listener.onChangePublishStatus(id, position)
            }

            R.id.stickTop -> {
                listener.onChangeStickTop(id, isStickTop, position)
            }

            R.id.show -> {
                listener.showTotalReply(id, uid, position, null)
            }
        }
        return true
    }

}
