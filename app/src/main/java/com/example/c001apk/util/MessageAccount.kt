package com.example.c001apk.util

/** Capture before dispatch; do not relabel an old response with the account active on arrival. */
data class MessageAccount(val uid: String, val generation: Long) {
    fun isCurrent(): Boolean = this == current()

    companion object {
        fun current(): MessageAccount? = synchronized(PrefManager) {
            if (PrefManager.isLogin && PrefManager.uid.isNotBlank())
                MessageAccount(PrefManager.uid, PrefManager.messageAccountGeneration) else null
        }
    }
}
