package com.netapp.marketplacescanner.data.net

import com.netapp.marketplacescanner.data.db.SavedSearch
import java.net.URLEncoder

/** Builds the facebook.com Marketplace URLs the scanner loads. */
object MarketplaceUrl {

    /** The search-results URL for a saved search. */
    fun search(s: SavedSearch): String {
        val base = StringBuilder("https://www.facebook.com/marketplace/")
        if (s.locationId.isNotBlank()) base.append(s.locationId).append("/")
        if (s.category.isNotBlank()) base.append("category/${enc(s.category)}/")
        else base.append("search/")

        val params = buildList {
            if (s.query.isNotBlank()) add("query=${enc(s.query)}")
            s.minPrice?.let { add("minPrice=$it") }
            s.maxPrice?.let { add("maxPrice=$it") }
            add("radius_km=${s.radiusKm}")
            val sort = if (s.sortBy == SavedSearch.SORT_NEAREST) "distance_ascend"
            else "creation_time_descend"
            add("sortBy=$sort")
            add("exact=false")
        }
        return base.append("?").append(params.joinToString("&")).toString()
    }

    /** The user's own saved-items page on Marketplace. */
    const val SAVED = "https://www.facebook.com/marketplace/you/saved"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Desktop UA so Facebook serves the layout the text extractor expects. */
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
}
