import SwiftUI

private let faqItems: [(String, String)] = [
    ("How often does train data update?",
     "Trains refresh on the interval you set (15–120 s). Commuter feeds like MBTA and SEPTA update in near-real-time; Amtrak typically updates every 1–2 minutes on their end."),
    ("Why do I see no trains on the map?",
     "Make sure location permission is granted and your GPS has a fix. The app only fetches trains within your configured radius (default 500 mi). Try tapping the refresh button on the map."),
    ("How does the AI Decoder work?",
     "It sends your symbol to Claude AI, which cross-references a built-in railroad symbol database to return origin, destination, train type, and notes. An Anthropic API key is required."),
    ("Can I use the app without internet?",
     "The map, community reports, and live train data all require internet. The frequency reference and saved locations work fully offline."),
    ("Why can't I submit a community report?",
     "The Submit button requires a GPS fix. Wait for the location indicator to show coordinates, then try again."),
    ("How do I earn achievements?",
     "Achievements unlock automatically as you use the app — identifying locomotives, spotting fast trains, visiting rail yards, and more. Check the Photo screen to see your progress."),
]

struct SettingsView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var expandedFAQ: Int? = nil

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                List {
                    // ── Subscription status ───────────────────────────────────
                    if vm.isInTrial || !vm.isPremium {
                        Section(header: Text("Subscription").foregroundColor(.textMuted)) {
                            if vm.isInTrial {
                                HStack(spacing: 12) {
                                    Image(systemName: "timer").foregroundColor(.green).frame(width: 22)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Free trial active").foregroundColor(.textPrimary)
                                        Text(vm.trialDaysLeft > 1 ? "\(vm.trialDaysLeft) days remaining"
                                             : vm.trialDaysLeft == 1 ? "Last day — expires tomorrow"
                                             : "Expires today")
                                            .font(.system(size: 12)).foregroundColor(.textMuted)
                                    }
                                }
                            }
                            NavigationLink {
                                UpgradeView(vm: vm)
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "star.fill").foregroundColor(.yellow).frame(width: 22)
                                    Text(vm.isPremium ? "Premium Active" : "Upgrade to Pro")
                                        .foregroundColor(.textPrimary)
                                }
                            }
                        }
                        .listRowBackground(Color.bgCard)
                    }

                    // ── Community ─────────────────────────────────────────────
                    Section(header: Text("Community").foregroundColor(.textMuted)) {
                        HStack(spacing: 12) {
                            Image(systemName: "person.fill").foregroundColor(.railBlue).frame(width: 22)
                            TextField("Display name", text: $vm.userName)
                                .foregroundColor(.textPrimary).submitLabel(.done)
                        }
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Image(systemName: "location.circle").foregroundColor(.railBlue).frame(width: 22)
                                Text("Default report radius").foregroundColor(.textPrimary)
                                Spacer()
                                Text(vm.reportRadiusMiles >= 999 ? "All" : "\(vm.reportRadiusMiles) mi")
                                    .font(.system(size: 13, weight: .semibold)).foregroundColor(.railBlue)
                            }
                            Slider(value: Binding(
                                get: { Double(vm.reportRadiusMiles) },
                                set: { vm.reportRadiusMiles = Int($0) }
                            ), in: 25...999, step: 25).tint(.railBlue)
                            Text("Distance filter shown when opening Community")
                                .font(.system(size: 11)).foregroundColor(.textMuted)
                        }
                        .padding(.vertical, 4)
                    }
                    .listRowBackground(Color.bgCard)

                    // ── Train Filters ─────────────────────────────────────────
                    Section(header: Text("Train Filters").foregroundColor(.textMuted)) {
                        ToggleRow(label: "Amtrak",           icon: "train.side.front.car", value: $vm.showAmtrak)
                        ToggleRow(label: "Freight",          icon: "shippingbox.fill",      value: $vm.showFreight)
                        ToggleRow(label: "Commuter (other)", icon: "tram.fill",             value: $vm.showCommuter)
                    }
                    .listRowBackground(Color.bgCard)

                    // ── Commuter Rail Agencies ────────────────────────────────
                    Section(header: Text("Commuter Rail Agencies").foregroundColor(.textMuted)) {
                        ToggleRow(label: "MBTA Commuter Rail",    icon: "tram", value: $vm.showMBTA,
                                  subtitle: "Boston area · MBTA GTFS-RT")
                        ToggleRow(label: "SEPTA Regional Rail",   icon: "tram", value: $vm.showSEPTA,
                                  subtitle: "Philadelphia area · SEPTA")
                        ToggleRow(label: "MTA LIRR",              icon: "tram", value: $vm.showLIRR,
                                  subtitle: "Long Island · MTA GTFS-RT")
                        ToggleRow(label: "MTA Metro-North",       icon: "tram", value: $vm.showMetroNorth,
                                  subtitle: "New York area · MTA GTFS-RT")
                        ToggleRow(label: "Metra",                 icon: "tram", value: $vm.showMetra,
                                  subtitle: "Chicago area · Metra GTFS-RT")
                        ToggleRow(label: "Caltrain",              icon: "tram", value: $vm.showCaltrain,
                                  subtitle: "Bay Area · 511.org GTFS-RT")
                        ToggleRow(label: "Sound Transit Sounder", icon: "tram", value: $vm.showSoundTransit,
                                  subtitle: "Seattle area · GTFS-RT")
                    }
                    .listRowBackground(Color.bgCard)

                    // ── Map Settings ──────────────────────────────────────────
                    Section(header: Text("Map Settings").foregroundColor(.textMuted)) {
                        // Refresh interval
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Image(systemName: "arrow.clockwise").foregroundColor(.railBlue).frame(width: 22)
                                Text("Refresh Interval").foregroundColor(.textPrimary)
                                Spacer()
                                Text("\(vm.refreshIntervalSec)s")
                                    .font(.system(size: 13, weight: .semibold)).foregroundColor(.railBlue)
                            }
                            Slider(value: Binding(
                                get: { Double(vm.refreshIntervalSec) },
                                set: { vm.refreshIntervalSec = Int($0) }
                            ), in: 15...120, step: 15)
                            .tint(.railBlue)
                            HStack {
                                Text("15s").font(.system(size: 10)).foregroundColor(.textMuted)
                                Spacer()
                                Text("120s").font(.system(size: 10)).foregroundColor(.textMuted)
                            }
                        }
                        .padding(.vertical, 4)

                        // Train radius
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Image(systemName: "circle.dashed").foregroundColor(.railBlue).frame(width: 22)
                                Text("Train Search Radius").foregroundColor(.textPrimary)
                                Spacer()
                                Text("\(vm.trainRadiusMiles) mi")
                                    .font(.system(size: 13, weight: .semibold)).foregroundColor(.railBlue)
                            }
                            Slider(value: Binding(
                                get: { Double(vm.trainRadiusMiles) },
                                set: { vm.trainRadiusMiles = Int($0) }
                            ), in: 100...1000, step: 100)
                            .tint(.railBlue)
                            HStack {
                                Text("100 mi").font(.system(size: 10)).foregroundColor(.textMuted)
                                Spacer()
                                Text("1000 mi").font(.system(size: 10)).foregroundColor(.textMuted)
                            }
                        }
                        .padding(.vertical, 4)

                        // Rail overlay default
                        Toggle(isOn: $vm.railOverlayDefault) {
                            HStack(spacing: 12) {
                                Image(systemName: "map").foregroundColor(.railBlue).frame(width: 22)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Rail Overlay by Default").foregroundColor(.textPrimary)
                                    Text("OpenRailwayMap tile layer")
                                        .font(.system(size: 11)).foregroundColor(.textMuted)
                                }
                            }
                        }
                        .tint(.railBlue)
                    }
                    .listRowBackground(Color.bgCard)

                    // ── Approach Notifications ────────────────────────────────
                    Section(header: Text("Approach Notifications").foregroundColor(.textMuted)) {
                        Toggle(isOn: $vm.approachNotificationsEnabled) {
                            HStack(spacing: 12) {
                                Image(systemName: "bell.badge.fill").foregroundColor(.railBlue).frame(width: 22)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Train Approach Alerts").foregroundColor(.textPrimary)
                                    Text("Pro feature").font(.system(size: 11)).foregroundColor(.yellow)
                                }
                            }
                        }
                        .tint(.railBlue)
                        .disabled(!vm.isPremium)
                        .onChange(of: vm.approachNotificationsEnabled) { enabled in
                            if enabled { NotificationManager.shared.requestPermission() }
                        }

                        if vm.approachNotificationsEnabled && vm.isPremium {
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text("Alert within").font(.system(size: 13)).foregroundColor(.textMuted)
                                    Spacer()
                                    Text("\(vm.approachEtaThreshold) min")
                                        .font(.system(size: 13, weight: .semibold)).foregroundColor(.railBlue)
                                }
                                Slider(value: Binding(
                                    get: { Double(vm.approachEtaThreshold) },
                                    set: { vm.approachEtaThreshold = Int($0) }
                                ), in: 5...60, step: 5)
                                .tint(.railBlue)
                            }
                        }
                    }
                    .listRowBackground(Color.bgCard)

                    // ── Achievements ──────────────────────────────────────────
                    Section {
                        NavigationLink {
                            AchievementsView(achievements: vm.achievements)
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "trophy.fill").foregroundColor(.yellow)
                                Text("Achievements").foregroundColor(.textPrimary).font(.system(size: 15, weight: .medium))
                                Spacer()
                                let earned = vm.achievements.filter { $0.earned }.count
                                Text("\(earned)/\(vm.achievements.count)")
                                    .font(.system(size: 13)).foregroundColor(.textMuted)
                            }
                        }
                    }
                    .listRowBackground(Color.bgCard)

                    // ── Premium ───────────────────────────────────────────────
                    Section {
                        NavigationLink {
                            UpgradeView(vm: vm)
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "star.fill").foregroundColor(.yellow)
                                Text(vm.isPremium ? "Premium Active" : "Upgrade to Premium")
                                    .foregroundColor(.textPrimary).font(.system(size: 15, weight: .medium))
                            }
                        }
                    }
                    .listRowBackground(Color.bgCard)

                    // ── About ─────────────────────────────────────────────────
                    Section(header: Text("About").foregroundColor(.textMuted)) {
                        InfoRow(label: "Version",
                                value: "\(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—") (iOS)")
                        InfoRow(label: "Map data",  value: "Apple Maps + OpenRailwayMap")
                        InfoRow(label: "Train data", value: "Amtrak · MBTA · SEPTA · GTFS-RT")
                        InfoRow(label: "AI",        value: "Claude (Anthropic)")
                        InfoRow(label: "Rosters",   value: "DieselShop.us")
                    }
                    .listRowBackground(Color.bgCard)

                    // ── Links ─────────────────────────────────────────────────
                    Section(header: Text("Links").foregroundColor(.textMuted)) {
                        LinkRow(label: "Privacy Policy",     url: "https://railfancopilot.com/privacy")
                        LinkRow(label: "Terms of Service",   url: "https://railfancopilot.com/terms")
                        LinkRow(label: "Support / Feedback", url: "mailto:support@railfancopilot.com")
                    }
                    .listRowBackground(Color.bgCard)

                    // ── Help & FAQ ────────────────────────────────────────────
                    Section(header: Text("Help & FAQ").foregroundColor(.textMuted)) {
                        ForEach(faqItems.indices, id: \.self) { i in
                            FAQRow(
                                question: faqItems[i].0,
                                answer: faqItems[i].1,
                                isExpanded: expandedFAQ == i,
                                onToggle: { expandedFAQ = expandedFAQ == i ? nil : i }
                            )
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

// ── Reusable rows ─────────────────────────────────────────────────────────────
struct ToggleRow: View {
    let label: String; let icon: String
    @Binding var value: Bool
    var subtitle: String? = nil

    var body: some View {
        Toggle(isOn: $value) {
            HStack(spacing: 12) {
                Image(systemName: icon).foregroundColor(.railBlue).frame(width: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).foregroundColor(.textPrimary)
                    if let sub = subtitle {
                        Text(sub).font(.system(size: 11)).foregroundColor(.textMuted)
                    }
                }
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

struct FAQRow: View {
    let question: String; let answer: String
    let isExpanded: Bool; let onToggle: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onToggle) {
                HStack {
                    Text(question)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.textPrimary)
                        .multilineTextAlignment(.leading)
                    Spacer()
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12))
                        .foregroundColor(.textMuted)
                }
                .padding(.vertical, 4)
            }
            if isExpanded {
                Text(answer)
                    .font(.system(size: 13))
                    .foregroundColor(.textSecondary)
                    .lineSpacing(4)
                    .padding(.top, 8)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: isExpanded)
    }
}
