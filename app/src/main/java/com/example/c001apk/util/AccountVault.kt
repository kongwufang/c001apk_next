package com.example.c001apk.util

import com.example.c001apk.logic.model.AccountSession
import java.util.UUID

/** The UI never stages a login in active preferences; only confirmed sessions can be committed. */
internal object AccountVault {
    fun accounts() = PrefManager.savedAccounts()
    private class Draft(val key: String, val generation: Long, val identity: AccountIdentity)
    private var draft: Draft? = null

    @Synchronized
    fun prepareLogin(id: String): String? {
        val (generation, identity) = synchronized(PrefManager) {
            PrefManager.messageAccountGeneration to (PrefManager.addAccountIdentity(id) ?: return null)
        }
        return UUID.randomUUID().toString().also { draft = Draft(it, generation, identity) }
    }

    @Synchronized
    fun pendingLogin(key: String): Pair<Long, AccountIdentity>? =
        draft?.takeIf { it.key == key }?.let { it.generation to it.identity }

    @Synchronized
    fun clearDraft(key: String) { if (draft?.key == key) draft = null }
    fun switchTo(uid: String) = PrefManager.switchAccount(uid)
    fun delete(uid: String) = PrefManager.removeAccount(uid)
    fun logoutCurrent() = PrefManager.logout()
    fun finishLogin(generation: Long, session: AccountSession, identity: AccountIdentity) =
        PrefManager.activateAddedAccount(generation, session, identity)
}
