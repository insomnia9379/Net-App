package com.netapp.marketplacescanner.data.net

import com.netapp.marketplacescanner.data.model.ScannedListing

/**
 * Extracts listings from a Marketplace search response.
 *
 * Facebook does not publish an API, so the search page ships its data as JSON
 * embedded in <script> tags. The exact shape changes over time, so this parser
 * is intentionally tolerant: it locates each listing by its title field and
 * then pulls the associated id / price / photo / location fields from whichever
 * occurrence sits *nearest* the title. Nearest-match matters because Facebook's
 * field ordering is not stable — the price, for example, is emitted before the
 * title, while the location comes after — so a one-directional scan would grab
 * fields from an adjacent listing.
 *
 * Keeping all the brittle, FB-specific extraction in this one file means the
 * rest of the app is insulated from layout changes — only this file needs
 * updating when the markup shifts.
 */
object MarketplaceParser {

    // The field that anchors every listing object in the payload.
    private val TITLE = Regex("\"marketplace_listing_title\":\"((?:\\\\.|[^\"\\\\])*)\"")
    private val ID = Regex("\"id\":\"(\\d{6,})\"")
    private val STORY_KEY = Regex("\"story_key\":\"(\\d{6,})\"")
    private val FORMATTED_AMOUNT = Regex("\"formatted_amount\":\"((?:\\\\.|[^\"\\\\])*)\"")
    private val AMOUNT = Regex("\"amount\":\"(\\d+)\"")
    private val IMAGE_URI = Regex("\"uri\":\"(https:\\\\?/\\\\?/[^\"]*?(?:jpg|png|webp)[^\"]*)\"")
    private val CITY = Regex("\"city\":\"((?:\\\\.|[^\"\\\\])*)\"")
    private val STATE = Regex("\"state\":\"((?:\\\\.|[^\"\\\\])*)\"")

    private const val WINDOW = 2500

    fun parse(body: String): List<ScannedListing> {
        val results = LinkedHashMap<String, ScannedListing>()

        for (titleMatch in TITLE.findAll(body)) {
            val title = unescape(titleMatch.groupValues[1]).trim()
            if (title.isEmpty()) continue

            // Anchor on the middle of the title so "nearest" is measured fairly
            // against fields on either side of it.
            val center = (titleMatch.range.first + titleMatch.range.last) / 2

            val id = nearest(ID, body, center)
                ?: nearest(STORY_KEY, body, center)
                ?: continue
            if (results.containsKey(id)) continue

            val priceText = nearest(FORMATTED_AMOUNT, body, center)?.let { unescape(it) }.orEmpty()
            val price = nearest(AMOUNT, body, center)?.toIntOrNull() ?: parsePrice(priceText)
            val image = nearest(IMAGE_URI, body, center)?.let { unescape(it) }.orEmpty()
            val city = nearest(CITY, body, center)?.let { unescape(it) }.orEmpty()
            val state = nearest(STATE, body, center)?.let { unescape(it) }.orEmpty()
            val location = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")

            results[id] = ScannedListing(
                id = id,
                title = title,
                price = price,
                priceText = priceText.ifBlank { price?.let { "$$it" } ?: "" },
                location = location,
                imageUrl = image,
                url = "https://www.facebook.com/marketplace/item/$id/",
            )
        }
        return results.values.toList()
    }

    /**
     * Returns the first capture group of the [pattern] occurrence closest to
     * [center], searched within +/- [WINDOW] characters. Distance is measured to
     * the nearest edge of each match so a field just before the title beats one
     * belonging to the next listing further down the payload.
     */
    private fun nearest(pattern: Regex, body: String, center: Int): String? {
        val from = (center - WINDOW).coerceAtLeast(0)
        val to = (center + WINDOW).coerceAtMost(body.length)
        if (from >= to) return null

        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (m in pattern.findAll(body.substring(from, to))) {
            val start = from + m.range.first
            val end = from + m.range.last
            val distance = when {
                end < center -> center - end
                start > center -> start - center
                else -> 0
            }
            if (distance < bestDistance) {
                bestDistance = distance
                best = m.groupValues[1]
            }
        }
        return best
    }

    /** Pulls leading digits out of a formatted price like "$1,250". */
    private fun parsePrice(text: String): Int? {
        val digits = text.filter { it.isDigit() }
        return digits.takeIf { it.isNotEmpty() }?.toIntOrNull()
    }

    /** Minimal JSON/unicode unescaping for values pulled out by regex. */
    private fun unescape(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            i += 4
                        }
                    }
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
