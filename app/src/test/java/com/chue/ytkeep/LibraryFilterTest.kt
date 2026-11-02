package com.chue.ytkeep

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private val items = listOf(
        item("1", "Zulu", "NASA", 10, 100),
        item("2", "Alpha", "ESA", 30, 300),
        item("3", "Beta", "NASA", 20, 200)
    )

    @Test
    fun filtersByTitleOrUploaderAndSortsByNewest() {
        assertEquals(
            listOf("3", "1"),
            Lib.filter(items, " nasa ", "newest").map { it.id }
        )
    }

    @Test
    fun sortsByNameAndSize() {
        assertEquals(
            listOf("2", "3", "1"),
            Lib.filter(items, "", "name").map { it.id }
        )
        assertEquals(
            listOf("2", "3", "1"),
            Lib.filter(items, "", "size").map { it.id }
        )
    }

    private fun item(id: String, title: String, uploader: String, ts: Long, bytes: Long) =
        Lib.Item(id, title, uploader, "$id.mp4", bytes, false, ts)
}
