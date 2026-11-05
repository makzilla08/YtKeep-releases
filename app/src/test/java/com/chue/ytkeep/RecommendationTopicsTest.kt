package com.chue.ytkeep

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationTopicsTest {
    @Test
    fun disabledTopicsAreRemovedFromSelectableTopics() {
        val result = Feed.enabledTopics(setOf("music", "gaming"))

        assertEquals(
            Feed.topics.map { it.id }.filterNot { it == "music" || it == "gaming" },
            result.map { it.id }
        )
    }

    @Test
    fun noDisabledTopicsKeepsAllTopicsAvailable() {
        assertEquals(Feed.topics, Feed.enabledTopics(emptySet()))
    }
}
