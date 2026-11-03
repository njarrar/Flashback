package com.njarrar.backthen.core

import kotlinx.serialization.Serializable
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.pow

// A reel: a short video of picked photos, each stamped with its date, with a move between them.
// This file holds the timing and choices only. ReelRenderer.kt draws the frames and
// ReelExport.kt writes them to an MP4, all on the phone.

/** How each photo shows its date. */
@Serializable
enum class ReelLook { YEAR, FILM, CLEAN }

/** The move from one photo to the next. */
@Serializable
enum class ReelMove { FADE, SLIDE, ZOOM, FLASH }

@Serializable
enum class ReelSpeed(val seconds: Double) { FAST(2.0), NORMAL(2.8), SLOW(3.8) }

@Serializable
enum class ReelSize(val width: Int, val height: Int, val label: String) {
    /** 9:16, for Reels, Stories and TikTok. */
    STORY(1080, 1920, "9:16"),
    /** 4:5, for feed posts. */
    PORTRAIT(1080, 1350, "4:5"),
    SQUARE(1080, 1080, "1:1"),
}

@Serializable
data class ReelOptions(
    val look: ReelLook = ReelLook.YEAR,
    val move: ReelMove = ReelMove.FADE,
    val newestFirst: Boolean = true,
    val speed: ReelSpeed = ReelSpeed.NORMAL,
    val size: ReelSize = ReelSize.STORY,
    /** Fill the frame with the photo, cropping it, instead of showing the whole photo. */
    val fill: Boolean = false,
    /** A last card with the app icon. */
    val endCard: Boolean = true,
)

/** When each card of a reel starts and ends. Cards overlap by the length of the move between them. */
class ReelPlan(count: Int, options: ReelOptions, reduceMotion: Boolean = false) {
    sealed interface Kind {
        data object Intro : Kind
        /** A photo, by its place in the reel's order. */
        data class Photo(val index: Int) : Kind
        data object Outro : Kind
    }

    data class Item(val kind: Kind, val start: Double, val length: Double) {
        val end: Double get() = start + length
    }

    /** What shows at one moment: one card, or two while the move runs from [a] to [b]. */
    data class Moment(val a: Item, val b: Item?, val progress: Double)

    val move: ReelMove = if (reduceMotion && options.move == ReelMove.FLASH) ReelMove.FADE else options.move
    /** Seconds two cards share while one moves to the next. */
    val overlap: Double = when (move) {
        ReelMove.FLASH -> 0.45
        ReelMove.SLIDE -> 0.5
        else -> 0.65
    }
    val items: List<Item>
    val total: Double

    init {
        val kinds = buildList {
            add(Kind.Intro to INTRO)
            repeat(minOf(count, MAX_PHOTOS)) { add(Kind.Photo(it) to options.speed.seconds) }
            if (options.endCard) add(Kind.Outro to OUTRO)
        }
        var start = 0.0
        items = kinds.map { (kind, length) -> Item(kind, start, length).also { start += length - overlap } }
        total = start + overlap
    }

    val frameCount: Int get() = ceil(total * FPS - 1e-6).toInt()

    fun moment(time: Double): Moment {
        val t = time.coerceIn(0.0, total)
        var k = 0
        items.forEachIndexed { n, item -> if (item.start <= t) k = n }
        if (k > 0 && t < items[k - 1].end) {
            return Moment(items[k - 1], items[k], ((t - items[k].start) / overlap).coerceIn(0.0, 1.0))
        }
        return Moment(items[k], null, 0.0)
    }

    companion object {
        const val MAX_PHOTOS = 30
        const val FPS = 30
        const val INTRO = 2.0
        const val OUTRO = 2.0
    }
}

/** The photos a reel shows, in its order: newest or oldest first, at most [ReelPlan.MAX_PHOTOS]. */
fun reelOrder(photos: List<Photo>, newestFirst: Boolean): List<Photo> =
    (if (newestFirst) photos.sortedByDescending { it.time } else photos.sortedBy { it.time }).take(ReelPlan.MAX_PHOTOS)

/** The date the way film cameras printed it: "'15 11 3". Always these digits, since it copies the camera. */
fun filmStamp(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val d = localDate(epochMs, zone)
    return String.format(java.util.Locale.ROOT, "'%02d %d %d", d.year % 100, d.monthValue, d.dayOfMonth)
}

/** Smooth start and end for moves. */
fun reelEase(p: Double): Double = if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2
