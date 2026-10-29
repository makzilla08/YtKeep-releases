package com.chue.ytkeep

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationRankerTest {
    @Test
    fun excludesRecentlyOpenedVideosAndKeepsMultipleTopics() {
        val candidates = listOf(
            hit("recent", "tech", "channel-1", 10),
            hit("tech-1", "tech", "channel-1", 9),
            hit("tech-2", "tech", "channel-1", 8),
            hit("music-1", "music", "channel-2", 7),
            hit("sport-1", "sport", "channel-3", 6)
        )

        val result = RecommendationRanker.rank(
            candidates = candidates,
            recentVideoIds = setOf("recent"),
            limit = 3
        )

        assertEquals(listOf("tech-1", "tech-2", "music-1"), result.map { it.id })
    }

    @Test
    fun limitsRepeatedChannelsWhenOtherResultsAreAvailable() {
        val candidates = listOf(
            hit("a", "tech", "same-channel", 10),
            hit("b", "tech", "same-channel", 9),
            hit("c", "tech", "same-channel", 8),
            hit("d", "music", "other-channel", 7),
            hit("e", "sport", "third-channel", 6)
        )

        val result = RecommendationRanker.rank(
            candidates = candidates,
            recentVideoIds = emptySet(),
            limit = 4
        )

        assertEquals(listOf("a", "b", "d", "e"), result.map { it.id })
    }

    private fun hit(id: String, topic: String, channelId: String, score: Int) =
        RecommendationRanker.RankedHit(
            hit = Hit(
                id = id,
                title = id,
                channel = channelId,
                seconds = 0,
                thumb = null,
                channelId = channelId,
                sourceTopicId = topic
            ),
            score = score
        )
}
