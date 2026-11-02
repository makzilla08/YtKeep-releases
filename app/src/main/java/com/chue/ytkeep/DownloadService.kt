package com.chue.ytkeep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processing = AtomicBoolean(false)
    @Volatile private var activeTaskId: String? = null
    @Volatile private var activeProcessId: String? = null

    override fun onCreate() {
        super.onCreate()
        DownloadQueue.recoverInterrupted(this)
        startForeground(1, build("เตรียมดาวน์โหลด…", 0))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getStringExtra(EXTRA_TASK_ID)
            if (id != null && id == activeTaskId) {
                activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
            }
        }
        if (processing.compareAndSet(false, true)) {
            scope.launch {
                processQueue(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun processQueue(startId: Int) {
        try {
            while (true) {
                val task = DownloadQueue.nextQueued(this) ?: break
                DownloadQueue.markRunning(this, task.id)
                if (DownloadQueue.get(this, task.id)?.state != DownloadState.RUNNING) continue
                activeTaskId = task.id
                notify("กำลังโหลด: ${task.title}", 0)
                try {
                    if (download(task)) {
                        DownloadQueue.complete(this, task.id)
                    } else {
                        DownloadQueue.markCancelled(this, task.id)
                    }
                } catch (e: YoutubeDL.CanceledException) {
                    if (DownloadQueue.get(this, task.id)?.state == DownloadState.CANCELLING) {
                        DownloadQueue.markCancelled(this, task.id)
                    } else if (DownloadQueue.get(this, task.id)?.state != DownloadState.CANCELLED) {
                        DownloadQueue.fail(this, task.id, "ดาวน์โหลดถูกหยุด: ${errorMessage(e)}")
                    }
                } catch (e: Exception) {
                    DownloadQueue.fail(this, task.id, errorMessage(e))
                } finally {
                    activeTaskId = null
                    activeProcessId = null
                }
            }
        } finally {
            processing.set(false)
            if (DownloadQueue.nextQueued(this) != null) {
                if (processing.compareAndSet(false, true)) {
                    scope.launch { processQueue(startId) }
                }
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
    }

    private fun download(task: DownloadTask): Boolean {
        val safe = task.title.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").take(70).ifBlank { "media" }
        val tmp = File(Lib.dir(this), "tmp_${task.id}")
        var target: File? = null
        var indexed = false
        try {
            Yt.ensure(this)
            if (tmp.exists() && !tmp.deleteRecursively()) {
                error("ล้างไฟล์ชั่วคราวเดิมไม่สำเร็จ")
            }
            if (!tmp.mkdirs() && !tmp.isDirectory) error("สร้างโฟลเดอร์ดาวน์โหลดไม่สำเร็จ")
            activeProcessId = tmp.absolutePath
            if (DownloadQueue.get(this, task.id)?.state != DownloadState.RUNNING) return false

            val req = YoutubeDLRequest(task.videoUrl)
                .addOption("-f", task.selector)
                .addOption("--no-playlist")
                .addOption("--no-mtime")
                .addOption(Yt.YT_ARGS_KEY, Yt.YT_CLIENT_VR)
                .addOption("-o", File(tmp, "media.%(ext)s").absolutePath)

            var lastProgressAt = 0L
            var lastNotifyAt = 0L
            var lastProgress = -1
            try {
                YoutubeDL.getInstance().execute(req, tmp.absolutePath) { progress, _, _ ->
                    if (DownloadQueue.get(this, task.id)?.state == DownloadState.CANCELLING) {
                        YoutubeDL.getInstance().destroyProcessById(tmp.absolutePath)
                    }
                    val pct = (progress * 100).toInt().coerceIn(0, 100)
                    val now = System.currentTimeMillis()
                    if (pct != lastProgress && now - lastProgressAt > 900) {
                        lastProgressAt = now
                        lastProgress = pct
                        DownloadQueue.progress(this, task.id, pct)
                    }
                    if (now - lastNotifyAt > 700) {
                        lastNotifyAt = now
                        notify("กำลังโหลด: ${task.title}", pct)
                    }
                }
            } catch (e: Exception) {
                if (e !is YoutubeDL.CanceledException) Yt.maybeUpdate(this, force = true)
                throw e
            }

            if (DownloadQueue.get(this, task.id)?.state != DownloadState.RUNNING) return false
            val produced = tmp.listFiles()?.firstOrNull { it.isFile && !it.name.endsWith(".part") }
                ?: error("ไม่พบไฟล์ที่ดาวน์โหลด")
            val completedFile = File(Lib.dir(this), "$safe-${task.id}.${produced.extension}")
            target = completedFile
            if (!produced.renameTo(completedFile)) {
                produced.copyTo(completedFile, overwrite = true)
                produced.delete()
            }
            if (!tmp.deleteRecursively()) error("ลบไฟล์ชั่วคราวไม่สำเร็จ")

            Lib.add(
                this,
                Lib.Item(
                    id = task.id,
                    title = task.title,
                    uploader = task.uploader,
                    file = completedFile.name,
                    bytes = completedFile.length(),
                    audio = task.audio,
                    ts = System.currentTimeMillis()
                )
            )
            indexed = true
            sendBroadcast(Intent(ACTION_CHANGED).setPackage(packageName))
            return true
        } finally {
            if (tmp.exists() && !tmp.deleteRecursively()) {
                error("ลบไฟล์ชั่วคราวไม่สำเร็จ")
            }
            if (!indexed) target?.delete()
        }
    }

    private fun errorMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    private fun build(title: String, pct: Int): Notification {
        val text = if (pct in 1..99) "$pct%" else "กำลังทำงาน"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .apply { if (pct in 1..99) setProgress(100, pct, false) }
            .build()
    }

    private fun notify(title: String, pct: Int) {
        getSystemService(NotificationManager::class.java).notify(1, build(title, pct))
    }

    override fun onDestroy() {
        activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "dl"
        const val ACTION_CHANGED = "com.chue.ytkeep.CHANGED"
        const val ACTION_PROCESS = "com.chue.ytkeep.PROCESS_DOWNLOADS"
        const val ACTION_CANCEL = "com.chue.ytkeep.CANCEL_DOWNLOAD"
        const val EXTRA_TASK_ID = "taskId"

        fun channel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(CHANNEL, "ดาวน์โหลด", NotificationManager.IMPORTANCE_LOW)
                ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            }
        }

        fun enqueue(ctx: Context, c: Choice, info: Info) {
            channel(ctx)
            DownloadQueue.enqueue(ctx, c, info)
            process(ctx)
        }

        fun retry(ctx: Context, taskId: String) {
            if (DownloadQueue.retry(ctx, taskId)) process(ctx)
        }

        fun cancel(ctx: Context, taskId: String) {
            if (!DownloadQueue.cancel(ctx, taskId)) return
            val intent = Intent(ctx, DownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_TASK_ID, taskId)
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        }

        private fun process(ctx: Context) {
            channel(ctx)
            androidx.core.content.ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, DownloadService::class.java).setAction(ACTION_PROCESS)
            )
        }
    }
}
