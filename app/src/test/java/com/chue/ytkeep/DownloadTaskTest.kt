package com.chue.ytkeep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskTest {
    private val task = DownloadTask(
        id = "task-1",
        videoUrl = "https://youtube.com/watch?v=video",
        selector = "best",
        title = "Video",
        uploader = "Channel",
        quality = "720p",
        audio = false,
        createdAt = 1L
    )

    @Test
    fun runningTaskCanBeCancelledAndRetried() {
        val running = task.start()
        val cancelling = running.cancel()
        val cancelled = cancelling.cancelled()
        val retried = cancelled.retry()

        assertEquals(DownloadState.RUNNING, running.state)
        assertEquals(DownloadState.CANCELLING, cancelling.state)
        assertEquals(DownloadState.CANCELLED, cancelled.state)
        assertEquals(DownloadState.QUEUED, retried.state)
        assertEquals(0, retried.progress)
    }

    @Test
    fun progressAndCompletionOnlyApplyToActiveTasks() {
        val progress = task.start().withProgress(42)
        val completed = progress.complete()

        assertEquals(42, progress.progress)
        assertEquals(DownloadState.COMPLETED, completed.state)
        assertEquals(100, completed.progress)
        assertEquals(task, task.withProgress(42))
    }

    @Test
    fun interruptedTaskCanRetryWithAnExplicitError() {
        val interrupted = task.start().recover()
        val retried = interrupted.retry()

        assertEquals(DownloadState.FAILED, interrupted.state)
        assertEquals("ดาวน์โหลดหยุดก่อนเสร็จ กดลองใหม่ได้", interrupted.error)
        assertEquals(DownloadState.QUEUED, retried.state)
        assertEquals(null, retried.error)
    }

    @Test
    fun terminalTaskCannotBeCancelledAgainOrRetriedAfterCompletion() {
        val completed = task.start().complete()

        assertEquals(completed, completed.cancel())
        assertEquals(completed, completed.retry())
    }

    @Test
    fun onlyFinishedStatesAreTerminalForQueueClearing() {
        assertTrue(DownloadState.COMPLETED.isTerminal)
        assertTrue(DownloadState.FAILED.isTerminal)
        assertTrue(DownloadState.CANCELLED.isTerminal)

        assertFalse(DownloadState.QUEUED.isTerminal)
        assertFalse(DownloadState.RUNNING.isTerminal)
        assertFalse(DownloadState.CANCELLING.isTerminal)
    }
}
