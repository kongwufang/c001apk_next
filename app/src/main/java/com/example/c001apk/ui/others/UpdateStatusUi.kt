package com.example.c001apk.ui.others

import android.widget.TextView
import androidx.core.view.isVisible
import com.example.c001apk.R
import com.example.c001apk.util.UpdateState
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Used by the drawer card and the manually opened About dialog. */
internal fun bindUpdateStatus(
    status: TextView,
    progress: LinearProgressIndicator,
    state: UpdateState,
) {
    val percent = (state as? UpdateState.Downloading)?.percent?.coerceIn(0, 100)
    val text = when {
        percent != null -> status.context.getString(R.string.update_downloading_progress, percent)
        state is UpdateState.Waiting -> status.context.getString(R.string.update_waiting_for_wifi)
        state is UpdateState.Ready -> status.context.getString(R.string.update_ready_to_install)
        state == UpdateState.Checking -> status.context.getString(R.string.about_checking_update)
        else -> null
    }
    status.isVisible = text != null
    if (status.text != text) status.text = text
    progress.isVisible = percent != null
    if (percent != null) progress.setProgressCompat(percent, false)
}
