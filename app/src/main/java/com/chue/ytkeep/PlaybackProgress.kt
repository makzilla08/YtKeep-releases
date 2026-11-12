package com.chue.ytkeep

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import java.security.MessageDigest

internal sealed interface MediaIdentity {
    val mediaId: String

    data class YouTube(val videoId: String) : MediaIdentity {
        override val mediaId: String = "yt:$videoId"
    }

    data class Local(val uri: String) : MediaIdentity {
        override val mediaId: String = "local:$uri"
    }

    companion object {
        private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

        fun forPlayback(videoId: String?, uri: String): MediaIdentity? {
            if (!videoId.isNullOrBlank() && videoIdPattern.matches(videoId)) {
                return YouTube(videoId)
            }
            if (uri.startsWith("file:") || uri.startsWith("content:")) {
                return Local(uri)
            }
            return null
        }

        fun fromMediaId(mediaId: String?): MediaIdentity? {
            if (mediaId.isNullOrBlank()) return null
            if (mediaId.startsWith("yt:")) {
                val id = mediaId.removePrefix("yt:")
                return id.takeIf(videoIdPattern::matches)?.let(::YouTube)
            }
            if (mediaId.startsWith("local:")) {
                return mediaId.removePrefix("local:").takeIf(String::isNotBlank)?.let(::Local)
            }
            return mediaId.takeIf(videoIdPattern::matches)?.let(::YouTube)
        }

        fun youtubeVideoId(mediaId: String?): String? =
            (fromMediaId(mediaId) as? YouTube)?.videoId
    }
}

internal data class ResumePoint(
    val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long?,
    val savedAtMs: Long
)

internal object ResumePolicy {
    const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    const val MAX_RECORDS = 100
    const val CHECKPOINT_INTERVAL_MS = 10_000L
    private const val MIN_RESUME_MS = 5_000L
    private const val COMPLETION_TAIL_MS = 15_000L

    fun checkpoint(
        mediaKey: String?,
        positionMs: Long,
        durationMs: Long?,
        nowMs: Long
    ): ResumePoint? {
        if (mediaKey.isNullOrBlank() || positionMs < MIN_RESUME_MS) return null
        if (durationMs != null && durationMs > 0 && isNearEnd(positionMs, durationMs)) return null
        return ResumePoint(mediaKey, positionMs, durationMs?.takeIf { it > 0 }, nowMs)
    }

    fun restorePosition(
        point: ResumePoint?,
        expectedKey: String?,
        actualDurationMs: Long?,
        nowMs: Long
    ): Long? {
        if (point == null || expectedKey.isNullOrBlank() || point.mediaKey != expectedKey) return null
        if (point.positionMs < MIN_RESUME_MS || point.savedAtMs > nowMs) return null
        if (nowMs - point.savedAtMs > MAX_AGE_MS) return null

        val durationMs = actualDurationMs?.takeIf { it > 0 } ?: point.durationMs?.takeIf { it > 0 }
        if (durationMs != null && isNearEnd(point.positionMs, durationMs)) return null
        return if (durationMs == null) point.positionMs else point.positionMs.coerceAtMost(durationMs)
    }

    private fun isNearEnd(positionMs: Long, durationMs: Long): Boolean {
        val tail = minOf(COMPLETION_TAIL_MS, (durationMs / 20).coerceAtLeast(1_000L))
        return positionMs >= (durationMs - tail).coerceAtLeast(0L)
    }
}

internal interface ResumePointStore {
    fun get(mediaKey: String): ResumePoint?
    fun put(point: ResumePoint)
    fun remove(mediaKey: String)
}

internal class SharedPreferencesResumePointStore(context: Context) : ResumePointStore {
    private val preferences = context.applicationContext
        .getSharedPreferences("ytkeep", Context.MODE_PRIVATE)

    @Synchronized
    override fun get(mediaKey: String): ResumePoint? {
        val prefix = recordPrefix(mediaKey)
        val values = preferences.all
        val storedKey = values["$prefix.key"] as? String
        val position = values["$prefix.position"] as? Long
        val duration = values["$prefix.duration"] as? Long
        val savedAt = values["$prefix.savedAt"] as? Long
        if (storedKey != mediaKey || position == null || savedAt == null) {
            removePrefix(prefix)
            return null
        }
        return ResumePoint(storedKey, position, duration?.takeIf { it > 0 }, savedAt)
    }

    @Synchronized
    override fun put(point: ResumePoint) {
        val prefix = recordPrefix(point.mediaKey)
        preferences.edit()
            .putString("$prefix.key", point.mediaKey)
            .putLong("$prefix.position", point.positionMs)
            .putLong("$prefix.duration", point.durationMs ?: -1L)
            .putLong("$prefix.savedAt", point.savedAtMs)
            .apply()
        prune(point.savedAtMs)
    }

    @Synchronized
    override fun remove(mediaKey: String) {
        removePrefix(recordPrefix(mediaKey))
    }

    @Synchronized
    private fun prune(nowMs: Long) {
        val recordPrefixes = preferences.all.keys
            .filter { it.startsWith(RECORD_PREFIX) && it.endsWith(".savedAt") }
            .map { it.removeSuffix("savedAt") }
            .distinct()
        val records = recordPrefixes.mapNotNull { prefix ->
            val values = preferences.all
            val key = values["$prefix.key"] as? String
            val savedAt = values["$prefix.savedAt"] as? Long
            if (key == null || savedAt == null || savedAt > nowMs ||
                nowMs - savedAt > ResumePolicy.MAX_AGE_MS
            ) {
                removePrefix(prefix)
                null
            } else {
                prefix to savedAt
            }
        }
        records.sortedByDescending { it.second }
            .drop(ResumePolicy.MAX_RECORDS)
            .forEach { (prefix, _) -> removePrefix(prefix) }
    }

    private fun removePrefix(prefix: String) {
        preferences.edit()
            .remove("$prefix.key")
            .remove("$prefix.position")
            .remove("$prefix.duration")
            .remove("$prefix.savedAt")
            .apply()
    }

    private fun recordPrefix(mediaKey: String): String =
        "$RECORD_PREFIX${sha256(mediaKey)}."

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val RECORD_PREFIX = "playbackResume."
    }
}

internal class PlaybackProgress(
    private val player: Player,
    private val store: ResumePointStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private data class Snapshot(
        val mediaKey: String,
        val positionMs: Long,
        val durationMs: Long?
    )

    private var lastSnapshot: Snapshot? = null
    private var restoreAttemptedKey: String? = null
    private var restoringKey: String? = null

    private val checkpoint = object : Runnable {
        override fun run() {
            checkpointCurrent()
            handler.postDelayed(this, ResumePolicy.CHECKPOINT_INTERVAL_MS)
        }
    }

    fun start() {
        lastSnapshot = snapshot()
        handler.removeCallbacks(checkpoint)
        handler.postDelayed(checkpoint, ResumePolicy.CHECKPOINT_INTERVAL_MS)
    }

    fun onMediaItemTransition() {
        lastSnapshot?.let(::persist)
        restoreAttemptedKey = null
        lastSnapshot = snapshot()
        restoreIfReady()
    }

    fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
            player.currentMediaItem?.mediaId?.let(MediaIdentity::fromMediaId)?.mediaId?.let(store::remove)
            lastSnapshot = null
            restoreAttemptedKey = player.currentMediaItem?.mediaId
            restoringKey = null
            return
        }
        if (state == Player.STATE_READY) restoreIfReady()
    }

    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (!playWhenReady) checkpointCurrent()
    }

    fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        val oldKey = MediaIdentity.fromMediaId(oldPosition.mediaItem?.mediaId)?.mediaId
        val newKey = MediaIdentity.fromMediaId(newPosition.mediaItem?.mediaId)?.mediaId
        if (oldKey != null && oldKey != newKey) {
            val oldDuration = lastSnapshot?.takeIf { it.mediaKey == oldKey }?.durationMs
            val oldSnapshot = Snapshot(oldKey, oldPosition.positionMs.coerceAtLeast(0L), oldDuration)
            persist(oldSnapshot)
            lastSnapshot = oldSnapshot
        }
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
        val key = currentMediaKey() ?: return
        if (restoringKey == key) {
            restoringKey = null
            lastSnapshot = snapshot()
            return
        }
        if (newPosition.positionMs < 5_000L) {
            store.remove(key)
        } else {
            checkpointCurrent()
        }
    }

    fun stop() {
        handler.removeCallbacks(checkpoint)
        checkpointCurrent()
    }

    private fun restoreIfReady() {
        if (player.playbackState != Player.STATE_READY) return
        val current = snapshot() ?: return
        if (restoreAttemptedKey == current.mediaKey) return
        restoreAttemptedKey = current.mediaKey
        val durationMs = current.durationMs
        val position = ResumePolicy.restorePosition(
            store.get(current.mediaKey),
            current.mediaKey,
            durationMs,
            nowMs()
        ) ?: return
        if (position <= 0L || current.positionMs > 0L) return
        restoringKey = current.mediaKey
        player.seekTo(position)
        lastSnapshot = current.copy(positionMs = position)
    }

    private fun checkpointCurrent() {
        val current = snapshot() ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            store.remove(current.mediaKey)
        } else {
            persist(current)
        }
        lastSnapshot = current
    }

    private fun persist(snapshot: Snapshot) {
        val record = ResumePolicy.checkpoint(
            snapshot.mediaKey,
            snapshot.positionMs,
            snapshot.durationMs,
            nowMs()
        )
        if (record == null) store.remove(snapshot.mediaKey) else store.put(record)
    }

    private fun snapshot(): Snapshot? {
        val mediaKey = currentMediaKey() ?: return null
        return Snapshot(
            mediaKey = mediaKey,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
        )
    }

    private fun currentMediaKey(): String? =
        player.currentMediaItem?.mediaId?.let(MediaIdentity::fromMediaId)?.mediaId
}
