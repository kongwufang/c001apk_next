package com.example.c001apk.logic.model

import com.example.c001apk.util.AccountIdentity

/** Not a data class: diagnostic toString must never include credentials or identifiers. */
internal class StoredAccount(val session: AccountSession, val identity: AccountIdentity) {
    val uid get() = session.uid
    val avatar get() = session.avatar
    val displayName get() = AccountSession.decodeCookieName(session.username)
}
