package com.chue.ytkeep

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ผู้จัดการคิวการเล่น (Now Playing Queue) สำหรับเล่นต่อเนื่อง สลับเพลง และบันทึกเป็นเพลย์ลิสต์
 */
object QueueManager {

    enum class RepeatMode { OFF, ALL, ONE }

    data class QueueState(
        val items: List<Hit> = emptyList(),
        val currentIndex: Int = -1,
        val title: String = "คิวเล่นต่อเนื่อง",
        val repeatMode: RepeatMode = RepeatMode.OFF,
        val isShuffle: Boolean = false,
        val isLoadingNext: Boolean = false
    ) {
        val currentItem: Hit? get() = items.getOrNull(currentIndex)
        val hasNext: Boolean
            get() = if (repeatMode != RepeatMode.OFF) items.isNotEmpty() else currentIndex + 1 < items.size
        val hasPrevious: Boolean
            get() = if (repeatMode != RepeatMode.OFF) items.isNotEmpty() else currentIndex > 0
    }

    var state by mutableStateOf(QueueState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playJob: Job? = null

    /**
     * ตั้งค่าคิวใหม่และเริ่มเล่น
     */
    fun playQueue(
        ctx: Context?,
        player: Player?,
        items: List<Hit>,
        startIndex: Int = 0,
        title: String = "คิวเล่นต่อเนื่อง",
        onReady: (() -> Unit)? = null
    ) {
        if (items.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, items.size - 1)
        state = state.copy(
            items = items,
            currentIndex = validIndex,
            title = title,
            isLoadingNext = false
        )
        if (ctx != null && player != null) {
            playCurrent(ctx, player, onReady)
        }
    }

    /**
     * แทรกเล่นต่อจากคลิปปัจจุบัน
     */
    fun playNext(hit: Hit) {
        val current = state.items.toMutableList()
        val insertIndex = (state.currentIndex + 1).coerceAtMost(current.size)
        current.removeAll { it.id == hit.id }
        current.add(insertIndex, hit)
        state = state.copy(items = current)
    }

    /**
     * เพิ่มต่อท้ายคิว
     */
    fun addToQueue(hit: Hit) {
        addToQueue(listOf(hit))
    }

    fun addToQueue(hits: List<Hit>) {
        if (hits.isEmpty()) return
        val current = state.items.toMutableList()
        hits.forEach { h ->
            if (current.none { it.id == h.id }) current.add(h)
        }
        val newIndex = if (state.currentIndex == -1 && current.isNotEmpty()) 0 else state.currentIndex
        state = state.copy(items = current, currentIndex = newIndex)
    }

    /**
     * ลบคลิปออกจากคิว
     */
    fun removeFromQueue(index: Int) {
        if (index !in state.items.indices) return
        val current = state.items.toMutableList()
        current.removeAt(index)
        val newIndex = when {
            current.isEmpty() -> -1
            index < state.currentIndex -> state.currentIndex - 1
            index == state.currentIndex -> index.coerceAtMost(current.size - 1)
            else -> state.currentIndex
        }
        state = state.copy(items = current, currentIndex = newIndex)
    }

    /**
     * ข้ามไปเพลงถัดไป
     */
    fun skipToNext(ctx: Context, player: Player?, onReady: (() -> Unit)? = null) {
        if (state.items.isEmpty()) return
        val nextIndex = when {
            state.isShuffle && state.items.size > 1 -> {
                val available = state.items.indices.filter { it != state.currentIndex }
                available.randomOrNull() ?: 0
            }
            state.currentIndex + 1 < state.items.size -> state.currentIndex + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> return
        }
        state = state.copy(currentIndex = nextIndex)
        playCurrent(ctx, player, onReady)
    }

    /**
     * ข้ามไปเพลงก่อนหน้า
     */
    fun skipToPrevious(ctx: Context, player: Player?, onReady: (() -> Unit)? = null) {
        if (state.items.isEmpty()) return
        val prevIndex = when {
            state.currentIndex > 0 -> state.currentIndex - 1
            state.repeatMode == RepeatMode.ALL -> state.items.size - 1
            else -> 0
        }
        state = state.copy(currentIndex = prevIndex)
        playCurrent(ctx, player, onReady)
    }

    fun jumpToIndex(ctx: Context, player: Player?, index: Int, onReady: (() -> Unit)? = null) {
        if (index in state.items.indices) {
            state = state.copy(currentIndex = index)
            playCurrent(ctx, player, onReady)
        }
    }

    fun toggleRepeat() {
        val next = when (state.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        state = state.copy(repeatMode = next)
    }

    fun toggleShuffle() {
        state = state.copy(isShuffle = !state.isShuffle)
    }

    /**
     * เมื่อเพลงปัจจุบันเล่นจบ (ดักจาก Player.Listener.onPlaybackStateChanged)
     */
    fun onTrackEnded(ctx: Context, player: Player?) {
        if (state.items.isEmpty() || state.currentIndex !in state.items.indices) return
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                player?.seekTo(0)
                player?.play()
            }
            RepeatMode.ALL -> {
                skipToNext(ctx, player)
            }
            RepeatMode.OFF -> {
                if (state.currentIndex + 1 < state.items.size) {
                    skipToNext(ctx, player)
                }
            }
        }
    }

    /**
     * เล่นคลิปตำแหน่งปัจจุบัน
     */
    fun playCurrent(ctx: Context, player: Player?, onReady: (() -> Unit)? = null) {
        val item = state.currentItem ?: return
        playJob?.cancel()
        state = state.copy(isLoadingNext = true)
        playJob = scope.launch {
            val streamUrl = withContext(Dispatchers.IO) {
                if (item.url.startsWith("file://") || item.url.startsWith("content://")) {
                    item.url
                } else {
                    runCatching {
                        Yt.direct(ctx, item.url, "b")
                    }.getOrNull()
                }
            }
            state = state.copy(isLoadingNext = false)
            if (streamUrl != null && player != null) {
                val metadata = MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.channel)
                if (!item.thumb.isNullOrBlank()) metadata.setArtworkUri(Uri.parse(item.thumb))
                val mediaItem = MediaItem.Builder()
                    .setMediaId(MediaIdentity.forPlayback(item.id, streamUrl)?.mediaId.orEmpty())
                    .setUri(streamUrl)
                    .setMediaMetadata(metadata.build())
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                Store.recordInteraction(ctx, item, Store.InterestSignal.OPENED)
                onReady?.invoke()
            }
        }
    }

    /**
     * บันทึกคิวปัจจุบันเป็นเพลย์ลิสต์
     */
    fun saveAsPlaylist(ctx: Context, name: String): Store.Playlist? {
        if (state.items.isEmpty()) return null
        return Store.createPlaylist(ctx, name, state.items)
    }

    fun clearQueue() {
        state = QueueState()
        playJob?.cancel()
    }
}
