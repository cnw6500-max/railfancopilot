import SwiftUI

struct ContentView: View {
    @StateObject private var vm = RailFanViewModel()
    @State private var showOnboarding = !UserDefaults.standard.bool(forKey: "onboardingShown")

    var body: some View {
        ZStack {
            TabView {
                MapView(vm: vm)
                    .tabItem { Label("Map",       systemImage: "map.fill") }
                ScannerView(vm: vm)
                    .tabItem { Label("Scanner",   systemImage: "antenna.radiowaves.left.and.right") }
                DecoderView(vm: vm)
                    .tabItem { Label("Decoder",   systemImage: "cpu") }
                PhotoView(vm: vm)
                    .tabItem { Label("Photo",     systemImage: "camera.fill") }
                CommunityView(vm: vm)
                    .tabItem { Label("Community", systemImage: "person.3.fill") }
                EncyclopediaView()
                    .tabItem { Label("Roster",    systemImage: "book.fill") }
                SavedLocationsView(vm: vm)
                    .tabItem { Label("Saved",     systemImage: "bookmark.fill") }
                WebcamsView()
                    .tabItem { Label("Webcams",   systemImage: "video.fill") }
                SpotsView(vm: vm)
                    .tabItem { Label("Spots",     systemImage: "camera.aperture") }
                TripLogView(vm: vm)
                    .tabItem { Label("Trip Log",  systemImage: "book.closed.fill") }
                SettingsView(vm: vm)
                    .tabItem { Label("Settings",  systemImage: "gear") }
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
