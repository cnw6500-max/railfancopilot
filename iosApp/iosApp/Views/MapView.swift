import SwiftUI
import MapKit
import CoreLocation
import UIKit
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

    // STB / NTAD rail-line overlays
    @State private var showRailLines: Bool = false
    @State private var showAbandoned: Bool = false
    @State private var railSegments: [RailSegment] = []
    @State private var abandonedLines: [AbandonedRailLine] = []
    @State private var selectedAbandoned: AbandonedRailLine? = nil
    @State private var tappedSegmentLabel: String? = nil
    @State private var overlayFetchTask: Task<Void, Never>? = nil

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
            mapLayer
            contentOverlay
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
        .sheet(item: $selectedAbandoned) { line in
            AbandonedLineSheet(line: line)
        }
        .onChange(of: showRailLines) { on in
            if on { scheduleOverlayFetch(immediate: true) } else { railSegments = []; tappedSegmentLabel = nil }
        }
        .onChange(of: showAbandoned) { on in
            if on { scheduleOverlayFetch(immediate: true) } else { abandonedLines = [] }
        }
    }

    /// Debounced fetch of STB rail-line / abandoned-line overlays for the visible region.
    /// Rail lines need span ≤ ~1.5° (≈ zoom 9); abandoned lines ≤ ~6° (≈ zoom 7).
    private func scheduleOverlayFetch(immediate: Bool = false) {
        guard showRailLines || showAbandoned else { return }
        overlayFetchTask?.cancel()
        let region = self.region
        overlayFetchTask = Task {
            if !immediate { try? await Task.sleep(nanoseconds: 600_000_000) }
            guard !Task.isCancelled else { return }
            if showRailLines && region.span.latitudeDelta <= 1.5 {
                let segs = await StbRailService.shared.fetchRailSegments(region: region)
                if !Task.isCancelled && !segs.isEmpty { await MainActor.run { railSegments = segs } }
            }
            if showAbandoned && region.span.latitudeDelta <= 6.0 {
                let lines = await StbRailService.shared.fetchAbandonedLines(region: region)
                if !Task.isCancelled && !lines.isEmpty { await MainActor.run { abandonedLines = lines } }
            }
        }
    }

    @ViewBuilder
    private var mapLayer: some View {
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
                railSegments: showRailLines ? railSegments : [],
                abandonedLines: showAbandoned ? abandonedLines : [],
                onTrainTapped: { selectedTrain = $0 },
                onSightingTapped: { selectedSighting = $0 },
                onYardTapped: { selectedYard = $0 },
                onSegmentTapped: { tappedSegmentLabel = $0.label },
                onAbandonedTapped: { selectedAbandoned = $0 },
                onMapTapped: { tappedSegmentLabel = nil },
                onRegionChanged: { region = $0; scheduleOverlayFetch() }
            )
            .ignoresSafeArea()
    }

    @ViewBuilder
    private var contentOverlay: some View {
        VStack(spacing: 0) {
            searchBarSection
            searchResultsSection
            filterChipsSection
            staleDataBanner
            networkErrorBanner
            Spacer()
            tappedSegmentCapsule
            fabControlsSection
        }
    }

    // ── Tapped rail-line label ─────────────────────────────────────
    @ViewBuilder
    private var tappedSegmentCapsule: some View {
        if let label = tappedSegmentLabel {
            HStack(spacing: 8) {
                Image(systemName: "tram.fill").foregroundColor(.railBlue).font(.system(size: 12))
                Text(label).font(.system(size: 13)).foregroundColor(.textPrimary).lineLimit(2)
                Button { tappedSegmentLabel = nil } label: {
                    Image(systemName: "xmark").font(.system(size: 10, weight: .bold)).foregroundColor(.textMuted)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(Color.bgCard.opacity(0.95))
            .clipShape(Capsule())
            .overlay(Capsule().stroke(Color.borderLight, lineWidth: 0.5))
            .padding(.bottom, 8)
        }
    }

    // ── Search bar ────────────────────────────────────────────────
    @ViewBuilder
    private var searchBarSection: some View {
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
    }

    // Search results dropdown
    @ViewBuilder
    private var searchResultsSection: some View {
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
    }

    // ── Filter chips (railroad + map layer) ───────────────────────
    @ViewBuilder
    private var filterChipsSection: some View {
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
                        FilterChipView(label: "Rail Lines", selected: showRailLines) { showRailLines.toggle() }
                        FilterChipView(label: "Abandoned", selected: showAbandoned) { showAbandoned.toggle() }
                        FilterChipView(label: "Station Board", selected: false) { showStationBoard = true }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                }
                .background(Color.bgPrimary.opacity(0.92))
    }

    // ── Stale data banner ─────────────────────────────────────────
    @ViewBuilder
    private var staleDataBanner: some View {
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
    }

    // ── Network error banner ──────────────────────────────────────
    @ViewBuilder
    private var networkErrorBanner: some View {
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
    }

    // ── FAB controls ──────────────────────────────────────────────
    @ViewBuilder
    private var fabControlsSection: some View {
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
    var railSegments: [RailSegment] = []
    var abandonedLines: [AbandonedRailLine] = []
    var onTrainTapped: (TrainLocation) -> Void
    var onSightingTapped: (FirestoreSighting) -> Void
    var onYardTapped: (ClassificationYard) -> Void
    var onSegmentTapped: (RailSegment) -> Void = { _ in }
    var onAbandonedTapped: (AbandonedRailLine) -> Void = { _ in }
    var onMapTapped: () -> Void = {}
    var onRegionChanged: (MKCoordinateRegion) -> Void

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.showsUserLocation = true
        map.setRegion(region, animated: false)
        // Tap recognizer for polyline overlays (rail lines / abandoned lines).
        let tap = UITapGestureRecognizer(target: context.coordinator,
                                         action: #selector(Coordinator.handleTap(_:)))
        tap.cancelsTouchesInView = false
        tap.delegate = context.coordinator
        map.addGestureRecognizer(tap)
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

        // Train trails (polylines) — leave rail/abandoned overlays alone
        let polys = map.overlays.compactMap { $0 as? MKPolyline }
            .filter { !($0 is RailLinePolyline) && !($0 is AbandonedPolyline) }
        map.removeOverlays(polys)
        for (_, coords) in trainTrails where coords.count >= 2 {
            map.addOverlay(MKPolyline(coordinates: coords, count: coords.count), level: .aboveRoads)
        }

        // STB abandoned / railbanked lines (dashed, under rail lines)
        let abandonedIds = Set(abandonedLines.map { $0.id })
        if context.coordinator.abandonedIds != abandonedIds {
            map.removeOverlays(map.overlays.compactMap { $0 as? AbandonedPolyline })
            for line in abandonedLines {
                let p = AbandonedPolyline(coordinates: line.coordinates, count: line.coordinates.count)
                p.line = line
                map.addOverlay(p, level: .aboveRoads)
            }
            context.coordinator.abandonedIds = abandonedIds
        }

        // STB / NTAD rail lines (owner-colored)
        let segIds = Set(railSegments.map { $0.id })
        if context.coordinator.segmentIds != segIds {
            map.removeOverlays(map.overlays.compactMap { $0 as? RailLinePolyline })
            for seg in railSegments {
                let p = RailLinePolyline(coordinates: seg.coordinates, count: seg.coordinates.count)
                p.segment = seg
                map.addOverlay(p, level: .aboveRoads)
            }
            context.coordinator.segmentIds = segIds
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

    class Coordinator: NSObject, MKMapViewDelegate, UIGestureRecognizerDelegate {
        var parent: RailFanMapRepresentable
        var suppressRegionCallback = false
        var segmentIds: Set<Int> = []
        var abandonedIds: Set<String> = []
        init(_ p: RailFanMapRepresentable) { parent = p }

        func gestureRecognizer(_ g: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        @objc func handleTap(_ g: UITapGestureRecognizer) {
            guard let map = g.view as? MKMapView else { return }
            let pt = g.location(in: map)
            // Let annotation views handle their own taps.
            if let hit = map.hitTest(pt, with: nil), hit is MKAnnotationView || hit.superview is MKAnnotationView { return }
            let coord = map.convert(pt, toCoordinateFrom: map)
            let mp = MKMapPoint(coord)
            // ~14 screen points of tolerance, in map points
            let tol = map.visibleMapRect.size.width / Double(max(map.bounds.width, 1)) * 14
            let probe = MKMapRect(x: mp.x - tol, y: mp.y - tol, width: tol * 2, height: tol * 2)

            var bestSeg: RailLinePolyline? = nil
            var bestAb: AbandonedPolyline? = nil
            var bestDist = Double.greatestFiniteMagnitude
            for ov in map.overlays {
                guard let poly = ov as? MKPolyline, poly is RailLinePolyline || poly is AbandonedPolyline,
                      poly.boundingMapRect.intersects(probe) else { continue }
                let d = Self.distance(from: mp, to: poly)
                if d < tol && d < bestDist {
                    bestDist = d
                    bestSeg = poly as? RailLinePolyline
                    bestAb = poly as? AbandonedPolyline
                }
            }
            if let s = bestSeg?.segment { parent.onSegmentTapped(s) }
            else if let a = bestAb?.line { parent.onAbandonedTapped(a) }
            else { parent.onMapTapped() }
        }

        private static func distance(from p: MKMapPoint, to poly: MKPolyline) -> Double {
            let pts = poly.points()
            var best = Double.greatestFiniteMagnitude
            guard poly.pointCount >= 2 else { return best }
            for i in 0..<(poly.pointCount - 1) {
                let a = pts[i], b = pts[i + 1]
                let dx = b.x - a.x, dy = b.y - a.y
                let len2 = dx * dx + dy * dy
                let t = len2 == 0 ? 0 : max(0, min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2))
                let cx = a.x + t * dx - p.x, cy = a.y + t * dy - p.y
                best = min(best, (cx * cx + cy * cy).squareRoot())
            }
            return best
        }

        func mapView(_ mapView: MKMapView, regionDidChangeAnimated: Bool) {
            guard !suppressRegionCallback else { return }
            parent.onRegionChanged(mapView.region)
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tile = overlay as? MKTileOverlay { return MKTileOverlayRenderer(tileOverlay: tile) }
            if let rail = overlay as? RailLinePolyline {
                let r = MKPolylineRenderer(polyline: rail)
                r.strokeColor = railLineUIColor(rail.segment.ownerName.isEmpty ? rail.segment.ownerMark : rail.segment.ownerName)
                r.lineWidth = rail.segment.tracks >= 2 ? 5 : 4
                r.lineCap = .round
                return r
            }
            if let ab = overlay as? AbandonedPolyline {
                let r = MKPolylineRenderer(polyline: ab)
                r.strokeColor = ab.line.railbanked
                    ? UIColor(red: 0.29, green: 0.87, blue: 0.50, alpha: 0.9)
                    : UIColor(red: 0.69, green: 0.75, blue: 0.77, alpha: 0.9)
                r.lineWidth = 3.5
                r.lineDashPattern = [8, 6]
                return r
            }
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
    @State private var showTimetable = false
    @State private var showBoardingDialog = false
    @State private var boardingInput = ""
    @State private var showShareSheet = false
    @State private var shareText = ""

    private var isAmtrak: Bool { train.railroad.name == "AMTRAK" }
    private var speeds: [Int] { vm.speedHistory[train.id] ?? [] }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    headerSection
                    speedRow
                    speedSparklineSection
                    etaBarSection
                    detailsCardSection
                    amtrakTimetableSection
                    consistSection
                    actionButtonsSection
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
        .alert("Save Location", isPresented: $showSaveDialog) {
            TextField("Location name", text: $saveName)
            Button("Save") { saveCurrentLocation() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Save this spot as a railfan location?")
        }
        .alert("Start Trip", isPresented: $showBoardingDialog) {
            TextField("Boarding station (optional)", text: $boardingInput)
            Button("Start") {
                vm.startTrip(train: train, boardingStation: boardingInput)  // startTrip trims + nil-ifies internally
                boardingInput = ""
                dismiss()
            }
            Button("Cancel", role: .cancel) { boardingInput = "" }
        } message: {
            Text("Riding \(train.symbol)")
        }
    }

    @ViewBuilder
    private var headerSection: some View {
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
    }

    @ViewBuilder
    private var speedRow: some View {
                    // Speed gauge + compass row
                    HStack(spacing: 12) {
                        SpeedGaugeView(speedMph: Int(train.speedMph))
                        CompassView(heading: Double(train.headingDegrees))
                    }
                    .padding(.horizontal)
    }

    @ViewBuilder
    private var speedSparklineSection: some View {
                    // Speed sparkline (recent history)
                    if speeds.count >= 2 {
                        SpeedSparklineView(speeds: speeds, currentMph: Int(train.speedMph))
                            .padding(.horizontal)
                    }
    }

    @ViewBuilder
    private var etaBarSection: some View {
                    // ETA approach bar (if available)
                    if let eta = train.etaMinutes {
                        ETABarView(etaMinutes: Int(truncating: eta), threshold: vm.approachEtaThreshold)
                            .padding(.horizontal)
                    }
    }

    @ViewBuilder
    private var detailsCardSection: some View {
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
    }

    @ViewBuilder
    private var amtrakTimetableSection: some View {
                    // Amtrak timetable (only for Amtrak trains)
                    if isAmtrak {
                        AmtrakTimetableCard(symbol: train.symbol)
                    }
    }

    @ViewBuilder
    private var consistSection: some View {
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
    }

    @ViewBuilder
    private var actionButtonsSection: some View {
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

                        // Full timetable (Amtrak only)
                        if isAmtrak {
                            Button {
                                vm.loadTimetable(for: train)
                                showTimetable = true
                            } label: {
                                HStack {
                                    Image(systemName: "calendar.badge.clock")
                                    Text("Full Timetable")
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
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 32)
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
        .padding(.horizontal, 16).padding(.vertical, 10)
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

// ── Share sheet (wraps UIActivityViewController for SwiftUI) ──────────────────
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
