import SwiftUI
import MapKit
import shared

// ── Swift conformances for KMP types ─────────────────────────────────────────
extension TrainLocation: @retroactive Identifiable {}

extension CLLocationCoordinate2D: @retroactive Equatable {
    public static func == (lhs: CLLocationCoordinate2D, rhs: CLLocationCoordinate2D) -> Bool {
        lhs.latitude == rhs.latitude && lhs.longitude == rhs.longitude
    }
}

struct MapView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 41.8781, longitude: -87.6298),
        span:   MKCoordinateSpan(latitudeDelta: 5.0, longitudeDelta: 5.0)
    )
    @State private var selectedTrain: TrainLocation? = nil
    @State private var centeredOnUser = false
    @State private var showStationBoard = false

    private let railroads: [(name: String, label: String)] = [
        ("AMTRAK", "Amtrak"),  ("BNSF", "BNSF"), ("UP", "UP"),
        ("CSX", "CSX"),        ("NS", "NS"),      ("CN", "CN"),
        ("CP", "CPKC"),        ("KCS", "KCS"),    ("OTHER", "Commuter")
    ]

    var body: some View {
        ZStack(alignment: .top) {
            // Map with train + community pins
            Map(coordinateRegion: $region,
                showsUserLocation: true,
                annotationItems: vm.filteredTrains) { train in
                MapAnnotation(coordinate: CLLocationCoordinate2D(
                    latitude:  train.latitude,
                    longitude: train.longitude)) {
                    TrainAnnotationView(train: train)
                        .onTapGesture { selectedTrain = train }
                }
            }
            .ignoresSafeArea()

            // Filter chips + Station Board
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        FilterChipView(label: "All", selected: vm.selectedRailroad == nil) {
                            vm.setRailroadFilter(nil)
                        }
                        ForEach(railroads, id: \.name) { rr in
                            FilterChipView(label: rr.label,
                                           selected: vm.selectedRailroad == rr.name) {
                                vm.setRailroadFilter(vm.selectedRailroad == rr.name ? nil : rr.name)
                            }
                        }
                        FilterChipView(label: "Station Board", selected: false) {
                            showStationBoard = true
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                }
                .background(Color.bgPrimary.opacity(0.92))
            }

            // FABs
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    VStack(spacing: 10) {
                        Button { centerOnUser() } label: {
                            Image(systemName: "location.fill")
                                .font(.system(size: 18)).foregroundColor(.railBlue)
                                .frame(width: 44, height: 44).background(Color.bgCard)
                                .clipShape(Circle()).shadow(color: .black.opacity(0.4), radius: 4)
                        }
                        Button { vm.refreshTrains() } label: {
                            Image(systemName: vm.isLoadingTrains ? "arrow.triangle.2.circlepath" : "arrow.clockwise")
                                .font(.system(size: 18)).foregroundColor(.railBlue)
                                .frame(width: 44, height: 44).background(Color.bgCard)
                                .clipShape(Circle()).shadow(color: .black.opacity(0.4), radius: 4)
                        }
                    }
                    .padding(.trailing, 16).padding(.bottom, 24)
                }
            }

            // Train count badge
            VStack {
                Spacer()
                HStack {
                    Text("\(vm.filteredTrains.count) trains")
                        .font(.system(size: 12, weight: .medium)).foregroundColor(.textSecondary)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(Color.bgCard.opacity(0.9)).cornerRadius(8)
                        .padding(.leading, 12).padding(.bottom, 28)
                    Spacer()
                }
            }
        }
        .onChange(of: vm.userLocation) { loc in
            guard let loc, !centeredOnUser else { return }
            centeredOnUser = true
            region = MKCoordinateRegion(center: loc,
                span: MKCoordinateSpan(latitudeDelta: 3.0, longitudeDelta: 3.0))
        }
        .sheet(item: $selectedTrain) { train in
            TrainDetailSheet(train: train, vm: vm)
        }
        .sheet(isPresented: $showStationBoard) {
            StationBoardSheet(vm: vm)
        }
    }

    private func centerOnUser() {
        guard let loc = vm.userLocation else { return }
        withAnimation {
            region = MKCoordinateRegion(center: loc,
                span: MKCoordinateSpan(latitudeDelta: 1.5, longitudeDelta: 1.5))
        }
    }
}

// ── Train annotation pin ──────────────────────────────────────────────────────
struct TrainAnnotationView: View {
    let train: TrainLocation
    var body: some View {
        VStack(spacing: 2) {
            Image(systemName: "arrow.up.circle.fill")
                .font(.system(size: 24))
                .foregroundColor(colorForRailroad(train.railroad.name))
                .rotationEffect(.degrees(Double(train.headingDegrees)))
                .shadow(color: .black.opacity(0.5), radius: 2)
            Text(shortLabel)
                .font(.system(size: 9, weight: .bold)).foregroundColor(.white)
                .padding(.horizontal, 4).padding(.vertical, 1)
                .background(Color.bgCard.opacity(0.85)).cornerRadius(4)
        }
    }
    private var shortLabel: String { train.symbol.components(separatedBy: " ").prefix(2).joined(separator: " ") }
    private func colorForRailroad(_ name: String) -> Color {
        switch name {
        case "BNSF": return Color(red:1.0,green:0.4,blue:0.0)
        case "UP":   return Color(red:1.0,green:0.8,blue:0.0)
        case "CSX":  return Color(red:0.0,green:0.34,blue:0.66)
        case "NS":   return Color(red:0.6,green:0.6,blue:0.6)
        case "CN":   return Color(red:0.8,green:0.0,blue:0.0)
        case "CP":   return Color(red:0.55,green:0.0,blue:0.0)
        case "AMTRAK": return Color(red:0.12,green:0.23,blue:0.54)
        default:     return Color.railBlue
        }
    }
}

// ── Filter chip ───────────────────────────────────────────────────────────────
struct FilterChipView: View {
    let label: String; let selected: Bool; let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.system(size: 12, weight: .medium))
                .foregroundColor(selected ? .white : .textSecondary)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(selected ? Color.railBlueMid : Color.bgCard)
                .cornerRadius(16)
                .overlay(RoundedRectangle(cornerRadius: 16)
                    .stroke(selected ? Color.railBlue : Color.border, lineWidth: 0.5))
        }
    }
}

// ── Train detail sheet ────────────────────────────────────────────────────────
struct TrainDetailSheet: View {
    let train: TrainLocation
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss

    @State private var showTimetable = false
    @State private var showBoardingDialog = false
    @State private var boardingInput = ""
    @State private var showShareSheet = false
    @State private var shareText = ""

    var speeds: [Int] { vm.speedHistory[train.id] ?? [] }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {

                    // Header
                    VStack(alignment: .leading, spacing: 4) {
                        Text(train.symbol)
                            .font(.system(size: 22, weight: .semibold)).foregroundColor(.textPrimary)
                        Text(train.railroad.displayName)
                            .font(.system(size: 14)).foregroundColor(.textMuted)
                    }
                    .padding(.horizontal)

                    // Stat cards
                    HStack(spacing: 10) {
                        StatCard(value: "\(train.speedMph) mph", label: "Speed")
                        StatCard(value: "\(train.headingDegrees)°", label: "Heading")
                        StatCard(value: statusLabel, label: "Status")
                    }
                    .padding(.horizontal)

                    // Speed sparkline
                    if speeds.count >= 2 {
                        SpeedSparklineView(speeds: speeds, currentMph: Int(train.speedMph))
                            .padding(.horizontal)
                    }

                    // Route details
                    VStack(spacing: 0) {
                        DetailRow(label: "Origin",      value: train.origin)
                        DetailRow(label: "Destination", value: train.destination)
                        if let eta = train.etaMinutes {
                            DetailRow(label: "ETA", value: "\(eta.intValue) min")
                        }
                        if let mp = train.milepost {
                            DetailRow(label: "Milepost", value: String(format: "%.1f", mp.doubleValue))
                        }
                        if let sub = train.subdivision, !sub.isEmpty {
                            DetailRow(label: "Subdivision", value: sub)
                        }
                    }
                    .cardStyle()
                    .padding(.horizontal)

                    // Consist
                    if !train.consist.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Consist").font(.system(size: 13)).foregroundColor(.textMuted)
                            Text(train.consist.joined(separator: " · "))
                                .font(.system(size: 14)).foregroundColor(.textSecondary)
                        }
                        .padding(.horizontal)
                    }

                    // ── Action buttons ────────────────────────────────────────
                    VStack(spacing: 10) {

                        // Ride this Train / active trip
                        if let active = vm.activeTrip, active.trainId == train.id {
                            HStack {
                                Circle().fill(Color.green).frame(width: 8, height: 8)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Riding").font(.system(size: 11)).foregroundColor(.green)
                                    Text("\(String(format:"%.1f", active.distanceMiles)) mi · \(active.durationMinutes) min")
                                        .font(.system(size: 13, weight: .semibold)).foregroundColor(.textPrimary)
                                }
                                Spacer()
                                Button { vm.endTrip() } label: {
                                    HStack(spacing: 4) {
                                        Image(systemName: "stop.fill").font(.system(size: 12))
                                        Text("End Trip").font(.system(size: 13, weight: .semibold))
                                    }
                                    .foregroundColor(.green)
                                    .padding(.horizontal, 12).padding(.vertical, 6)
                                    .background(Color.green.opacity(0.15)).cornerRadius(8)
                                }
                            }
                            .padding(12)
                            .background(Color(red:0.06,green:0.17,blue:0.09))
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.green.opacity(0.4), lineWidth: 1))
                        } else if vm.activeTrip == nil {
                            Button { showBoardingDialog = true } label: {
                                HStack {
                                    Image(systemName: "train.side.front.car")
                                    Text("Ride this Train")
                                        .font(.system(size: 14, weight: .semibold))
                                }
                                .foregroundColor(.green)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(Color.green.opacity(0.12)).cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.green.opacity(0.4), lineWidth: 0.5))
                            }
                            .buttonStyle(.plain)
                        }

                        // Timetable (Amtrak only)
                        if train.railroad.name == "AMTRAK" {
                            Button {
                                vm.loadTimetable(for: train)
                                showTimetable = true
                            } label: {
                                HStack {
                                    Image(systemName: "calendar.badge.clock")
                                    Text("Timetable")
                                        .font(.system(size: 14, weight: .semibold))
                                }
                                .foregroundColor(.railBlue)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(Color.railBlue.opacity(0.1)).cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.railBlue.opacity(0.4), lineWidth: 0.5))
                            }
                            .buttonStyle(.plain)
                        }

                        // Share sighting
                        Button {
                            shareText = buildShareText()
                            showShareSheet = true
                        } label: {
                            HStack {
                                Image(systemName: "square.and.arrow.up")
                                Text("Share Sighting")
                                    .font(.system(size: 14, weight: .semibold))
                            }
                            .foregroundColor(.textSecondary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color.bgInput).cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.borderLight, lineWidth: 0.5))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 32)
                }
                .padding(.vertical)
            }
            .background(Color.bgPrimary)
            .navigationTitle("Train Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showTimetable) { TimetableSheet(vm: vm, train: train) }
        .sheet(isPresented: $showShareSheet) {
            ShareSheet(items: [shareText])
        }
        .alert("Start Trip", isPresented: $showBoardingDialog) {
            TextField("Boarding station (optional)", text: $boardingInput)
            Button("Start") {
                vm.startTrip(train: train, boardingStation: boardingInput.nilIfEmpty)
                boardingInput = ""
                dismiss()
            }
            Button("Cancel", role: .cancel) { boardingInput = "" }
        } message: {
            Text("Riding \(train.symbol)")
        }
    }

    private var statusLabel: String {
        switch train.status.name {
        case "ON_TIME": return "On Time"
        case "DELAYED": return "Late"
        case "STOPPED": return "Stopped"
        default:        return "Unknown"
        }
    }

    private func buildShareText() -> String {
        var lines: [String] = ["🚂 \(train.symbol)"]
        lines.append("\(train.railroad.displayName) · \(train.speedMph) mph · \(statusLabel)")
        if !train.origin.isEmpty && !train.destination.isEmpty {
            lines.append("\(train.origin) → \(train.destination)")
        }
        if let sub = train.subdivision, !sub.isEmpty { lines.append("Subdivision: \(sub)") }
        if let mp = train.milepost { lines.append("MP \(String(format:"%.1f", mp.doubleValue))") }
        lines.append("via Railfan Copilot")
        return lines.joined(separator: "\n")
    }
}

// ── Speed sparkline ───────────────────────────────────────────────────────────
struct SpeedSparklineView: View {
    let speeds: [Int]
    let currentMph: Int

    private var lineColor: Color {
        currentMph > 70 ? .green : currentMph > 30 ? Color.railBlueMid : Color.railBlue
    }

    var body: some View {
        HStack(spacing: 8) {
            GeometryReader { geo in
                let w = geo.size.width
                let h = geo.size.height
                let maxV = CGFloat(speeds.max() ?? 10).magnitude > 0
                    ? CGFloat(speeds.max()!) : 10
                let pts: [CGPoint] = speeds.enumerated().map { i, spd in
                    let x = speeds.count > 1 ? CGFloat(i) / CGFloat(speeds.count - 1) * w : 0
                    let y = h - (CGFloat(spd) / maxV) * h * 0.9
                    return CGPoint(x: x, y: y)
                }
                ZStack {
                    // Fill area
                    Path { p in
                        guard !pts.isEmpty else { return }
                        p.move(to: CGPoint(x: pts[0].x, y: h))
                        pts.forEach { p.addLine(to: $0) }
                        p.addLine(to: CGPoint(x: pts.last!.x, y: h))
                        p.closeSubpath()
                    }
                    .fill(lineColor.opacity(0.12))

                    // Line
                    Path { p in
                        guard pts.count >= 2 else { return }
                        p.move(to: pts[0])
                        pts.dropFirst().forEach { p.addLine(to: $0) }
                    }
                    .stroke(lineColor.opacity(0.7), style: StrokeStyle(lineWidth: 2, lineCap: .round))

                    // Current dot
                    if let last = pts.last {
                        Circle().fill(lineColor).frame(width: 6, height: 6)
                            .position(last)
                    }
                }
            }
            .frame(height: 28)

            Text("\(currentMph) mph")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(lineColor)
        }
    }
}

// ── Timetable sheet ───────────────────────────────────────────────────────────
struct TimetableSheet: View {
    @ObservedObject var vm: RailFanViewModel
    let train: TrainLocation
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                if vm.isTimetableLoading {
                    ProgressView("Loading timetable…").tint(.railBlue)
                } else if let err = vm.timetableError {
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle").font(.system(size: 32))
                            .foregroundColor(.yellow)
                        Text(err).foregroundColor(.textSecondary).multilineTextAlignment(.center)
                    }.padding()
                } else {
                    ScrollView {
                        VStack(spacing: 0) {
                            ForEach(vm.timetableStops) { stop in
                                TimetableStopRow(stop: stop)
                            }
                        }
                        .padding(.vertical)
                    }
                }
            }
            .navigationTitle("Timetable — \(train.symbol)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss(); vm.clearTimetable() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

struct TimetableStopRow: View {
    let stop: TimetableStopSwift
    var body: some View {
        HStack(spacing: 12) {
            // Timeline dot
            VStack(spacing: 0) {
                Rectangle().fill(Color.border).frame(width: 1)
                Circle()
                    .fill(stop.hasArrived ? Color.railBlue : Color.borderLight)
                    .frame(width: 10, height: 10)
                Rectangle().fill(Color.border).frame(width: 1)
            }
            .frame(width: 12)

            VStack(alignment: .leading, spacing: 4) {
                Text(stop.code)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(stop.hasArrived ? .textPrimary : .textMuted)
                if let dep = stop.actualDeparture ?? stop.scheduledDeparture {
                    HStack(spacing: 6) {
                        Text("DEP \(dep)").font(.system(size: 12)).foregroundColor(.railBlue)
                        if let status = stop.departureStatus {
                            Text(status).font(.system(size: 11))
                                .foregroundColor(status.lowercased().contains("late") ? .yellow : .green)
                        }
                    }
                }
                if let arr = stop.actualArrival ?? stop.scheduledArrival {
                    Text("ARR \(arr)").font(.system(size: 11)).foregroundColor(.textMuted)
                }
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

// ── Station Board sheet ───────────────────────────────────────────────────────
struct StationBoardSheet: View {
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss
    @State private var codeInput = ""

    private let common = ["NYP", "CHI", "LAX", "WAS", "BOS", "SEA", "NOL", "SAS", "EMY"]

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 14) {

                        // Input row
                        HStack(spacing: 8) {
                            TextField("Station code (e.g. CHI)", text: $codeInput)
                                .textInputAutocapitalization(.characters)
                                .autocorrectionDisabled()
                                .padding(10).background(Color.bgInput).cornerRadius(8)
                                .foregroundColor(.textPrimary)
                                .submitLabel(.search)
                                .onSubmit { if !codeInput.isEmpty { vm.loadStationDepartures(code: codeInput) } }
                            Button {
                                if !codeInput.isEmpty { vm.loadStationDepartures(code: codeInput) }
                            } label: {
                                if vm.isStationLoading {
                                    ProgressView().tint(.white).frame(width: 44, height: 36)
                                } else {
                                    Image(systemName: "magnifyingglass")
                                        .foregroundColor(.white).frame(width: 44, height: 36)
                                }
                            }
                            .background(codeInput.isEmpty ? Color.railBlueDark : Color.railBlueMid)
                            .cornerRadius(8)
                            .disabled(codeInput.isEmpty || vm.isStationLoading)
                        }

                        // Quick-pick chips
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(common, id: \.self) { code in
                                    Button { codeInput = code; vm.loadStationDepartures(code: code) } label: {
                                        Text(code).font(.system(size: 12, weight: .semibold))
                                            .foregroundColor(.railBlue)
                                            .padding(.horizontal, 10).padding(.vertical, 5)
                                            .background(Color.bgCard).cornerRadius(8)
                                            .overlay(RoundedRectangle(cornerRadius: 8)
                                                .stroke(Color.borderLight, lineWidth: 0.5))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }

                        if let err = vm.stationError {
                            HStack(spacing: 8) {
                                Image(systemName: "info.circle").foregroundColor(.yellow)
                                Text(err).font(.system(size: 13)).foregroundColor(.textSecondary)
                            }
                            .padding(10).background(Color.bgCard).cornerRadius(8)
                        }

                        ForEach(vm.stationDepartures) { dep in
                            let stop = dep.stops.first { $0.code == codeInput.uppercased() }
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(dep.symbol)
                                        .font(.system(size: 14, weight: .semibold)).foregroundColor(.textPrimary)
                                    Text(dep.routeName)
                                        .font(.system(size: 12)).foregroundColor(.textMuted)
                                }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 2) {
                                    if let dep2 = stop?.actualDeparture ?? stop?.scheduledDeparture {
                                        Text("DEP \(dep2)").font(.system(size: 12, weight: .semibold))
                                            .foregroundColor(.railBlue)
                                    }
                                    if let arr = stop?.actualArrival ?? stop?.scheduledArrival {
                                        Text("ARR \(arr)").font(.system(size: 11)).foregroundColor(.textMuted)
                                    }
                                    if let status = stop?.departureStatus {
                                        Text(status).font(.system(size: 10))
                                            .foregroundColor(status.lowercased().contains("late") ? .yellow : .green)
                                    }
                                }
                            }
                            .padding(12).background(Color.bgCard).cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.borderLight, lineWidth: 0.5))
                        }
                        Spacer(minLength: 40)
                    }
                    .padding()
                }
            }
            .navigationTitle("Station Board")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss(); vm.clearStationDepartures() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

// ── Share sheet wrapper ───────────────────────────────────────────────────────
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

// ── Helpers ───────────────────────────────────────────────────────────────────
struct StatCard: View {
    let value: String; let label: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.system(size: 15, weight: .semibold)).foregroundColor(.railBlue)
            Text(label).font(.system(size: 11)).foregroundColor(.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10).background(Color.bgInput).cornerRadius(10)
    }
}

struct DetailRow: View {
    let label: String; let value: String
    var body: some View {
        HStack {
            Text(label).font(.system(size: 13)).foregroundColor(.textMuted)
            Spacer()
            Text(value).font(.system(size: 13)).foregroundColor(.textSecondary)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .overlay(Divider().background(Color.border), alignment: .bottom)
    }
}

private extension String {
    var nilIfEmpty: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self }
}
