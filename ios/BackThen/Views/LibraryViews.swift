import Photos
import PhotosUI
import SwiftUI

private struct Header<Trailing: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(alignment: .bottom, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.nunito(34, .bold)).tight(34).foregroundStyle(Palette.ink).accessibilityAddTraits(.isHeader)
                Text(subtitle).font(.nunito(15)).foregroundStyle(Palette.muted)
            }
            Spacer(minLength: 0)
            trailing()
        }
        .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 16)
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
    @State private var selecting = false
    @State private var picked: [Photo] = []
    @State private var newestFirst = true
    @State private var shareFor: ShareRequest?
    @State private var albumFor: [Photo]?

    var body: some View {
        let favs = store.saved.favorites.sorted { newestFirst ? $0.time > $1.time : $0.time < $1.time }
        let pickedKeys = Set(picked.map(\.key))
        let allSelected = !favs.isEmpty && favs.allSatisfy { pickedKeys.contains($0.key) }
        let selection = Selection(
            on: selecting,
            keys: pickedKeys,
            start: { p in
                selecting = true
                picked = [p]
            },
            batch: { photos, select in
                if select {
                    let existing = Set(picked.map(\.key))
                    picked += photos.filter { !existing.contains($0.key) }
                } else {
                    let drop = Set(photos.map(\.key))
                    picked = picked.filter { !drop.contains($0.key) }
                }
            }
        ) { p in
            if let i = picked.firstIndex(where: { $0.key == p.key }) { picked.remove(at: i) } else { picked.append(p) }
        }

        ScrollView {
            Header(
                title: L.s("tab_favorites"),
                subtitle: favs.isEmpty ? L.s("favorites_none")
                    : L.s("photos_from_years", ["photos": L.p("photos", favs.count), "years": L.p("years", Set(favs.map { yearOf($0.time) }).count)])
            ) {
                if !favs.isEmpty {
                    HStack(spacing: 0) {
                        Button {
                            if selecting { stopSelecting() } else { selecting = true }
                        } label: {
                            Text(L.s(selecting ? "cancel" : "select"))
                                .font(.nunito(15, .semibold))
                                .padding(.horizontal, 14).frame(minHeight: 40).contentShape(Rectangle())
                        }
                        Palette.line.frame(width: 1, height: 20)
                        Menu {
                            if favs.count >= 2 {
                                Button { startReel(favs) } label: { Label(L.s("make_reel"), systemImage: "play.rectangle") }
                            }
                            Button { shareFor = ShareRequest(title: L.s("tab_favorites"), photos: favs) } label: {
                                Label(L.s("share"), systemImage: "square.and.arrow.up")
                            }
                            Picker(L.s("reel_order"), selection: $newestFirst) {
                                Text(L.s("reel_newest_first")).tag(true)
                                Text(L.s("reel_oldest_first")).tag(false)
                            }
                            .pickerStyle(.inline)
                        } label: {
                            Image(systemName: "ellipsis").font(.system(size: 16, weight: .bold))
                                .frame(width: 44, height: 40).contentShape(Rectangle())
                        }
                        .accessibilityLabel(L.s("options"))
                    }
                    .foregroundStyle(Palette.ink)
                    .background(Palette.glass, in: Capsule())
                    .overlay(Capsule().stroke(Palette.line, lineWidth: 1))
                }
            }
            if favs.isEmpty {
                EmptyNote(icon: "heart", title: L.s("favorites_empty_title"), text: L.s("favorites_empty_body"))
            } else {
                SquareGrid(photos: favs, showDate: true, selection: selection)
            }
            Color.clear.frame(height: 120)
        }
        .background(Palette.bg)
        .overlay(alignment: .bottom) {
            if selecting {
                SelectBar(
                    count: picked.count,
                    allSelected: allSelected,
                    onToggleAll: { picked = allSelected ? [] : favs },
                    share: { shareFor = ShareRequest(title: L.p("photos", picked.count), photos: picked) },
                    album: { albumFor = picked },
                    reel: { startReel(picked) }
                )
                .padding(.bottom, 4)
            }
        }
        .sheet(item: $shareFor) { ShareSheetView(request: $0) }
        .sheet(isPresented: Binding(get: { albumFor != nil }, set: { if !$0 { albumFor = nil } })) {
            AlbumSheet(photos: albumFor ?? [], created: { album in store.openAlbumID = album.id }, done: stopSelecting)
        }
        .toolbar(selecting ? .hidden : .visible, for: .tabBar)
        .sensoryFeedback(.selection, trigger: picked.count) { _, _ in selecting }
        .onChange(of: store.tab) { _, _ in stopSelecting() }
        .onChange(of: favs.count) { _, n in if n == 0 && selecting { stopSelecting() } }
    }

    private func startReel(_ photos: [Photo]) {
        guard photos.count >= 2 else { return store.showToast(L.s("reel_need_two"), kind: .info, undoLabel: nil, undo: nil) }
        if photos.count > ReelPlan.maxPhotos { store.showToast(L.p("reel_too_many", ReelPlan.maxPhotos), kind: .info, undoLabel: nil, undo: nil) }
        store.reel = ReelRequest(photos: photos)
        stopSelecting()
    }

    private func stopSelecting() {
        selecting = false
        picked = []
    }
}

/// Adaptive 1 / 2 / 3 / 4-photo cover collage with zero empty gray quadrants.
private struct AlbumCover: View {
    let photos: [Photo]

    var body: some View {
        let shown = Array(photos.prefix(4))
        Group {
            switch shown.count {
            case 0:
                ZStack {
                    Palette.fill
                    Image(systemName: "photo.on.rectangle")
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(Palette.muted)
                }
            case 1:
                PhotoImage(photo: shown[0])
            case 2:
                HStack(spacing: 2) {
                    PhotoImage(photo: shown[0]).frame(width: 35, height: 72)
                    PhotoImage(photo: shown[1]).frame(width: 35, height: 72)
                }
            case 3:
                HStack(spacing: 2) {
                    PhotoImage(photo: shown[0]).frame(width: 35, height: 72)
                    VStack(spacing: 2) {
                        PhotoImage(photo: shown[1]).frame(width: 35, height: 35)
                        PhotoImage(photo: shown[2]).frame(width: 35, height: 35)
                    }
                }
            default:
                LazyVGrid(columns: [GridItem(.fixed(35), spacing: 2), GridItem(.fixed(35), spacing: 2)], spacing: 2) {
                    ForEach(shown) { p in PhotoImage(photo: p).frame(width: 35, height: 35) }
                }
            }
        }
        .frame(width: 72, height: 72)
        .background(Palette.fill)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityHidden(true)
    }
}

struct AlbumsView: View {
    @Environment(AppStore.self) private var store
    @State private var path: [String] = []
    @State private var creatingAlbum = false
    @State private var renameTarget: Album?
    @State private var renameText = ""
    @State private var deleteTarget: Album?
    @State private var shareFor: ShareRequest?

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
                Pill(label: L.s("new_album"), systemImage: "plus", height: 44) {
                    creatingAlbum = true
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
                            AlbumCover(photos: a.photos)
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
                    .contextMenu {
                        Button(L.s("rename"), systemImage: "pencil") {
                            renameText = a.name
                            renameTarget = a
                        }
                        if !a.photos.isEmpty {
                            Button(L.s("share_album"), systemImage: "square.and.arrow.up") {
                                shareFor = ShareRequest(title: a.name, photos: a.photos)
                            }
                        }
                        if a.photos.count >= 2 {
                            Button(L.s("make_reel"), systemImage: "play.rectangle") {
                                if a.photos.count > ReelPlan.maxPhotos {
                                    store.showToast(L.p("reel_too_many", ReelPlan.maxPhotos), kind: .info, undoLabel: nil, undo: nil)
                                }
                                store.reel = ReelRequest(photos: a.photos)
                            }
                        }
                        Divider()
                        Button(L.s("delete_album"), systemImage: "trash", role: .destructive) {
                            deleteTarget = a
                        }
                    }
                }
            }
            .padding(.horizontal, 12)
            Color.clear.frame(height: 120)
        }
        .background(Palette.bg)
        .sheet(isPresented: $creatingAlbum) {
            NewAlbumSheet { album in
                path.append(album.id)
            }
        }
        .sheet(item: $shareFor) { ShareSheetView(request: $0) }
        .alert(L.s("rename_album"), isPresented: Binding(get: { renameTarget != nil }, set: { if !$0 { renameTarget = nil } })) {
            TextField(L.s("album_name"), text: $renameText)
            Button(L.s("cancel"), role: .cancel) {}
            Button(L.s("save")) {
                if let target = renameTarget { store.renameAlbum(id: target.id, name: renameText) }
            }
            .disabled(renameText.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .confirmationDialog(
            L.s("delete_album_confirm", ["name": deleteTarget?.name ?? ""]),
            isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } }),
            titleVisibility: .visible
        ) {
            Button(L.s("delete_album"), role: .destructive) {
                if let target = deleteTarget {
                    let name = target.name
                    store.deleteAlbum(target.id)
                    store.showToast(L.s("album_deleted", ["name": name]), kind: .info, undoLabel: nil, undo: nil)
                }
            }
        } message: {
            Text(L.s("delete_album_note"))
        }
    }
}

/// In-place New Album sheet launched from `+ New` on the Albums tab.
private struct NewAlbumSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let created: (Album) -> Void
    @State private var name = ""
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var chosen: [Photo] = []
    @State private var loadingPicks = false
    @FocusState private var focused: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Text(L.s("new_album_name")).font(.nunito(22, .bold)).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.muted2)
                            .frame(width: 36, height: 36).background(Palette.fill, in: Circle())
                            .frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel(L.s("close"))
                }

                TextField(L.s("album_name_example"), text: $name)
                    .font(.nunito(16, .medium))
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit(create)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .frame(minHeight: 48)
                    .background(Palette.bg, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(focused ? Palette.accent : Palette.line, lineWidth: 1))

                PhotosPicker(selection: $pickerItems, maxSelectionCount: 100, matching: .images, photoLibrary: .shared()) {
                    HStack(spacing: 10) {
                        Image(systemName: "photo.badge.plus").font(.system(size: 17, weight: .semibold))
                        Text(chosen.isEmpty ? L.s("phone_photos") : L.s("n_selected", ["photos": L.p("photos", chosen.count)]))
                            .font(.nunito(15, .semibold))
                        Spacer()
                        if loadingPicks { ProgressView().tint(Palette.ink) }
                        else { Image(systemName: "chevron.forward").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.muted) }
                    }
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 14)
                    .frame(minHeight: 48)
                    .background(Palette.bg, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Palette.line, lineWidth: 1))
                }
                .onChange(of: pickerItems) { _, items in
                    let ids = items.compactMap(\.itemIdentifier)
                    guard !ids.isEmpty else { chosen = []; return }
                    loadingPicks = true
                    Task {
                        let fetched: [Photo] = await Task.detached(priority: .userInitiated) {
                            var out: [Photo] = []
                            PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil).enumerateObjects { a, _, _ in
                                out.append(Photo(key: "d:\(a.localIdentifier)", source: .device, ref: a.localIdentifier,
                                                 width: a.pixelWidth, height: a.pixelHeight, time: a.creationDate ?? Date()))
                            }
                            return out
                        }.value
                        chosen = fetched
                        loadingPicks = false
                    }
                }

                if !store.saved.favorites.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L.s("tab_favorites")).font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(store.saved.favorites.prefix(20)) { p in
                                    let on = chosen.contains { $0.key == p.key }
                                    Button {
                                        if on { chosen.removeAll { $0.key == p.key } } else { chosen.append(p) }
                                    } label: {
                                        PhotoImage(photo: p)
                                            .frame(width: 64, height: 64)
                                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                            .overlay {
                                                if on {
                                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                        .strokeBorder(Palette.accent, lineWidth: 2.5)
                                                }
                                            }
                                            .overlay(alignment: .topTrailing) {
                                                Image(systemName: on ? "checkmark.circle.fill" : "circle")
                                                    .font(.system(size: 16, weight: .semibold))
                                                    .foregroundStyle(.white)
                                                    .background(Circle().fill(on ? Palette.accentFill : Color.black.opacity(0.38)))
                                                    .padding(4)
                                            }
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityAddTraits(on ? .isSelected : [])
                                }
                            }
                        }
                    }
                }

                Pill(label: L.s("create"), height: 50, busy: loadingPicks, action: create)
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(20)
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.medium, .large])
        .presentationCornerRadius(36)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
        .onAppear { focused = true }
    }

    private func create() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        let album = store.createAlbum(trimmed, photos: chosen)
        dismiss()
        created(album)
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
    @State private var saving = false
    @State private var showNavTitle = false
    @Environment(\.dynamicTypeSize) private var textSize

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text(album.name).font(.nunito(30, .bold)).tight(30).foregroundStyle(Palette.ink).accessibilityAddTraits(.isHeader)
                    .onGeometryChange(for: Bool.self) { $0.frame(in: .global).maxY < 96 } action: { showNavTitle = $0 }
                Text(L.s("album_made", ["photos": L.p("photos", album.photos.count), "date": L.date(album.createdAt, "MMMd")]))
                    .font(.nunito(15)).foregroundStyle(Palette.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 4).padding(.bottom, 16)
            // Side by side, or one above the other once large text no longer fits both.
            let buttons = textSize >= .xxLarge ? AnyLayout(VStackLayout(spacing: 10)) : AnyLayout(HStackLayout(spacing: 10))
            buttons {
                Pill(label: L.s("share_album"), systemImage: "square.and.arrow.up", height: 46, wide: true) {
                    shareFor = ShareRequest(title: album.name, photos: album.photos)
                }
                .disabled(album.photos.isEmpty)
                Pill(label: L.s("save_to_photos"), systemImage: "square.and.arrow.down", kind: .secondary, height: 46, wide: true, busy: saving) {
                    guard !saving else { return }
                    saving = true
                    Task {
                        await store.saveToPhotos(name: album.name, photos: album.photos)
                        saving = false
                    }
                }
                .disabled(album.photos.isEmpty)
            }
            .padding(.horizontal, 16).padding(.bottom, album.photos.count >= 2 ? 10 : 16)

            if album.photos.count >= 2 {
                Pill(label: L.s("make_reel"), systemImage: "play.rectangle", kind: .tint, height: 46, wide: true) {
                    if album.photos.count > ReelPlan.maxPhotos {
                        store.showToast(L.p("reel_too_many", ReelPlan.maxPhotos), kind: .info, undoLabel: nil, undo: nil)
                    }
                    store.reel = ReelRequest(photos: album.photos)
                }
                .padding(.horizontal, 16).padding(.bottom, 16)
            }

            if album.photos.isEmpty {
                EmptyNote(icon: "photo.on.rectangle", title: L.s("album_empty"), text: L.s("albums_empty_body"))
            } else {
                SquareGrid(photos: album.photos, onRemove: { photo in
                    store.removeFromAlbum(album.id, photoKey: photo.key)
                })
            }
            Color.clear.frame(height: 120)
        }
        .background(Palette.bg)
        .sheet(item: $shareFor) { ShareSheetView(request: $0) }
        .navigationTitle(showNavTitle ? album.name : "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    if album.photos.count >= 2 {
                        Button(L.s("make_reel"), systemImage: "play.rectangle") {
                            if album.photos.count > ReelPlan.maxPhotos {
                                store.showToast(L.p("reel_too_many", ReelPlan.maxPhotos), kind: .info, undoLabel: nil, undo: nil)
                            }
                            store.reel = ReelRequest(photos: album.photos)
                        }
                    }
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
                let name = album.name
                dismiss()
                store.deleteAlbum(album.id)
                store.showToast(L.s("album_deleted", ["name": name]), kind: .info, undoLabel: nil, undo: nil)
            }
        } message: {
            Text(L.s("delete_album_note"))
        }
    }
}
