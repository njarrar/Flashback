import SwiftUI

struct RootView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        @Bindable var store = store
        Group {
            if store.saved.onboarded {
                // Native tab view: each tab keeps its own stack and scroll place.
                TabView(selection: $store.tab) {
                    NavigationStack {
                        TodayView().toolbar(.hidden, for: .navigationBar)
                    }
                    .tabItem {
                        Label(L.s("tab_today"), systemImage: "clock.arrow.circlepath")
                            .symbolVariants(.none)
                    }
                    .tag(AppTab.today)

                    NavigationStack {
                        FavoritesView().toolbar(.hidden, for: .navigationBar)
                    }
                    .tabItem {
                        Label(L.s("tab_favorites"), systemImage: store.tab == .favorites ? "heart.fill" : "heart")
                            .symbolVariants(.none)
                    }
                    .tag(AppTab.favorites)

                    AlbumsView()
                        .tabItem {
                            Label(L.s("tab_albums"), systemImage: store.tab == .albums ? "photo.on.rectangle.fill" : "photo.on.rectangle")
                                .symbolVariants(.none)
                        }
                        .tag(AppTab.albums)
                }
                .tint(Palette.accent)
                .fullScreenCover(item: $store.reel) { ReelView(request: $0) }
            } else {
                OnboardingView()
            }
        }
        .overlay(alignment: .bottom) {
            if let toast = store.toast, store.viewer == nil, store.reel == nil {
                ToastView(
                    message: toast,
                    kind: store.toastKind,
                    undoLabel: store.toastUndoLabel,
                    onUndo: store.toastUndoLabel != nil ? { store.triggerToastUndo() } : nil
                )
                .padding(.bottom, 104)
                .padding(.horizontal, 16)
            }
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: store.toast)
        .sensoryFeedback(.success, trigger: store.albumSaves)
        .fullScreenCover(item: $store.viewer) { PhotoViewer(viewer: $0) }
        #if DEBUG
        .task {
            if let scene = AppStore.launchScene { await store.runDemo(scene) }
        }
        #endif
    }
}
