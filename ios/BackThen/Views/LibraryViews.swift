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
            Header(title: L.s("tab_favorites"), subtitle: favs.isEmpty ? L.s("favorites_none")
                   : L.s("photos_from_years", ["photos": L.p("photos", favs.count), "years": L.p("years", Set(favs.map { yearOf($0.time) }).count)])) { EmptyView() }
            if favs.isEmpty {
                EmptyNote(icon: "heart", title: L.s("favorites_empty_title"), text: L.s("favorites_empty_body"))
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
            Header(title: L.s("tab_albums"), subtitle: L.p("albums", albums.count)) {
                Pill(label: L.s("new_album"), systemImage: "plus", height: 40) {
                    store.tab = .today
                    store.say(L.s("toast_new_album_hint"))
                }
                .fixedSize()
            }
            if albums.isEmpty {
                EmptyNote(icon: "photo.on.rectangle", title: L.s("albums_empty_title"), text: L.s("albums_empty_body"))
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
                                Text(L.s("album_made", ["photos": L.p("photos", a.photos.count), "date": L.date(a.createdAt, "MMMd")]))
                                    .font(.nunito(14)).foregroundStyle(Palette.muted)
                            }
                            Spacer()
                            Image(systemName: "chevron.forward").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.faint)
                        }
                        .padding(8)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(RowPressStyle())
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
    @State private var shareFor: ShareRequest?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text(album.name).font(.nunito(30, .bold)).tight(30).foregroundStyle(Palette.ink).accessibilityAddTraits(.isHeader)
                Text(L.s("album_made", ["photos": L.p("photos", album.photos.count), "date": L.date(album.createdAt, "MMMd")]))
                    .font(.nunito(15)).foregroundStyle(Palette.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 4).padding(.bottom, 16)
            HStack(spacing: 10) {
                Pill(label: L.s("share_album"), systemImage: "square.and.arrow.up", height: 46, wide: true) {
                    shareFor = ShareRequest(title: album.name, photos: album.photos)
                }
                .disabled(album.photos.isEmpty)
                Pill(label: L.s("save_to_photos"), systemImage: "square.and.arrow.down", kind: .secondary, height: 46, wide: true) {
                    Task { await store.saveToPhotos(name: album.name, photos: album.photos) }
                }
                .disabled(album.photos.isEmpty)
            }
            .padding(.horizontal, 16).padding(.bottom, 16)
            SquareGrid(photos: album.photos)
            Color.clear.frame(height: 120)
        }
        .background(Palette.bg)
        .sheet(item: $shareFor) { ShareSheetView(request: $0) }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button(L.s("rename"), systemImage: "pencil") {
                        newName = album.name
                        renaming = true
                    }
                    Divider()
                    Button(L.s("delete_album"), systemImage: "trash", role: .destructive) { confirmDelete = true }
                } label: {
                    Image(systemName: "ellipsis").font(.system(size: 17, weight: .bold))
                }
                .accessibilityLabel(L.s("more"))
            }
        }
        .alert(L.s("rename_album"), isPresented: $renaming) {
            TextField(L.s("album_name"), text: $newName)
            Button(L.s("cancel"), role: .cancel) {}
            Button(L.s("save")) { store.renameAlbum(id: album.id, name: newName) }
                .disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .confirmationDialog(L.s("delete_album_confirm", ["name": album.name]), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(L.s("delete_album"), role: .destructive) {
                dismiss()
                store.deleteAlbum(album.id)
            }
        } message: {
            Text(L.s("delete_album_note"))
        }
    }
}
