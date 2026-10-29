package com.chue.ytkeep

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ความจำของแอป: ช่องที่ติดตาม, คลิปที่บันทึก, คุณภาพเริ่มต้นของไฟล์ที่ดาวน์โหลด
 * เก็บใน SharedPreferences ("ytkeep")
 */
object Store {
    private const val K_FOLLOW = "follows"
    private const val K_SAVED = "saved"
    private const val K_QUALITY = "defQuality"
    private const val K_RECOMMENDATIONS = "recommendationPrefs"

    enum class InterestSignal(val weight: Int) {
        OPENED(1),
        SAVED(3),
        DOWNLOADED(3)
    }

    data class RecommendationPreferences(
        val queries: Map<String, Int> = emptyMap(),
        val topics: Map<String, Int> = emptyMap(),
        val channels: Map<String, Int> = emptyMap(),
        val recentVideoIds: List<String> = emptyList()
    )

    /** ตัวเลือกคุณภาพเริ่มต้น: audio (ประหยัดสุด) / 360 / 480 / 720 / 1080 / best */
    fun defQuality(ctx: Context): String = sp(ctx).getString(K_QUALITY, "audio") ?: "audio"

    fun setDefQuality(ctx: Context, q: String) {
        sp(ctx).edit().putString(K_QUALITY, q).apply()
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ytkeep", Context.MODE_PRIVATE)

    // ---------- ติดตาม ----------
    fun follows(ctx: Context): List<Pair<String, String>> = runCatching {
        val a = JSONArray(sp(ctx).getString(K_FOLLOW, "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            o.optString("name") to o.optString("url")
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun follow(ctx: Context, name: String, url: String, channelId: String = "") {
        if (url.isBlank()) return
        val list = follows(ctx)
        if (list.any { it.second == url }) return
        val a = JSONArray()
        (list + (name.ifBlank { url } to url)).forEach {
            a.put(JSONObject().put("name", it.first).put("url", it.second))
        }
        sp(ctx).edit().putString(K_FOLLOW, a.toString()).apply()
        if (channelId.isNotBlank()) updateRecommendationPreferences(ctx) { current ->
            current.copy(channels = addScore(current.channels, channelId, 3, 100))
        }
    }

    @Synchronized
    fun unfollow(ctx: Context, url: String) {
        val a = JSONArray()
        follows(ctx).filter { it.second != url }.forEach {
            a.put(JSONObject().put("name", it.first).put("url", it.second))
        }
        sp(ctx).edit().putString(K_FOLLOW, a.toString()).apply()
        val channelId = url.substringAfterLast('/').substringBefore('?')
        if (channelId.isNotBlank()) updateRecommendationPreferences(ctx) { current ->
            current.copy(channels = current.channels - channelId)
        }
    }

    // ---------- บันทึกไว้ดู ----------
    fun saved(ctx: Context): List<Hit> = runCatching {
        val a = JSONArray(sp(ctx).getString(K_SAVED, "[]"))
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { toHit(it) } }
    }.getOrDefault(emptyList())

    fun isSaved(ctx: Context, id: String) = saved(ctx).any { it.id == id }

    @Synchronized
    fun toggleSave(ctx: Context, hit: Hit) {
        val already = saved(ctx).any { it.id == hit.id }
        val next = if (already) saved(ctx).filterNot { it.id == hit.id } else saved(ctx) + hit
        val a = JSONArray()
        next.forEach { a.put(fromHit(it)) }
        sp(ctx).edit().putString(K_SAVED, a.toString()).apply()
        if (!already) recordInteraction(ctx, hit, InterestSignal.SAVED)
    }

    private fun fromHit(h: Hit) = JSONObject()
        .put("id", h.id).put("title", h.title).put("channel", h.channel)
        .put("seconds", h.seconds).put("thumb", h.thumb ?: "")
        .put("views", h.views).put("age", h.age).put("channelId", h.channelId)
        .put("sourceQuery", h.sourceQuery ?: "")
        .put("sourceTopicId", h.sourceTopicId ?: "")

    private fun toHit(o: JSONObject) = Hit(
        id = o.optString("id"),
        title = o.optString("title"),
        channel = o.optString("channel"),
        seconds = o.optLong("seconds", 0L),
        thumb = o.optString("thumb").takeIf { it.isNotBlank() },
        views = o.optString("views"),
        age = o.optString("age"),
        channelId = o.optString("channelId"),
        sourceQuery = o.optString("sourceQuery").takeIf { it.isNotBlank() },
        sourceTopicId = o.optString("sourceTopicId").takeIf { it.isNotBlank() }
    )

    fun recommendationPreferences(ctx: Context): RecommendationPreferences = runCatching {
        val root = JSONObject(sp(ctx).getString(K_RECOMMENDATIONS, "{}") ?: "{}")
        fun scores(name: String): Map<String, Int> {
            val o = root.optJSONObject(name) ?: return emptyMap()
            return buildMap {
                for (key in o.keys()) {
                    val score = o.optInt(key)
                    if (score > 0) put(key, score)
                }
            }
        }
        val recent = root.optJSONArray("recentVideoIds") ?: JSONArray()
        RecommendationPreferences(
            queries = scores("queries"),
            topics = scores("topics"),
            channels = scores("channels"),
            recentVideoIds = (0 until recent.length())
                .map { recent.optString(it) }
                .filter { it.isNotBlank() }
        )
    }.getOrElse { error ->
        android.util.Log.w("YtKeep", "Could not read recommendation preferences", error)
        RecommendationPreferences()
    }

    @Synchronized
    fun recordInteraction(
        ctx: Context,
        hit: Hit,
        signal: InterestSignal
    ) {
        updateRecommendationPreferences(ctx) { current ->
            val query = hit.sourceQuery
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.take(80)
                ?.lowercase(java.util.Locale.ROOT)
                ?.takeIf { it.isNotBlank() && !it.contains("://") }
            val topics = hit.sourceTopicId?.takeIf { it.isNotBlank() }
            var next = current.copy(
                queries = decay(current.queries),
                topics = decay(current.topics),
                channels = decay(current.channels),
                recentVideoIds = (current.recentVideoIds.filterNot { it == hit.id } + hit.id)
                    .takeLast(100)
            )
            if (query != null) {
                next = next.copy(queries = addScore(next.queries, query, signal.weight, 20))
            }
            if (topics != null) {
                next = next.copy(topics = addScore(next.topics, topics, signal.weight, 20))
            }
            if (hit.channelId.isNotBlank()) {
                next = next.copy(
                    channels = addScore(next.channels, hit.channelId, signal.weight, 100)
                )
            }
            next
        }
    }

    @Synchronized
    fun clearRecommendationHistory(ctx: Context) {
        sp(ctx).edit().remove(K_RECOMMENDATIONS).apply()
    }

    private fun updateRecommendationPreferences(
        ctx: Context,
        update: (RecommendationPreferences) -> RecommendationPreferences
    ) {
        val next = update(recommendationPreferences(ctx))
        val root = JSONObject()
        fun JSONObject.putScores(name: String, values: Map<String, Int>) {
            val scores = JSONObject()
            values.forEach { (key, value) -> scores.put(key, value) }
            put(name, scores)
        }
        root.putScores("queries", next.queries)
        root.putScores("topics", next.topics)
        root.putScores("channels", next.channels)
        root.put("recentVideoIds", JSONArray().apply {
            next.recentVideoIds.forEach(::put)
        })
        sp(ctx).edit().putString(K_RECOMMENDATIONS, root.toString()).apply()
    }

    private fun addScore(
        current: Map<String, Int>,
        key: String,
        amount: Int,
        maxEntries: Int
    ): Map<String, Int> = (current + (key to ((current[key] ?: 0) + amount).coerceAtMost(100)))
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenByDescending { it.key == key }
        )
        .take(maxEntries)
        .associate { it.key to it.value }

    private fun decay(scores: Map<String, Int>): Map<String, Int> =
        scores.mapValues { (_, score) -> (score * 4 / 5).coerceAtLeast(1) }
            .filterValues { it > 0 }
}
