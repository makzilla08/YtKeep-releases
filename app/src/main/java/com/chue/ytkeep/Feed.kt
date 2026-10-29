package com.chue.ytkeep

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/**
 * ค้นหา/ฟีดผ่าน InnerTube (API เดียวกับหน้าเว็บ YouTube) — เร็ว ~0.5-1 วิ ไม่ต้องล็อกอิน
 * ใช้เฉพาะการค้นหา/สำรวจ ส่วนดาวน์โหลดและลิงก์สตรีมยังใช้ yt-dlp
 */
object Feed {
    const val LATEST = "CAISAhAB"   // เรียงตามวันที่อัปโหลด, เฉพาะวิดีโอ
    const val POPULAR = "CAMSAhAB"  // เรียงตามยอดวิว, เฉพาะวิดีโอ

    data class Topic(val id: String, val label: String, val query: String)

    val topics = listOf(
        Topic("news", "📰 ข่าว", "ข่าว"),
        Topic("music", "🎵 เพลง", "เพลง"),
        Topic("sport", "⚽ กีฬา", "กีฬา"),
        Topic("tech", "💻 เทค", "เทคโนโลยี"),
        Topic("gaming", "🎮 เกม", "เกม"),
        Topic("science", "🔬 วิทยาศาสตร์", "วิทยาศาสตร์"),
        Topic("learning", "📚 ความรู้", "สอนความรู้"),
        Topic("comedy", "😂 ตลก", "ตลก"),
        Topic("cooking", "🍳 ทำอาหาร", "ทำอาหาร"),
        Topic("travel", "✈️ ท่องเที่ยว", "ท่องเที่ยว"),
        Topic("nature", "🌿 ธรรมชาติ", "สัตว์ธรรมชาติ"),
        Topic("movies", "🎬 หนัง", "รีวิวหนัง")
    )

    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CONTEXT =
        """{"client":{"clientName":"WEB","clientVersion":"2.20260101.00.00","hl":"th","gl":"TH"}}"""

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    private fun post(path: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$path?key=$KEY&prettyPrint=false")
            .header("User-Agent", UA)
            .post(body.toString().toRequestBody(json))
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException("YouTube ตอบ ${r.code}")
            return JSONObject(text)
        }
    }

    /** ค้นหาแบบเร็ว: sort = LATEST (ใหม่สุด) หรือ POPULAR (ยอดวิว) */
    fun search(
        query: String,
        sort: String = LATEST,
        limit: Int = 20,
        topicId: String? = null
    ): List<Hit> {
        val body = JSONObject()
            .put("context", JSONObject(CONTEXT))
            .put("query", query)
        if (sort.isNotBlank()) body.put("params", sort)
        return parseHits(post("search", body)).take(limit).map {
            it.copy(
                sourceQuery = query.takeIf { topicId == null },
                sourceTopicId = topicId
            )
        }
    }

    suspend fun recommendations(ctx: android.content.Context, limit: Int = 30): List<Hit> =
        coroutineScope {
            val preferences = Store.recommendationPreferences(ctx)
            val chosenTopics = preferences.topics.entries
                .sortedByDescending { it.value }
                .mapNotNull { score -> topics.firstOrNull { it.id == score.key }?.let { it to score.value } }
                .take(2)
            val preferredTopicIds = chosenTopics.mapTo(HashSet()) { it.first.id }
            val today = java.time.LocalDate.now().dayOfYear - 1
            val explorationCount = if (
                preferences.queries.isEmpty() && chosenTopics.isEmpty()
            ) 5 else 1
            val explorationTopics = (0 until topics.size)
                .map { topics[(today + it) % topics.size] }
                .filterNot { it.id in preferredTopicIds }
                .take(explorationCount)

            data class Request(val query: String, val topicId: String?, val weight: Int)

            val requests = buildList {
                preferences.queries.entries
                    .sortedByDescending { it.value }
                    .take(2)
                    .forEach { (query, score) ->
                        add(Request(query, null, score.coerceIn(1, 6)))
                    }
                chosenTopics.forEach { (topic, score) ->
                    add(Request(topic.query, topic.id, score.coerceIn(1, 6)))
                }
                explorationTopics.forEach { topic ->
                    add(Request(topic.query, topic.id, 1))
                }
            }.distinctBy { it.query.lowercase() }.take(5)

            val results = requests.map { request ->
                async(Dispatchers.IO) {
                    request to search(request.query, LATEST, 12, request.topicId)
                }
            }.awaitAll()
            val candidates = LinkedHashMap<String, RecommendationRanker.RankedHit>()
            results.forEach { (request, hits) ->
                hits.forEach { hit ->
                    val score = request.weight +
                        (preferences.channels[hit.channelId] ?: 0).coerceIn(0, 5)
                    val current = candidates[hit.id]
                    if (current == null || score > current.score) {
                        candidates[hit.id] = RecommendationRanker.RankedHit(hit, score)
                    }
                }
            }
            RecommendationRanker.rank(
                candidates = candidates.values.toList(),
                recentVideoIds = preferences.recentVideoIds.toSet(),
                limit = limit
            )
        }

    data class Channel(
        val id: String,
        val name: String,
        val thumb: String?,
        val subs: String
    ) {
        val url: String get() = "https://www.youtube.com/channel/$id"
    }

    /** ค้นหาช่อง (พิมพ์ชื่อช่อง) */
    fun searchChannels(query: String, limit: Int = 6): List<Channel> {
        val body = JSONObject().put("context", JSONObject(CONTEXT)).put("query", query)
        val out = ArrayList<Channel>()
        val seen = HashSet<String>()
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> {
                    o.optJSONObject("channelRenderer")?.let { c ->
                        val id = c.optString("channelId")
                        if (id.isNotBlank() && seen.add(id)) {
                            val thumbs = c.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            out.add(
                                Channel(
                                    id = id,
                                    name = c.optJSONObject("title")?.optString("simpleText")
                                        ?: c.optJSONObject("title")?.optJSONArray("runs")
                                            ?.optJSONObject(0)?.optString("text").orEmpty(),
                                    thumb = if (thumbs != null && thumbs.length() > 0)
                                        thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") else null,
                                    subs = c.optJSONObject("subscriberCountText")?.optString("simpleText")
                                        ?: c.optJSONObject("videoCountText")?.optString("simpleText").orEmpty()
                                )
                            )
                        }
                    }
                    for (k in o.keys()) if (k != "channelRenderer") walk(o.opt(k))
                }
                is org.json.JSONArray -> for (i in 0 until o.length()) walk(o.opt(i))
            }
        }
        walk(post("search", body))
        return out.take(limit)
    }

    /** วิดีโอใหม่ของช่อง (ใช้ yt-dlp เพราะ InnerTube ต้องใช้ params ที่เปลี่ยนบ่อย) */
    fun channel(ctx: android.content.Context, url: String, limit: Int = 8): List<Hit> =
        runCatching { Yt.channelVideos(ctx, url, limit) }.getOrDefault(emptyList())

    fun parseHits(root: JSONObject): List<Hit> {
        val out = ArrayList<Hit>()
        val seen = HashSet<String>()
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> {
                    o.optJSONObject("videoRenderer")?.let { v ->
                        val id = v.optString("videoId")
                        val live = v.optJSONArray("badges")?.toString()?.contains("LIVE") == true
                        if (id.isNotBlank() && !live && seen.add(id)) {
                            val thumbs = v.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumb = if (thumbs != null && thumbs.length() > 0)
                                thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") else null
                            out.add(
                                Hit(
                                    id = id,
                                    title = v.optJSONObject("title")?.optJSONArray("runs")
                                        ?.optJSONObject(0)?.optString("text").orEmpty(),
                                    channel = v.optJSONObject("ownerText")?.optJSONArray("runs")
                                        ?.optJSONObject(0)?.optString("text").orEmpty(),
                                    seconds = parseLen(v.optJSONObject("lengthText")?.optString("simpleText")),
                                    thumb = thumb,
                                    views = v.optJSONObject("viewCountText")?.optString("simpleText").orEmpty(),
                                    age = v.optJSONObject("publishedTimeText")?.optString("simpleText").orEmpty(),
                                    channelId = v.optJSONObject("ownerText")?.optJSONArray("runs")
                                        ?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                                        ?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                                )
                            )
                        }
                    }
                    o.optJSONObject("richItemRenderer")?.let { walk(it) }
                    o.optJSONObject("content")?.let { walk(it) }
                    for (k in o.keys()) if (k != "videoRenderer") walk(o.opt(k))
                }
                is org.json.JSONArray -> for (i in 0 until o.length()) walk(o.opt(i))
            }
        }
        walk(root)
        return out
    }

    private fun parseLen(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        var sec = 0L
        for (p in s.split(":")) sec = sec * 60 + (p.trim().toLongOrNull() ?: return 0)
        return sec
    }
}
