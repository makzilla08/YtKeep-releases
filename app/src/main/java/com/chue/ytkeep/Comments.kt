package com.chue.ytkeep

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

internal data class VideoComment(
    val id: String,
    val author: String,
    val text: String,
    val publishedLabel: String,
    val likeCount: Long?
)

@JvmInline
internal value class CommentCursor(val token: String)

internal data class CommentPage(
    val comments: List<VideoComment>,
    val next: CommentCursor?
)

internal sealed interface CommentsResult {
    data class Page(val value: CommentPage) : CommentsResult
    data object Disabled : CommentsResult
    data object Unavailable : CommentsResult
    data object Failure : CommentsResult
}

internal fun interface CommentsRepository {
    suspend fun load(videoId: String, cursor: CommentCursor?): CommentsResult
}

internal object InnerTubeCommentsRepository : CommentsRepository {
    override suspend fun load(videoId: String, cursor: CommentCursor?): CommentsResult {
        if (MediaIdentity.youtubeVideoId("yt:$videoId") == null) return CommentsResult.Unavailable
        return try {
            val body = JSONObject().put("context", InnerTubeClient.context())
            if (cursor == null) body.put("videoId", videoId) else body.put("continuation", cursor.token)
            val response = InnerTubeClient.postCancellable("next", body)
            withContext(Dispatchers.Default) { CommentParser.parse(response) }
        } catch (_: IOException) {
            CommentsResult.Failure
        } catch (_: IllegalStateException) {
            CommentsResult.Failure
        } catch (_: JSONException) {
            CommentsResult.Unavailable
        }
    }
}

internal object CommentParser {
    private const val COMMENTS_TARGET = "engagement-panel-comments-section"
    private val disabledMessages = listOf(
        "comments are turned off",
        "comments are disabled",
        "ความคิดเห็นถูกปิด",
        "ปิดความคิดเห็นแล้ว"
    )

    fun parse(root: JSONObject): CommentsResult {
        val panel = findCommentsPanel(root)
        val content = panel?.optJSONObject("content")
        val source = content ?: root.takeIf(::containsCommentContent)
            ?: return if (hasDisabledSignal(root)) CommentsResult.Disabled else CommentsResult.Unavailable

        if (hasDisabledSignal(source)) return CommentsResult.Disabled

        val comments = LinkedHashMap<String, VideoComment>()
        var next: CommentCursor? = null
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    value.optJSONObject("commentThreadRenderer")?.let { thread ->
                        parseThread(thread)?.let { comments.putIfAbsent(it.id, it) }
                    }
                    if (next == null) next = continuation(value)
                    val keys = value.keys()
                    while (keys.hasNext()) walk(value.opt(keys.next()))
                }
                is JSONArray -> for (index in 0 until value.length()) walk(value.opt(index))
            }
        }
        walk(source)

        return CommentsResult.Page(CommentPage(comments.values.toList(), next))
    }

    private fun findCommentsPanel(root: JSONObject): JSONObject? {
        fun find(value: Any?): JSONObject? = when (value) {
            is JSONObject -> {
                val panel = value.optJSONObject("engagementPanelSectionListRenderer")
                if (panel?.optString("targetId") == COMMENTS_TARGET) panel
                else {
                    val keys = value.keys()
                    var found: JSONObject? = null
                    while (keys.hasNext() && found == null) found = find(value.opt(keys.next()))
                    found
                }
            }
            is JSONArray -> {
                var found: JSONObject? = null
                for (index in 0 until value.length()) {
                    if (found != null) break
                    found = find(value.opt(index))
                }
                found
            }
            else -> null
        }
        return find(root)
    }

    private fun containsCommentContent(root: JSONObject): Boolean {
        fun contains(value: Any?): Boolean = when (value) {
            is JSONObject -> value.has("commentThreadRenderer") ||
                value.has("commentSectionRenderer") ||
                value.keys().asSequence().any { contains(value.opt(it)) }
            is JSONArray -> (0 until value.length()).any { contains(value.opt(it)) }
            else -> false
        }
        return contains(root)
    }

    private fun parseThread(thread: JSONObject): VideoComment? {
        val comment = thread.optJSONObject("comment")
            ?.optJSONObject("commentRenderer")
            ?: thread.optJSONObject("commentRenderer")
            ?: return null
        val id = comment.optString("commentId")
        val commentText = text(comment.optJSONObject("contentText"))
        if (id.isBlank() || commentText.isBlank()) return null
        return VideoComment(
            id = id,
            author = text(comment.optJSONObject("authorText")),
            text = commentText,
            publishedLabel = text(comment.optJSONObject("publishedTimeText")),
            likeCount = parseCount(text(comment.optJSONObject("voteCount")))
        )
    }

    private fun continuation(value: JSONObject): CommentCursor? {
        value.optJSONObject("continuationCommand")?.optString("token")
            ?.takeIf(String::isNotBlank)?.let { return CommentCursor(it) }
        value.optJSONObject("nextContinuationData")?.optString("continuation")
            ?.takeIf(String::isNotBlank)?.let { return CommentCursor(it) }
        return null
    }

    private fun hasDisabledSignal(root: JSONObject): Boolean {
        fun hasSignal(value: Any?): Boolean {
            return when (value) {
                is JSONObject -> {
                    if (value.optBoolean("commentsDisabled", false)) {
                        true
                    } else {
                        val message = value.optJSONObject("message")?.let(::text)
                            ?: value.optJSONObject("text")?.let(::text)
                            ?: ""
                        disabledMessages.any { it in message.lowercase() } ||
                            value.keys().asSequence().any { hasSignal(value.opt(it)) }
                    }
                }
                is JSONArray -> (0 until value.length()).any { hasSignal(value.opt(it)) }
                else -> false
            }
        }
        return hasSignal(root)
    }

    private fun text(value: JSONObject?): String {
        if (value == null) return ""
        value.optString("simpleText").takeIf(String::isNotBlank)?.let { return it }
        val runs = value.optJSONArray("runs") ?: return ""
        return buildString {
            for (index in 0 until runs.length()) {
                append(runs.optJSONObject(index)?.optString("text").orEmpty())
            }
        }
    }

    private fun parseCount(value: String): Long? {
        val normalized = value.trim().replace(",", "").lowercase()
        val suffix = normalized.lastOrNull()?.takeIf { it == 'k' || it == 'm' }
        val number = normalized.removeSuffix(suffix?.toString().orEmpty()).toDoubleOrNull() ?: return null
        val multiplier = when (suffix) {
            'k' -> 1_000.0
            'm' -> 1_000_000.0
            else -> 1.0
        }
        return (number * multiplier).toLong().takeIf { it >= 0L }
    }
}
