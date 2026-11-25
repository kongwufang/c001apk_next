package com.example.c001apk.ui.account

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityAccountManagerBinding
import com.example.c001apk.databinding.ItemAccountBinding
import com.example.c001apk.logic.model.StoredAccount
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.ui.login.WebLoginActivity
import com.example.c001apk.ui.main.MainActivity
import com.example.c001apk.util.AccountVault
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.PrefManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Entry in the Mine drawer; the account list is above the existing logout button. */
class AccountManagerActivity : BaseActivity<ActivityAccountManagerBinding>() {
    private var restarting = false
    private val addAccount = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) restartMain() else renderAccounts()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.toolbar.setNavigationIcon(R.drawable.ic_back)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnAddAccount.setOnClickListener { startAddAccount() }
        binding.btnLogout.setOnClickListener { confirmLogout() }
    }

    override fun onResume() {
        super.onResume()
        renderAccounts()
    }

    private fun renderAccounts() {
        val accounts = AccountVault.accounts()
        val currentUid = if (PrefManager.isLogin) PrefManager.uid else ""
        binding.accountList.removeAllViews()
        binding.accountEmpty.isVisible = accounts.isEmpty()
        binding.btnLogout.isVisible = currentUid.isNotEmpty()
        for (account in accounts) {
            val item = ItemAccountBinding.inflate(layoutInflater, binding.accountList, false)
            item.name.text = account.displayName.ifBlank { account.uid }
            item.uid.text = getString(R.string.accountUid, account.uid)
            item.currentBadge.isVisible = account.uid == currentUid
            item.deleteButton.isVisible = account.uid != currentUid
            if (account.avatar.isNotBlank()) ImageUtil.showIMG(item.avatar, account.avatar)
            else item.avatar.setImageResource(R.drawable.ic_account)
            item.deleteButton.setOnClickListener { if (!restarting) confirmDelete(account) }
            item.root.isClickable = account.uid != currentUid
            if (item.root.isClickable) item.root.setOnClickListener {
                if (restarting) return@setOnClickListener
                if (AccountVault.switchTo(account.uid)) restartMain()
                else toast(R.string.accountSwitchFailed)
            }
            binding.accountList.addView(item.root)
        }
    }

    private fun startAddAccount() {
        if (PrefManager.isLogin && PrefManager.SZLMID.isBlank()) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.accountSetCurrentIdentity)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> com.example.c001apk.util.SzlmIdPrompt.showConsent(this) }
                .show()
            return
        }
        val view = layoutInflater.inflate(R.layout.item_x_app_token, null, false)
        val input = view.findViewById<EditText>(R.id.editText)
        input.hint = getString(R.string.szlmId)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accountAdd)
            .setMessage(R.string.accountIdentityNote)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.login, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val draft = AccountVault.prepareLogin(input.text.toString().trim())
                if (draft == null) {
                    input.error = getString(R.string.accountIdentityInvalid)
                } else {
                    dialog.dismiss()
                    addAccount.launch(Intent(this, WebLoginActivity::class.java).apply {
                        putExtra(WebLoginActivity.EXTRA_ADD_ACCOUNT, draft)
                    })
                }
            }
        }
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun confirmDelete(account: StoredAccount) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accountDeleteTitle)
            .setMessage(R.string.accountDeleteMessage)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                toast(if (AccountVault.delete(account.uid)) R.string.accountDeleted else R.string.accountSwitchFailed)
                renderAccounts()
            }.show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logoutTitle)
            .setMessage(R.string.accountLogoutNote)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (AccountVault.logoutCurrent()) restartMain() else toast(R.string.accountSwitchFailed)
            }.show()
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun restartMain() {
        if (restarting) return
        restarting = true
        binding.btnAddAccount.isEnabled = false
        binding.btnLogout.isEnabled = false
        // Web login and embedded Coolapk pages share this WebView cookie jar.
        // Finish clearing it before entering the new account's pages.
        android.webkit.CookieManager.getInstance().removeAllCookies {
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }
    }
}
