package com.chue.ytkeep

import java.util.Locale

/**
 * แปลข้อความอายุคลิปที่ YouTube ส่งมา ("2 สัปดาห์ที่ผ่านมา", "1 year ago") เป็นจำนวนวันโดยประมาณ
 * ใช้กรอง/จัดลำดับฟีดแนะนำไม่ให้ดันของเดือนก่อน ปีก่อน ขึ้นก่อนของใหม่
 */
internal object Freshness {
    private val pattern = Regex(
        "(\\d+)\\s*(วินาที|นาที|ชั่วโมง|วัน|สัปดาห์|เดือน|ปี|second|minute|hour|day|week|month|year)",
        RegexOption.IGNORE_CASE
    )

    /** จำนวนวันโดยประมาณ · null = อ่านไม่ได้ (ไม่ระบุอายุ) */
    fun days(age: String?): Int? {
        if (age.isNullOrBlank()) return null
        val match = pattern.find(age.lowercase(Locale.ROOT)) ?: return null
        val amount = match.groupValues[1].arabicDigits().toIntOrNull() ?: return null
        val unit = match.groupValues[2]
        val daysPerUnit = when {
            unit.startsWith("วินาที") || unit.startsWith("second") -> 0
            unit.startsWith("นาที") || unit.startsWith("minute") -> 0
            unit.startsWith("ชั่วโมง") || unit.startsWith("hour") -> 0
            unit.startsWith("วัน") || unit.startsWith("day") -> 1
            unit.startsWith("สัปดาห์") || unit.startsWith("week") -> 7
            unit.startsWith("เดือน") || unit.startsWith("month") -> 30
            else -> 365
        }
        return amount * daysPerUnit
    }

    private fun String.arabicDigits(): String = buildString {
        for (ch in this@arabicDigits) {
            append(if (ch in '๐'..'๙') ('0' + (ch - '๐')) else ch)
        }
    }
}
