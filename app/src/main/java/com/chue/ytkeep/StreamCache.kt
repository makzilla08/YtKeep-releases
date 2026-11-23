package com.chue.ytkeep

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * StreamCache: cache + single-flight สำหรับ direct stream URL
 *
 * กติกาสำคัญ:
 * - yt-dlp ถูกเรียกทีละงานเท่านั้น (ytdlpLock) เพราะ getInfo() ยกเลิกไม่ได้
 * - งานที่ผู้ใช้กดเล่นมีความสำคัญกว่า prefetch — prefetch จะไม่เริ่มถ้ามีคนรอกดเล่นอยู่
 * - คำขอซ้ำวิดีโอเดียวกันระหว่างดึงอยู่จะรอ Deferred ตัวเดียวกัน ไม่ยิง yt-dlp ซ้ำ
 */
object StreamCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // URL ของ YouTube หมดอายุไว — เก็บ 30 นาทีพอ
    private const val TTL_MS = 30 * 60 * 1000L
    private const val MAX_ENTRIES = 20

    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val inflightLock = Mutex()
    private val inflight = HashMap<String, Deferred<String>>()
    private val ytdlpLock = Mutex()
    private val interactive = AtomicInteger(0)

    private fun key(videoUrl: String, selector: String) = "$videoUrl|$selector"

    fun get(videoUrl: String, selector: String): String? {
        val k = key(videoUrl, selector)
        val entry = cache[k] ?: return null
        return if (System.currentTimeMillis() < entry.second) {
            entry.first
        } else {
            cache.remove(k)
            null
        }
    }

    private fun put(videoUrl: String, selector: String, directUrl: String) {
        cleanExpired()
        val k = key(videoUrl, selector)
        if (cache.size >= MAX_ENTRIES && !cache.containsKey(k)) {
            cache.minByOrNull { it.value.second }?.key?.let(cache::remove)
        }
        cache[k] = directUrl to (System.currentTimeMillis() + TTL_MS)
    }

    /** เรียกเมื่อผู้ใช้กดเล่นจริง — มีสิทธิ์เหนือ prefetch */
    suspend fun getOrFetch(ctx: Context, videoUrl: String, selector: String): String {
        get(videoUrl, selector)?.let { return it }
        interactive.incrementAndGet()
        try {
            return fetch(ctx.applicationContext, videoUrl, selector)
        } finally {
            interactive.decrementAndGet()
        }
    }

    private suspend fun fetch(ctx: Context, videoUrl: String, selector: String): String {
        val k = key(videoUrl, selector)
        val job = inflightLock.withLock {
            inflight[k] ?: scope.async {
                try {
                    val url = ytdlpLock.withLock {
                        Yt.direct(ctx.applicationContext, videoUrl, selector)
                    }
                    put(videoUrl, selector, url)
                    url
                } finally {
                    inflightLock.withLock { inflight.remove(k) }
                }
            }.also { inflight[k] = it }
        }
        return job.await()
    }

    /** เตรียม URL ล่วงหน้าแบบเงียบ ๆ — ไม่แย่งคิวกับการกดเล่นของผู้ใช้ */
    fun prefetch(ctx: Context, videoUrl: String, selector: String = "b") {
        if (get(videoUrl, selector) != null) return
        scope.launch {
            if (interactive.get() > 0) return@launch
            runCatching { fetch(ctx.applicationContext, videoUrl, selector) }
        }
    }

    fun clear() {
        cache.clear()
    }

    private fun cleanExpired() {
        val now = System.currentTimeMillis()
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            if (now >= it.next().value.second) it.remove()
        }
    }
}
