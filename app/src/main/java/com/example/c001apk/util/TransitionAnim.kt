package com.example.c001apk.util

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.FragmentTransaction
import com.example.c001apk.R

/** Ordinary pages use platform transitions by default; floating composers still slide vertically. */
object TransitionAnim {
    const val MODE_SYSTEM = "system"
    const val MODE_CUSTOM = "custom"

    // Keep the saved `m2` key: Android's fast_out_slow_in is the same curve.
    const val CURVE_ANDROID = "m2"
    const val CURVE_M3_EMPHASIZED = "emph"
    const val TYPE_SLIDE = "slide"
    const val TYPE_PARALLAX = "parallax"
    const val TYPE_NONE = "none"

    private const val FALLBACK_DURATION = 300

    private data class Animations(val enter: Int, val exit: Int)

    private fun res(type: String, curve: String, duration: Int, slot: String): Int =
        TransitionAnimTable.resolve("${type}_${curve}_${duration}_$slot").takeIf { it != 0 }
            ?: TransitionAnimTable.resolve("${TYPE_SLIDE}_${CURVE_M3_EMPHASIZED}_${FALLBACK_DURATION}_$slot")

    private fun resolve(enterSlot: String, exitSlot: String, sheet: Boolean = false): Animations? {
        val custom = PrefManager.animMode == MODE_CUSTOM
        val type = if (custom) PrefManager.animType else null
        if (type == TYPE_NONE) return Animations(0, 0)
        if (sheet) return if (enterSlot == "rin") Animations(R.anim.anim_bottom_sheet_slide_up, 0)
            else Animations(0, R.anim.anim_bottom_sheet_slide_down)
        if (type == null) return null
        val curve = PrefManager.animCurve
        val duration = PrefManager.animDuration
        return Animations(res(type, curve, duration, enterSlot), res(type, curve, duration, exitSlot))
    }

    fun startActivity(context: Context, intent: Intent, sheet: Boolean = false) {
        val anim = resolve("rin", "lout", sheet)
        if (anim == null) context.startActivity(intent)
        else context.startActivity(intent, ActivityOptions.makeCustomAnimation(context, anim.enter, anim.exit).toBundle())
    }

    fun enterOptionsCompat(context: Context, sheet: Boolean = false): ActivityOptionsCompat? =
        resolve("rin", "lout", sheet)?.let { ActivityOptionsCompat.makeCustomAnimation(context, it.enter, it.exit) }

    /** The external picker follows the platform unless custom animations are enabled. */
    fun photoPickerOptions(context: Context): ActivityOptionsCompat? {
        if (PrefManager.animMode != MODE_CUSTOM) return null
        return enterOptionsCompat(context, sheet = true)
    }

    /** Only shorten an actual second close, never consume, enqueue or synthesize Back. */
    @Suppress("DEPRECATION")
    internal inline fun finish(activity: Activity, sheet: Boolean = false, close: () -> Unit) {
        if (activity.isFinishing) return
        val taskId = activity.taskId
        val fast = RapidReturn.shouldSkipClose(activity)
        if (fast && Build.VERSION.SDK_INT >= 34) {
            // Set before finish for the newer window/shell transition pipeline.
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        close()
        if (!activity.isFinishing) {
            if (fast && Build.VERSION.SDK_INT >= 34) {
                activity.clearOverrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE)
            }
            return
        }
        if (fast) {
            if (Build.VERSION.SDK_INT < 34) activity.overridePendingTransition(0, 0)
        } else applyReturn(activity, sheet)
        RapidReturn.onClose(activity, taskId)
    }

    /** Call after finish(); (0, 0) is explicit no-animation, not the system default. */
    @Suppress("DEPRECATION")
    internal fun applyReturn(activity: Activity, sheet: Boolean = false) {
        resolve("lin", "rout", sheet)?.let { activity.overridePendingTransition(it.enter, it.exit) }
    }

    /** In-place Fragment navigation has no Activity window transition to inherit. */
    fun applyFragment(transaction: FragmentTransaction): FragmentTransaction {
        val forward = resolve("rin", "lout") ?: return transaction
        val back = resolve("lin", "rout") ?: return transaction
        return transaction.setCustomAnimations(forward.enter, forward.exit, back.enter, back.exit)
    }
}
