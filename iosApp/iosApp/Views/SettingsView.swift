import SwiftUI

struct SettingsView: View {
    @ObservedObject var vm: RailFanViewModel

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                List {
                    // Train filters
                    Section(header: Text("Train Filters").foregroundColor(.textMuted)) {
                        ToggleRow(label: "Amtrak",           icon: "train.side.front.car",
                                  value: $vm.showAmtrak)
                        ToggleRow(label: "Commuter Rail",    icon: "tram.fill",
                                  value: $vm.showCommuter)
                        ToggleRow(label: "Freight",          icon: "shippingbox.fill",
                                  value: $vm.showFreight)
                    }
                    .listRowBackground(Color.bgCard)

                    // App info
                    Section(header: Text("About").foregroundColor(.textMuted)) {
                        InfoRow(label: "Version",   value: "2.1.3 (21)")
                        InfoRow(label: "Platform",  value: "iOS — Powered by KMP")
                        InfoRow(label: "Map data",  value: "Apple Maps (MapKit)")
                        InfoRow(label: "Rosters",   value: "DieselShop.us")
                        InfoRow(label: "Streams",   value: "RailroadRadio.net")
                    }
                    .listRowBackground(Color.bgCard)

                    // Links
                    Section(header: Text("Links").foregroundColor(.textMuted)) {
                        LinkRow(label: "Privacy Policy",
                                url: "https://railfancopilot.com/privacy")
                        LinkRow(label: "Terms of Service",
                                url: "https://railfancopilot.com/terms")
                        LinkRow(label: "Support / Feedback",
                                url: "mailto:support@railfancopilot.com")
                    }
                    .listRowBackground(Color.bgCard)

                    // Achievements
                    Section {
                        NavigationLink {
                            AchievementsView()
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "trophy.fill")
                                    .foregroundColor(.yellow)
                                Text("Achievements")
                                    .foregroundColor(.textPrimary)
                                    .font(.system(size: 15, weight: .medium))
                            }
                        }
                    }
                    .listRowBackground(Color.bgCard)

                    // Premium
                    Section {
                        NavigationLink {
                            UpgradeView(vm: vm)
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "star.fill")
                                    .foregroundColor(.yellow)
                                Text(vm.isPremium ? "Premium Active" : "Upgrade to Premium")
                                    .foregroundColor(.textPrimary)
                                    .font(.system(size: 15, weight: .medium))
                            }
                        }
                    }
                    .listRowBackground(Color.bgCard)
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}

struct ToggleRow: View {
    let label: String
    let icon: String
    @Binding var value: Bool
    var body: some View {
        Toggle(isOn: $value) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .foregroundColor(.railBlue)
                    .frame(width: 22)
                Text(label).foregroundColor(.textPrimary)
            }
        }
        .tint(.railBlue)
    }
}

struct InfoRow: View {
    let label: String; let value: String
    var body: some View {
        HStack {
            Text(label).foregroundColor(.textMuted).font(.system(size: 14))
            Spacer()
            Text(value).foregroundColor(.textSecondary).font(.system(size: 14))
        }
    }
}

struct LinkRow: View {
    let label: String; let url: String
    var body: some View {
        Button {
            if let u = URL(string: url) { UIApplication.shared.open(u) }
        } label: {
            HStack {
                Text(label).foregroundColor(.textPrimary).font(.system(size: 14))
                Spacer()
                Image(systemName: "arrow.up.right").foregroundColor(.railBlue).font(.system(size: 12))
            }
        }
    }
}
