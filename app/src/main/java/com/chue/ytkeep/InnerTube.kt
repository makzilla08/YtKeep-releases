package com.chue.ytkeep

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * InnerTube: ทางลัดดึง URL เล่นจาก YouTube โดยไม่ต้องปลุก Python/yt-dlp
 *
 * ใช้ ANDROID client ซึ่งตอบเร็ว ~0.2-0.4 วิ และคืน URL แบบ muxed (360p) ที่ ExoPlayer เล่นได้เลย
 * ข้อจำกัด: YouTube ให้แค่ความละเอียดระดับ muxed ผ่านทางนี้ — HD ยังต้องใช้ yt-dlp ดาวน์โหลด
 */
object InnerTube {
    private const val CLIENT_VERSION = "21.26.364"
    private const val UA = "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android 11) gzip"

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** คืน URL สตรีม muxed ที่เล่นได้ทันที หรือ null ถ้าใช้ไม่ได้ */
    suspend fun fastStreamUrl(ctx: Context, videoId: String, maxHeight: Int = 360): String? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null
            runCatching {
                val client = JSONObject()
                    .put("clientName", "ANDROID")
                    .put("clientVersion", CLIENT_VERSION)
                    .put("androidSdkVersion", 30)
                    .put("userAgent", UA)
                    .put("osName", "Android")
                    .put("osVersion", "11")
                    .put("hl", "en")
                    .put("timeZone", "UTC")
                    .put("utcOffsetMinutes", 0)

                val body = JSONObject()
                    .put("context", JSONObject().put("client", client))
                    .put("videoId", videoId)
                    .put("contentCheckOk", true)
                    .put("racyCheckOk", true)

                val req = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", UA)
                    .header("X-Youtube-Client-Name", "3")
                    .header("X-Youtube-Client-Version", CLIENT_VERSION)
                    .header("Origin", "https://www.youtube.com")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val text = resp.body?.string().orEmpty()
                    if (text.isBlank()) return@use null
                    val json = JSONObject(text)
                    if (json.optJSONObject("playabilityStatus")?.optString("status") != "OK") {
                        return@use null
                    }
                    val formats = json.optJSONObject("streamingData")?.optJSONArray("formats")
                        ?: return@use null

                    var best: JSONObject? = null
                    var bestHeight = -1
                    for (i in 0 until formats.length()) {
                        val f = formats.optJSONObject(i) ?: continue
                        val url = f.optString("url")
                        if (!url.startsWith("http")) continue
                        val h = f.optInt("height", 0)
                        if (h > maxHeight) continue
                        if (h > bestHeight) {
                            best = f
                            bestHeight = h
                        }
                    }
                    (best ?: formats.optJSONObject(0))?.optString("url")?.takeIf { it.startsWith("http") }
                }
            }.getOrNull()
        }

    /** แปลง URL หน้า watch ให้เป็น videoId */
    fun videoIdOf(url: String): String = when {
        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
        url.contains("v=") -> url.substringAfter("v=").substringBefore("&").substringBefore("?")
        else -> ""
    }
}
