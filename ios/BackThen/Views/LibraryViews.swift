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
                .frame(width: 56, height: 56).background(Palette.accTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
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
            Header(title: "Favorites", subtitle: favs.isEmpty ? "Nothing saved yet"
                   : "\(plural(favs.count, "photo")) from \(plural(Set(favs.map { yearOf($0.time) }).count, "year"))") { EmptyView() }
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
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            list
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: String.self) { id in
                    if let album = store.saved.albums.first(where: { $0.id == id }) {
                        AlbumDetailView(album: album)
                    }
                }
        }
        // Back button and menu stay ink; coral is kept for the active tab.
        .tint(Palette.ink)
        // Other screens ask for an album by setting openAlbumID; turn that into a push.
        .task(id: store.openAlbumID) {
            guard let id = store.openAlbumID else { return }
            path = [id]
            store.openAlbumID = nil
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
                    Button { path.append(a.id) } label: {
                        HStack(spacing: 14) {
                            LazyVGrid(columns: [GridItem(.fixed(35), spacing: 2), GridItem(.fixed(35), spacing: 2)], spacing: 2) {
                                ForEach(a.photos.prefix(4)) { p in PhotoImage(photo: p).frame(width: 35, height: 35) }
                            }
                            .frame(width: 72, height: 72, alignment: .topLeading)
                            .background(Palette.fill)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
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
    @Environment(\.dismiss) private var dismiss
    let album: Album
    @State private var confirmDelete = false
    @State private var renaming = false
    @State private var newName = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text(album.name).font(.nunito(30, .bold)).tight(30).foregroundStyle(Palette.ink).accessibilityAddTraits(.isHeader)
                Text("\(plural(album.photos.count, "photo")) · made \(album.createdAt.formatted(.dateTime.month(.abbreviated).day()))")
                    .font(.nunito(15)).foregroundStyle(Palette.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 4).padding(.bottom, 16)
            HStack(spacing: 10) {
                Pill(label: "Share album", systemImage: "square.and.arrow.up", height: 46, wide: true) {
                    Task { await Share.photos(album.photos, title: album.name) }
                }
                Pill(label: "Save to Photos", systemImage: "square.and.arrow.down", kind: .secondary, height: 46, wide: true) {
                    Task { await store.saveToPhotos(album) }
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 16)
            SquareGrid(photos: album.photos)
            Color.clear.frame(height: 120)
        }
        .background(Palette.bg)
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("Rename", systemImage: "pencil") {
                        newName = album.name
                        renaming = true
                    }
                    Divider()
                    Button("Delete album", systemImage: "trash", role: .destructive) { confirmDelete = true }
                } label: {
                    Image(systemName: "ellipsis").font(.system(size: 17, weight: .bold))
                }
                .accessibilityLabel("More")
            }
        }
        .alert("Rename album", isPresented: $renaming) {
            TextField("Album name", text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Save") { store.renameAlbum(id: album.id, name: newName) }
                .disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .confirmationDialog("Delete \"\(album.name)\"?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete album", role: .destructive) {
                dismiss()
                store.deleteAlbum(album.id)
            }
        } message: {
            Text("The photos stay in your library.")
        }
    }
}
