import SwiftUI

private struct Header<Trailing: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.nunito(34, .bold)).tight(34).foregroundStyle(Palette.ink).accessibilityAddTraits(.isHeader)
                Text(subtitle).font(.nunito(15)).foregroundStyle(Palette.muted)
            }
            Spacer()
            trailing()
        }
        .padding(.horizontal, 20).padding(.top, 52).padding(.bottom, 16)
    }
}

private struct EmptyNote: View {
    let icon: String
    let title: String
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: icon).font(.system(size: 26)).foregroundStyle(Palette.accent)
                .frame(width: 56, height: 56).background(Palette.accTint, in: RoundedRectangle(cornerRadius: 16))
            Text(title).font(.nunito(22, .bold)).tight(22).foregroundStyle(Palette.ink)
            Text(text).font(.nunito(15)).foregroundStyle(Palette.muted).frame(maxWidth: 300, alignment: .leading)
        }
        .padding(.horizontal, 32).padding(.top, 40)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct FavoritesView: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        let favs = store.saved.favorites
        ScrollView {
            Header(title: "Favorites", subtitle: favs.isEmpty ? "Nothing saved yet" : plural(favs.count, "photo")) { EmptyView() }
            if favs.isEmpty {
                EmptyNote(icon: "heart", title: "No favorites yet", text: "Open any photo and tap the heart. It will be kept here, whatever day it's from.")
            } else {
                SquareGrid(photos: favs, showYear: true)
            }
            Color.clear.frame(height: 120)
        }
        .background(Palette.bg)
    }
}

struct AlbumsView: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        if let id = store.openAlbumID, let album = store.saved.albums.first(where: { $0.id == id }) {
            AlbumDetailView(album: album)
        } else {
            list
        }
    }

    private var list: some View {
        let albums = store.saved.albums
        return ScrollView {
            Header(title: "Albums", subtitle: plural(albums.count, "album")) {
                Pill(label: "New", systemImage: "plus", height: 40) {
                    store.tab = .today
                    store.say("Tap Select, pick photos, then tap Album")
                }
                .fixedSize()
            }
            if albums.isEmpty {
                EmptyNote(icon: "photo.on.rectangle", title: "No albums yet", text: "On Today, tap Select, pick a few photos, then tap Album.")
            }
            VStack(spacing: 4) {
                ForEach(albums) { a in
                    Button { store.openAlbumID = a.id } label: {
                        HStack(spacing: 14) {
                            LazyVGrid(columns: [GridItem(.fixed(35), spacing: 2), GridItem(.fixed(35), spacing: 2)], spacing: 2) {
                                ForEach(a.photos.prefix(4)) { p in PhotoImage(photo: p).frame(width: 35, height: 35) }
                            }
                            .frame(width: 72, height: 72, alignment: .topLeading)
                            .background(Palette.fill)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            VStack(alignment: .leading, spacing: 3) {
                                Text(a.name).font(.nunito(17, .semibold)).foregroundStyle(Palette.ink).lineLimit(1)
                                Text("\(plural(a.photos.count, "photo")), made \(a.createdAt.formatted(.dateTime.month(.abbreviated).day()))")
                                    .font(.nunito(14)).foregroundStyle(Palette.muted)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.faint)
                        }
                        .padding(8)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            Color.clear.frame(height: 120)
        }
        .background(Palette.bg)
    }
}

struct AlbumDetailView: View {
    @Environment(AppStore.self) private var store
    let album: Album
    @State private var confirmDelete = false
    @State private var shareFor: ShareRequest?

    var body: some View {
        ScrollView {
            HStack(spacing: 8) {
                GlassButton(accessibility: "Albums", action: { store.openAlbumID = nil }) {
                    Label("Albums", systemImage: "chevron.left").font(.nunito(15, .semibold)).padding(.horizontal, 12)
                }
                Spacer()
                GlassButton(accessibility: "Save to Photos", action: { Task { await store.saveToPhotos(name: album.name, photos: album.photos) } }) {
                    Image(systemName: "square.and.arrow.down").font(.system(size: 16))
                }
                GlassButton(accessibility: "Delete album", action: { confirmDelete = true }) {
                    Image(systemName: "trash").font(.system(size: 16))
                }
                Pill(label: "Share album", systemImage: "square.and.arrow.up", kind: .accent, height: 40) {
                    shareFor = ShareRequest(title: album.name, photos: album.photos)
                }
                .fixedSize()
            }
            .padding(.horizontal, 16).padding(.top, 8)
            VStack(alignment: .leading, spacing: 4) {
                Text(album.name).font(.nunito(30, .bold)).tight(30).foregroundStyle(Palette.ink).accessibilityAddTraits(.isHeader)
                Text("\(plural(album.photos.count, "photo")), made \(album.createdAt.formatted(.dateTime.month(.abbreviated).day()))")
                    .font(.nunito(15)).foregroundStyle(Palette.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 16)
            SquareGrid(photos: album.photos)
            Color.clear.frame(height: 120)
        }
        .background(Palette.bg)
        .sheet(item: $shareFor) { ShareSheetView(request: $0) }
        .confirmationDialog("Delete \"\(album.name)\"?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                store.openAlbumID = nil
                store.deleteAlbum(album.id)
            }
        } message: {
            Text("The photos stay in your library.")
        }
    }
}
