import SwiftUI

struct TripLogView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var sessions: [TripLogSession] = []
    @State private var showAddSession = false
    @State private var selectedSession: TripLogSession? = nil

    var totalTrains: Int { sessions.reduce(0) { $0 + $1.trainsSeen.count } }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                if sessions.isEmpty {
                    emptyState
                } else {
                    VStack(spacing: 0) {
                        // Stats strip
                        HStack(spacing: 0) {
                            StatPill(value: "\(sessions.count)", label: "Sessions")
                            Divider().background(Color.border).frame(height: 30)
                            StatPill(value: "\(totalTrains)", label: "Trains Logged")
                            Divider().background(Color.border).frame(height: 30)
                            StatPill(value: topRailroad(), label: "Top Railroad")
                        }
                        .padding(.vertical, 12)
                        .background(Color.bgCard)
                        .overlay(Divider().background(Color.border), alignment: .bottom)

                        List {
                            ForEach(sessions) { session in
                                Button { selectedSession = session } label: {
                                    SessionRow(session: session)
                                }
                                .listRowBackground(Color.bgCard)
                                .listRowSeparatorTint(Color.border)
                            }
                            .onDelete { idx in
                                sessions.remove(atOffsets: idx)
                                PersistenceManager.shared.saveTripLog(sessions)
                            }
                        }
                        .listStyle(.plain)
                        .scrollContentBackground(.hidden)
                    }
                }
            }
            .navigationTitle("Trip Log")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showAddSession = true } label: {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(.railBlue).font(.system(size: 20))
                    }
                }
            }
            .onAppear { sessions = PersistenceManager.shared.loadTripLog() }
        }
        .sheet(isPresented: $showAddSession) {
            AddSessionSheet(vm: vm) { newSession in
                sessions.insert(newSession, at: 0)
                PersistenceManager.shared.saveTripLog(sessions)
            }
        }
        .sheet(item: $selectedSession) { session in
            SessionDetailSheet(session: session)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "book.closed")
                .font(.system(size: 52)).foregroundColor(.railBlueDark)
            Text("No Sessions Yet").font(.system(size: 18, weight: .semibold)).foregroundColor(.textPrimary)
            Text("Tap + to log a spotting session. Record every train you see, where you were, and your notes.")
                .font(.system(size: 14)).foregroundColor(.textMuted).multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Button { showAddSession = true } label: {
                Label("Start a Session", systemImage: "plus")
                    .font(.system(size: 15, weight: .semibold)).foregroundColor(.white)
                    .padding(.horizontal, 24).padding(.vertical, 12)
                    .background(Color.railBlueMid).cornerRadius(10)
            }
        }
    }

    private func topRailroad() -> String {
        var counts: [String: Int] = [:]
        for s in sessions { for t in s.trainsSeen { counts[t.railroad, default: 0] += 1 } }
        return counts.max(by: { $0.value < $1.value })?.key ?? "—"
    }
}

// ── Stats pill ────────────────────────────────────────────────────────────────
struct StatPill: View {
    let value: String; let label: String
    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 17, weight: .bold)).foregroundColor(.railBlue)
            Text(label).font(.system(size: 10)).foregroundColor(.textMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

// ── Session row ───────────────────────────────────────────────────────────────
struct SessionRow: View {
    let session: TripLogSession
    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .center, spacing: 2) {
                Text(dayString(session.date)).font(.system(size: 18, weight: .bold)).foregroundColor(.railBlue)
                Text(monthString(session.date)).font(.system(size: 10)).foregroundColor(.textMuted)
            }
            .frame(width: 40)
            .padding(8).background(Color.bgInput).cornerRadius(8)

            VStack(alignment: .leading, spacing: 4) {
                Text(session.locationName.isEmpty ? "Unknown Location" : session.locationName)
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(.textPrimary)
                if !session.railroad.isEmpty {
                    Text(session.railroad).font(.system(size: 12)).foregroundColor(.textMuted)
                }
                HStack(spacing: 4) {
                    Image(systemName: "train.side.front.car").font(.system(size: 10)).foregroundColor(.railBlue)
                    Text("\(session.trainsSeen.count) train\(session.trainsSeen.count == 1 ? "" : "s") logged")
                        .font(.system(size: 12)).foregroundColor(.textSecondary)
                }
            }
            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 12)).foregroundColor(.textMuted)
        }
        .padding(.vertical, 6)
    }

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "d"; return f
    }()
    private static let monthFormatter: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "MMM"; return f
    }()
    private func dayString(_ d: Date) -> String { Self.dayFormatter.string(from: d) }
    private func monthString(_ d: Date) -> String { Self.monthFormatter.string(from: d) }
}

// ── Add session sheet ─────────────────────────────────────────────────────────
struct AddSessionSheet: View {
    @ObservedObject var vm: RailFanViewModel
    let onSave: (TripLogSession) -> Void
    @Environment(\.dismiss) var dismiss

    @State private var date = Date()
    @State private var locationName = ""
    @State private var railroad = ""
    @State private var notes = ""
    @State private var trains: [TripLogTrain] = []
    @State private var showAddTrain = false

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                List {
                    Section(header: Text("Session Info").foregroundColor(.textMuted)) {
                        DatePicker("Date", selection: $date, displayedComponents: [.date, .hourAndMinute])
                            .colorScheme(.dark).foregroundColor(.textPrimary)

                        HStack {
                            Image(systemName: "mappin").foregroundColor(.railBlue)
                            TextField("Location (e.g. Rochelle, IL)", text: $locationName)
                                .foregroundColor(.textPrimary)
                        }
                        HStack {
                            Image(systemName: "tram").foregroundColor(.railBlue)
                            TextField("Railroad(s) (e.g. BNSF/UP)", text: $railroad)
                                .foregroundColor(.textPrimary)
                        }
                        HStack(alignment: .top) {
                            Image(systemName: "note.text").foregroundColor(.railBlue).padding(.top, 2)
                            TextField("Notes", text: $notes, axis: .vertical)
                                .foregroundColor(.textPrimary).lineLimit(3...6)
                        }
                    }
                    .listRowBackground(Color.bgCard)

                    Section(header: HStack {
                        Text("Trains Spotted").foregroundColor(.textMuted)
                        Spacer()
                        Button { showAddTrain = true } label: {
                            Image(systemName: "plus.circle.fill").foregroundColor(.railBlue)
                        }
                    }) {
                        if trains.isEmpty {
                            Text("Tap + to log a train").font(.system(size: 13)).foregroundColor(.textMuted)
                        } else {
                            ForEach(trains) { t in
                                TrainLogRow(train: t)
                            }
                            .onDelete { idx in trains.remove(atOffsets: idx) }
                        }
                    }
                    .listRowBackground(Color.bgCard)
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("New Session")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }.foregroundColor(.railBlue)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        let session = TripLogSession(
                            id: UUID().uuidString, date: date,
                            locationName: locationName.isEmpty ? (vm.locationName.isEmpty ? "Unknown" : vm.locationName) : locationName,
                            railroad: railroad, notes: notes, trainsSeen: trains)
                        onSave(session); dismiss()
                    }
                    .font(.system(size: 15, weight: .semibold)).foregroundColor(.railBlue)
                }
            }
            .onAppear {
                if locationName.isEmpty { locationName = vm.locationName }
            }
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showAddTrain) {
            AddTrainSheet(vm: vm) { t in trains.append(t) }
        }
    }
}

// ── Add train to session ──────────────────────────────────────────────────────
struct AddTrainSheet: View {
    @ObservedObject var vm: RailFanViewModel
    let onAdd: (TripLogTrain) -> Void
    @Environment(\.dismiss) var dismiss

    @State private var symbol = ""
    @State private var railroad = ""
    @State private var type = ""
    @State private var speedMph = 0
    @State private var notes = ""

    private let types = ["Intermodal","Coal","Grain","Autorack","Mixed Freight","Manifest","Passenger","Empty"]
    private let railroads = ["BNSF","UP","CSX","NS","CN","CP","KCS","Amtrak","Metra","MBTA","SEPTA","LIRR","Metro-North","Caltrain","Sound Transit","Other"]

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                List {
                    Section(header: Text("Train Details").foregroundColor(.textMuted)) {
                        HStack {
                            Image(systemName: "number").foregroundColor(.railBlue)
                            TextField("Symbol (e.g. QCHILA-01)", text: $symbol)
                                .textInputAutocapitalization(.characters)
                                .foregroundColor(.textPrimary)
                        }
                        Picker("Railroad", selection: $railroad) {
                            Text("Select…").tag("")
                            ForEach(railroads, id: \.self) { Text($0).tag($0) }
                        }
                        .foregroundColor(.textPrimary)
                        Picker("Train Type", selection: $type) {
                            Text("Select…").tag("")
                            ForEach(types, id: \.self) { Text($0).tag($0) }
                        }
                        .foregroundColor(.textPrimary)
                        HStack {
                            Image(systemName: "speedometer").foregroundColor(.railBlue)
                            Text("Speed").foregroundColor(.textPrimary)
                            Spacer()
                            Text("\(speedMph) mph").foregroundColor(.railBlue).font(.system(size: 13, weight: .semibold))
                        }
                        Slider(value: Binding(get: { Double(speedMph) }, set: { speedMph = Int($0) }),
                               in: 0...110, step: 5).tint(.railBlue)
                        HStack {
                            Image(systemName: "note.text").foregroundColor(.railBlue)
                            TextField("Notes (optional)", text: $notes).foregroundColor(.textPrimary)
                        }
                    }
                    .listRowBackground(Color.bgCard)

                    // Quick-fill from live trains
                    if !vm.filteredTrains.isEmpty {
                        Section(header: Text("Quick-Fill from Live Trains").foregroundColor(.textMuted)) {
                            ForEach(vm.filteredTrains.prefix(5)) { t in
                                Button {
                                    symbol = t.symbol
                                    railroad = t.railroad.displayName
                                    speedMph = Int(t.speedMph)
                                } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(t.symbol).font(.system(size: 13, weight: .semibold)).foregroundColor(.textPrimary)
                                            Text(t.railroad.displayName).font(.system(size: 11)).foregroundColor(.textMuted)
                                        }
                                        Spacer()
                                        Text("\(t.speedMph) mph").font(.system(size: 12)).foregroundColor(.textMuted)
                                    }
                                }
                            }
                        }
                        .listRowBackground(Color.bgCard)
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Log Train")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }.foregroundColor(.railBlue)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Add") {
                        let t = TripLogTrain(id: UUID().uuidString, symbol: symbol,
                                            railroad: railroad, type: type,
                                            speedMph: speedMph, notes: notes)
                        onAdd(t); dismiss()
                    }
                    .font(.system(size: 15, weight: .semibold)).foregroundColor(.railBlue)
                    .disabled(railroad.isEmpty)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

// ── Session detail sheet ──────────────────────────────────────────────────────
struct SessionDetailSheet: View {
    let session: TripLogSession
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        // Header
                        VStack(alignment: .leading, spacing: 6) {
                            Text(session.locationName)
                                .font(.system(size: 20, weight: .bold)).foregroundColor(.textPrimary)
                            Label(session.date.formatted(date: .long, time: .shortened),
                                  systemImage: "calendar")
                                .font(.system(size: 13)).foregroundColor(.textMuted)
                            if !session.railroad.isEmpty {
                                Label(session.railroad, systemImage: "tram")
                                    .font(.system(size: 13)).foregroundColor(.textMuted)
                            }
                        }
                        .padding(.horizontal)

                        // Notes
                        if !session.notes.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Label("Notes", systemImage: "note.text").font(.system(size: 13)).foregroundColor(.textMuted)
                                Text(session.notes).font(.system(size: 14)).foregroundColor(.textSecondary).lineSpacing(4)
                            }
                            .padding(14).cardStyle().padding(.horizontal)
                        }

                        // Trains spotted
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Image(systemName: "train.side.front.car").foregroundColor(.railBlue)
                                Text("\(session.trainsSeen.count) Train\(session.trainsSeen.count == 1 ? "" : "s") Spotted")
                                    .font(.system(size: 15, weight: .semibold)).foregroundColor(.textPrimary)
                            }
                            if session.trainsSeen.isEmpty {
                                Text("No trains logged for this session.")
                                    .font(.system(size: 13)).foregroundColor(.textMuted)
                            } else {
                                ForEach(session.trainsSeen) { t in
                                    TrainLogRow(train: t)
                                        .padding(10).background(Color.bgInput).cornerRadius(10)
                                }
                            }
                        }
                        .padding(14).cardStyle().padding(.horizontal)

                        Spacer(minLength: 40)
                    }
                    .padding(.top)
                }
            }
            .background(Color.bgPrimary)
            .navigationTitle("Session")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

// ── Train log row ─────────────────────────────────────────────────────────────
struct TrainLogRow: View {
    let train: TripLogTrain
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "train.side.front.car")
                .foregroundColor(.railBlueDark).font(.system(size: 14))
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if !train.symbol.isEmpty {
                        Text(train.symbol)
                            .font(.system(size: 13, weight: .bold, design: .monospaced))
                            .foregroundColor(.textPrimary)
                    }
                    if !train.railroad.isEmpty {
                        Text(train.railroad).font(.system(size: 12)).foregroundColor(.textMuted)
                    }
                }
                HStack(spacing: 8) {
                    if !train.type.isEmpty {
                        Text(train.type).font(.system(size: 11)).foregroundColor(.textSecondary)
                    }
                    if train.speedMph > 0 {
                        Text("· \(train.speedMph) mph").font(.system(size: 11)).foregroundColor(.textMuted)
                    }
                }
            }
            Spacer()
        }
    }
}
