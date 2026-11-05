package com.chue.ytkeep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaylistAndQueueTest {

    private fun sampleHit(id: String, title: String = "Title $id", channel: String = "Channel $id") =
        Hit(
            id = id,
            title = title,
            channel = channel,
            seconds = 120L,
            thumb = "https://img.youtube.com/$id.jpg",
            views = "10K",
            age = "1 วัน",
            channelId = "UC$id"
        )

    @Before
    fun setUp() {
        QueueManager.clearQueue()
    }

    @Test
    fun queueInitializationAndPlaybackState() {
        val items = listOf(sampleHit("1"), sampleHit("2"), sampleHit("3"))
        QueueManager.playQueue(
            ctx = null,
            player = null,
            items = items,
            startIndex = 0,
            title = "เพลงร็อค"
        )

        val state = QueueManager.state
        assertEquals(3, state.items.size)
        assertEquals(0, state.currentIndex)
        assertEquals("1", state.currentItem?.id)
        assertEquals("เพลงร็อค", state.title)
        assertTrue(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun playNextInsertsItemImmediatelyAfterCurrent() {
        val items = listOf(sampleHit("1"), sampleHit("2"), sampleHit("3"))
        QueueManager.playQueue(
            ctx = null,
            player = null,
            items = items,
            startIndex = 0
        )

        val nextHit = sampleHit("inserted")
        QueueManager.playNext(nextHit)

        val state = QueueManager.state
        assertEquals(4, state.items.size)
        assertEquals(0, state.currentIndex)
        assertEquals("1", state.currentItem?.id)
        assertEquals("inserted", state.items[1].id)
        assertEquals("2", state.items[2].id)
    }

    @Test
    fun addToQueueAppendsToEnd() {
        val items = listOf(sampleHit("1"), sampleHit("2"))
        QueueManager.playQueue(
            ctx = null,
            player = null,
            items = items,
            startIndex = 0
        )

        QueueManager.addToQueue(sampleHit("3"))
        val state = QueueManager.state
        assertEquals(3, state.items.size)
        assertEquals("3", state.items.last().id)
    }

    @Test
    fun removeFromQueueAdjustsIndicesCorrectly() {
        val items = listOf(sampleHit("1"), sampleHit("2"), sampleHit("3"))
        QueueManager.playQueue(
            ctx = null,
            player = null,
            items = items,
            startIndex = 1
        ) // currently playing "2"

        // ลบเพลงแรก "1"
        QueueManager.removeFromQueue(0)
        assertEquals(2, QueueManager.state.items.size)
        assertEquals(0, QueueManager.state.currentIndex) // เลื่อนกลับมา 0 แต่ยังชี้ "2"
        assertEquals("2", QueueManager.state.currentItem?.id)
    }

    @Test
    fun toggleRepeatCyclesThroughModes() {
        assertEquals(QueueManager.RepeatMode.OFF, QueueManager.state.repeatMode)
        QueueManager.toggleRepeat()
        assertEquals(QueueManager.RepeatMode.ALL, QueueManager.state.repeatMode)
        QueueManager.toggleRepeat()
        assertEquals(QueueManager.RepeatMode.ONE, QueueManager.state.repeatMode)
        QueueManager.toggleRepeat()
        assertEquals(QueueManager.RepeatMode.OFF, QueueManager.state.repeatMode)
    }

    @Test
    fun toggleShuffleTogglesBoolean() {
        assertFalse(QueueManager.state.isShuffle)
        QueueManager.toggleShuffle()
        assertTrue(QueueManager.state.isShuffle)
        QueueManager.toggleShuffle()
        assertFalse(QueueManager.state.isShuffle)
    }

    @Test
    fun playlistDataModelHoldsItemsAndProperties() {
        val hit1 = sampleHit("a")
        val hit2 = sampleHit("b")
        val pl = Store.Playlist(
            id = "pl_123",
            name = "เพลงโปรด",
            createdAt = 1000L,
            items = listOf(hit1, hit2)
        )

        assertEquals("pl_123", pl.id)
        assertEquals("เพลงโปรด", pl.name)
        assertEquals(2, pl.items.size)
        assertEquals("a", pl.items[0].id)
        assertEquals("b", pl.items[1].id)
    }
}
