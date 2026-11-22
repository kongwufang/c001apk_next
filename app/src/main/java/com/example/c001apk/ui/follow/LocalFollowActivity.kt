package com.example.c001apk.ui.follow

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.c001apk.logic.repository.FollowRepo
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocalFollowActivity : AppCompatActivity() {
    @Inject lateinit var followRepo: FollowRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = LocalFollowListView(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(MaterialToolbar(context).apply {
                title = "本机关注"
                setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                navigationContentDescription = "返回"
                setNavigationOnClickListener { finish() }
            })
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        list.observe(followRepo, this)
    }
}
