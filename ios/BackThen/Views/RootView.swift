import SwiftUI

struct RootView: View {
    @Environment(AppStore.self) private var store
    @State private var hideTabs = false

    var body: some View {
        @Bindable var store = store
        Group {
            if store.saved.onboarded {
                ZStack(alignment: .bottom) {
                    switch store.tab {
                    case .today: TodayView(hideTabs: $hideTabs)
                    case .favorites: FavoritesView()
                    case .albums: AlbumsView()
                    }
                    if !hideTabs { TabBar(tab: $store.tab) }
                }
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
        .fullScreenCover(item: $store.viewer) { PhotoViewer(viewer: $0) }
    }
}

/// Floating glass tab bar, as in the iPhone design.
private struct TabBar: View {
    @Binding var tab: AppTab

    var body: some View {
        HStack(spacing: 0) {
            item(.today, "Today", "clock.arrow.circlepath")
            item(.favorites, "Favorites", "heart")
            item(.albums, "Albums", "photo.on.rectangle")
        }
        .padding(4)
        .frame(height: 62)
        .background(.regularMaterial, in: Capsule())
        .overlay(Capsule().stroke(Palette.line))
        .shadow(color: Palette.hex(0x141416, 0.14), radius: 14, y: 8)
        .padding(.horizontal, 24)
        .padding(.bottom, 4)
    }

    private func item(_ t: AppTab, _ label: String, _ icon: String) -> some View {
        let on = tab == t
        return Button { tab = t } label: {
            VStack(spacing: 2) {
                Image(systemName: on && icon == "heart" ? "heart.fill" : icon).font(.system(size: 20, weight: on ? .semibold : .regular))
                Text(label).font(.nunito(11, .semibold))
            }
            .foregroundStyle(on ? Palette.accent : Palette.ink)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(on ? Palette.accent.opacity(0.1) : .clear, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? [.isSelected, .isButton] : .isButton)
    }
}
