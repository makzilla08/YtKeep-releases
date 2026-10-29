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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        startForeground(1, build("เตรียมดาวน์โหลด…", 0))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoUrl = intent?.getStringExtra("videoUrl")
        val selector = intent?.getStringExtra("selector") ?: "b"
        val title = intent?.getStringExtra("title") ?: "วิดีโอ"
        val uploader = intent?.getStringExtra("uploader") ?: ""
        val audio = intent?.getBooleanExtra("audio", false) ?: false
        if (videoUrl.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        active.incrementAndGet()
        scope.launch {
            gate.withPermit {
                runCatching { download(videoUrl, selector, title, uploader, audio) }
                    .onFailure { notify("โหลดไม่สำเร็จ: ${it.message}", 0) }
            }
            if (active.decrementAndGet() == 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun download(
        videoUrl: String,
        selector: String,
        title: String,
        uploader: String,
        audio: Boolean
    ) {
        Yt.ensure(this)
        val safe = title.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").take(70).ifBlank { "media" }
        val id = System.currentTimeMillis().toString()
        val tmp = File(Lib.dir(this), "tmp_$id").apply { mkdirs() }

        val req = YoutubeDLRequest(videoUrl)
            .addOption("-f", selector)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption(Yt.YT_ARGS_KEY, Yt.YT_CLIENT_VR)
            .addOption("-o", File(tmp, "media.%(ext)s").absolutePath)

        var lastNotify = 0L
        try {
            YoutubeDL.getInstance().execute(req, tmp.absolutePath) { progress, _, _ ->
                val now = System.currentTimeMillis()
                if (now - lastNotify > 700) {
                    lastNotify = now
                    notify("กำลังโหลด: $title", (progress * 100).toInt().coerceIn(0, 100))
                }
            }
        } catch (e: Exception) {
            tmp.deleteRecursively()
            Yt.maybeUpdate(this, force = true)
            throw e
        }

        val produced = tmp.listFiles()?.firstOrNull { it.isFile && !it.name.endsWith(".part") }
            ?: run {
                tmp.deleteRecursively()
                error("ไม่พบไฟล์ที่ดาวน์โหลด")
            }
        val target = File(Lib.dir(this), "$safe-$id.${produced.extension}")
        if (!produced.renameTo(target)) {
            produced.copyTo(target, overwrite = true)
            produced.delete()
        }
        tmp.deleteRecursively()

        Lib.add(
            this,
            Lib.Item(
                id = id,
                title = title,
                uploader = uploader,
                file = target.name,
                bytes = target.length(),
                audio = audio,
                ts = System.currentTimeMillis()
            )
        )
        sendBroadcast(Intent(ACTION_CHANGED).setPackage(packageName))
    }

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
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "dl"
        const val ACTION_CHANGED = "com.chue.ytkeep.CHANGED"
        private val gate = Semaphore(1)

        fun channel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(CHANNEL, "ดาวน์โหลด", NotificationManager.IMPORTANCE_LOW)
                ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            }
        }

        fun enqueue(ctx: Context, c: Choice, info: Info) {
            channel(ctx)
            val i = Intent(ctx, DownloadService::class.java)
                .putExtra("videoUrl", info.videoUrl)
                .putExtra("selector", c.selector)
                .putExtra("title", info.title)
                .putExtra("uploader", info.uploader)
                .putExtra("audio", c.audio)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
    }
}
