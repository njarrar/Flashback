import SwiftUI

/// Full-screen photo viewer: swipe between photos, heart, share, add to album.
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
                    PhotoImage(photo: p, contentMode: .fit).tag(i)
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
