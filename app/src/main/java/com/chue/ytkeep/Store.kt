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
    private const val K_DISABLED_TOPICS = "disabledRecommendationTopics"
    private const val K_CUSTOM_TOPICS = "customRecommendationTopics"
    private const val K_PLAYLISTS = "playlists"

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

    // ---------- เพลย์ลิสต์ (Playlists) ----------
    data class Playlist(
        val id: String,
        val name: String,
        val createdAt: Long = System.currentTimeMillis(),
        val items: List<Hit> = emptyList()
    )

    fun playlists(ctx: Context): List<Playlist> = runCatching {
        val a = JSONArray(sp(ctx).getString(K_PLAYLISTS, "[]"))
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { toPlaylist(it) } }
    }.getOrDefault(emptyList())

    fun getPlaylist(ctx: Context, playlistId: String): Playlist? =
        playlists(ctx).firstOrNull { it.id == playlistId }

    @Synchronized
    fun createPlaylist(ctx: Context, name: String, initialItems: List<Hit> = emptyList()): Playlist {
        val trimmed = name.trim().ifBlank { "เพลย์ลิสต์ใหม่" }
        val id = "pl_${System.currentTimeMillis()}_${(100..999).random()}"
        val playlist = Playlist(
            id = id,
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            items = initialItems.distinctBy { it.id }
        )
        val all = playlists(ctx) + playlist
        savePlaylists(ctx, all)
        return playlist
    }

    @Synchronized
    fun deletePlaylist(ctx: Context, playlistId: String) {
        val remaining = playlists(ctx).filterNot { it.id == playlistId }
        savePlaylists(ctx, remaining)
    }

    @Synchronized
    fun renamePlaylist(ctx: Context, playlistId: String, newName: String) {
        val trimmed = newName.trim().ifBlank { return }
        val updated = playlists(ctx).map {
            if (it.id == playlistId) it.copy(name = trimmed) else it
        }
        savePlaylists(ctx, updated)
    }

    @Synchronized
    fun addToPlaylist(ctx: Context, playlistId: String, hit: Hit) {
        addToPlaylist(ctx, playlistId, listOf(hit))
    }

    @Synchronized
    fun addToPlaylist(ctx: Context, playlistId: String, hits: List<Hit>) {
        if (hits.isEmpty()) return
        val current = playlists(ctx)
        val target = current.firstOrNull { it.id == playlistId } ?: return
        val existingIds = target.items.map { it.id }.toSet()
        val newItems = hits.filterNot { it.id in existingIds }
        if (newItems.isEmpty()) return
        val updatedPlaylist = target.copy(items = target.items + newItems)
        val updated = current.map { if (it.id == playlistId) updatedPlaylist else it }
        savePlaylists(ctx, updated)
    }

    @Synchronized
    fun removeFromPlaylist(ctx: Context, playlistId: String, videoId: String) {
        val current = playlists(ctx)
        val target = current.firstOrNull { it.id == playlistId } ?: return
        val updatedPlaylist = target.copy(items = target.items.filterNot { it.id == videoId })
        val updated = current.map { if (it.id == playlistId) updatedPlaylist else it }
        savePlaylists(ctx, updated)
    }

    private fun savePlaylists(ctx: Context, list: List<Playlist>) {
        val a = JSONArray()
        list.forEach { a.put(fromPlaylist(it)) }
        sp(ctx).edit().putString(K_PLAYLISTS, a.toString()).apply()
    }

    private fun fromPlaylist(p: Playlist) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("createdAt", p.createdAt)
        val arr = JSONArray()
        p.items.forEach { arr.put(fromHit(it)) }
        put("items", arr)
    }

    private fun toPlaylist(o: JSONObject): Playlist {
        val arr = o.optJSONArray("items") ?: JSONArray()
        val hits = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { toHit(it) }
        }
        return Playlist(
            id = o.optString("id"),
            name = o.optString("name"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            items = hits
        )
    }

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

    fun disabledTopicIds(ctx: Context): Set<String> = runCatching {
        val topics = JSONArray(sp(ctx).getString(K_DISABLED_TOPICS, "[]"))
        (0 until topics.length())
            .map { topics.optString(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }.getOrElse { error ->
        android.util.Log.w("YtKeep", "Could not read disabled recommendation topics", error)
        emptySet()
    }

    @Synchronized
    fun setTopicEnabled(ctx: Context, topicId: String, enabled: Boolean) {
        if (topicId.isBlank()) return
        val disabled = disabledTopicIds(ctx).toMutableSet()
        if (enabled) disabled.remove(topicId) else disabled.add(topicId)
        sp(ctx).edit().putString(
            K_DISABLED_TOPICS,
            JSONArray().apply { disabled.sorted().forEach(::put) }.toString()
        ).apply()
    }

    fun customTopics(ctx: Context): List<Feed.Topic> = runCatching {
        val arr = JSONArray(sp(ctx).getString(K_CUSTOM_TOPICS, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = o.optString("label").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val query = o.optString("query").takeIf { it.isNotBlank() } ?: label
            Feed.Topic(id, label, query)
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun addCustomTopic(ctx: Context, raw: String): Feed.Topic? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isBlank()) return null
        val existing = customTopics(ctx)
        val normalizedId = "custom:" + text.lowercase(java.util.Locale.ROOT)
        existing.firstOrNull { it.id == normalizedId || it.label.equals(text, ignoreCase = true) }
            ?.let { return it }
        // avoid collision with built-in topics
        if (Feed.topics.any { it.id == normalizedId || it.label.equals(text, ignoreCase = true) }) return null
        val topic = Feed.Topic(normalizedId, text, text)
        val arr = JSONArray()
        (existing + topic).forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("label", t.label).put("query", t.query))
        }
        sp(ctx).edit().putString(K_CUSTOM_TOPICS, arr.toString()).apply()
        return topic
    }

    @Synchronized
    fun removeCustomTopic(ctx: Context, topicId: String) {
        if (!topicId.startsWith("custom:")) return
        val arr = JSONArray()
        customTopics(ctx).filterNot { it.id == topicId }.forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("label", t.label).put("query", t.query))
        }
        sp(ctx).edit().putString(K_CUSTOM_TOPICS, arr.toString()).apply()
        // also remove from disabled list
        val disabled = disabledTopicIds(ctx).toMutableSet()
        if (disabled.remove(topicId)) {
            sp(ctx).edit().putString(
                K_DISABLED_TOPICS,
                JSONArray().apply { disabled.sorted().forEach(::put) }.toString()
            ).apply()
        }
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
