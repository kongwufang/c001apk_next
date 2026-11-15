package com.example.c001apk.logic.model

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class UserProfileModerationTest {
    private fun profile(fields: String): UserProfileResponse.Data = Gson().fromJson(
        "{\"data\":{\"uid\":\"32864090\",$fields}}", UserProfileResponse::class.java
    ).data!!

    @Test fun displaysSilencedForTheObservedServerFlag() {
        // Minimal public-field fixture from both /v6/user/space and /v6/user/profile.
        val data = profile("\"disallowPost\":1,\"groupid\":7,\"usergroupid\":1003,\"block_status\":-1")
        assertEquals(1, data.disallowPost)
        assertTrue(data.isSilenced)
    }

    @Test fun normalUserAndMissingFlagAreNotSilenced() {
        assertFalse(profile("\"disallowPost\":0").isSilenced)
        assertFalse(profile("\"disallowPost\":null").isSilenced)
        assertFalse(profile("\"status\":1").isSilenced)
    }

    @Test fun unrelatedFlagsAndUnknownValuesCannotInventASilencedStatus() {
        assertFalse(profile("\"isBlackList\":1,\"isBeBlackList\":1,\"groupid\":7,\"block_status\":-1").isSilenced)
        assertFalse(profile("\"disallowPost\":2").isSilenced)
        assertFalse(profile("\"disallowPost\":-1").isSilenced)
    }

    @Test fun updatedProfileCanRemoveTheBadge() {
        assertTrue(profile("\"disallowPost\":1").isSilenced)
        assertFalse(profile("\"disallowPost\":0").isSilenced)
    }
}
