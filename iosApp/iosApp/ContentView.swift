import SwiftUI

struct ContentView: View {
    @StateObject private var vm = RailFanViewModel()
    @StateObject private var alertStore = AlertStore.shared
    @State private var showOnboarding = !UserDefaults.standard.bool(forKey: "onboardingShown")

    var body: some View {
        ZStack {
            TabView {
                MapView(vm: vm)
                    .tabItem { Label("Map", systemImage: "map.fill") }

                CommunityView(vm: vm)
                    .tabItem { Label("Community", systemImage: "person.3.fill") }

                AlertsView(vm: vm)
                    .tabItem { Label("Alerts", systemImage: "bell.fill") }
                    .badge(alertStore.unreadCount > 0 ? alertStore.unreadCount : 0)

                WatchlistView(vm: vm)
                    .tabItem { Label("Watchlist", systemImage: "bookmark.fill") }

                MoreView(vm: vm)
                    .tabItem { Label("More", systemImage: "square.grid.2x2.fill") }
            }
            .accentColor(.railBlue)
            .onAppear { configureTabBar() }

            if showOnboarding {
                OnboardingView {
                    UserDefaults.standard.set(true, forKey: "onboardingShown")
                    showOnboarding = false
                }
                .transition(.opacity)
                .zIndex(1)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: showOnboarding)
    }

    private func configureTabBar() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Color.bgPrimary)

        let itemAppearance = UITabBarItemAppearance()
        itemAppearance.normal.iconColor   = UIColor(Color.textMuted)
        itemAppearance.normal.titleTextAttributes   = [.foregroundColor: UIColor(Color.textMuted)]
        itemAppearance.selected.iconColor = UIColor(Color.railBlue)
        itemAppearance.selected.titleTextAttributes = [.foregroundColor: UIColor(Color.railBlue)]
        appearance.stackedLayoutAppearance = itemAppearance

        UITabBar.appearance().standardAppearance   = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}
