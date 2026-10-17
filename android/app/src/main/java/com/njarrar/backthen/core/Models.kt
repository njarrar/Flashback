package com.njarrar.backthen.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class PhotoSource { DEVICE }

/** One photo from the phone's library. */
@Immutable
@Serializable
data class Photo(
    /** Stable key: "d:<mediaStoreId>". */
    val key: String,
    val source: PhotoSource,
    val uri: String,
    val width: Int,
    val height: Int,
    /** Capture time in epoch ms. */
    val time: Long,
)

@Immutable
@Serializable
data class Album(
    val id: String,
    val name: String,
    val photos: List<Photo>,
    val createdAt: Long,
)

@Immutable
data class YearGroup(val year: Int, val photos: List<Photo>)

/** Photo counts per day: "month-day" -> year -> count. */
typealias DayIndex = Map<String, Map<Int, Int>>
