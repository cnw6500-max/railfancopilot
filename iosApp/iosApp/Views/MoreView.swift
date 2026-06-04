import SwiftUI

struct MoreView: View {
    @ObservedObject var vm: RailFanViewModel

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 10) {
                        MoreRow(icon: "antenna.radiowaves.left.and.right", color: .green,
                                title: "Scanner",
                                subtitle: "Live railroad radio feeds") {
                            AnyView(ScannerView(vm: vm))
                        }
                        MoreRow(icon: "cpu", color: .railBlue,
                                title: "Decoder",
                                subtitle: "AI train symbol decoder") {
                            AnyView(DecoderView(vm: vm))
                        }
                        MoreRow(icon: "camera.fill", color: .purple,
                                title: "Photo Tools",
                                subtitle: "Loco ID, consist analysis, sun tracker") {
                            AnyView(PhotoView(vm: vm))
                        }
                        MoreRow(icon: "book.fill", color: .orange,
                                title: "Roster",
                                subtitle: "Locomotive encyclopedia") {
                            AnyView(EncyclopediaView())
                        }
                        MoreRow(icon: "bookmark.fill", color: .yellow,
                                title: "Saved Locations",
                                subtitle: "Your private saved spots") {
                            AnyView(SavedLocationsView(vm: vm))
                        }
                        MoreRow(icon: "gear", color: .gray,
                                title: "Settings",
                                subtitle: "Display, notifications, about") {
                            AnyView(SettingsView(vm: vm))
                        }
                        MoreRow(icon: "train.side.front.car", color: .green,
                                title: "Trip Log",
                                subtitle: "Log your train rides — miles & duration") {
                            AnyView(TripsView(vm: vm))
                        }
                        Spacer(minLength: 40)
                    }
                    .padding(.top)
                }
            }
            .navigationTitle("More")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}

// ── More Row ──────────────────────────────────────────────────────────────────
struct MoreRow<Destination: View>: View {
    let icon: String
    let color: Color
    let title: String
    let subtitle: String
    let destination: () -> Destination

    init(icon: String, color: Color, title: String, subtitle: String,
         @ViewBuilder destination: @escaping () -> Destination) {
        self.icon = icon
        self.color = color
        self.title = title
        self.subtitle = subtitle
        self.destination = destination
    }

    // Convenience initialiser for AnyView wrapping
    init(icon: String, color: Color, title: String, subtitle: String,
         destination: @escaping () -> AnyView) where Destination == AnyView {
        self.icon = icon
        self.color = color
        self.title = title
        self.subtitle = subtitle
        self.destination = destination
    }

    var body: some View {
        NavigationLink(destination: destination()) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .frame(width: 38, height: 38)
                    .background(color)
                    .cornerRadius(10)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.textPrimary)
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundColor(.textMuted)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.textMuted)
            }
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(14)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
        }
        .padding(.horizontal)
        .buttonStyle(.plain)
    }
}
