package com.example.c001apk.util

/** A content revision, rather than every APK update, decides whether confirmation is needed. */
internal object OnboardingPolicy {
    private const val LEGACY_REVISION = 1

    fun requiredRevision(appVersionCode: Int): Int = when {
        appVersionCode >= 10012 -> 3 // V1.0.12: confirm the guide again, including the emoji download policy.
        appVersionCode >= 10011 -> 2
        else -> LEGACY_REVISION
    }

    fun isComplete(appVersionCode: Int, legacyCompleted: Boolean, completedRevision: Int): Boolean =
        legacyCompleted && maxOf(completedRevision, LEGACY_REVISION) >= requiredRevision(appVersionCode)

    fun revisionAfterCompletion(appVersionCode: Int, completedRevision: Int): Int =
        maxOf(completedRevision, requiredRevision(appVersionCode))
}
