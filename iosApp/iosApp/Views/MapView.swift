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
    @StateObject private var firestore = FirestoreManager.shared
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 41.8781, longitude: -87.6298), // Chicago default
        span:   MKCoordinateSpan(latitudeDelta: 5.0, longitudeDelta: 5.0)
    )
    @State private var selectedTrain: TrainLocation? = nil
    @State private var selectedSighting: FirestoreSighting? = nil
    @State private var showSightings = true
    @State private var centeredOnUser = false

    // Railroad filter chips
    private let railroads: [(name: String, label: String)] = [
        ("AMTRAK", "Amtrak"),  ("BNSF", "BNSF"), ("UP", "UP"),
        ("CSX", "CSX"),        ("NS", "NS"),      ("CN", "CN"),
        ("CP", "CPKC"),        ("KCS", "KCS"),    ("OTHER", "Commuter")
    ]

    // Combined annotation model so both train pins and sighting pins go in one Map
    private enum MapPin: Identifiable {
        case train(TrainLocation)
        case sighting(FirestoreSighting)
        var id: String {
            switch self {
            case .train(let t):    return "t-\(t.id)"
            case .sighting(let s):
                let stableId = s.id ?? "ts\(Int(s.timestampMs))"
                return "s-\(stableId)"
            }
        }
        var coordinate: CLLocationCoordinate2D {
            switch self {
            case .train(let t):    return CLLocationCoordinate2D(latitude: t.latitude,    longitude: t.longitude)
            case .sighting(let s): return CLLocationCoordinate2D(latitude: s.latitude,    longitude: s.longitude)
            }
        }
    }

    private var allPins: [MapPin] {
        var pins = vm.filteredTrains.map { MapPin.train($0) }
        if showSightings {
            pins += firestore.sightings.map { MapPin.sighting($0) }
        }
        return pins
    }

    var body: some View {
        ZStack(alignment: .top) {
            // Map — trains + sighting pins
            Map(coordinateRegion: $region,
                showsUserLocation: true,
                annotationItems: allPins) { pin in
                MapAnnotation(coordinate: pin.coordinate) {
                    switch pin {
                    case .train(let train):
                        TrainAnnotationView(train: train)
                            .onTapGesture {
                                selectedSighting = nil
                                selectedTrain = train
                            }
                    case .sighting(let sighting):
                        Button {
                            selectedTrain = nil
                            selectedSighting = sighting
                        } label: {
                            VStack(spacing: 2) {
                                Image(systemName: "eye.circle.fill")
                                    .font(.system(size: 22))
                                    .foregroundColor(.cyan)
                                    .shadow(color: .black.opacity(0.5), radius: 2)
                                Text(sighting.railroad)
                                    .font(.system(size: 8, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 3).padding(.vertical, 1)
                                    .background(Color.cyan.opacity(0.85))
                                    .cornerRadius(3)
                            }
                        }
                    }
                }
            }
            .ignoresSafeArea()

            // Filter chips
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        FilterChipView(label: "All", selected: vm.selectedRailroad == nil) {
                            vm.setRailroadFilter(nil)
                        }
                        ForEach(railroads, id: \.name) { rr in
                            FilterChipView(label: rr.label,
                                           selected: vm.selectedRailroad == rr.name) {
                                vm.setRailroadFilter(
                                    vm.selectedRailroad == rr.name ? nil : rr.name)
                            }
                        }
                        // Sightings toggle
                        FilterChipView(label: "👁 Sightings", selected: showSightings) {
                            showSightings.toggle()
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                }
                .background(Color.bgPrimary.opacity(0.92))
            }

            // Location + refresh buttons
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    VStack(spacing: 10) {
                        Button { centerOnUser() } label: {
                            Image(systemName: "location.fill")
                                .font(.system(size: 18))
                                .foregroundColor(.railBlue)
                                .frame(width: 44, height: 44)
                                .background(Color.bgCard)
                                .clipShape(Circle())
                                .shadow(color: .black.opacity(0.4), radius: 4)
                        }
                        Button { vm.refreshTrains() } label: {
                            Image(systemName: vm.isLoadingTrains ? "arrow.triangle.2.circlepath" : "arrow.clockwise")
                                .font(.system(size: 18))
                                .foregroundColor(.railBlue)
                                .frame(width: 44, height: 44)
                                .background(Color.bgCard)
                                .clipShape(Circle())
                                .shadow(color: .black.opacity(0.4), radius: 4)
                                .rotationEffect(.degrees(vm.isLoadingTrains ? 360 : 0))
                                .animation(vm.isLoadingTrains
                                    ? .linear(duration: 1).repeatForever(autoreverses: false)
                                    : .default, value: vm.isLoadingTrains)
                        }
                    }
                    .padding(.trailing, 16)
                    .padding(.bottom, 24)
                }
            }

            // Train count badge
            VStack {
                Spacer()
                HStack {
                    Text("\(vm.filteredTrains.count) trains")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.textSecondary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.bgCard.opacity(0.9))
                        .cornerRadius(8)
                        .padding(.leading, 12)
                        .padding(.bottom, 28)
                    Spacer()
                }
            }
        }
        .onAppear {
            // Start sighting listener so map pins show without visiting Community tab
            let lat = vm.userLocation?.latitude  ?? 41.8781
            let lon = vm.userLocation?.longitude ?? -87.6298
            firestore.startListening(lat: lat, lon: lon, radiusMiles: 200)
        }
        .onChange(of: vm.userLocation) { loc in
            guard let loc, !centeredOnUser else { return }
            centeredOnUser = true
            region = MKCoordinateRegion(
                center: loc,
                span: MKCoordinateSpan(latitudeDelta: 3.0, longitudeDelta: 3.0))
            // Restart listener with real GPS coordinates
            firestore.startListening(lat: loc.latitude, lon: loc.longitude, radiusMiles: 200)
        }
        .sheet(item: $selectedTrain) { train in
            TrainDetailSheet(train: train)
        }
        .sheet(item: $selectedSighting) { sighting in
            SightingDetailSheet(sighting: sighting, vm: vm)
        }
    }

    private func centerOnUser() {
        guard let loc = vm.userLocation else { return }
        withAnimation {
            region = MKCoordinateRegion(
                center: loc,
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
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 4)
                .padding(.vertical, 1)
                .background(Color.bgCard.opacity(0.85))
                .cornerRadius(4)
        }
    }

    private var shortLabel: String {
        train.symbol.components(separatedBy: " ").prefix(2).joined(separator: " ")
    }

    private func colorForRailroad(_ name: String) -> Color {
        switch name {
        case "BNSF":   return Color(red: 1.0, green: 0.4, blue: 0.0)
        case "UP":     return Color(red: 1.0, green: 0.8, blue: 0.0)
        case "CSX":    return Color(red: 0.0, green: 0.34, blue: 0.66)
        case "NS":     return Color(red: 0.6, green: 0.6, blue: 0.6)
        case "CN":     return Color(red: 0.8, green: 0.0, blue: 0.0)
        case "CP":     return Color(red: 0.55, green: 0.0, blue: 0.0)
        case "AMTRAK": return Color(red: 0.12, green: 0.23, blue: 0.54)
        default:       return Color.railBlue
        }
    }
}

// ── Filter chip ───────────────────────────────────────────────────────────────
struct FilterChipView: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(selected ? .white : .textSecondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
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
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Header
                    VStack(alignment: .leading, spacing: 4) {
                        Text(train.symbol)
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundColor(.textPrimary)
                        Text(train.railroad.displayName)
                            .font(.system(size: 14))
                            .foregroundColor(.textMuted)
                    }
                    .padding(.horizontal)

                    // Stat cards
                    HStack(spacing: 10) {
                        StatCard(value: "\(train.speedMph) mph", label: "Speed")
                        StatCard(value: "\(train.headingDegrees)°", label: "Heading")
                        StatCard(value: statusLabel, label: "Status")
                    }
                    .padding(.horizontal)

                    // Details
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

                    if !train.consist.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Consist").font(.system(size: 13)).foregroundColor(.textMuted)
                            Text(train.consist.joined(separator: " · "))
                                .font(.system(size: 14))
                                .foregroundColor(.textSecondary)
                        }
                        .padding(.horizontal)
                    }
                }
                .padding(.vertical)
            }
            .background(Color.bgPrimary)
            .navigationTitle("Train Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var statusLabel: String {
        switch train.status.name {
        case "ON_TIME":  return "On Time"
        case "DELAYED":  return "Late"
        case "STOPPED":  return "Stopped"
        default:         return "Unknown"
        }
    }
}

struct StatCard: View {
    let value: String; let label: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.system(size: 15, weight: .semibold)).foregroundColor(.railBlue)
            Text(label).font(.system(size: 11)).foregroundColor(.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color.bgInput)
        .cornerRadius(10)
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
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .overlay(Divider().background(Color.border), alignment: .bottom)
    }
}
