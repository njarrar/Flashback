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
func layoutMosaic(_ count: Int) -> (tiles: [Tile], rows: Int) {
    var used = Set<[Int]>()
    var tiles: [Tile] = []
    var rows = 0
    for (cols, rs) in spans(for: count) {
        var r = 0
        placing: while true {
            for c in 0...(mosaicColumns - cols) {
                let cells = (0..<rs).flatMap { dr in (0..<cols).map { dc in [r + dr, c + dc] } }
                if cells.contains(where: used.contains) { continue }
                cells.forEach { used.insert($0) }
                tiles.append(Tile(col: c, row: r, cols: cols, rows: rs))
                rows = max(rows, r + rs)
                break placing
            }
            r += 1
        }
    }
    return (tiles, rows)
}
