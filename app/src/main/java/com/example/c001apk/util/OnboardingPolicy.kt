package com.example.c001apk.util

/** A content revision, rather than every APK update, decides whether confirmation is needed. */
internal object OnboardingPolicy {
    const val UPDATED_FLOW_FROM_VERSION = 10011
    private const val LEGACY_REVISION = 1
    private const val UPDATED_REVISION = 2

    fun requiredRevision(appVersionCode: Int): Int =
        if (appVersionCode >= UPDATED_FLOW_FROM_VERSION) UPDATED_REVISION else LEGACY_REVISION

    fun isComplete(appVersionCode: Int, legacyCompleted: Boolean, completedRevision: Int): Boolean =
        legacyCompleted && maxOf(completedRevision, LEGACY_REVISION) >= requiredRevision(appVersionCode)

    fun revisionAfterCompletion(appVersionCode: Int, completedRevision: Int): Int =
        maxOf(completedRevision, requiredRevision(appVersionCode))
}
