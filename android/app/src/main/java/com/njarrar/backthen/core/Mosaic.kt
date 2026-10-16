package com.njarrar.backthen.core

import androidx.compose.runtime.Immutable

// Lays out a year's photos on a 6-column grid, as in the design: one large lead photo,
// then smaller tiles, packed so rows have no gaps.

const val COLUMNS = 6

@Immutable
data class Tile(val col: Int, val row: Int, val cols: Int, val rows: Int)

// Spans (cols to rows) for 1 to 7 photos, taken from the design.
private val PATTERNS = mapOf(
    1 to listOf(6 to 4),
    2 to listOf(3 to 4, 3 to 4),
    3 to listOf(4 to 4, 2 to 2, 2 to 2),
    4 to listOf(3 to 3, 3 to 3, 3 to 3, 3 to 3),
    5 to listOf(4 to 4, 2 to 2, 2 to 2, 3 to 3, 3 to 3),
    6 to listOf(4 to 4, 2 to 2, 2 to 2, 2 to 2, 2 to 2, 2 to 2),
    7 to listOf(4 to 4, 2 to 2, 2 to 2, 2 to 2, 2 to 2, 2 to 2, 6 to 3),
)

/** Spans for any count. Past 7, the rest go in rows of three, with the last row stretched to fill. */
fun spansFor(count: Int): List<Pair<Int, Int>> {
    if (count <= 0) return emptyList()
    if (count <= 7) return PATTERNS.getValue(count)
    val spans = PATTERNS.getValue(6).toMutableList()
    val rest = count - 6
    val full = rest / 3 * 3
    repeat(full) { spans += 2 to 2 }
    when (rest - full) {
        1 -> spans += 6 to 3
        2 -> spans += listOf(3 to 3, 3 to 3)
    }
    return spans
}

/** Places tiles in reading order, each at the first free spot that fits. Returns tiles and row count. */
fun layoutMosaic(count: Int): Pair<List<Tile>, Int> {
    val spans = spansFor(count)
    if (spans.isEmpty()) return emptyList<Tile>() to 0
    var rowMasks = IntArray(maxOf(16, count * 2))
    val tiles = ArrayList<Tile>(spans.size)
    var rows = 0
    for ((cols, rs) in spans) {
        val baseMask = (1 shl cols) - 1
        var r = 0
        placing@ while (true) {
            if (r + rs > rowMasks.size) {
                rowMasks = rowMasks.copyOf(maxOf(rowMasks.size * 2, r + rs))
            }
            for (c in 0..COLUMNS - cols) {
                val mask = baseMask shl c
                var free = true
                for (dr in 0 until rs) {
                    if ((rowMasks[r + dr] and mask) != 0) {
                        free = false
                        break
                    }
                }
                if (!free) continue
                for (dr in 0 until rs) rowMasks[r + dr] = rowMasks[r + dr] or mask
                tiles += Tile(c, r, cols, rs)
                rows = maxOf(rows, r + rs)
                break@placing
            }
            r++
        }
    }
    return tiles to rows
}

/** Width over height for a photo tile, kept in a sane range so odd sizes don't make huge tiles. */
fun tileAspect(width: Int, height: Int): Float =
    if (width <= 0 || height <= 0) 1f else (width.toFloat() / height).coerceIn(0.6f, 1.8f)

/**
 * Splits photos into columns of about equal height, as in the design's strip layout.
 * Each photo, in order, goes to the shortest column so far. Returns indexes per column.
 */
fun masonryColumns(aspects: List<Float>, columns: Int = 2): List<List<Int>> {
    val cols = List(columns) { ArrayList<Int>() }
    val heights = FloatArray(columns)
    aspects.forEachIndexed { i, a ->
        val c = heights.indices.minBy { heights[it] }
        cols[c] += i
        heights[c] += 1f / a
    }
    return cols
}
