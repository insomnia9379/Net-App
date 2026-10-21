package com.netapp.marketplacescanner.data.net

/**
 * Scores a listing's price against the going rate for its search, so the app
 * can flag good deals without a hardcoded price list for every product.
 *
 * The "market value" is the median price of the other matching listings in the
 * same scan (the comparables). A listing well under that median is a STEAL, at
 * or under it is FAIR, above it is HIGH. An explicit per-search price ceiling,
 * when set, always counts as a STEAL. Too few comparables (or no price) → UNKNOWN.
 */
object DealScorer {
    const val STEAL = "STEAL"
    const val FAIR = "FAIR"
    const val HIGH = "HIGH"
    const val UNKNOWN = "UNKNOWN"

    private const val MIN_COMPARABLES = 4
    private const val STEAL_RATIO = 0.70 // <=70% of market value is a steal

    /** Median of the given prices, or null if empty. */
    fun median(prices: List<Int>): Int? {
        if (prices.isEmpty()) return null
        val s = prices.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    fun score(price: Int?, marketValue: Int?, underThreshold: Int?, comparableCount: Int): String {
        if (price == null) return UNKNOWN
        if (underThreshold != null && price <= underThreshold) return STEAL
        if (marketValue == null || marketValue <= 0 || comparableCount < MIN_COMPARABLES) return UNKNOWN
        return when {
            price <= marketValue * STEAL_RATIO -> STEAL
            price <= marketValue -> FAIR
            else -> HIGH
        }
    }

    fun badge(level: String): String = when (level) {
        STEAL -> "🔥 STEAL"
        FAIR -> "✓ FAIR"
        HIGH -> "⚠ HIGH"
        else -> "• ?"
    }

    fun isDeal(level: String): Boolean = level == STEAL || level == FAIR
}
