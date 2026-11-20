import Photos
import SwiftUI
import UIKit
import WidgetKit

// Home screen widget: a photo from this day in a past year, with its year and how long ago.
// A tap opens Today. It picks another photo from the day every hour.
// It reads the phone's Photos library with the app's own permission.

@main
struct BackThenWidgets: WidgetBundle {
    init() { L.start() }

    var body: some Widget { TodayWidget() }
}

struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetInfo.kind, provider: TodayProvider()) { entry in
            TodayWidgetView(entry: entry)
        }
        .configurationDisplayName(L.s("widget_name"))
        .description(L.s("widget_description"))
        .supportedFamilies([.systemSmall, .systemMedium])
        .contentMarginsDisabled()
    }
}

struct TodayEntry: TimelineEntry {
    enum Shown {
        case photo(UIImage, year: Int, date: String)
        /// No photos from this day, or the day is hidden.
        case empty
        /// No photo access yet, or the phone's photos are turned off in the app.
        case needsApp
        /// The widget gallery's example.
        case sample
    }

    let date: Date
    let day: MonthDay
    let shown: Shown
}

struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry {
        TodayEntry(date: Date(), day: .today, shown: .sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) {
        if context.isPreview { return completion(placeholder(in: context)) }
        // Never block getSnapshot on synchronous iCloud network downloads.
        completion(Self.entry(size: context.displaySize, now: Date(), allowNetwork: false))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        let now = Date()
        let cal = Calendar.app
        let startOfToday = cal.startOfDay(for: now)
        let tomorrow = cal.date(byAdding: .day, value: 1, to: startOfToday)
            .map { cal.startOfDay(for: $0) } ?? now.addingTimeInterval(86_400)
        let day = MonthDay.of(now, calendar: cal)

        guard Self.canShowPhotos(on: day) else {
            let single = Self.entry(size: context.displaySize, now: now, allowNetwork: false)
            return completion(Timeline(entries: [single], policy: .after(tomorrow)))
        }

        let assets = Self.photos(on: day, now: now)
        guard !assets.isEmpty else {
            let empty = TodayEntry(date: now, day: day, shown: .empty)
            return completion(Timeline(entries: [empty], policy: .after(tomorrow)))
        }

        // Batch hourly entries up to midnight so WidgetKit refreshes far less often per day.
        var dates: [Date] = [now]
        if assets.count > 1 {
            var cursor = now
            let maxEntries = 8
            while dates.count < maxEntries,
                  let nextHour = cal.nextDate(after: cursor, matching: DateComponents(minute: 0, second: 0), matchingPolicy: .nextTime),
                  nextHour < tomorrow {
                dates.append(nextHour)
                cursor = nextHour
            }
        }

        var entries: [TodayEntry] = []
        entries.reserveCapacity(dates.count)
        for (idx, slotDate) in dates.enumerated() {
            if let shown = Self.pickPhoto(from: assets, day: day, at: slotDate, size: context.displaySize, allowNetwork: idx == 0) {
                entries.append(TodayEntry(date: slotDate, day: day, shown: shown))
            }
        }
        if entries.isEmpty {
            return completion(Timeline(entries: [TodayEntry(date: now, day: day, shown: .empty)], policy: .after(tomorrow)))
        }
        if entries.count > 1 {
            completion(Timeline(entries: entries, policy: .atEnd))
        } else {
            completion(Timeline(entries: entries, policy: .after(tomorrow)))
        }
    }

    private static func canShowPhotos(on day: MonthDay) -> Bool {
        let info = WidgetInfo.read()
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        // Without the app's settings the widget can't tell which days are hidden, so it shows
        // no photos. Debug builds still do, since a free Apple ID can't share the settings.
        #if DEBUG
        let device = info?.device ?? true
        #else
        let device = info?.device ?? false
        #endif
        guard status == .authorized || status == .limited, device else { return false }
        return info?.hidden.contains(day.key) != true
    }

    /// What to show now. Follows the app: hidden days stay hidden.
    static func entry(size: CGSize, now: Date = Date(), allowNetwork: Bool = true) -> TodayEntry {
        let day = MonthDay.of(now)
        func make(_ shown: TodayEntry.Shown) -> TodayEntry { TodayEntry(date: now, day: day, shown: shown) }
        let info = WidgetInfo.read()
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        #if DEBUG
        let device = info?.device ?? true
        #else
        let device = info?.device ?? false
        #endif
        guard status == .authorized || status == .limited, device else { return make(.needsApp) }
        if info?.hidden.contains(day.key) == true { return make(.empty) }
        let assets = photos(on: day, now: now)
        guard !assets.isEmpty else { return make(.empty) }
        guard let shown = pickPhoto(from: assets, day: day, at: now, size: size, allowNetwork: allowNetwork) else {
            return make(.empty)
        }
        return make(shown)
    }

    private static func photoStartIndex(for date: Date, count: Int) -> Int {
        guard count > 0 else { return 0 }
        let cal = Calendar.app
        let year = cal.component(.year, from: date)
        let ordinal = cal.ordinality(of: .day, in: .year, for: date) ?? 1
        let hour = cal.component(.hour, from: date)
        let slot = ((year * 366) + (ordinal - 1)) * 24 + hour
        return ((slot % count) + count) % count
    }

    private static func pickPhoto(from assets: [PHAsset], day: MonthDay, at date: Date, size: CGSize, allowNetwork: Bool) -> TodayEntry.Shown? {
        guard !assets.isEmpty else { return nil }
        // A new photo each hour, in a set local-time order through the day. Photos already on the
        // phone come first; only if none of the next few are does it try one from iCloud.
        let start = photoStartIndex(for: date, count: assets.count)
        let next = (0..<min(5, assets.count)).map { assets[(start + $0) % assets.count] }
        let passes = allowNetwork ? [false, true] : [false]
        for network in passes {
            for asset in network ? Array(next.prefix(1)) : next {
                guard let time = asset.creationDate, let image = image(asset, size: size, network: network) else { continue }
                let year = yearOf(time)
                return .photo(image, year: year, date: fullDate(year: year, day))
            }
        }
        return nil
    }

    /// Photos taken on this day in past years, oldest first. This year's never count, as on Today.
    private static func photos(on day: MonthDay, now: Date) -> [PHAsset] {
        let options = PHFetchOptions()
        options.includeAssetSourceTypes = [.typeUserLibrary, .typeCloudShared, .typeiTunesSynced]
        let first = PHFetchOptions()
        first.includeAssetSourceTypes = options.includeAssetSourceTypes
        first.predicate = NSPredicate(format: "creationDate != nil")
        first.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        first.fetchLimit = 1
        guard let oldest = PHAsset.fetchAssets(with: .image, options: first).firstObject?.creationDate else { return [] }
        let thisYear = yearOf(now)
        let from = max(yearOf(oldest), 1970)
        guard from < thisYear else { return [] }
        let ranges = (from..<thisYear).compactMap { dayRange(year: $0, day) }
        guard !ranges.isEmpty else { return [] }
        options.predicate = NSCompoundPredicate(orPredicateWithSubpredicates: ranges.map { start, end in
            NSPredicate(format: "creationDate >= %@ AND creationDate < %@", start as NSDate, end as NSDate)
        })
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        let result = PHAsset.fetchAssets(with: .image, options: options)
        var out: [PHAsset] = []
        out.reserveCapacity(result.count)
        result.enumerateObjects { asset, _, _ in out.append(asset) }
        return out
    }

    /// The photo center-cropped and redrawn to exact widget pixels so wide panoramas never exceed
    /// WidgetKit's archival image size limit.
    private static func image(_ asset: PHAsset, size: CGSize, network: Bool) -> UIImage? {
        let o = PHImageRequestOptions()
        o.isSynchronous = true
        o.deliveryMode = .highQualityFormat
        o.resizeMode = .fast
        o.isNetworkAccessAllowed = network
        let w = max(size.width, 160)
        let h = max(size.height, 160)
        let scale = min(2, 800 / max(w, h))
        let target = CGSize(width: floor(w * scale), height: floor(h * scale))
        var out: UIImage?
        PHImageManager.default().requestImage(for: asset, targetSize: target, contentMode: .aspectFill, options: o) { image, _ in
            out = image
        }
        guard let raw = out else { return nil }
        return cropToTarget(raw, target: target)
    }

    private static func cropToTarget(_ image: UIImage, target: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            let imgSize = image.size
            guard imgSize.width > 0, imgSize.height > 0 else { return }
            let fillScale = max(target.width / imgSize.width, target.height / imgSize.height)
            let drawW = imgSize.width * fillScale
            let drawH = imgSize.height * fillScale
            let rect = CGRect(
                x: (target.width - drawW) / 2,
                y: (target.height - drawH) / 2,
                width: drawW,
                height: drawH
            )
            image.draw(in: rect)
        }
    }
}

struct TodayWidgetView: View {
    let entry: TodayEntry

    private let accent = Color(red: 0xE5 / 255, green: 0x53 / 255, blue: 0x3D / 255)

    var body: some View {
        content
            .widgetURL(URL(string: "backthen://today"))
            .environment(\.layoutDirection, L.lang.rtl ? .rightToLeft : .leftToRight)
            .environment(\.locale, L.locale)
    }

    @ViewBuilder private var content: some View {
        switch entry.shown {
        case let .photo(image, year, date):
            caption(year)
                .containerBackground(for: .widget) {
                    Image(uiImage: image).resizable().scaledToFill()
                        .overlay(scrim)
                        .accessibilityLabel(L.s("photo_from", ["date": date]))
                }
        case .sample:
            caption(2021)
                .containerBackground(for: .widget) {
                    LinearGradient(colors: [Color(red: 0.98, green: 0.78, blue: 0.62), accent], startPoint: .topLeading, endPoint: .bottomTrailing)
                        .overlay(scrim)
                }
        case .empty, .needsApp:
            VStack(alignment: .leading, spacing: 4) {
                Image(systemName: "photo.on.rectangle.angled")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(accent)
                    .accessibilityHidden(true)
                Spacer(minLength: 0)
                Text(entry.day.label)
                    .font(.system(size: 20, weight: .black, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(L.s(isNeedsApp ? "widget_open_app" : "widget_empty"))
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .padding(16)
            .containerBackground(for: .widget) { Color(uiColor: .systemBackground) }
        }
    }

    private var isNeedsApp: Bool {
        if case .needsApp = entry.shown { return true }
        return false
    }

    /// The year and how long ago, in white on the bottom of the photo.
    private func caption(_ year: Int) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(L.num(year)).font(.system(size: 28, weight: .black, design: .rounded))
            Text(agoLabel(year, thisYear: yearOf(entry.date))).font(.system(size: 13, weight: .semibold, design: .rounded)).opacity(0.9).lineLimit(1)
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .padding(14)
    }

    /// Darkens the bottom of the photo so the year stays readable on any picture.
    private var scrim: some View {
        LinearGradient(colors: [.clear, .black.opacity(0.2), .black.opacity(0.65)], startPoint: .center, endPoint: .bottom)
    }
}
