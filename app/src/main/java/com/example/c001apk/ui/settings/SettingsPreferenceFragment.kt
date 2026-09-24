package com.example.c001apk.ui.settings

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.ui.blacklist.BlackListActivity
import com.example.c001apk.ui.main.MainActivity
import com.example.c001apk.ui.others.AboutActivity
import com.example.c001apk.ui.settings.params.ParamsActivity
import com.example.c001apk.util.CacheDataManager
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.TokenDeviceUtils.applyDefaultFingerprint
import com.example.c001apk.util.TokenDeviceUtils.randHexString
import com.example.c001apk.util.doOnMainThreadIdle
import com.example.c001apk.util.setBottomPaddingSpace
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import rikka.core.util.ResourceUtils
import rikka.material.preference.MaterialSwitchPreference
import rikka.preference.SimpleMenuPreference
import kotlin.system.exitProcess

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView {
        val recyclerView =
            super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        recyclerView.apply {
            //overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy > 0) {
                        (activity as? MainActivity)?.hideNavigationView()
                    } else if (dy < 0) {
                        (activity as? MainActivity)?.showNavigationView()
                    }
                }
            })

            doOnMainThreadIdle {
                recyclerView.setBottomPaddingSpace()
            }

        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(resources.getDrawable(R.drawable.divider, requireContext().theme))
    }

    /**
     * 进入二级设置页。
     *
     * androidx 默认实现只是把当前 [PreferenceScreen] 换成子页（同一个 RecyclerView 换数据），
     * 所以看起来是「闪现」；这里改成新开一个 Fragment 并压栈，配合自定义转场动画，
     * 做出和 App 其它页面一致的「从右到左滑入 / 返回时向右滑出」。返回栈交给 FragmentManager。
     */
    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        val key = preferenceScreen.key ?: return
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.right_in, R.anim.left_out_fragment,
                R.anim.left_in, R.anim.right_out
            )
            .replace(R.id.settingsContainer, newInstance(key), key)
            .addToBackStack(key)
            .commit()
    }

    private fun syncToolbarTitle() {
        val title = preferenceScreen?.title?.takeIf { it.isNotBlank() }
            ?: getString(R.string.tab_setting)
        (activity as? SettingsActivity)?.supportActionBar?.title = title
    }

    override fun onResume() {
        super.onResume()
        syncToolbarTitle()
        // 开关可能被别处改动（如风险弹窗里关闭了「校验 SSL 证书」），回到页面时重新同步互斥状态
        syncSslDebugState()
    }

    class SettingsPreferenceDataStore : PreferenceDataStore() {
        override fun getString(key: String?, defValue: String?): String {
            return when (key) {
                "darkTheme" -> PrefManager.darkTheme.toString()
                "themeColor" -> PrefManager.themeColor
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }

        override fun putString(key: String?, value: String?) {
            when (key) {
                "darkTheme" -> PrefManager.darkTheme = value?.toInt() ?: 0
                "themeColor" -> PrefManager.themeColor = value ?: "MATERIAL_DEFAULT"
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return when (key) {
                "blackDarkTheme" -> PrefManager.blackDarkTheme
                "followSystemAccent" -> PrefManager.followSystemAccent
                "showEmoji" -> PrefManager.showEmoji
                "customToken" -> PrefManager.customToken
                "isRecordHistory" -> PrefManager.isRecordHistory
                "isIconMiniCard" -> PrefManager.isIconMiniCard
                "isOpenLinkOutside" -> PrefManager.isOpenLinkOutside
                "isColorFilter" -> PrefManager.isColorFilter
                "verifySsl" -> PrefManager.isVerifySsl
                "sslDebug" -> PrefManager.isSslDebug
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }

        override fun putBoolean(key: String?, value: Boolean) {
            when (key) {
                "blackDarkTheme" -> PrefManager.blackDarkTheme = value
                "followSystemAccent" -> PrefManager.followSystemAccent = value
                "showEmoji" -> PrefManager.showEmoji = value
                "customToken" -> PrefManager.customToken = value
                "isRecordHistory" -> PrefManager.isRecordHistory = value
                "isIconMiniCard" -> PrefManager.isIconMiniCard = value
                "isOpenLinkOutside" -> PrefManager.isOpenLinkOutside = value
                "isColorFilter" -> PrefManager.isColorFilter = value
                "verifySsl" -> PrefManager.isVerifySsl = value
                "sslDebug" -> PrefManager.isSslDebug = value
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }
    }

    @SuppressLint("SetTextI18n", "InflateParams")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = SettingsPreferenceDataStore()
        setPreferencesFromResource(R.xml.settings, rootKey)

        findPreference<SimpleMenuPreference>("darkTheme")?.setOnPreferenceChangeListener { _, newValue ->
            val newMode = (newValue as String).toInt()
            if (PrefManager.darkTheme != newMode) {
                AppCompatDelegate.setDefaultNightMode(newMode)
            }
            true
        }

        findPreference<MaterialSwitchPreference>("blackDarkTheme")?.setOnPreferenceChangeListener { _, _ ->
            if (ResourceUtils.isNightMode(requireContext().resources.configuration))
                activity?.recreate()
            true
        }

        findPreference<MaterialSwitchPreference>("followSystemAccent")?.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
            true
        }

        findPreference<SimpleMenuPreference>("themeColor")?.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
            true
        }

        findPreference<MaterialSwitchPreference>("showEmoji")?.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
            true
        }

        // 「关于」已提到一级，点击直接打开关于页（不再有中间的二级目录）
        findPreference<Preference>("about")?.apply {
            summary = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
            setOnPreferenceClickListener {
                IntentUtil.startActivity<AboutActivity>(requireContext()) {}
                true
            }
        }


        findPreference<Preference>("params")?.setOnPreferenceClickListener {
            IntentUtil.startActivity<ParamsActivity>(requireContext()) {}
            true
        }

        findPreference<Preference>("szlmId")?.setOnPreferenceClickListener {
            val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_x_app_token, null, false)
            val editText: EditText = view.findViewById(R.id.editText)
            editText.highlightColor = ColorUtils.setAlphaComponent(
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorPrimaryDark,
                    0
                ), 128
            )
            editText.setText(PrefManager.SZLMID)
            MaterialAlertDialogBuilder(requireContext()).apply {
                setView(view)
                setTitle(requireContext().getString(R.string.szlmId))
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    PrefManager.SZLMID = editText.text.toString()
                    // 注意：szlmId 是设备串的首字段，改它就得重造设备串，而任何非官方设备串
                    // 都会被酷安要求验证码。所以这里只记录 SZLMID，设备后缀维持官方那一组。
                    applyDefaultFingerprint()
                }
                if (BuildConfig.DEBUG) {
                    setNeutralButton(R.string.random_value) { _, _ ->
                        PrefManager.SZLMID = randHexString(16)
                        applyDefaultFingerprint()
                    }
                }
            }.create().apply {
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                editText.requestFocus()
            }.show()
            true
        }

        findPreference<Preference>("userBlackList")?.setOnPreferenceClickListener {
            IntentUtil.startActivity<BlackListActivity>(requireContext()) {
                putExtra("type", "user")
            }
            true
        }

        findPreference<Preference>("topicBlackList")?.setOnPreferenceClickListener {
            IntentUtil.startActivity<BlackListActivity>(requireContext()) {
                putExtra("type", "topic")
            }
            true
        }

        findPreference<Preference>("spamWord")?.setOnPreferenceClickListener {
            IntentUtil.startActivity<SpamShieldActivity>(requireContext()) {
                putExtra("type", "word")
            }
            true
        }

        findPreference<Preference>("spamUser")?.setOnPreferenceClickListener {
            IntentUtil.startActivity<SpamShieldActivity>(requireContext()) {
                putExtra("type", "user")
            }
            true
        }

        findPreference<Preference>("spamNode")?.setOnPreferenceClickListener {
            IntentUtil.startActivity<SpamShieldActivity>(requireContext()) {
                putExtra("type", "node")
            }
            true
        }

        findPreference<Preference>("fontScale")?.setOnPreferenceClickListener {
            val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_font_scale, null, false)
            val slider: Slider = view.findViewById(R.id.slider)
            val fontScale: TextView = view.findViewById(R.id.fontScale)
            slider.apply {
                valueFrom = 0.80f
                valueTo = 1.30f
                value = PrefManager.FONTSCALE.toFloat()
                setLabelFormatter { value ->
                    String.format("%.2f", value)
                }
            }
            slider.addOnChangeListener { _, value, _ ->
                fontScale.text = "字体大小: ${String.format("%.2f", value)}"
                fontScale.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * value)
            }
            fontScale.text = "字体大小: ${PrefManager.FONTSCALE}"
            fontScale.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * PrefManager.FONTSCALE.toFloat())
            MaterialAlertDialogBuilder(requireContext()).apply {
                setView(view)
                setTitle(R.string.font_scale)
                setNegativeButton(android.R.string.cancel, null)
                setNeutralButton("重置") { _, _ ->
                    PrefManager.FONTSCALE = "1.00"
                    (requireActivity() as? MainActivity)?.recreate()
                }
                setPositiveButton(android.R.string.ok) { _, _ ->
                    PrefManager.FONTSCALE = String.format("%.2f", slider.value)
                    (requireActivity() as? MainActivity)?.recreate()
                }
                show()
            }
            true
        }


        findPreference<Preference>("clearCache")?.apply {
            summary = CacheDataManager.getTotalCacheSize(requireContext())
            setOnPreferenceClickListener {
                val currentSize = CacheDataManager.getTotalCacheSize(requireContext())
                summary = currentSize
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle("确定清除缓存吗？")
                    setMessage("当前缓存$currentSize")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        CacheDataManager.clearAllCache(requireContext())
                        summary = "刚刚清理"
                    }
                    show()
                }
                true
            }
        }

        findPreference<Preference>("imageQuality")?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle("图片画质")
                val items = arrayOf("网络自适应", "原图", "普清")
                val index = when (PrefManager.imageQuality) {
                    "auto" -> 0
                    "origin" -> 1
                    else -> 2
                }
                setSingleChoiceItems(
                    items,
                    index
                ) { dialog: DialogInterface, position: Int ->
                    when (position) {
                        0 -> PrefManager.imageQuality = "auto"

                        1 -> PrefManager.imageQuality = "origin"

                        2 -> PrefManager.imageQuality = "thumbnail"
                    }
                    dialog.dismiss()
                }
                show()
            }
            true
        }

        findPreference<MaterialSwitchPreference>("isColorFilter")?.setOnPreferenceChangeListener { _, _ ->
            if (ResourceUtils.isNightMode(requireContext().resources.configuration))
                activity?.recreate()
            true
        }

        // 「校验 SSL 证书」与「网络传输调试模式」互斥：
        // 打开严格校验时强制关掉调试模式，并刷新调试开关的可用状态
        findPreference<MaterialSwitchPreference>("verifySsl")?.setOnPreferenceChangeListener { _, value ->
            if (value == true && PrefManager.isSslDebug) {
                PrefManager.isSslDebug = false
            }
            syncSslDebugState()
            true
        }

        // 「网络传输调试模式」：开启要二次确认 + 倒计时，关闭直接生效（两者都需重启应用）
        syncSslDebugState()
        findPreference<MaterialSwitchPreference>("sslDebug")?.setOnPreferenceChangeListener { _, value ->
            when {
                // 严格校验开着时不可开启（开关本应是禁用态，这里兜底拦截）
                value == true && PrefManager.isVerifySsl -> false

                // 开启：先弹二次确认，确认后再落库并刷新开关
                value == true -> {
                    showSslDebugConfirmDialog()
                    false
                }

                // 关闭：直接落库 + 提示
                else -> {
                    PrefManager.isSslDebug = false
                    syncSslDebugState()
                    Toast.makeText(
                        requireContext(),
                        R.string.ssl_debug_disabled_toast,
                        Toast.LENGTH_LONG
                    ).show()
                    false
                }
            }
        }

    }

    /** 按「校验 SSL 证书」的当前值刷新调试开关：互斥时禁用并换成说明文案 */
    private fun syncSslDebugState() {
        val debug = findPreference<MaterialSwitchPreference>("sslDebug") ?: return
        val strict = PrefManager.isVerifySsl
        if (strict && PrefManager.isSslDebug) {
            PrefManager.isSslDebug = false
        }
        debug.isChecked = PrefManager.isSslDebug
        debug.isEnabled = !strict
        debug.summary = getString(
            if (strict) R.string.settings_ssl_debug_locked_summary
            else R.string.settings_ssl_debug_summary
        )
    }

    /**
     * 开启「网络传输调试模式」前的二次确认：正向按钮先禁用并倒计时，
     * 防止误触一键放开全部 SSL 校验。
     */
    private fun showSslDebugConfirmDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ssl_debug_confirm_title)
            .setMessage(R.string.ssl_debug_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ssl_debug_confirm_ok, null)
            .create()

        dialog.setOnShowListener {
            val confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val handler = Handler(Looper.getMainLooper())
            var remain = 5
            confirm.isEnabled = false
            confirm.text = getString(R.string.ssl_debug_confirm_ok_wait, remain)

            val tick = object : Runnable {
                override fun run() {
                    remain--
                    if (remain > 0) {
                        confirm.text = getString(R.string.ssl_debug_confirm_ok_wait, remain)
                        handler.postDelayed(this, 1000L)
                    } else {
                        confirm.isEnabled = true
                        confirm.text = getString(R.string.ssl_debug_confirm_ok)
                    }
                }
            }
            handler.postDelayed(tick, 1000L)

            confirm.setOnClickListener {
                handler.removeCallbacks(tick)
                PrefManager.isSslDebug = true
                syncSslDebugState()
                dialog.dismiss()
                Toast.makeText(
                    requireContext(),
                    R.string.ssl_debug_enabled_toast,
                    Toast.LENGTH_LONG
                ).show()
                showSslDebugRestartDialog()
            }
        }
        dialog.show()
    }

    /** 开启后提示需要重启（网络栈在 Application 启动时构建，只有整个进程重启才生效） */
    private fun showSslDebugRestartDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ssl_debug_restart_title)
            .setMessage(R.string.ssl_debug_restart_message)
            .setNegativeButton(R.string.ssl_debug_restart_later, null)
            .setPositiveButton(R.string.ssl_debug_restart_now) { _, _ -> restartApp() }
            .show()
    }

    /**
     * 立即重启应用：用 AlarmManager 预约拉起一个新的启动 Intent，再杀掉当前进程
     * （直接杀进程系统不会自动拉起）。正式发布版本同样适用。
     */
    private fun restartApp() {
        val appContext = requireContext().applicationContext
        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        if (intent == null) {
            // 拿不到启动 Intent（极罕见）：退化为重建当前 Activity，至少刷新界面
            activity?.recreate()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return@runCatching
            val triggerAt = System.currentTimeMillis() + 200L
            // Android 12+ 未获精确闹钟授权时 setExact 会抛异常，此时退化为普通 set
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExact(AlarmManager.RTC, triggerAt, pendingIntent)
            } else {
                am.set(AlarmManager.RTC, triggerAt, pendingIntent)
            }
        }
        exitProcess(0)
    }

    companion object {
        /** rootKey = 某个二级 PreferenceScreen 的 key，为空则是完整的一级设置页 */
        fun newInstance(rootKey: String?) = SettingsPreferenceFragment().apply {
            arguments = Bundle().apply {
                putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, rootKey)
            }
        }
    }

}