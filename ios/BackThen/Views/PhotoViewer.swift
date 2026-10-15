import SwiftUI

/// Full-screen photo viewer: swipe between photos, pinch or double-tap to zoom, swipe down to close,
/// heart, share, add to album.
struct PhotoViewer: View {
    @Environment(AppStore.self) private var store
    let viewer: Viewer
    @State private var index: Int
    @State private var albumFor: [Photo]?
    private let ink = Palette.hex(0xF4F4F2)

    init(viewer: Viewer) {
        self.viewer = viewer
        _index = State(initialValue: viewer.start)
    }

    var body: some View {
        let photos = viewer.photos
        let photo = photos[min(index, photos.count - 1)]
        let fav = store.favoriteKeys.contains(photo.key)
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Button { store.viewer = nil } label: {
                    Image(systemName: "xmark").font(.system(size: 17, weight: .bold)).foregroundStyle(ink)
                        .frame(width: 44, height: 44).background(.white.opacity(0.12), in: Circle())
                }
                .accessibilityLabel("Close")
                VStack(spacing: 1) {
                    Text(photo.time.formatted(.dateTime.month(.wide).day().year())).font(.nunito(15, .semibold)).foregroundStyle(ink)
                    Text("\(agoLabel(yearOf(photo.time)))  ·  \(index + 1) of \(photos.count)").font(.nunito(13)).foregroundStyle(ink.opacity(0.62))
                }
                .frame(maxWidth: .infinity)
                Color.clear.frame(width: 44, height: 44)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)

            TabView(selection: $index) {
                ForEach(Array(photos.enumerated()), id: \.element.key) { i, p in
                    ZoomablePhoto(photo: p, current: i == index) { store.viewer = nil }.tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            if photos.count > 1 && photos.count <= 20 {
                HStack(spacing: 5) {
                    ForEach(photos.indices, id: \.self) { i in
                        Circle().fill(i == index ? ink : ink.opacity(0.3)).frame(width: 6, height: 6)
                    }
                }
                .padding(.top, 14).padding(.bottom, 4)
            }

            HStack {
                action(fav ? "heart.fill" : "heart", fav ? "Remove from favorites" : "Favorite", tint: fav ? Palette.hex(0xFF6A52) : ink) {
                    store.toggleFavorite(photo)
                }
                Spacer()
                action("square.and.arrow.up", "Share", tint: ink) {
                    Task { await Share.photos([photo], title: photo.time.formatted(date: .long, time: .omitted)) }
                }
                Spacer()
                action("plus.square", "Add to album", tint: ink) { albumFor = [photo] }
            }
            .padding(.horizontal, 48).padding(.vertical, 8)
        }
        .background(Palette.hex(0x0D0D0E).ignoresSafeArea())
        .overlay(alignment: .bottom) {
            if let toast = store.toast { ToastView(message: toast).padding(.bottom, 96) }
        }
        .sheet(isPresented: Binding(get: { albumFor != nil }, set: { if !$0 { albumFor = nil } })) {
            AlbumSheet(photos: albumFor ?? [], created: { album in
                store.viewer = nil
                store.openAlbumID = album.id
            })
        }
        .statusBarHidden()
    }

    private func action(_ icon: String, _ label: String, tint: Color, run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Image(systemName: icon).font(.system(size: 26)).foregroundStyle(tint).frame(width: 56, height: 56)
        }
        .accessibilityLabel(label)
    }
}

/// One photo that zooms with a pinch (up to 4x) or a double tap, and pans while zoomed.
/// At normal size, a drag down closes the viewer.
private struct ZoomablePhoto: View {
    let photo: Photo
    let current: Bool
    let close: () -> Void
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var pull: CGFloat = 0

    var body: some View {
        PhotoImage(photo: photo, contentMode: .fit)
            .scaleEffect(scale)
            .offset(x: offset.width, y: offset.height + pull)
            .gesture(
                MagnifyGesture()
                    .onChanged { scale = min(max(lastScale * $0.magnification, 1), 4) }
                    .onEnded { _ in
                        if scale < 1.05 { withAnimation(.spring(duration: 0.25)) { reset() } } else { lastScale = scale }
                    }
            )
            .simultaneousGesture(
                DragGesture(minimumDistance: 20)
                    .onChanged { drag in
                        if scale > 1 {
                            offset = CGSize(width: lastOffset.width + drag.translation.width,
                                            height: lastOffset.height + drag.translation.height)
                        } else if drag.translation.height > abs(drag.translation.width) * 1.3 {
                            pull = drag.translation.height
                        }
                    }
                    .onEnded { _ in
                        if scale > 1 {
                            lastOffset = offset
                        } else if pull > 110 {
                            close()
                        } else {
                            withAnimation(.spring(duration: 0.2)) { pull = 0 }
                        }
                    }
            )
            .onTapGesture(count: 2) {
                withAnimation(.spring(duration: 0.25)) {
                    if scale > 1 { reset() } else { scale = 2.5; lastScale = 2.5 }
                }
            }
            // Swiping away resets the zoom, so the photo looks normal when you come back to it.
            .onChange(of: current) { _, now in if !now { reset() } }
    }

    private func reset() {
        scale = 1
        lastScale = 1
        offset = .zero
        lastOffset = .zero
        pull = 0
    }
}
