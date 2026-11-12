package com.chue.ytkeep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackProgressTest {
    private val now = 1_800_000_000_000L

    @Test
    fun mediaIdentitySeparatesYoutubeAndLocalPlayback() {
        val youtube = MediaIdentity.forPlayback("abcDEF01234", "https://stream.example/video")
        val local = MediaIdentity.forPlayback("", "file:///media/clip.mp4")

        assertEquals("yt:abcDEF01234", youtube?.mediaId)
        assertEquals("local:file:///media/clip.mp4", local?.mediaId)
        assertEquals("abcDEF01234", MediaIdentity.youtubeVideoId(youtube?.mediaId))
        assertNull(MediaIdentity.youtubeVideoId(local?.mediaId))
    }

    @Test
    fun restoresOnlyMatchingRecentProgress() {
        val point = ResumePolicy.checkpoint("yt:abcDEF01234", 60_000, 120_000, now)!!

        assertEquals(
            60_000L,
            ResumePolicy.restorePosition(point, "yt:abcDEF01234", 120_000, now)
        )
        assertNull(ResumePolicy.restorePosition(point, "yt:otherID000", 120_000, now))
        assertNull(ResumePolicy.restorePosition(point, "yt:abcDEF01234", 120_000, now + ResumePolicy.MAX_AGE_MS + 1))
        assertNull(ResumePolicy.restorePosition(point, "yt:abcDEF01234", 120_000, now - 1))
    }

    @Test
    fun ignoresProgressNearCompletionAndAtTheBeginning() {
        assertNull(ResumePolicy.checkpoint("yt:abcDEF01234", 1_000, 120_000, now))
        assertNull(ResumePolicy.checkpoint("yt:abcDEF01234", 116_000, 120_000, now))

        val point = ResumePoint("yt:abcDEF01234", 116_000, 120_000, now)
        assertNull(ResumePolicy.restorePosition(point, point.mediaKey, 120_000, now))
    }

    @Test
    fun usesCurrentDurationWhenAClipDurationChanges() {
        val point = ResumePoint("yt:abcDEF01234", 90_000, 180_000, now)

        assertNull(ResumePolicy.restorePosition(point, point.mediaKey, 90_000, now))
        assertNotNull(ResumePolicy.restorePosition(point, point.mediaKey, 180_000, now))
    }
}
