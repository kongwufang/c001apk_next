package com.example.c001apk.util

import com.example.c001apk.logic.model.AccountSession
import java.util.UUID

/** The UI never stages a login in active preferences; only confirmed sessions can be committed. */
internal object AccountVault {
    fun accounts() = PrefManager.savedAccounts()
    private class Draft(val key: String, val generation: Long)
    private var draft: Draft? = null

    @Synchronized
    fun prepareLogin(): String = UUID.randomUUID().toString().also {
        draft = Draft(it, PrefManager.messageAccountGeneration)
    }

    @Synchronized
    fun pendingLogin(key: String): Long? = draft?.takeIf { it.key == key }?.generation

    @Synchronized
    fun clearDraft(key: String) { if (draft?.key == key) draft = null }
    fun switchTo(uid: String) = PrefManager.switchAccount(uid)
    fun delete(uid: String) = PrefManager.removeAccount(uid)
    fun logoutCurrent() = PrefManager.logout()
    fun finishLogin(generation: Long, session: AccountSession) =
        PrefManager.activateAddedAccount(generation, session)
}
