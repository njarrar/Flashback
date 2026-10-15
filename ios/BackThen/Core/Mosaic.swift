import Foundation

// Lays out a year's photos on a 6-column grid, as in the design: one large lead photo,
// then smaller tiles, packed so rows have no gaps.

let mosaicColumns = 6

struct Tile: Equatable {
    let col: Int, row: Int, cols: Int, rows: Int
}

// Spans (cols, rows) for 1 to 7 photos, taken from the design.
private let patterns: [Int: [(Int, Int)]] = [
    1: [(6, 4)],
    2: [(3, 4), (3, 4)],
    3: [(4, 4), (2, 2), (2, 2)],
    4: [(3, 3), (3, 3), (3, 3), (3, 3)],
    5: [(4, 4), (2, 2), (2, 2), (3, 3), (3, 3)],
    6: [(4, 4), (2, 2), (2, 2), (2, 2), (2, 2), (2, 2)],
    7: [(4, 4), (2, 2), (2, 2), (2, 2), (2, 2), (2, 2), (6, 3)],
]

/// Spans for any count. Past 7, the rest go in rows of three, with the last row stretched to fill.
func spans(for count: Int) -> [(Int, Int)] {
    if count <= 0 { return [] }
    if count <= 7 { return patterns[count]! }
    var out = patterns[6]!
    let rest = count - 6
    let full = rest / 3 * 3
    out += Array(repeating: (2, 2), count: full)
    switch rest - full {
    case 1: out.append((6, 3))
    case 2: out += [(3, 3), (3, 3)]
    default: break
    }
    return out
}

/// Places tiles in reading order, each at the first free spot that fits.
/// Each row's taken cells are bits in one byte, so checking a spot needs no new arrays.
func layoutMosaic(_ count: Int) -> (tiles: [Tile], rows: Int) {
    let tileSpans = spans(for: count)
    var used: [UInt8] = []
    var tiles: [Tile] = []
    tiles.reserveCapacity(tileSpans.count)
    var rows = 0
    for (cols, rs) in tileSpans {
        var r = 0
        placing: while true {
            while used.count < r + rs { used.append(0) }
            for c in 0...(mosaicColumns - cols) {
                let mask = UInt8(((1 << cols) - 1) << c)
                if (0..<rs).contains(where: { used[r + $0] & mask != 0 }) { continue }
                for dr in 0..<rs { used[r + dr] |= mask }
                tiles.append(Tile(col: c, row: r, cols: cols, rows: rs))
                rows = max(rows, r + rs)
                break placing
            }
            r += 1
        }
    }
    return (tiles, rows)
}

/// Width over height for a photo tile, kept in a sane range so odd sizes don't make huge tiles.
func tileAspect(width: Int, height: Int) -> CGFloat {
    guard width > 0, height > 0 else { return 1 }
    return min(max(CGFloat(width) / CGFloat(height), 0.6), 1.8)
}

/// Splits photos into columns of about equal height, as in the design's strip layout.
/// Each photo, in order, goes to the shortest column so far. Returns indexes per column.
func masonryColumns(_ aspects: [CGFloat], columns: Int = 2) -> [[Int]] {
    var cols = Array(repeating: [Int](), count: columns)
    var heights = Array(repeating: CGFloat(0), count: columns)
    for (i, a) in aspects.enumerated() {
        let c = heights.indices.min { heights[$0] < heights[$1] }!
        cols[c].append(i)
        heights[c] += 1 / a
    }
    return cols
}
