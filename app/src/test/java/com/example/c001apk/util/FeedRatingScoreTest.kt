package com.example.c001apk.util

import com.example.c001apk.logic.model.FeedContentResponse
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.network.LenientGson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedRatingScoreTest {
    @Test fun fractionalRatingDoesNotBreakFeedDetail() {
        val response = LenientGson.gson.fromJson(
            """{"data":{"id":"50131760","entityType":"feed","rating_score_old":6.75}}""",
            FeedContentResponse::class.java,
        )
        val item = requireNotNull(response.data)
        assertEquals("50131760", item.id)
        assertEquals(6.75, requireNotNull(item.ratingScoreOld), 0.0)
        assertEquals("6.75 分", UserCardUtils.scoreText(item.ratingScoreOld))
    }

    @Test fun ratingsInUserFeedStillAcceptIntegersNumericStringsAndMissingValues() {
        val response = LenientGson.gson.fromJson(
            """{"data":[{"entityType":"nodeRating","rating_score_old":8},
               {"entityType":"nodeRating","rating_score_old":"6.75"},
               {"entityType":"nodeRating","rating_score_old":null},
               {"entityType":"nodeRating"}]}""",
            HomeFeedResponse::class.java,
        )
        val rows = requireNotNull(response.data)
        assertEquals(4, rows.size)
        assertEquals(listOf("8 分", "6.75 分", "", ""), rows.map { UserCardUtils.scoreText(it.ratingScoreOld) })
        assertNull(rows.last().ratingScoreOld)
        assertEquals("", UserCardUtils.scoreText(0.0))
        assertEquals("", UserCardUtils.scoreText(Double.NaN))
    }
}
