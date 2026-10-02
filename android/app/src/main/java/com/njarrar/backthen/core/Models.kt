package com.njarrar.backthen.core

import kotlinx.serialization.Serializable

@Serializable
enum class PhotoSource { DEVICE, GOOGLE }

/** One photo, from the phone's library or a Google Photos import. */
@Serializable
data class Photo(
    /** Stable key, prefixed by source: "d:<mediaStoreId>" or "g:<googleId>". */
    val key: String,
    val source: PhotoSource,
    val uri: String,
    val width: Int,
    val height: Int,
    /** Capture time in epoch ms. */
    val time: Long,
)

@Serializable
data class Album(
    val id: String,
    val name: String,
    val photos: List<Photo>,
    val createdAt: Long,
)

data class YearGroup(val year: Int, val photos: List<Photo>)

/** Photo counts per day: "month-day" -> year -> count. */
typealias DayIndex = Map<String, Map<Int, Int>>
