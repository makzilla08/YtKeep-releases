package com.chue.ytkeep

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Lib {
    data class Item(
        val id: String,
        val title: String,
        val uploader: String,
        val file: String,
        val bytes: Long,
        val audio: Boolean,
        val ts: Long
    )

    fun dir(ctx: Context): File = File(ctx.getExternalFilesDir("media"), "").also { it.mkdirs() }

    private fun indexFile(ctx: Context) = File(ctx.filesDir, "index.json")

    @Synchronized
    fun all(ctx: Context): List<Item> {
        val f = indexFile(ctx)
        if (!f.exists()) return emptyList()
        return runCatching {
            val a = JSONArray(f.readText())
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val name = o.optString("file")
                if (name.isBlank() || !File(dir(ctx), name).exists()) return@mapNotNull null
                Item(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    uploader = o.optString("uploader"),
                    file = name,
                    bytes = o.optLong("bytes"),
                    audio = o.optBoolean("audio"),
                    ts = o.optLong("ts")
                )
            }.sortedByDescending { it.ts }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun add(ctx: Context, item: Item) {
        val list = all(ctx).filter { it.id != item.id } + item
        write(ctx, list)
    }

    @Synchronized
    fun remove(ctx: Context, item: Item) {
        val file = File(dir(ctx), item.file)
        if (file.exists() && !file.delete()) error("ลบไฟล์ไม่สำเร็จ: ${item.title}")
        write(ctx, all(ctx).filter { it.id != item.id })
    }

    fun filter(items: List<Item>, query: String, sort: String): List<Item> {
        val term = query.trim().lowercase(java.util.Locale.ROOT)
        val matches = items.filter {
            term.isBlank() ||
                it.title.lowercase(java.util.Locale.ROOT).contains(term) ||
                it.uploader.lowercase(java.util.Locale.ROOT).contains(term)
        }
        return when (sort) {
            "name" -> matches.sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
            "size" -> matches.sortedByDescending { it.bytes }
            "oldest" -> matches.sortedBy { it.ts }
            else -> matches.sortedByDescending { it.ts }
        }
    }

    private fun write(ctx: Context, list: List<Item>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("uploader", it.uploader)
                    .put("file", it.file)
                    .put("bytes", it.bytes)
                    .put("audio", it.audio)
                    .put("ts", it.ts)
            )
        }
        indexFile(ctx).writeText(a.toString())
    }
}
