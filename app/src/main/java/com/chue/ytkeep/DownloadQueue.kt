package com.chue.ytkeep

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class DownloadState {
    QUEUED,
    RUNNING,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val id: String,
    val videoUrl: String,
    val selector: String,
    val title: String,
    val uploader: String,
    val quality: String,
    val audio: Boolean,
    val createdAt: Long,
    val state: DownloadState = DownloadState.QUEUED,
    val progress: Int = 0,
    val error: String? = null
) {
    fun start() = if (state == DownloadState.QUEUED) copy(state = DownloadState.RUNNING) else this

    fun withProgress(value: Int) =
        if (state == DownloadState.RUNNING) copy(progress = value.coerceIn(0, 100)) else this

    fun complete() =
        if (state == DownloadState.RUNNING || state == DownloadState.CANCELLING) {
            copy(state = DownloadState.COMPLETED, progress = 100, error = null)
        } else {
            this
        }

    fun fail(message: String) =
        if (state == DownloadState.RUNNING || state == DownloadState.CANCELLING) {
            copy(state = DownloadState.FAILED, error = message)
        } else {
            this
        }

    fun cancel() = when (state) {
        DownloadState.QUEUED -> copy(state = DownloadState.CANCELLED)
        DownloadState.RUNNING -> copy(state = DownloadState.CANCELLING)
        else -> this
    }

    fun cancelled() =
        if (state == DownloadState.CANCELLING) copy(state = DownloadState.CANCELLED) else this

    fun retry() =
        if (state == DownloadState.FAILED || state == DownloadState.CANCELLED) {
            copy(state = DownloadState.QUEUED, progress = 0, error = null)
        } else {
            this
        }

    fun recover() =
        if (state == DownloadState.RUNNING || state == DownloadState.CANCELLING) {
            copy(
                state = DownloadState.FAILED,
                error = "ดาวน์โหลดหยุดก่อนเสร็จ กดลองใหม่ได้"
            )
        } else {
            this
        }
}

object DownloadQueue {
    private const val PREFERENCES = "ytkeep_downloads"
    private const val KEY_TASKS = "tasks"
    private val lock = Any()

    fun all(ctx: Context): List<DownloadTask> = synchronized(lock) {
        read(ctx).sortedByDescending { it.createdAt }
    }

    fun enqueue(ctx: Context, choice: Choice, info: Info): DownloadTask = synchronized(lock) {
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            videoUrl = info.videoUrl,
            selector = choice.selector,
            title = info.title,
            uploader = info.uploader,
            quality = choice.label,
            audio = choice.audio,
            createdAt = System.currentTimeMillis()
        )
        write(ctx, read(ctx) + task)
        task
    }

    fun nextQueued(ctx: Context): DownloadTask? = synchronized(lock) {
        read(ctx).filter { it.state == DownloadState.QUEUED }.minByOrNull { it.createdAt }
    }

    fun get(ctx: Context, id: String): DownloadTask? =
        synchronized(lock) { read(ctx).firstOrNull { it.id == id } }

    fun markRunning(ctx: Context, id: String) = update(ctx, id, DownloadTask::start)

    fun progress(ctx: Context, id: String, progress: Int) =
        update(ctx, id) { it.withProgress(progress) }

    fun complete(ctx: Context, id: String) = update(ctx, id, DownloadTask::complete)

    fun fail(ctx: Context, id: String, message: String) =
        update(ctx, id) { it.fail(message) }

    fun cancel(ctx: Context, id: String) = update(ctx, id, DownloadTask::cancel)

    fun markCancelled(ctx: Context, id: String) = update(ctx, id, DownloadTask::cancelled)

    fun retry(ctx: Context, id: String) = update(ctx, id, DownloadTask::retry)

    fun clearFinished(ctx: Context) = synchronized(lock) {
        write(
            ctx,
            read(ctx).filterNot {
                it.state == DownloadState.COMPLETED || it.state == DownloadState.CANCELLED
            }
        )
    }

    fun recoverInterrupted(ctx: Context) = synchronized(lock) {
        val directory = Lib.dir(ctx)
        write(
            ctx,
            read(ctx).map {
                if (it.state == DownloadState.RUNNING || it.state == DownloadState.CANCELLING) {
                    val temp = File(directory, "tmp_${it.id}")
                    val removed = !temp.exists() || temp.deleteRecursively()
                    val recovered = it.recover()
                    if (removed) recovered else recovered.copy(
                        error = "ดาวน์โหลดหยุดก่อนเสร็จ ลบไฟล์ชั่วคราวไม่สำเร็จ"
                    )
                } else {
                    it
                }
            }
        )
    }

    private fun update(ctx: Context, id: String, change: (DownloadTask) -> DownloadTask) =
        synchronized(lock) {
            val current = read(ctx)
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return@synchronized false
            val updated = current.toMutableList()
            val next = change(updated[index])
            if (next == updated[index]) return@synchronized false
            updated[index] = next
            write(ctx, updated)
            true
        }

    private fun read(ctx: Context): List<DownloadTask> {
        val prefs = ctx.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_TASKS, "[]") ?: "[]")
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            DownloadTask(
                id = item.getString("id"),
                videoUrl = item.getString("videoUrl"),
                selector = item.getString("selector"),
                title = item.getString("title"),
                uploader = item.getString("uploader"),
                quality = item.getString("quality"),
                audio = item.getBoolean("audio"),
                createdAt = item.getLong("createdAt"),
                state = DownloadState.valueOf(item.getString("state")),
                progress = item.optInt("progress").coerceIn(0, 100),
                error = item.optString("error").takeIf { it.isNotBlank() }
            )
        }
    }

    private fun write(ctx: Context, tasks: List<DownloadTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("videoUrl", task.videoUrl)
                    .put("selector", task.selector)
                    .put("title", task.title)
                    .put("uploader", task.uploader)
                    .put("quality", task.quality)
                    .put("audio", task.audio)
                    .put("createdAt", task.createdAt)
                    .put("state", task.state.name)
                    .put("progress", task.progress)
                    .put("error", task.error.orEmpty())
            )
        }
        ctx.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASKS, array.toString())
            .apply()
    }
}
