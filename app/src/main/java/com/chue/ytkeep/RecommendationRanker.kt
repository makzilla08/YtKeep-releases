package com.chue.ytkeep

internal object RecommendationRanker {
    data class RankedHit(val hit: Hit, val score: Int)

    /** ช่วงอายูคลิป: สด (<= 30 วัน) / กลาง (<= 90 วัน) / เก่า (เกิน) */
    const val FRESH_DAYS = 30
    const val STALE_DAYS = 90

    fun rank(
        candidates: List<RankedHit>,
        recentVideoIds: Set<String>,
        limit: Int,
        freshDays: Int = FRESH_DAYS,
        staleDays: Int = STALE_DAYS
    ): List<Hit> {
        if (limit <= 0) return emptyList()

        val ordered = candidates
            .map { it to (Freshness.days(it.hit.age) ?: 0) }
            .sortedWith(
                compareByDescending<Pair<RankedHit, Int>> { it.first.score }
                    .thenBy { it.first.hit.id }
            )
        val tiers = listOf(
            ordered.filter { it.second <= freshDays }.map { it.first },
            ordered.filter { it.second > freshDays && it.second <= staleDays }.map { it.first },
            ordered.filter { it.second > staleDays }.map { it.first }
        )
        val selected = LinkedHashMap<String, Hit>()

        fun append(
            items: List<RankedHit>,
            excludeRecent: Boolean,
            channelLimit: Int?,
            sourceLimit: Int?
        ) {
            val channelCounts = selected.values
                .filter { it.channelId.isNotBlank() }
                .groupingBy { it.channelId }
                .eachCount()
                .toMutableMap()
            val sourceCounts = selected.values
                .groupingBy(::sourceKey)
                .eachCount()
                .toMutableMap()

            for ((hit, _) in items) {
                if (selected.size >= limit) return
                if (hit.id in selected || (excludeRecent && hit.id in recentVideoIds)) continue
                if (channelLimit != null && hit.channelId.isNotBlank() &&
                    (channelCounts[hit.channelId] ?: 0) >= channelLimit
                ) continue
                val source = sourceKey(hit)
                if (sourceLimit != null && (sourceCounts[source] ?: 0) >= sourceLimit) continue

                selected[hit.id] = hit
                if (hit.channelId.isNotBlank()) {
                    channelCounts[hit.channelId] = (channelCounts[hit.channelId] ?: 0) + 1
                }
                sourceCounts[source] = (sourceCounts[source] ?: 0) + 1
            }
        }

        // เติมของใหม่ให้เต็มก่อน แล้วค่อยไล่ลงไปของกลาง/ของเก่า (ของเก่าจะโผล่เมื่อของใหม่ไม่พอจริง ๆ)
        tiers.forEach { tier ->
            append(tier, excludeRecent = true, channelLimit = 2, sourceLimit = 4)
            append(tier, excludeRecent = true, channelLimit = 3, sourceLimit = 12)
            append(tier, excludeRecent = false, channelLimit = 3, sourceLimit = 12)
        }
        append(ordered.map { it.first }, excludeRecent = false, channelLimit = null, sourceLimit = null)
        return selected.values.toList()
    }

    private fun sourceKey(hit: Hit): String =
        hit.sourceTopicId ?: hit.sourceQuery?.lowercase() ?: ""
}
