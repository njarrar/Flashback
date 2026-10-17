import Photos
import SwiftUI
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
        completion(Self.entry(size: context.displaySize))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        let now = Date()
        let entry = Self.entry(size: context.displaySize, now: now)
        // Back at the top of the next hour: a new photo, and a new day after midnight.
        let next = Calendar.app.nextDate(after: now, matching: DateComponents(minute: 0), matchingPolicy: .nextTime)
            ?? now.addingTimeInterval(3600)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }

    /// What to show now. Follows the app: hidden days stay hidden.
    static func entry(size: CGSize, now: Date = Date()) -> TodayEntry {
        let day = MonthDay.of(now)
        func make(_ shown: TodayEntry.Shown) -> TodayEntry { TodayEntry(date: now, day: day, shown: shown) }
        let info = WidgetInfo.read()
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        // Without the app's settings the widget can't tell which days are hidden, so it shows
        // no photos. Debug builds still do, since a free Apple ID can't share the settings.
        #if DEBUG
        let device = info?.device ?? true
        #else
        let device = info?.device ?? false
        #endif
        guard status == .authorized || status == .limited, device else { return make(.needsApp) }
        if info?.hidden.contains(day.key) == true { return make(.empty) }
        let assets = photos(on: day, now: now)
        guard !assets.isEmpty else { return make(.empty) }
        // A new photo each hour, in a set order through the day. Skip any that won't load (such as one only in iCloud).
        let hour = Calendar.app.component(.hour, from: now)
        let start = (Int(now.timeIntervalSince1970 / 86400) * 24 + hour) % assets.count
        for k in 0..<min(3, assets.count) {
            let asset = assets[(start + k) % assets.count]
            guard let time = asset.creationDate, let image = image(asset, size: size) else { continue }
            let year = yearOf(time)
            return make(.photo(image, year: year, date: fullDate(year: year, day)))
        }
        return make(.empty)
    }

    /// Photos taken on this day in past years, oldest first. This year's never count, as on Today.
    private static func photos(on day: MonthDay, now: Date) -> [PHAsset] {
        let options = PHFetchOptions()
        options.includeAssetSourceTypes = [.typeUserLibrary, .typeCloudShared, .typeiTunesSynced]
        let first = PHFetchOptions()
        first.includeAssetSourceTypes = options.includeAssetSourceTypes
        first.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        first.fetchLimit = 1
        guard let oldest = PHAsset.fetchAssets(with: .image, options: first).firstObject?.creationDate else { return [] }
        let thisYear = yearOf(now)
        let from = yearOf(oldest)
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

    /// The photo filled to the widget's size, at twice its points. Widgets can't hold large pictures.
    private static func image(_ asset: PHAsset, size: CGSize) -> UIImage? {
        let o = PHImageRequestOptions()
        o.isSynchronous = true
        o.deliveryMode = .highQualityFormat
        o.resizeMode = .fast
        o.isNetworkAccessAllowed = true
        let side = max(size.width, size.height, 160)
        let scale = min(2, 800 / side)
        let target = CGSize(width: max(size.width, 160) * scale, height: max(size.height, 160) * scale)
        var out: UIImage?
        PHImageManager.default().requestImage(for: asset, targetSize: target, contentMode: .aspectFill, options: o) { image, _ in
            out = image
        }
        return out
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
            Text(agoLabel(year)).font(.system(size: 13, weight: .semibold, design: .rounded)).opacity(0.9).lineLimit(1)
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
