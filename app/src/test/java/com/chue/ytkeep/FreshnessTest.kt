package com.chue.ytkeep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreshnessTest {
    @Test
    fun readsThaiRelativeAges() {
        assertEquals(0, Freshness.days("11 ชั่วโมงที่แล้ว"))
        assertEquals(3, Freshness.days("3 วันที่แล้ว"))
        assertEquals(14, Freshness.days("2 สัปดาห์ที่ผ่านมา"))
        assertEquals(60, Freshness.days("2 เดือนที่แล้ว"))
        assertEquals(365, Freshness.days("1 ปีที่แล้ว"))
    }

    @Test
    fun readsEnglishAndUntranslatedAges() {
        assertEquals(7, Freshness.days("1 week ago"))
        assertEquals(730, Freshness.days("2 years ago"))
    }

    @Test
    fun returnsNullWhenAgeIsUnknown() {
        assertNull(Freshness.days(""))
        assertNull(Freshness.days(null))
        assertNull(Freshness.days("เมื่อวาน"))
    }
}
