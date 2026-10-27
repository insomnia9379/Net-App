package com.netapp.marketplacescanner.data.model

/** A listing as parsed straight out of a Marketplace search response. */
data class ScannedListing(
    val id: String,
    val title: String,
    val price: Int?,
    val priceText: String,
    val location: String,
    val imageUrl: String,
    val url: String,
)
