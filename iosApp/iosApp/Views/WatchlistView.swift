import SwiftUI

struct WatchlistView: View {
    @ObservedObject var vm: RailFanViewModel
    @StateObject private var firestore = FirestoreManager.shared
    @State private var showAddDialog = false

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                if firestore.watchlist.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "bookmark.slash")
                            .font(.system(size: 48))
                            .foregroundColor(.railBlueDark)
                        Text("No Watchlist Entries")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.textPrimary)
                        Text("Follow specific locomotive road numbers or train symbols. You'll get a push notification when the community spots a match.")
                            .font(.system(size: 14))
                            .foregroundColor(.textMuted)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                        Button {
                            showAddDialog = true
                        } label: {
                            Label("Add to Watchlist", systemImage: "plus.circle.fill")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 12)
                                .background(Color.railBlueMid)
                                .cornerRadius(12)
                        }
                    }
                    .padding()
                } else {
                    ScrollView {
                        VStack(spacing: 10) {
                            ForEach(firestore.watchlist) { entry in
                                WatchlistCard(entry: entry) {
                                    Task { await firestore.removeWatchlistEntry(id: entry.id) }
                                }
                                .padding(.horizontal)
                            }
                            Spacer(minLength: 40)
                        }
                        .padding(.top)
                    }
                }
            }
            .navigationTitle("Watchlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showAddDialog = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(.railBlue)
                            .font(.system(size: 20))
                    }
                }
            }
            .sheet(isPresented: $showAddDialog) {
                AddWatchlistSheet(isPresented: $showAddDialog)
            }
        }
    }
}

// ── Watchlist Card ────────────────────────────────────────────────────────────
struct WatchlistCard: View {
    let entry: WatchlistEntry
    let onDelete: () -> Void
    @State private var showConfirm = false

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: entry.type == .symbol ? "train.side.front.car" : "number.square.fill")
                .font(.system(size: 20))
                .foregroundColor(.railBlue)
                .frame(width: 36, height: 36)
                .background(Color.railBlueDark)
                .cornerRadius(8)

            VStack(alignment: .leading, spacing: 4) {
                Text(entry.label)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.textPrimary)
                HStack(spacing: 6) {
                    Text(entry.type == .symbol ? "Train Symbol" : "Road Number")
                        .font(.system(size: 12))
                        .foregroundColor(.railBlue)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.railBlueDark)
                        .cornerRadius(4)
                    if !entry.railroad.isEmpty {
                        Text(entry.railroad)
                            .font(.system(size: 12))
                            .foregroundColor(.textMuted)
                    }
                }
            }

            Spacer()

            Button {
                showConfirm = true
            } label: {
                Image(systemName: "trash")
                    .foregroundColor(.textMuted)
                    .font(.system(size: 16))
            }
            .confirmationDialog("Remove \"\(entry.label)\" from watchlist?", isPresented: $showConfirm, titleVisibility: .visible) {
                Button("Remove", role: .destructive) { onDelete() }
                Button("Cancel", role: .cancel) {}
            }
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
    }
}

// ── Add Watchlist Sheet ───────────────────────────────────────────────────────
struct AddWatchlistSheet: View {
    @Binding var isPresented: Bool
    @StateObject private var firestore = FirestoreManager.shared
    @State private var selectedType: WatchlistType = .symbol
    @State private var value = ""
    @State private var railroad = ""
    @FocusState private var valueFocused: Bool

    private var isValid: Bool { !value.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {

                        // Type picker
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Watch type")
                                .font(.system(size: 13)).foregroundColor(.textMuted)
                            HStack(spacing: 10) {
                                ForEach([WatchlistType.symbol, .loco], id: \.self) { type in
                                    Button {
                                        selectedType = type
                                    } label: {
                                        HStack(spacing: 6) {
                                            Image(systemName: type == .symbol ? "train.side.front.car" : "number.square")
                                            Text(type == .symbol ? "Train Symbol" : "Road Number")
                                                .font(.system(size: 14, weight: .medium))
                                        }
                                        .foregroundColor(selectedType == type ? .white : .textMuted)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 10)
                                        .frame(maxWidth: .infinity)
                                        .background(selectedType == type ? Color.railBlueMid : Color.bgCard)
                                        .cornerRadius(10)
                                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                                    }
                                }
                            }
                        }

                        // Value field
                        VStack(alignment: .leading, spacing: 8) {
                            Text(selectedType == .symbol ? "Train Symbol" : "Road Number")
                                .font(.system(size: 13)).foregroundColor(.textMuted)
                            TextField(selectedType == .symbol ? "e.g. QCHILA, California Zephyr" : "e.g. 1234, UP 4141",
                                      text: $value)
                                .textFieldStyle(.plain)
                                .foregroundColor(.textPrimary)
                                .autocapitalization(.allCharacters)
                                .focused($valueFocused)
                                .padding(12)
                                .background(Color.bgCard)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                        }

                        // Railroad field
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Railroad (optional)")
                                .font(.system(size: 13)).foregroundColor(.textMuted)
                            TextField("e.g. UP, BNSF", text: $railroad)
                                .textFieldStyle(.plain)
                                .foregroundColor(.textPrimary)
                                .padding(12)
                                .background(Color.bgCard)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                        }

                        // Info
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: "bell.badge")
                                .foregroundColor(.railBlue)
                                .font(.system(size: 13))
                            Text("You'll receive a push notification when a community member reports a sighting matching this entry.")
                                .font(.system(size: 12))
                                .foregroundColor(.textMuted)
                        }
                        .padding(12)
                        .background(Color.bgCard)
                        .cornerRadius(10)

                        // Add button
                        Button {
                            addEntry()
                        } label: {
                            Text("Add to Watchlist")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(isValid ? Color.railBlueMid : Color.railBlueDark)
                                .cornerRadius(12)
                        }
                        .disabled(!isValid)
                    }
                    .padding()
                }
            }
            .navigationTitle("Add to Watchlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { isPresented = false }.foregroundColor(.railBlue)
                }
            }
            .onAppear { valueFocused = true }
        }
        .preferredColorScheme(.dark)
    }

    private func addEntry() {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        let label = railroad.isEmpty ? trimmed : "\(trimmed) (\(railroad))"
        let entry = WatchlistEntry(
            id: UUID().uuidString,
            type: selectedType,
            value: trimmed.uppercased(),
            railroad: railroad,
            label: label,
            addedMs: Date().timeIntervalSince1970 * 1000
        )
        Task {
            await firestore.addWatchlistEntry(entry)
            isPresented = false
        }
    }
}
