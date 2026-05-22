import SwiftUI
import UserNotifications

// ── Stored alert model ────────────────────────────────────────────────────────
struct RailAlert: Identifiable, Codable {
    var id: String
    var title: String
    var body: String
    var timestampMs: Double
    var isRead: Bool

    var minutesAgo: Int {
        let ms = Date().timeIntervalSince1970 * 1000 - timestampMs
        return max(0, Int(ms / 60000))
    }
}

// ── Alert store ───────────────────────────────────────────────────────────────
@MainActor
class AlertStore: ObservableObject {
    static let shared = AlertStore()
    private let key = "storedRailAlerts"

    @Published var alerts: [RailAlert] = []

    var unreadCount: Int { alerts.filter { !$0.isRead }.count }

    init() { load() }

    func add(title: String, body: String) {
        let alert = RailAlert(
            id: UUID().uuidString,
            title: title,
            body: body,
            timestampMs: Date().timeIntervalSince1970 * 1000,
            isRead: false
        )
        alerts.insert(alert, at: 0)
        if alerts.count > 50 { alerts = Array(alerts.prefix(50)) }
        save()
    }

    func markAllRead() {
        alerts = alerts.map { var a = $0; a.isRead = true; return a }
        save()
    }

    func delete(id: String) {
        alerts.removeAll { $0.id == id }
        save()
    }

    func clearAll() {
        alerts.removeAll()
        save()
    }

    private func load() {
        if let data = UserDefaults.standard.data(forKey: key),
           let decoded = try? JSONDecoder().decode([RailAlert].self, from: data) {
            alerts = decoded
        }
    }

    private func save() {
        if let data = try? JSONEncoder().encode(alerts) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }
}

// ── Alerts Screen ─────────────────────────────────────────────────────────────
struct AlertsView: View {
    @ObservedObject var vm: RailFanViewModel
    @StateObject private var store = AlertStore.shared
    @StateObject private var notifManager = NotificationManager.shared
    @State private var showClearConfirm = false

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                if store.alerts.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "bell.slash")
                            .font(.system(size: 48))
                            .foregroundColor(.railBlueDark)
                        Text("No Alerts Yet")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.textPrimary)
                        Text("Approach alerts and watchlist matches will appear here.")
                            .font(.system(size: 14))
                            .foregroundColor(.textMuted)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)

                        if !notifManager.isAuthorized {
                            Button {
                                notifManager.requestPermission()
                            } label: {
                                Label("Enable Notifications", systemImage: "bell.badge.fill")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 24)
                                    .padding(.vertical, 12)
                                    .background(Color.railBlueMid)
                                    .cornerRadius(12)
                            }
                        }
                    }
                    .padding()
                } else {
                    ScrollView {
                        VStack(spacing: 8) {
                            ForEach(store.alerts) { alert in
                                AlertCard(alert: alert, onDelete: { store.delete(id: alert.id) })
                                    .padding(.horizontal)
                            }
                            Spacer(minLength: 40)
                        }
                        .padding(.top)
                    }
                }
            }
            .navigationTitle("Alerts")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                if !store.alerts.isEmpty {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Clear All") { showClearConfirm = true }
                            .foregroundColor(.textMuted)
                            .font(.system(size: 14))
                    }
                }
            }
            .confirmationDialog("Clear all alerts?", isPresented: $showClearConfirm, titleVisibility: .visible) {
                Button("Clear All", role: .destructive) { store.clearAll() }
                Button("Cancel", role: .cancel) {}
            }
            .onAppear { store.markAllRead() }
        }
    }
}

// ── Alert Card ────────────────────────────────────────────────────────────────
struct AlertCard: View {
    let alert: RailAlert
    let onDelete: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "bell.fill")
                .font(.system(size: 16))
                .foregroundColor(.railBlue)
                .frame(width: 32, height: 32)
                .background(Color.railBlueDark)
                .cornerRadius(8)

            VStack(alignment: .leading, spacing: 4) {
                Text(alert.title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.textPrimary)
                Text(alert.body)
                    .font(.system(size: 13))
                    .foregroundColor(.textSecondary)
                Text("\(alert.minutesAgo)m ago")
                    .font(.system(size: 11))
                    .foregroundColor(.textMuted)
            }

            Spacer()

            Button(action: onDelete) {
                Image(systemName: "xmark")
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
            }
        }
        .padding(14)
        .background(alert.isRead ? Color.bgCard : Color.railBlueDark.opacity(0.4))
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
    }
}
