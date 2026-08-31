import SwiftUI
import MapKit
import CoreLocation
import shared

extension TrainLocation: @retroactive Identifiable {}

extension CLLocationCoordinate2D: @retroactive Equatable {
    public static func == (lhs: CLLocationCoordinate2D, rhs: CLLocationCoordinate2D) -> Bool {
        lhs.latitude == rhs.latitude && lhs.longitude == rhs.longitude
    }
}

// ── Railroad color helper (shared) ────────────────────────────────────────────
func rrColor(_ name: String) -> Color {
    switch name {
    case "BNSF":   return Color(red: 1.0,  green: 0.4,  blue: 0.0)
    case "UP":     return Color(red: 1.0,  green: 0.8,  blue: 0.0)
    case "CSX":    return Color(red: 0.0,  green: 0.34, blue: 0.66)
    case "NS":     return Color(red: 0.6,  green: 0.6,  blue: 0.6)
    case "CN":     return Color(red: 0.8,  green: 0.0,  blue: 0.0)
    case "CP":     return Color(red: 0.55, green: 0.0,  blue: 0.0)
    case "AMTRAK": return Color(red: 0.12, green: 0.23, blue: 0.54)
    default:       return Color.railBlue
    }
}

// ── MapView ───────────────────────────────────────────────────────────────────
struct MapView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 41.8781, longitude: -87.6298),
        span: MKCoordinateSpan(latitudeDelta: 5.0, longitudeDelta: 5.0))
    @State private var selectedTrain: TrainLocation? = nil
    @State private var centeredOnUser = false
    @State private var showRailOverlay: Bool = false
    @State private var showSightings: Bool = false
    @State private var showSatellite: Bool = false
    @State private var showYards: Bool = false
    @State private var showStationBoard: Bool = false
    @State private var selectedSighting: FirestoreSighting? = nil
    @State private var selectedYard: ClassificationYard? = nil
    @State private var searchQuery = ""
    @State private var showSearchResults = false
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var searchPin: CLLocationCoordinate2D? = nil

    private let railroads: [(name: String, label: String)] = [
        ("AMTRAK", "Amtrak"), ("BNSF", "BNSF"), ("UP", "UP"),
        ("CSX", "CSX"), ("NS", "NS"), ("CN", "CN"),
        ("CP", "CPKC"), ("KCS", "KCS"), ("OTHER", "Commuter")
    ]

    private var isStale: Bool {
        guard let d = vm.lastRefreshDate else { return false }
        return Date().timeIntervalSince(d) > 90
    }

    var body: some View {
        ZStack(alignment: .top) {
            // Native map with trails + tile overlay + sightings + yards
            RailFanMapRepresentable(
                region: $region,
                trains: vm.filteredTrains,
                trainTrails: vm.trainTrails,
                showRailOverlay: showRailOverlay,
                showSatellite: showSatellite,
                sightings: showSightings ? FirestoreManager.shared.sightings : [],
                yards: showYards ? classificationYards : [],
                searchPin: searchPin,
                onTrainTapped: { selectedTrain = $0 },
                onSightingTapped: { selectedSighting = $0 },
                onYardTapped: { selectedYard = $0 },
                onRegionChanged: { region = $0 }
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // ── Search bar ────────────────────────────────────────────────
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.textMuted)
                        .font(.system(size: 14))
                    TextField("Search location…", text: $searchQuery)
                        .foregroundColor(.textPrimary)
                        .font(.system(size: 14))
                        .autocorrectionDisabled()
                        .onChange(of: searchQuery) { q in
                            showSearchResults = !q.isEmpty
                            searchTask?.cancel()
                            searchTask = Task {
                                try? await Task.sleep(nanoseconds: 400_000_000)
                                if !Task.isCancelled { vm.searchLocations(query: q) }
                            }
                        }
                    if !searchQuery.isEmpty {
                        Button { searchQuery = ""; showSearchResults = false; searchPin = nil } label: {
                            Image(systemName: "xmark.circle.fill").foregroundColor(.textMuted)
                        }
                    }
                    if vm.isSearchingLocation { ProgressView().scaleEffect(0.7).tint(.railBlue) }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(Color.bgCard.opacity(0.97))
                .cornerRadius(10)
                .padding(.horizontal, 10)
                .padding(.top, 8)
                .shadow(color: .black.opacity(0.3), radius: 4)

                // Search results dropdown
                if showSearchResults && !vm.searchResults.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(vm.searchResults) { place in
                            Button {
                                selectPlace(place)
                            } label: {
                                HStack(spacing: 10) {
                                    Image(systemName: "mappin.circle")
                                        .foregroundColor(.railBlue)
                                        .font(.system(size: 14))
                                    Text(place.display_name)
                                        .font(.system(size: 13))
                                        .foregroundColor(.textPrimary)
                                        .lineLimit(2)
                                        .multilineTextAlignment(.leading)
                                    Spacer()
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                            }
                            if place.id != vm.searchResults.last?.id {
                                Divider().background(Color.border).padding(.leading, 12)
                            }
                        }
                    }
                    .background(Color.bgCard.opacity(0.97))
                    .cornerRadius(10)
                    .padding(.horizontal, 10)
                    .shadow(color: .black.opacity(0.3), radius: 6)
                }

                // ── Filter chips (railroad + map layer) ───────────────────────
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        FilterChipView(label: "All", selected: vm.selectedRailroad == nil) {
                            vm.setRailroadFilter(nil)
                        }
                        ForEach(railroads, id: \.name) { rr in
                            FilterChipView(label: rr.label, selected: vm.selectedRailroad == rr.name) {
                                vm.setRailroadFilter(vm.selectedRailroad == rr.name ? nil : rr.name)
                            }
                        }
                        Divider().frame(height: 20).background(Color.border)
                        FilterChipView(label: "Satellite", selected: showSatellite) { showSatellite.toggle() }
                        FilterChipView(label: "Yards", selected: showYards) { showYards.toggle() }
                        FilterChipView(label: "Station Board", selected: false) { showStationBoard = true }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                }
                .background(Color.bgPrimary.opacity(0.92))

                // ── Stale data banner ─────────────────────────────────────────
                if isStale {
                    HStack(spacing: 8) {
                        Image(systemName: "clock.badge.exclamationmark")
                            .foregroundColor(.orange)
                            .font(.system(size: 13))
                        if let d = vm.lastRefreshDate {
                            let mins = Int(Date().timeIntervalSince(d) / 60)
                            Text("Data is \(mins)m old")
                                .font(.system(size: 12))
                                .foregroundColor(.orange)
                        }
                        Spacer()
                        Button("Refresh") { vm.refreshTrains() }
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.railBlue)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color(red: 0.3, green: 0.15, blue: 0.0).opacity(0.9))
                }

                // ── Network error banner ──────────────────────────────────────
                if let err = vm.fetchError {
                    HStack(spacing: 8) {
                        Image(systemName: "wifi.exclamationmark").foregroundColor(.red).font(.system(size: 13))
                        Text(err).font(.system(size: 12)).foregroundColor(.red).lineLimit(1)
                        Spacer()
                        Button { vm.fetchError = nil } label: {
                            Image(systemName: "xmark").foregroundColor(.textMuted).font(.system(size: 11))
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color(red: 0.25, green: 0.0, blue: 0.0).opacity(0.9))
                }

                Spacer()

                // ── FAB controls ──────────────────────────────────────────────
                HStack {
                    // Train count badge
                    Text("\(vm.filteredTrains.count) trains")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.textSecondary)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(Color.bgCard.opacity(0.9))
                        .cornerRadius(8)
                        .padding(.leading, 12)

                    Spacer()

                    VStack(spacing: 10) {
                        // Community sightings toggle
                        Button {
                            showSightings.toggle()
                            if showSightings {
                                let lat = vm.userLocation?.latitude ?? 41.8781
                                let lon = vm.userLocation?.longitude ?? -87.6298
                                FirestoreManager.shared.startListening(lat: lat, lon: lon)
                            } else {
                                FirestoreManager.shared.stopListening()
                            }
                        } label: {
                            Image(systemName: showSightings ? "binoculars.fill" : "binoculars")
                                .font(.system(size: 16))
                                .foregroundColor(showSightings ? .white : .railBlue)
                                .frame(width: 44, height: 44)
                                .background(showSightings ? Color.railBlueMid : Color.bgCard)
                                .clipShape(Circle())
                                .shadow(color: .black.opacity(0.4), radius: 4)
                        }
                        // Rail overlay toggle
                        Button {
                            showRailOverlay.toggle()
                        } label: {
                            Image(systemName: showRailOverlay ? "map.fill" : "map")
                                .font(.system(size: 16))
                                .foregroundColor(showRailOverlay ? .white : .railBlue)
                                .frame(width: 44, height: 44)
                                .background(showRailOverlay ? Color.railBlueMid : Color.bgCard)
                                .clipShape(Circle())
                                .shadow(color: .black.opacity(0.4), radius: 4)
                        }
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
                            Image(systemName: "arrow.clockwise")
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
                }
                .padding(.bottom, 24)
            }
        }
        .onAppear { showRailOverlay = vm.railOverlayDefault }
        .onChange(of: vm.userLocation) { loc in
            guard let loc, !centeredOnUser else { return }
            centeredOnUser = true
            region = MKCoordinateRegion(center: loc,
                                        span: MKCoordinateSpan(latitudeDelta: 3.0, longitudeDelta: 3.0))
        }
        .sheet(item: $selectedTrain) { train in
            RichTrainDetailSheet(train: train, vm: vm)
        }
        .sheet(item: $selectedSighting) { sighting in
            SightingMapDetailSheet(sighting: sighting)
        }
        .sheet(item: $selectedYard) { yard in
            YardDetailSheet(yard: yard)
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

    private func selectPlace(_ place: NominatimPlace) {
        searchQuery = place.display_name.components(separatedBy: ",").first ?? place.display_name
        showSearchResults = false
        searchPin = place.coordinate
        withAnimation {
            region = MKCoordinateRegion(center: place.coordinate,
                                        span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5))
        }
    }
}

// ── MKMapView wrapper (supports tile overlays + polyline trails) ───────────────
struct RailFanMapRepresentable: UIViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let trains: [TrainLocation]
    let trainTrails: [String: [CLLocationCoordinate2D]]
    let showRailOverlay: Bool
    let showSatellite: Bool
    let sightings: [FirestoreSighting]
    let yards: [ClassificationYard]
    let searchPin: CLLocationCoordinate2D?
    var onTrainTapped: (TrainLocation) -> Void
    var onSightingTapped: (FirestoreSighting) -> Void
    var onYardTapped: (ClassificationYard) -> Void
    var onRegionChanged: (MKCoordinateRegion) -> Void

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.showsUserLocation = true
        map.setRegion(region, animated: false)
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        // Satellite toggle
        map.mapType = showSatellite ? .satellite : .standard

        // Tile overlay — disabled in satellite mode (tiles look bad over imagery)
        let tiles = map.overlays.compactMap { $0 as? MKTileOverlay }
        let shouldShowTiles = showRailOverlay && !showSatellite
        if shouldShowTiles && tiles.isEmpty {
            let t = MKTileOverlay(urlTemplate: "https://tiles.openrailwaymap.org/standard/{z}/{x}/{y}.png")
            t.canReplaceMapContent = false
            map.addOverlay(t, level: .aboveLabels)
        } else if !shouldShowTiles && !tiles.isEmpty {
            map.removeOverlays(tiles)
        }

        // Train annotations
        let existing = map.annotations.compactMap { $0 as? TrainMKAnnotation }
        let existingIds = Set(existing.map { $0.train.id })
        let newIds = Set(trains.map { $0.id })
        map.removeAnnotations(existing.filter { !newIds.contains($0.train.id) })
        let toAdd = trains.filter { !existingIds.contains($0.id) }.map { TrainMKAnnotation($0) }
        map.addAnnotations(toAdd)
        // Update positions for existing ones
        for ann in map.annotations.compactMap({ $0 as? TrainMKAnnotation }) {
            if let t = trains.first(where: { $0.id == ann.train.id }) {
                ann.train = t
            }
        }

        // Train trails (polylines)
        let polys = map.overlays.compactMap { $0 as? MKPolyline }
        map.removeOverlays(polys)
        for (_, coords) in trainTrails where coords.count >= 2 {
            map.addOverlay(MKPolyline(coordinates: coords, count: coords.count), level: .aboveRoads)
        }

        // Sighting annotations
        let existingSightings = map.annotations.compactMap { $0 as? SightingMKAnnotation }
        let existingSightingIds = Set(existingSightings.map { $0.sighting.id ?? "" })
        let newSightingIds = Set(sightings.compactMap { $0.id })
        map.removeAnnotations(existingSightings.filter { !newSightingIds.contains($0.sighting.id ?? "") })
        let sightingsToAdd = sightings.filter { !existingSightingIds.contains($0.id ?? "") }
        map.addAnnotations(sightingsToAdd.map { SightingMKAnnotation($0) })

        // Yard annotations
        let existingYards = map.annotations.compactMap { $0 as? YardMKAnnotation }
        let existingYardIds = Set(existingYards.map { $0.yard.id })
        let newYardIds = Set(yards.map { $0.id })
        map.removeAnnotations(existingYards.filter { !newYardIds.contains($0.yard.id) })
        let yardsToAdd = yards.filter { !existingYardIds.contains($0.id) }.map { YardMKAnnotation($0) }
        map.addAnnotations(yardsToAdd)

        // Search pin
        let pins = map.annotations.compactMap { $0 as? SearchPinAnnotation }
        map.removeAnnotations(pins)
        if let coord = searchPin {
            map.addAnnotation(SearchPinAnnotation(coordinate: coord))
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: RailFanMapRepresentable
        var suppressRegionCallback = false
        init(_ p: RailFanMapRepresentable) { parent = p }

        func mapView(_ mapView: MKMapView, regionDidChangeAnimated: Bool) {
            guard !suppressRegionCallback else { return }
            parent.onRegionChanged(mapView.region)
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tile = overlay as? MKTileOverlay { return MKTileOverlayRenderer(tileOverlay: tile) }
            if let poly = overlay as? MKPolyline {
                let r = MKPolylineRenderer(polyline: poly)
                r.strokeColor = UIColor(Color.railBlue).withAlphaComponent(0.5)
                r.lineWidth = 2
                return r
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }
            if let sa = annotation as? SightingMKAnnotation {
                let v = MKMarkerAnnotationView(annotation: sa, reuseIdentifier: "sighting")
                v.markerTintColor = UIColor.orange
                v.glyphImage = UIImage(systemName: "binoculars.fill")
                v.canShowCallout = false
                return v
            }
            if let search = annotation as? SearchPinAnnotation {
                let v = MKMarkerAnnotationView(annotation: search, reuseIdentifier: "search")
                v.markerTintColor = UIColor(Color.railBlue)
                v.glyphImage = UIImage(systemName: "mappin")
                return v
            }
            if let ya = annotation as? YardMKAnnotation {
                let v = MKMarkerAnnotationView(annotation: ya, reuseIdentifier: "yard")
                v.markerTintColor = UIColor(red: 0.6, green: 0.3, blue: 0.0, alpha: 1)
                v.glyphImage = UIImage(systemName: "building.2.fill")
                v.canShowCallout = false
                return v
            }
            guard let ta = annotation as? TrainMKAnnotation else { return nil }
            let view = MKAnnotationView(annotation: annotation, reuseIdentifier: "train")
            view.canShowCallout = false
            let icon = TrainPinSwiftUI(train: ta.train)
            let renderer = ImageRenderer(content: icon)
            renderer.scale = UIScreen.main.scale
            view.image = renderer.uiImage
            return view
        }

        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            mapView.deselectAnnotation(view.annotation, animated: false)
            if let ta = view.annotation as? TrainMKAnnotation {
                parent.onTrainTapped(ta.train)
            } else if let sa = view.annotation as? SightingMKAnnotation {
                parent.onSightingTapped(sa.sighting)
            } else if let ya = view.annotation as? YardMKAnnotation {
                parent.onYardTapped(ya.yard)
            }
        }
    }
}

final class TrainMKAnnotation: NSObject, MKAnnotation {
    var train: TrainLocation {
        didSet { coordinate = CLLocationCoordinate2D(latitude: train.latitude, longitude: train.longitude) }
    }
    @objc dynamic var coordinate: CLLocationCoordinate2D
    init(_ t: TrainLocation) {
        train = t
        coordinate = CLLocationCoordinate2D(latitude: t.latitude, longitude: t.longitude)
    }
}

final class SightingMKAnnotation: NSObject, MKAnnotation {
    let sighting: FirestoreSighting
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: sighting.latitude, longitude: sighting.longitude)
    }
    init(_ s: FirestoreSighting) { self.sighting = s }
}

final class SearchPinAnnotation: NSObject, MKAnnotation {
    let coordinate: CLLocationCoordinate2D
    init(coordinate: CLLocationCoordinate2D) { self.coordinate = coordinate }
}

struct TrainPinSwiftUI: View {
    let train: TrainLocation
    var body: some View {
        VStack(spacing: 2) {
            Image(systemName: "arrow.up.circle.fill")
                .font(.system(size: 22))
                .foregroundColor(rrColor(train.railroad.name))
                .rotationEffect(.degrees(Double(train.headingDegrees)))
                .shadow(color: .black.opacity(0.5), radius: 2)
            Text(train.symbol.components(separatedBy: " ").prefix(2).joined(separator: " "))
                .font(.system(size: 8, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 3).padding(.vertical, 1)
                .background(Color.bgCard.opacity(0.85))
                .cornerRadius(3)
        }
    }
}

// ── Filter chip ───────────────────────────────────────────────────────────────
struct FilterChipView: View {
    let label: String; let selected: Bool; let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(selected ? .white : .textSecondary)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(selected ? Color.railBlueMid : Color.bgCard)
                .cornerRadius(16)
                .overlay(RoundedRectangle(cornerRadius: 16)
                    .stroke(selected ? Color.railBlue : Color.border, lineWidth: 0.5))
        }
    }
}

// ── Amtrak timetable ──────────────────────────────────────────────────────────

struct AmtrakStop: Identifiable {
    let id = UUID()
    let code: String
    let name: String
    let schArr: String   // "HH:MM"
    let schDep: String
    let estArr: String?
    let estDep: String?
    let status: String   // "On Time", "+5 min", "-3 min", ""
    let isPast: Bool
}

// Parses Amtrak's unofficial TrainStatus JSON. The format returned by
// https://www.amtrak.com/services/data.trainStatus.json varies; we decode
// defensively and tolerate unknown keys.
@MainActor
final class AmtrakTimetableFetcher: ObservableObject {
    @Published var stops: [AmtrakStop] = []
    @Published var isLoading = false
    @Published var error: String? = nil

    // Extracts a numeric Amtrak train number from symbols like
    // "Capitol Limited #30", "Hiawatha #337", "30", "AMTRAK 30".
    static func trainNumber(from symbol: String) -> Int? {
        // Prefer explicit "#NNN" notation
        if let m = symbol.range(of: #"#(\d{1,5})"#, options: .regularExpression) {
            let digits = symbol[m].drop(while: { !$0.isNumber })
            if let n = Int(digits) { return n }
        }
        // Fall back to trailing standalone number (e.g. "AMTRAK 30")
        if let m = symbol.range(of: #"(?<!\d)(\d{1,4})(?!\d)\s*$"#, options: .regularExpression) {
            return Int(symbol[m].trimmingCharacters(in: .whitespaces))
        }
        return nil
    }

    func fetch(symbol: String) {
        guard let num = Self.trainNumber(from: symbol) else {
            error = "Train number not found in symbol"
            return
        }
        isLoading = true
        error = nil
        Task {
            do {
                let stops = try await AmtrakTimetableFetcher.fetchStops(trainNumber: num)
                self.stops = stops
            } catch {
                self.error = error.localizedDescription
            }
            isLoading = false
        }
    }

    // Hits the Amtrak unofficial TrainStatus endpoint used by many open-source
    // Amtrak apps. Returns an array of station stop objects.
    private static func fetchStops(trainNumber: Int) async throws -> [AmtrakStop] {
        // Endpoint documented by the community; returns JSON array of station objects.
        let dateStr = ISO8601DateFormatter().string(from: Date()).prefix(10)
        let urlStr = "https://www.amtrak.com/services/data.trainStatus.json?trainNum=\(trainNumber)&date=\(dateStr)&lang=en"
        guard let url = URL(string: urlStr) else { throw URLError(.badURL) }

        var req = URLRequest(url: url, timeoutInterval: 10)
        req.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }

        // The response is a JSON array of station dicts.
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw URLError(.cannotParseResponse)
        }

        let fmt = DateFormatter()
        fmt.dateFormat = "h:mm a"
        fmt.locale = Locale(identifier: "en_US_POSIX")

        return root.compactMap { d -> AmtrakStop? in
            guard let code = d["code"] as? String,
                  let name = (d["name"] ?? d["station_name"]) as? String else { return nil }
            let schArr = (d["sch_arr"] as? String) ?? ""
            let schDep = (d["sch_dep"] as? String) ?? ""
            let estArr = d["est_arr"] as? String
            let estDep = d["est_dep"] as? String
            let postDep = (d["postDep"] as? Bool) ?? false
            let postArr = (d["postArr"] as? Bool) ?? false

            var status = ""
            if let eArr = estArr, !eArr.isEmpty, !schArr.isEmpty {
                if let e = fmt.date(from: eArr), let s = fmt.date(from: schArr) {
                    let diff = Int(e.timeIntervalSince(s) / 60)
                    if diff == 0 { status = "On Time" }
                    else if diff > 0 { status = "+\(diff) min" }
                    else { status = "\(diff) min" }
                }
            }

            return AmtrakStop(code: code, name: name,
                              schArr: schArr, schDep: schDep,
                              estArr: estArr, estDep: estDep,
                              status: status, isPast: postDep || postArr)
        }
    }
}

// ── Amtrak timetable card ─────────────────────────────────────────────────────
struct AmtrakTimetableCard: View {
    @StateObject private var fetcher = AmtrakTimetableFetcher()
    let symbol: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "calendar.badge.clock").foregroundColor(.railBlue)
                Text("Timetable").font(.system(size: 14, weight: .semibold)).foregroundColor(.textPrimary)
                Spacer()
                if fetcher.isLoading { ProgressView().scaleEffect(0.7).tint(.railBlue) }
            }

            if fetcher.isLoading && fetcher.stops.isEmpty {
                Text("Fetching timetable…").font(.system(size: 12)).foregroundColor(.textMuted)
            } else if !fetcher.stops.isEmpty {
                VStack(spacing: 0) {
                    ForEach(fetcher.stops) { stop in
                        AmtrakStopRow(stop: stop)
                        if stop.id != fetcher.stops.last?.id {
                            Divider().background(Color.border).padding(.leading, 36)
                        }
                    }
                }
            } else if let err = fetcher.error {
                VStack(spacing: 8) {
                    Text(err).font(.system(size: 12)).foregroundColor(.textMuted)
                    Link(destination: URL(string: "https://www.amtrak.com/train-status.html")!) {
                        Label("View on Amtrak.com", systemImage: "safari")
                            .font(.system(size: 13, weight: .medium)).foregroundColor(.railBlue)
                    }
                }
            } else if !fetcher.isLoading {
                // Fetch completed with no stops and no error
                VStack(spacing: 8) {
                    Text("No timetable data available.").font(.system(size: 12)).foregroundColor(.textMuted)
                    Link(destination: URL(string: "https://www.amtrak.com/train-status.html")!) {
                        Label("View on Amtrak.com", systemImage: "safari")
                            .font(.system(size: 13, weight: .medium)).foregroundColor(.railBlue)
                    }
                }
            }
        }
        .padding(14).cardStyle().padding(.horizontal)
        .onAppear { fetcher.fetch(symbol: symbol) }
    }
}

struct AmtrakStopRow: View {
    let stop: AmtrakStop
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            // Timeline dot
            VStack(spacing: 0) {
                Circle()
                    .fill(stop.isPast ? Color.textMuted : Color.railBlue)
                    .frame(width: 8, height: 8)
                    .padding(.top, 5)
            }
            .frame(width: 16)

            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(stop.name).font(.system(size: 13, weight: .medium))
                        .foregroundColor(stop.isPast ? .textMuted : .textPrimary)
                    Spacer()
                    if !stop.status.isEmpty {
                        Text(stop.status)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(statusColor(stop.status))
                    }
                }
                HStack(spacing: 12) {
                    if !stop.schArr.isEmpty {
                        Label(stop.schArr, systemImage: "arrow.down.circle")
                            .font(.system(size: 11)).foregroundColor(.textMuted)
                    }
                    if !stop.schDep.isEmpty {
                        Label(stop.schDep, systemImage: "arrow.up.circle")
                            .font(.system(size: 11)).foregroundColor(.textMuted)
                    }
                }
                if let eArr = stop.estArr, !eArr.isEmpty, eArr != stop.schArr {
                    Text("Est. arr \(eArr)").font(.system(size: 11)).foregroundColor(.orange)
                }
            }
        }
        .padding(.vertical, 8)
        .opacity(stop.isPast ? 0.55 : 1.0)
    }

    private func statusColor(_ s: String) -> Color {
        if s == "On Time" { return .green }
        if s.hasPrefix("-") { return .green }
        return .orange
    }
}

// ── Rich train detail sheet ────────────────────────────────────────────────────
struct RichTrainDetailSheet: View {
    let train: TrainLocation
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss
    @State private var showSaveDialog = false
    @State private var saveName = ""

    private var isAmtrak: Bool { train.railroad.name == "AMTRAK" }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Header
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(train.symbol)
                                .font(.system(size: 22, weight: .semibold))
                                .foregroundColor(.textPrimary)
                            Text(train.railroad.displayName)
                                .font(.system(size: 14))
                                .foregroundColor(.textMuted)
                        }
                        Spacer()
                        Circle()
                            .fill(rrColor(train.railroad.name))
                            .frame(width: 14, height: 14)
                    }
                    .padding(.horizontal)

                    // Speed gauge + compass row
                    HStack(spacing: 12) {
                        SpeedGaugeView(speedMph: Int(train.speedMph))
                        CompassView(heading: Double(train.headingDegrees))
                    }
                    .padding(.horizontal)

                    // ETA approach bar (if available)
                    if let eta = train.etaMinutes {
                        ETABarView(etaMinutes: Int(truncating: eta), threshold: vm.approachEtaThreshold)
                            .padding(.horizontal)
                    }

                    // Details card
                    VStack(spacing: 0) {
                        DetailRow(label: "Origin",      value: train.origin)
                        DetailRow(label: "Destination", value: train.destination)
                        DetailRow(label: "Status",      value: statusLabel)
                        if let eta = train.etaMinutes {
                            DetailRow(label: "ETA", value: "\(Int(truncating: eta)) min")
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

                    // Amtrak timetable (only for Amtrak trains)
                    if isAmtrak {
                        AmtrakTimetableCard(symbol: train.symbol)
                    }

                    // Consist
                    if !train.consist.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Consist").font(.system(size: 13)).foregroundColor(.textMuted)
                            ForEach(train.consist, id: \.self) { unit in
                                HStack(spacing: 8) {
                                    Image(systemName: "train.side.front.car")
                                        .foregroundColor(.railBlueDark)
                                        .font(.system(size: 12))
                                    Text(unit).font(.system(size: 13)).foregroundColor(.textSecondary)
                                }
                            }
                        }
                        .padding(14).cardStyle().padding(.horizontal)
                    }

                    // Save location button
                    Button {
                        saveName = "\(train.railroad.displayName) at \(train.origin)"
                        showSaveDialog = true
                    } label: {
                        Label("Save as Railfan Location", systemImage: "bookmark.fill")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.railBlueMid)
                            .cornerRadius(10)
                    }
                    .padding(.horizontal)
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
        .alert("Save Location", isPresented: $showSaveDialog) {
            TextField("Location name", text: $saveName)
            Button("Save") { saveCurrentLocation() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Save this spot as a railfan location?")
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

    private func saveCurrentLocation() {
        let loc = SavedLocationShared(
            id: UUID().uuidString,
            name: saveName,
            latitude: train.latitude,
            longitude: train.longitude,
            notes: "Saved from live train \(train.symbol)",
            subdivision: train.subdivision,
            scannerFrequency: nil,
            photoTips: nil,
            createdMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        vm.saveLocation(loc)
    }
}

// ── Speed gauge (Canvas arc) ──────────────────────────────────────────────────
struct SpeedGaugeView: View {
    let speedMph: Int
    private let maxSpeed = 120.0

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                Canvas { ctx, size in
                    let center = CGPoint(x: size.width / 2, y: size.height * 0.85)
                    let radius = min(size.width, size.height) * 0.75
                    let startAngle = Angle.degrees(210)
                    let endAngle   = Angle.degrees(330)
                    // Background arc
                    var bg = Path()
                    bg.addArc(center: center, radius: radius,
                              startAngle: startAngle, endAngle: endAngle, clockwise: false)
                    ctx.stroke(bg, with: .color(Color.bgInput), style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    // Speed arc
                    let fraction = min(Double(speedMph) / maxSpeed, 1.0)
                    let sweepDeg = 120.0 * fraction
                    if fraction > 0 {
                        var fg = Path()
                        fg.addArc(center: center, radius: radius,
                                  startAngle: startAngle,
                                  endAngle: .degrees(210 + sweepDeg), clockwise: false)
                        ctx.stroke(fg, with: .color(Color.railBlue), style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    }
                }
                .frame(width: 80, height: 60)

                VStack(spacing: 0) {
                    Spacer()
                    Text("\(speedMph)")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundColor(.railBlue)
                    Text("mph")
                        .font(.system(size: 9))
                        .foregroundColor(.textMuted)
                }
                .frame(height: 60)
            }
            Text("Speed").font(.system(size: 11)).foregroundColor(.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(10).background(Color.bgInput).cornerRadius(10)
    }
}

// ── Compass indicator ─────────────────────────────────────────────────────────
struct CompassView: View {
    let heading: Double
    private var cardinalLabel: String {
        let dirs = ["N","NE","E","SE","S","SW","W","NW","N"]
        return dirs[Int((heading + 22.5) / 45) % 8]
    }
    private static let dirAngles: [String: Double] = ["N": 0, "E": 90, "S": 180, "W": 270]

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                Circle().fill(Color.bgInput).frame(width: 56, height: 56)
                ForEach(["N","E","S","W"], id: \.self) { dir in
                    let deg = Self.dirAngles[dir] ?? 0
                    let rad = deg * .pi / 180
                    Text(dir)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(dir == "N" ? .red : .textMuted)
                        .offset(x: 20 * sin(rad), y: -20 * cos(rad))
                }
                Image(systemName: "arrow.up")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.railBlue)
                    .rotationEffect(.degrees(heading))
            }
            Text(cardinalLabel).font(.system(size: 11)).foregroundColor(.textMuted)
            Text("Heading").font(.system(size: 10)).foregroundColor(.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(10).background(Color.bgInput).cornerRadius(10)
    }
}

// ── ETA approach bar ──────────────────────────────────────────────────────────
struct ETABarView: View {
    let etaMinutes: Int
    let threshold: Int
    private var fraction: Double { max(0, 1.0 - Double(etaMinutes) / Double(max(threshold, 1))) }
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("ETA to saved location")
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
                Spacer()
                Text("\(etaMinutes) min")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(etaMinutes <= 5 ? .red : .railBlue)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4).fill(Color.bgInput).frame(height: 8)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(etaMinutes <= 5 ? Color.red : Color.railBlue)
                        .frame(width: geo.size.width * fraction, height: 8)
                }
            }
            .frame(height: 8)
        }
        .padding(12).background(Color.bgCard).cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
    }
}

// ── Shared detail row (also used by RichTrainDetailSheet) ─────────────────────
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

// ── Sighting map detail sheet ──────────────────────────────────────────────────
struct SightingMapDetailSheet: View {
    let sighting: FirestoreSighting
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Text(sighting.railroad)
                            .font(.system(size: 12, weight: .bold)).foregroundColor(.white)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Color.railBlueMid).cornerRadius(6)
                        Text(sighting.trainSymbol)
                            .font(.system(size: 18, weight: .semibold)).foregroundColor(.textPrimary)
                        Spacer()
                        Text("\(sighting.minutesAgo)m ago")
                            .font(.system(size: 13)).foregroundColor(.textMuted)
                    }
                    .padding(.horizontal)

                    VStack(spacing: 0) {
                        DetailRow(label: "Location",  value: sighting.location)
                        DetailRow(label: "Reporter",  value: sighting.reporterName)
                        DetailRow(label: "Distance",  value: String(format: "%.0f mi away", sighting.distanceMiles))
                        DetailRow(label: "Upvotes",   value: "\(sighting.upvotes)")
                    }
                    .cardStyle().padding(.horizontal)

                    if !sighting.notes.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Label("Notes", systemImage: "note.text")
                                .font(.system(size: 13)).foregroundColor(.textMuted)
                            Text(sighting.notes)
                                .font(.system(size: 14)).foregroundColor(.textSecondary).lineSpacing(4)
                        }
                        .padding(14).cardStyle().padding(.horizontal)
                    }

                    Spacer(minLength: 40)
                }
                .padding(.top)
            }
            .background(Color.bgPrimary)
            .navigationTitle("Community Sighting")
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
