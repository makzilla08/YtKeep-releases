package com.chue.ytkeep

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat

const val UA =
    "Mozilla/5.0 (Linux; Android 14; Infinix X6532) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

data class Choice(
    val label: String,
    val url: String,
    val ext: String,
    val bytes: Long,
    val audio: Boolean,
    val selector: String
)

data class Hit(
    val id: String,
    val title: String,
    val channel: String,
    val seconds: Long,
    val thumb: String?,
    val views: String = "",
    val age: String = "",
    val channelId: String = "",
    val sourceQuery: String? = null,
    val sourceTopicId: String? = null
) {
    val url: String get() = "https://www.youtube.com/watch?v=$id"
}

data class Info(
    val videoUrl: String,
    val title: String,
    val uploader: String,
    val seconds: Long,
    val thumb: String?,
    val video: List<Choice>,
    val audio: List<Choice>
)

object Yt {
    const val FLASH = true
    const val YT_ARGS_KEY = "--extractor-args"
    const val YT_CLIENT_VR = "youtube:player_client=tv_embedded"

    @Volatile
    private var ready = false

    fun ensure(ctx: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            YoutubeDL.getInstance().init(ctx.applicationContext)
            runCatching { FFmpeg.getInstance().init(ctx.applicationContext) }
            ready = true
        }
    }

    fun maybeUpdate(ctx: Context, force: Boolean = false) {
        val sp = ctx.getSharedPreferences("ytkeep", Context.MODE_PRIVATE)
        val last = sp.getLong("ytdlp_update", 0L)
        if (!force && System.currentTimeMillis() - last < 3L * 24 * 3600 * 1000) return
        runCatching {
            YoutubeDL.getInstance()
                .updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel._NIGHTLY)
            sp.edit().putLong("ytdlp_update", System.currentTimeMillis()).apply()
        }
    }

    /** เวอร์ชัน yt-dlp ที่ฝังอยู่ (nightly จะขึ้นวันที่ล่าสุด) */
    fun version(ctx: Context): String = runCatching {
        ensure(ctx)
        YoutubeDL.getInstance().version(ctx.applicationContext) ?: "?"
    }.getOrDefault("?")

    fun updateNow(ctx: Context): String {
        ensure(ctx)
        val st = YoutubeDL.getInstance()
            .updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel._NIGHTLY)
        ctx.getSharedPreferences("ytkeep", Context.MODE_PRIVATE)
            .edit().putLong("ytdlp_update", System.currentTimeMillis()).apply()
        return "${version(ctx)} (${st?.name?.lowercase() ?: "ok"})"
    }

    fun fetchFast(ctx: Context, rawUrl: String): Info =
        fetch(ctx, rawUrl, "youtube:player_client=android")

    fun fetchHd(ctx: Context, rawUrl: String): Info = fetch(ctx, rawUrl, YT_CLIENT_VR)

    /** ค้นหาผ่าน yt-dlp (สำรอง ใช้เมื่อ InnerTube ล้มเหลว) */
    fun search(ctx: Context, query: String, limit: Int = 15): List<Hit> {
        ensure(ctx)
        maybeUpdate(ctx)
        val req = YoutubeDLRequest("ytsearch$limit:${query.trim()}")
            .addOption("--flat-playlist")
            .addOption("--dump-json")
            .addOption("--no-warnings")
        return parseFlat(YoutubeDL.getInstance().execute(req).out ?: "")
    }

    /** วิดีโอของช่อง (แบ่งหน้าครั้งละ limit รายการ เริ่มต้นที่ start) */
    fun channelVideos(ctx: Context, channelUrl: String, start: Int = 1, limit: Int = 30): List<Hit> {
        ensure(ctx)
        val end = (start + limit - 1).coerceAtLeast(start)
        val req = YoutubeDLRequest(channelUrl)
            .addOption("--flat-playlist")
            .addOption("--playlist-start", start.toString())
            .addOption("--playlist-end", end.toString())
            .addOption("--dump-json")
            .addOption("--no-warnings")
        return parseFlat(YoutubeDL.getInstance().execute(req).out ?: "").take(limit.coerceAtLeast(0))
    }

    private fun parseFlat(out: String): List<Hit> = out.lineSequence().mapNotNull { line ->
        val t = line.trim()
        if (!t.startsWith("{")) return@mapNotNull null
        runCatching {
            val o = org.json.JSONObject(t)
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val thumb = o.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: o.optJSONArray("thumbnails")?.let { a ->
                    if (a.length() > 0) a.optJSONObject(a.length() - 1)?.optString("url") else null
                }
            Hit(
                id = id,
                title = o.optString("title"),
                channel = o.optString("channel").ifBlank { o.optString("uploader") },
                seconds = o.optLong("duration", 0L),
                thumb = thumb,
                channelId = o.optString("channel_id")
            )
        }.getOrNull()
    }.toList()

    fun fetch(ctx: Context, rawUrl: String, clientArgs: String = YT_CLIENT_VR): Info {
        ensure(ctx)
        maybeUpdate(ctx)
        val url = rawUrl.trim()
        val req = YoutubeDLRequest(url).addOption(YT_ARGS_KEY, clientArgs)
        val v = try {
            YoutubeDL.getInstance().getInfo(req)
        } catch (e: Exception) {
            try {
                YoutubeDL.getInstance().getInfo(url)
            } catch (e2: Exception) {
                maybeUpdate(ctx, force = true)
                YoutubeDL.getInstance().getInfo(url)
            }
        }

        val formats = (v.formats ?: emptyList()).filter { it.ext != "mhtml" }

        fun nz(s: String?) = !s.isNullOrBlank() && s != "none"

        fun size(f: VideoFormat): Long {
            if (f.fileSize > 0) return f.fileSize
            if (f.fileSizeApproximate > 0) return f.fileSizeApproximate
            if (f.tbr > 0 && v.duration > 0) return f.tbr.toLong() * 125L * v.duration
            return 0L
        }

        fun rank(v: String?) = when {
            v == null -> 3
            v.startsWith("avc1") || v.startsWith("h264") -> 0
            v.startsWith("vp9") || v.startsWith("vp09") -> 1
            v.startsWith("av01") -> 2
            else -> 3
        }
        fun codec(v: String?) = when (rank(v)) {
            0 -> "h264"
            1 -> "vp9"
            2 -> "av1"
            else -> v?.substringBefore('.') ?: "?"
        }
        // ไฟล์ https ธรรมดา (ไม่มี manifest) merge เสถียรและขนาดตรงกว่า HLS/DASH
        fun plain(f: VideoFormat) = if (f.manifestUrl.isNullOrBlank()) 0 else 1
        val pref = compareByDescending<VideoFormat> { it.height }
            .thenBy { plain(it) }
            .thenBy { rank(it.vcodec) }
            .thenByDescending { it.tbr }

        val audioBytes = formats
            .filter { nz(it.acodec) && !nz(it.vcodec) }
            .maxOfOrNull { size(it) } ?: 0L

        val muxed = formats
            .filter { nz(it.vcodec) && nz(it.acodec) }
            .sortedWith(pref)
            .distinctBy { it.height }
            .take(3)
        val muxedHeights = muxed.map { it.height }.toSet()
        val videoOnly = formats
            .filter { nz(it.vcodec) && !nz(it.acodec) && it.height > 0 && it.height !in muxedHeights }
            .sortedWith(pref)
            .distinctBy { it.height }
            .take(5)

        val video = (muxed.map {
            Choice(
                label = "${it.height}p · ${fmtBytes(size(it))}",
                url = it.url ?: "",
                ext = it.ext ?: "mp4",
                bytes = size(it),
                audio = false,
                selector = it.formatId ?: "b"
            )
        } + videoOnly.map {
            // อ้าง format id ตรง ๆ เพื่อให้ขนาดที่แสดงตรงกับไฟล์ที่จะโหลดจริง
            Choice(
                label = "${it.height}p ${codec(it.vcodec)} · ${fmtBytes(size(it) + audioBytes)} + เสียง",
                url = it.url ?: "",
                ext = "mp4",
                bytes = size(it) + audioBytes,
                audio = false,
                selector = "${it.formatId}+ba[acodec^=mp4a]/${it.formatId}+ba/${it.formatId}"
            )
        }).sortedByDescending { it.label.substringBefore('p').toIntOrNull() ?: 0 }

        val audio = formats
            .filter { nz(it.acodec) && !nz(it.vcodec) }
            .sortedByDescending { if (it.abr > 0) it.abr else it.tbr }
            .distinctBy { it.abr }
            .take(4)
            .map {
                Choice(
                    label = "${if (it.abr > 0) it.abr else it.tbr} kbps · ${fmtBytes(if (it.fileSize > 0) it.fileSize else it.fileSizeApproximate)}",
                    url = it.url ?: "",
                    ext = it.ext ?: "m4a",
                    bytes = it.fileSize,
                    audio = true,
                    selector = it.formatId ?: "ba"
                )
            }

        if (video.isEmpty() && audio.isEmpty()) {
            throw IllegalStateException("ไม่พบสตรีม (yt-dlp อาจตกรุ่น — กดลองใหม่หรือต่อเน็ตเพื่ออัปเดต)")
        }

        return Info(
            videoUrl = url,
            title = v.title ?: v.fulltitle ?: "ไม่ทราบชื่อ",
            uploader = v.uploader ?: "",
            seconds = v.duration.toLong(),
            thumb = v.thumbnail,
            video = video,
            audio = audio
        )
    }

    fun direct(ctx: Context, videoUrl: String, selector: String): String {
        ensure(ctx)
        val req = YoutubeDLRequest(videoUrl)
            .addOption("-f", selector)
            .addOption("-g")
            .addOption("--no-playlist")
            .addOption(YT_ARGS_KEY, YT_CLIENT_VR)
        val out = YoutubeDL.getInstance().execute(req).out ?: ""
        return out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http") }
            ?: throw IllegalStateException("ดึงลิงก์เล่นไม่สำเร็จ")
    }

    fun videoFormatsForSelector(): String = "b[height<=720]/b"
}

fun fmtBytes(b: Long): String = when {
    b <= 0 -> "?"
    b > 1L shl 30 -> String.format("%.1f GB", b / 1073741824.0)
    b > 1L shl 20 -> String.format("%.0f MB", b / 1048576.0)
    else -> String.format("%.0f KB", b / 1024.0)
}

fun fmtTime(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

fun extractUrl(text: String): String {
    val m = Regex("https?://\\S+").find(text) ?: return text.trim()
    return m.value.trim()
}
