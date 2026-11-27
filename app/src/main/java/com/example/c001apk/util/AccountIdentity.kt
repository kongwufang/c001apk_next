package com.example.c001apk.util

/** Account-local model and version configuration, without a device-wide szlmID. */
internal class AccountIdentity(
    val deviceCodeSkeleton: String,
    val deviceSettings: Map<String, Any>,
)
