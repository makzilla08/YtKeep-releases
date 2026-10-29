package com.chue.ytkeep

internal object RecommendationRanker {
    data class RankedHit(val hit: Hit, val score: Int)

    fun rank(
        candidates: List<RankedHit>,
        recentVideoIds: Set<String>,
        limit: Int
    ): List<Hit> {
        if (limit <= 0) return emptyList()

        val ordered = candidates.sortedWith(
            compareByDescending<RankedHit> { it.score }
                .thenBy { it.hit.id }
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

        append(ordered, excludeRecent = true, channelLimit = 2, sourceLimit = 4)
        append(ordered, excludeRecent = true, channelLimit = 3, sourceLimit = 12)
        append(ordered, excludeRecent = false, channelLimit = 3, sourceLimit = 12)
        append(ordered, excludeRecent = false, channelLimit = null, sourceLimit = null)
        return selected.values.toList()
    }

    private fun sourceKey(hit: Hit): String =
        hit.sourceTopicId ?: hit.sourceQuery?.lowercase() ?: ""
}
