import SwiftUI

struct RootView: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        @Bindable var store = store
        Group {
            if store.saved.onboarded {
                // Native tab view: each tab keeps its own stack and scroll place.
                TabView(selection: $store.tab) {
                    NavigationStack {
                        TodayView().toolbar(.hidden, for: .navigationBar)
                    }
                    .tabItem { Label(L.s("tab_today"), systemImage: "clock.arrow.circlepath") }
                    .tag(AppTab.today)

                    NavigationStack {
                        FavoritesView().toolbar(.hidden, for: .navigationBar)
                    }
                    .tabItem { Label(L.s("tab_favorites"), systemImage: store.tab == .favorites ? "heart.fill" : "heart") }
                    .tag(AppTab.favorites)

                    AlbumsView()
                        .tabItem { Label(L.s("tab_albums"), systemImage: "photo.on.rectangle") }
                        .tag(AppTab.albums)
                }
                .tint(Palette.accent)
                // The tab bar fills symbols on its own; keep the Favorites heart an outline until it is picked.
                .environment(\.symbolVariants, .none)
            } else {
                OnboardingView()
            }
        }
        .overlay(alignment: .bottom) {
            if let toast = store.toast, store.viewer == nil {
                ToastView(message: toast).padding(.bottom, 104)
            }
        }
        .animation(.easeOut(duration: 0.2), value: store.toast)
        .sensoryFeedback(.success, trigger: store.albumSaves)
        .fullScreenCover(item: $store.viewer) { PhotoViewer(viewer: $0) }
        #if DEBUG
        .task {
            if let scene = AppStore.launchScene { await store.runDemo(scene) }
        }
        #endif
    }
}
