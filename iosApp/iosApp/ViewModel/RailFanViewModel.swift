import Foundation
import CoreLocation
import StoreKit
import UIKit
import shared   // KMP XCFramework

@MainActor
class RailFanViewModel: NSObject, ObservableObject, CLLocationManagerDelegate {

    // ── KMP bridge ────────────────────────────────────────────────────────────
    private let helper: IOSRailFanHelper

    // ── Location ──────────────────────────────────────────────────────────────
    private let locationManager = CLLocationManager()
    @Published var userLocation: CLLocationCoordinate2D? = nil
    @Published var locationName: String = ""

    // ── Trains ────────────────────────────────────────────────────────────────
    @Published var trains: [TrainLocation] = []
    @Published var isLoadingTrains = false
    @Published var selectedRailroad: String? = nil  // Railroad.name or nil = all
    /// trainId → last 10 speed readings (mph), oldest first — used for sparkline
    @Published var speedHistory: [String: [Int]] = [:]

    // ── Scanner ───────────────────────────────────────────────────────────────
    @Published var radioChannels: [RadioChannel] = []

    // ── Decoder ───────────────────────────────────────────────────────────────
    @Published var decoderResult: TrainSymbolDecodeResult? = nil
    @Published var isDecoding = false
    @Published var decoderError: String? = nil

    // ── Photo / Loco ID ───────────────────────────────────────────────────────
    @Published var locoIdResult: String? = nil
    @Published var isIdentifying = false
    @Published var locoIdError: String? = nil

    // ── Consist Analyzer ─────────────────────────────────────────────────────
    @Published var consistResult: String? = nil
    @Published var isAnalyzingConsist = false
    @Published var consistError: String? = nil

    // ── Decoder History ───────────────────────────────────────────────────────
    @Published var decoderHistory: [DecoderHistoryEntry] = []

    // ── Sun info ──────────────────────────────────────────────────────────────
    @Published var sunInfo: SunInfo? = nil

    // ── Saved locations ───────────────────────────────────────────────────────
    @Published var savedLocations: [SavedLocationShared] = []

    // ── Settings ──────────────────────────────────────────────────────────────
    @Published var showAmtrak    = true
    @Published var showCommuter  = true
    @Published var showFreight   = true
    @Published var isPurchased   = false
    @Published var isInTrial     = false
    @Published var trialDaysLeft = 0
    private var trialTimer: Timer?

    // Commuter feed toggles
    @Published var njtEnabled       = UserDefaults.standard.bool(forKey: "njtEnabled")
    @Published var vreEnabled       = UserDefaults.standard.bool(forKey: "vreEnabled")
    @Published var marcEnabled      = UserDefaults.standard.bool(forKey: "marcEnabled")
    @Published var metrolinkEnabled = UserDefaults.standard.bool(forKey: "metrolinkEnabled")

    // Notification preferences
    @Published var goldenHourAlertsEnabled = UserDefaults.standard.object(forKey: "goldenHourAlerts") as? Bool ?? true

    // Favorite scanner feed URLs
    @Published var favoriteFeedUrls: Set<String> = {
        let arr = UserDefaults.standard.stringArray(forKey: "favoriteFeedUrls") ?? []
        return Set(arr)
    }()

    // ── Community reports (for map pins) ─────────────────────────────────────
    @Published var communityReports: [CommunityReportSwift] = []

    // ── In-app review ─────────────────────────────────────────────────────────
    private var reviewPromptedFirstData = UserDefaults.standard.bool(forKey: "reviewFirstDataDone")
    private var reviewPromptedTrialEnd  = UserDefaults.standard.bool(forKey: "reviewTrialEndDone")
    private var lastReviewExitMs        = UserDefaults.standard.double(forKey: "reviewLastExitMs")

    func maybeRequestReviewFirstData() {
        guard !reviewPromptedFirstData else { return }
        reviewPromptedFirstData = true
        UserDefaults.standard.set(true, forKey: "reviewFirstDataDone")
        requestReview()
    }
    func maybeRequestReviewTrialEnd() {
        guard !reviewPromptedTrialEnd, !isPurchased else { return }
        reviewPromptedTrialEnd = true
        UserDefaults.standard.set(true, forKey: "reviewTrialEndDone")
        requestReview()
    }
    func maybeRequestReviewOnBackground() {
        let now = Date().timeIntervalSince1970 * 1000
        let cooldown: Double = 30 * 24 * 60 * 60 * 1000
        guard now - lastReviewExitMs >= cooldown else { return }
        lastReviewExitMs = now
        UserDefaults.standard.set(now, forKey: "reviewLastExitMs")
        requestReview()
    }
    @MainActor private func requestReview() {
        guard let scene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene else { return }
        SKStoreReviewController.requestReview(in: scene)
    }

    // ── Loco number lookup ────────────────────────────────────────────────────
    @Published var locoNumberResult: String? = nil
    @Published var isLocoNumberLoading = false
    @Published var locoNumberError: String? = nil

    // ── Timetable ─────────────────────────────────────────────────────────────
    @Published var timetableStops: [TimetableStopSwift] = []
    @Published var isTimetableLoading = false
    @Published var timetableError: String? = nil

    // ── Station departures ────────────────────────────────────────────────────
    @Published var stationDepartures: [StationDepartureSwift] = []
    @Published var isStationLoading = false
    @Published var stationError: String? = nil

    // ── Trip logging ──────────────────────────────────────────────────────────
    @Published var activeTrip: TripLogSwift? = nil
    @Published var completedTrips: [TripLogSwift] = []
    private var tripLastLocation: CLLocation? = nil

    var totalTripMiles: Double { completedTrips.reduce(0) { $0 + $1.distanceMiles } }
    var totalTripHours: Double { completedTrips.reduce(0) { $0 + Double($1.durationMinutes) / 60.0 } }

    /// Pro features are active while purchased or within the 7-day trial window.
    var isPremium: Bool { isPurchased || isInTrial }

    // ── Community ─────────────────────────────────────────────────────────────
    @Published var userName: String = UserDefaults.standard.string(forKey: "userName") ?? "Railfan" {
        didSet { UserDefaults.standard.set(userName, forKey: "userName") }
    }

    // ── Approach Notifications ────────────────────────────────────────────────
    @Published var approachEtaThreshold: Int = 10  // minutes
    @Published var approachNotificationsEnabled: Bool = false
    private var notifiedTrainIds: Set<String> = []

    // ── Init ──────────────────────────────────────────────────────────────────
    override init() {
        helper = IOSRailFanHelper(
            anthropicApiKey: Secrets.anthropicApiKey,
            metraUser:       Secrets.metraUser,
            metraPassword:   Secrets.metraPassword,
            mtaApiKey:       Secrets.mtaApiKey,
            fiveElevenKey:   Secrets.fiveElevenKey
        )
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
        radioChannels = helper.getAARFrequencies() as? [RadioChannel] ?? []
        loadSavedLocations()
        loadDecoderHistory()
        loadTrips()
        isPurchased = UserDefaults.standard.bool(forKey: "isPremium")
        seedTrialIfNeeded()
        Task {
            await FirestoreManager.shared.ensureAuth()
            // Verify premium status against StoreKit (handles reinstalls & new devices)
            if await StoreManager.shared.checkEntitlement() {
                unlockPremium()
            }
        }
    }

    deinit { helper.cancel() }

    // ── CLLocationManagerDelegate ─────────────────────────────────────────────
    nonisolated func locationManager(_ manager: CLLocationManager,
                                     didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        Task { @MainActor in
            let isFirst = self.userLocation == nil
            self.userLocation = loc.coordinate
            self.refreshTrains()
            self.updateSunInfo()
            self.reverseGeocodeCurrentLocation()
            if isFirst {
                FirestoreManager.shared.startSpotsListener(lat: loc.coordinate.latitude, lon: loc.coordinate.longitude)
            }
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse ||
           manager.authorizationStatus == .authorizedAlways {
            manager.startUpdatingLocation()
        }
        if manager.authorizationStatus == .authorizedAlways {
            Task { @MainActor in self.refreshGeofences() }
        }
    }

    // Geofence entry — fires when user physically enters a saved spot radius
    nonisolated func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        Task { @MainActor in
            guard approachNotificationsEnabled, isPremium else { return }
            if let loc = savedLocations.first(where: { $0.id == region.identifier }) {
                NotificationManager.shared.sendProximityNotification(locationName: loc.name)
            }
        }
    }

    // ── Train refresh ─────────────────────────────────────────────────────────
    func refreshTrains() {
        guard let loc = userLocation else { return }
        isLoadingTrains = true
        helper.getAllTrains(
            lat: loc.latitude, lon: loc.longitude,
            onSuccess: { [weak self] trains in
                guard let self else { return }
                Task { @MainActor in
                    let loaded = trains as? [TrainLocation] ?? []
                    self.trains = loaded
                    self.isLoadingTrains = false
                    self.checkApproachNotifications()
                    // Accumulate speed history for sparkline (last 10 readings per train)
                    var updated = self.speedHistory
                    for t in loaded {
                        var hist = updated[t.id] ?? []
                        hist.append(Int(t.speedMph))
                        updated[t.id] = Array(hist.suffix(10))
                    }
                    updated.keys.filter { id in !loaded.contains { $0.id == id } }
                        .forEach { updated.removeValue(forKey: $0) }
                    self.speedHistory = updated
                    // Accumulate active trip distance
                    self.accumulateTripDistance()
                }
            },
            onError: { [weak self] _ in
                Task { @MainActor in self?.isLoadingTrains = false }
            }
        )
    }

    var filteredTrains: [TrainLocation] {
        trains.filter { train in
            let rr = train.railroad.name
            // Apply railroad-type toggles from Settings
            if !showAmtrak  && rr == "AMTRAK"                              { return false }
            if !showCommuter && rr == "OTHER"                              { return false }
            if !showFreight  && ["BNSF","UP","CSX","NS","CN","CP","KCS"].contains(rr) { return false }
            // Apply chip filter (nil = all)
            if let chip = selectedRailroad, rr != chip                    { return false }
            return true
        }
    }

    func setRailroadFilter(_ name: String?) { selectedRailroad = name }

    // ── Symbol decoder ────────────────────────────────────────────────────────
    func decodeSymbol(_ symbol: String) {
        guard !symbol.isBlank else { return }
        isDecoding = true
        decoderResult = nil
        decoderError = nil
        Task {
            do {
                let json = try await FirebaseFunctionsClient.shared.decodeTrainSymbol(
                    symbol: symbol, localContext: "(no local match found)")
                // Parse JSON into TrainSymbolDecodeResult via KMP helper
                if let result = helper.parseDecodeResult(json: json) {
                    decoderResult = result
                    saveToDecoderHistory(result)
                } else {
                    decoderError = "Could not parse decode result"
                }
            } catch {
                decoderError = error.localizedDescription
            }
            isDecoding = false
        }
    }

    // ── Loco identifier ───────────────────────────────────────────────────────
    func identifyLoco(jpegData: Data) {
        isIdentifying = true
        locoIdResult = nil
        locoIdError  = nil
        Task {
            do {
                locoIdResult = try await FirebaseFunctionsClient.shared.identifyLocomotive(jpegData: jpegData)
            } catch {
                locoIdError = error.localizedDescription
            }
            isIdentifying = false
        }
    }

    // ── Consist analyzer ─────────────────────────────────────────────────────
    func analyzeConsist(jpegData: Data) {
        isAnalyzingConsist = true
        consistResult = nil
        consistError  = nil
        Task {
            do {
                consistResult = try await FirebaseFunctionsClient.shared.analyzeConsist(jpegData: jpegData)
            } catch {
                consistError = error.localizedDescription
            }
            isAnalyzingConsist = false
        }
    }

    // ── Sun info ──────────────────────────────────────────────────────────────
    func updateSunInfo() {
        guard let loc = userLocation else { return }
        sunInfo = helper.calculateSunInfo(lat: loc.latitude, lon: loc.longitude)
    }

    // ── Reverse geocode ───────────────────────────────────────────────────────
    private func reverseGeocodeCurrentLocation() {
        guard let loc = userLocation else { return }
        helper.reverseGeocode(lat: loc.latitude, lon: loc.longitude) { [weak self] name in
            Task { @MainActor in self?.locationName = name ?? "" }
        }
    }

    // ── Saved locations (UserDefaults) ────────────────────────────────────────
    private func loadSavedLocations() {
        // Loaded from UserDefaults JSON array
        if let data = UserDefaults.standard.data(forKey: "savedLocations"),
           let decoded = try? JSONDecoder().decode([SavedLocationCodable].self, from: data) {
            savedLocations = decoded.map { $0.toShared() }
        }
    }

    func saveLocation(_ loc: SavedLocationShared) {
        savedLocations.append(loc)
        persistLocations()
        refreshGeofences()
    }

    func deleteLocation(id: String) {
        // Remove geofence before deleting
        let region = locationManager.monitoredRegions.first { $0.identifier == id }
        if let r = region { locationManager.stopMonitoring(for: r) }
        savedLocations.removeAll { $0.id == id }
        persistLocations()
    }

    // ── Geofence monitoring ───────────────────────────────────────────────────
    func refreshGeofences() {
        guard approachNotificationsEnabled, isPremium,
              locationManager.authorizationStatus == .authorizedAlways else { return }
        // Clear all existing railfan geofences
        for region in locationManager.monitoredRegions {
            locationManager.stopMonitoring(for: region)
        }
        // Register one per saved location (1-mile radius)
        for loc in savedLocations {
            let center = CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude)
            let region = CLCircularRegion(center: center, radius: 1609.34, identifier: loc.id)
            region.notifyOnEntry = true
            region.notifyOnExit  = false
            locationManager.startMonitoring(for: region)
        }
    }

    func enableApproachNotifications() {
        // Escalate to Always authorization so geofences work in background
        locationManager.requestAlwaysAuthorization()
        NotificationManager.shared.requestPermission()
        approachNotificationsEnabled = true
        refreshGeofences()
    }

    private func persistLocations() {
        let codable = savedLocations.map { SavedLocationCodable(from: $0) }
        if let data = try? JSONEncoder().encode(codable) {
            UserDefaults.standard.set(data, forKey: "savedLocations")
        }
    }

    // ── Approach Notifications ────────────────────────────────────────────────
    func checkApproachNotifications() {
        guard approachNotificationsEnabled, isPremium else { return }
        guard !savedLocations.isEmpty, !trains.isEmpty else { return }

        for location in savedLocations {
            let locCL = CLLocation(latitude: location.latitude, longitude: location.longitude)

            for train in trains {
                let trainCL = CLLocation(latitude: train.latitude, longitude: train.longitude)
                let distanceMeters = locCL.distance(from: trainCL)
                let distanceMiles = distanceMeters / 1609.34
                let speedMph = Double(train.speedMph)
                guard speedMph > 5 else { continue }
                let etaMinutes = Int((distanceMiles / speedMph) * 60)

                if etaMinutes <= approachEtaThreshold && etaMinutes > 0 {
                    let key = "\(train.id)-\(location.id)"
                    if !notifiedTrainIds.contains(key) {
                        notifiedTrainIds.insert(key)
                        NotificationManager.shared.sendApproachNotification(
                            trainSymbol: train.symbol,
                            railroad: train.railroad.displayName,
                            etaMinutes: etaMinutes,
                            locationName: location.name
                        )
                    }
                }
            }
        }
        // Clear old notifications after 30 minutes
        if notifiedTrainIds.count > 100 { notifiedTrainIds.removeAll() }
    }

    // ── Premium ───────────────────────────────────────────────────────────────
    func unlockPremium() {
        isPurchased = true
        UserDefaults.standard.set(true, forKey: "isPremium")
    }

    // ── Decoder history ───────────────────────────────────────────────────────
    private func saveToDecoderHistory(_ result: TrainSymbolDecodeResult) {
        let entry = DecoderHistoryEntry(
            id: UUID().uuidString,
            symbol: result.symbol,
            origin: result.origin,
            destination: result.destination,
            railroad: result.railroad.displayName,
            timestampMs: Date().timeIntervalSince1970 * 1000
        )
        decoderHistory.removeAll { $0.symbol == entry.symbol }   // deduplicate
        decoderHistory.insert(entry, at: 0)
        if decoderHistory.count > 20 { decoderHistory = Array(decoderHistory.prefix(20)) }
        if let data = try? JSONEncoder().encode(decoderHistory) {
            UserDefaults.standard.set(data, forKey: "decoderHistory")
        }
    }

    private func loadDecoderHistory() {
        if let data = UserDefaults.standard.data(forKey: "decoderHistory"),
           let decoded = try? JSONDecoder().decode([DecoderHistoryEntry].self, from: data) {
            decoderHistory = decoded
        }
    }

    func clearDecoderHistory() {
        decoderHistory = []
        UserDefaults.standard.removeObject(forKey: "decoderHistory")
    }

    // ── Agency toggle saves ───────────────────────────────────────────────────
    func setNjt(_ on: Bool)       { njtEnabled = on;       UserDefaults.standard.set(on, forKey: "njtEnabled") }
    func setVre(_ on: Bool)       { vreEnabled = on;       UserDefaults.standard.set(on, forKey: "vreEnabled") }
    func setMarc(_ on: Bool)      { marcEnabled = on;      UserDefaults.standard.set(on, forKey: "marcEnabled") }
    func setMetrolink(_ on: Bool) { metrolinkEnabled = on; UserDefaults.standard.set(on, forKey: "metrolinkEnabled") }
    func setGoldenHourAlerts(_ on: Bool) {
        goldenHourAlertsEnabled = on
        UserDefaults.standard.set(on, forKey: "goldenHourAlerts")
    }

    // ── Favourite scanner feeds ───────────────────────────────────────────────
    func toggleFavouriteFeed(_ url: String) {
        if favoriteFeedUrls.contains(url) { favoriteFeedUrls.remove(url) }
        else { favoriteFeedUrls.insert(url) }
        UserDefaults.standard.set(Array(favoriteFeedUrls), forKey: "favoriteFeedUrls")
    }

    // ── Loco number lookup ────────────────────────────────────────────────────
    func lookupLocoNumber(_ roadNumber: String) {
        guard !roadNumber.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        isLocoNumberLoading = true
        locoNumberResult = nil
        locoNumberError  = nil
        Task {
            do {
                locoNumberResult = try await FirebaseFunctionsClient.shared.lookupLocoNumber(roadNumber: roadNumber)
            } catch {
                locoNumberError = "Lookup failed — check your connection"
            }
            isLocoNumberLoading = false
        }
    }

    // ── Timetable ─────────────────────────────────────────────────────────────
    func loadTimetable(for train: TrainLocation) {
        guard train.railroad == .AMTRAK else {
            timetableError = "Timetables are currently available for Amtrak only"
            timetableStops = []
            return
        }
        let trainNum = train.symbol.components(separatedBy: "#").last?
            .trimmingCharacters(in: .whitespaces) ?? ""
        guard !trainNum.isEmpty, trainNum.allSatisfy(\.isNumber) else {
            timetableError = "Could not determine train number from \"\(train.symbol)\""
            return
        }
        isTimetableLoading = true
        timetableError = nil
        Task {
            do {
                let url = URL(string: "https://api.amtraker.com/v3/trains/\(trainNum)")!
                let (data, _) = try await URLSession.shared.data(from: url)
                let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                let allTrains = json?.values.compactMap { $0 as? [[String: Any]] }.flatMap { $0 }
                let stations = allTrains?.first?["stations"] as? [[String: Any]] ?? []
                timetableStops = stations.map { TimetableStopSwift(json: $0) }
                if timetableStops.isEmpty { timetableError = "No timetable data for train #\(trainNum)" }
            } catch {
                timetableError = "Couldn't load timetable — check your connection"
            }
            isTimetableLoading = false
        }
    }

    func clearTimetable() { timetableStops = []; timetableError = nil }

    // ── Station departures ────────────────────────────────────────────────────
    func loadStationDepartures(code: String) {
        guard !code.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        isStationLoading = true
        stationError = nil
        stationDepartures = []
        let upper = code.uppercased()
        Task {
            do {
                let url = URL(string: "https://api.amtraker.com/v3/stations/\(upper)")!
                let (data, _) = try await URLSession.shared.data(from: url)
                let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                let allTrains = json?.values.compactMap { $0 as? [[String: Any]] }.flatMap { $0 } ?? []
                stationDepartures = allTrains.map { t in
                    let stations = (t["stations"] as? [[String: Any]] ?? []).map { TimetableStopSwift(json: $0) }
                    return StationDepartureSwift(
                        symbol:    "Amtrak #\(t["trainNum"] as? String ?? "")",
                        routeName: t["routeName"] as? String ?? "",
                        stops:     stations
                    )
                }.sorted {
                    let a = $0.stops.first { $0.code == upper }?.scheduledDeparture ?? "99:99"
                    let b = $1.stops.first { $0.code == upper }?.scheduledDeparture ?? "99:99"
                    return a < b
                }
                if stationDepartures.isEmpty { stationError = "No trains found for \"\(upper)\"" }
            } catch {
                stationError = "Couldn't load departures — check your connection"
            }
            isStationLoading = false
        }
    }

    // ── Trip logging ──────────────────────────────────────────────────────────
    func startTrip(train: TrainLocation, boardingStation: String? = nil) {
        guard activeTrip == nil else { return }
        let trip = TripLogSwift(
            id: UUID().uuidString, trainId: train.id,
            trainSymbol: train.symbol, railroad: train.railroad.displayName,
            startMs: Int64(Date().timeIntervalSince1970 * 1000),
            boardingStation: boardingStation?.trimmingCharacters(in: .whitespaces).nilIfEmpty
        )
        activeTrip = trip
        if let loc = userLocation {
            tripLastLocation = CLLocation(latitude: loc.latitude, longitude: loc.longitude)
        }
        saveTrips()
    }

    func accumulateTripDistance() {
        guard var trip = activeTrip, let loc = userLocation else { return }
        let current = CLLocation(latitude: loc.latitude, longitude: loc.longitude)
        if let last = tripLastLocation {
            let addedMiles = last.distance(from: current) / 1609.34
            if addedMiles > 0.05 {
                trip.distanceMiles += addedMiles
                activeTrip = trip
            }
        }
        tripLastLocation = current
    }

    func endTrip(notes: String? = nil, alightingStation: String? = nil) {
        guard var trip = activeTrip else { return }
        trip.endMs = Int64(Date().timeIntervalSince1970 * 1000)
        trip.notes = notes?.trimmingCharacters(in: .whitespaces).nilIfEmpty
        trip.alightingStation = alightingStation?.trimmingCharacters(in: .whitespaces).nilIfEmpty
        completedTrips.insert(trip, at: 0)
        activeTrip = nil
        tripLastLocation = nil
        saveTrips()
        maybeRequestReviewFirstData()
    }

    func deleteTrip(id: String) {
        completedTrips.removeAll { $0.id == id }
        saveTrips()
    }

    private func loadTrips() {
        if let data = UserDefaults.standard.data(forKey: "completedTrips"),
           let trips = try? JSONDecoder().decode([TripLogSwift].self, from: data) {
            completedTrips = trips
        }
        if let data = UserDefaults.standard.data(forKey: "activeTrip"),
           let trip = try? JSONDecoder().decode(TripLogSwift.self, from: data) {
            activeTrip = trip
        }
    }

    private func saveTrips() {
        if let data = try? JSONEncoder().encode(completedTrips) {
            UserDefaults.standard.set(data, forKey: "completedTrips")
        }
        if let trip = activeTrip, let data = try? JSONEncoder().encode(trip) {
            UserDefaults.standard.set(data, forKey: "activeTrip")
        } else {
            UserDefaults.standard.removeObject(forKey: "activeTrip")
        }
    }

    private func seedTrialIfNeeded() {
        let saved = UserDefaults.standard.double(forKey: "trialStartMs")
        if saved == 0 {
            let now = Date().timeIntervalSince1970 * 1000
            UserDefaults.standard.set(now, forKey: "trialStartMs")
        }
        refreshTrialState()
        // Re-evaluate every minute so expiry takes effect without a restart.
        trialTimer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            self?.refreshTrialState()
        }
    }

    private func refreshTrialState() {
        let trialDurationMs: Double = 7 * 24 * 60 * 60 * 1000
        let startMs = UserDefaults.standard.double(forKey: "trialStartMs")
        let elapsedMs = Date().timeIntervalSince1970 * 1000 - startMs
        let nowInTrial = elapsedMs < trialDurationMs
        let remainingMs = max(0, trialDurationMs - elapsedMs)
        let daysLeft = Int(ceil(remainingMs / (24 * 60 * 60 * 1000)))
        if isInTrial != nowInTrial { isInTrial = nowInTrial }
        if trialDaysLeft != daysLeft { trialDaysLeft = daysLeft }
    }
}

// ── Decoder history entry ─────────────────────────────────────────────────────
struct DecoderHistoryEntry: Codable, Identifiable {
    let id: String
    let symbol: String
    let origin: String
    let destination: String
    let railroad: String
    let timestampMs: Double

    var dateLabel: String {
        let date = Date(timeIntervalSince1970: timestampMs / 1000)
        let fmt = DateFormatter()
        fmt.dateFormat = "MMM d, h:mm a"
        return fmt.string(from: date)
    }
}

// ── Codable wrapper for SavedLocationShared ───────────────────────────────────
private struct SavedLocationCodable: Codable {
    let id, name: String
    let latitude, longitude: Double
    let notes, subdivision, scannerFrequency, photoTips: String?
    let createdMs: Int64

    init(from s: SavedLocationShared) {
        id = s.id; name = s.name
        latitude = s.latitude; longitude = s.longitude
        notes = s.notes; subdivision = s.subdivision
        scannerFrequency = s.scannerFrequency; photoTips = s.photoTips
        createdMs = s.createdMs
    }

    func toShared() -> SavedLocationShared {
        SavedLocationShared(
            id: id, name: name, latitude: latitude, longitude: longitude,
            notes: notes, subdivision: subdivision,
            scannerFrequency: scannerFrequency, photoTips: photoTips,
            createdMs: createdMs
        )
    }
}

private extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespaces).isEmpty }
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

// ── Swift-side model types (not in KMP) ───────────────────────────────────────

struct CommunityReportSwift: Identifiable {
    let id: String
    let latitude: Double
    let longitude: Double
    let text: String
    let trainSymbol: String?
    let railroad: String?
    let userName: String
    let timestampMs: Int64
}

struct TripLogSwift: Codable, Identifiable {
    let id: String
    let trainId: String
    let trainSymbol: String
    let railroad: String
    let startMs: Int64
    var endMs: Int64 = 0
    var distanceMiles: Double = 0
    var boardingStation: String? = nil
    var alightingStation: String? = nil
    var notes: String? = nil

    var isActive: Bool { endMs == 0 }
    var durationMinutes: Int {
        guard endMs > startMs else { return 0 }
        return Int((endMs - startMs) / 60_000)
    }
    var startDate: Date { Date(timeIntervalSince1970: Double(startMs) / 1000) }
}

struct TimetableStopSwift: Identifiable {
    let id = UUID()
    let code: String
    let scheduledArrival: String?
    let scheduledDeparture: String?
    let actualArrival: String?
    let actualDeparture: String?
    let arrivalStatus: String?
    let departureStatus: String?
    let isBus: Bool
    let hasDeparted: Bool
    let hasArrived: Bool

    init(json: [String: Any]) {
        code               = json["code"] as? String ?? ""
        isBus              = json["bus"]     as? Bool ?? false
        hasDeparted        = json["postdep"] as? Bool ?? false
        hasArrived         = json["postarr"] as? Bool ?? false
        arrivalStatus      = (json["arrCmnt"] as? String)?.nilIfEmpty
        departureStatus    = (json["depCmnt"] as? String)?.nilIfEmpty

        func fmt(_ iso: String?) -> String? {
            guard let s = iso else { return nil }
            let f = ISO8601DateFormatter()
            f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            var d = f.date(from: s)
            if d == nil {
                f.formatOptions = [.withInternetDateTime]
                d = f.date(from: s)
            }
            guard let date = d else { return nil }
            let out = DateFormatter()
            out.dateFormat = "h:mm a"
            return out.string(from: date)
        }

        scheduledArrival   = fmt(json["schArr"] as? String)
        scheduledDeparture = fmt(json["schDep"] as? String)
        actualArrival      = fmt(json["arr"]    as? String)
        actualDeparture    = fmt(json["dep"]    as? String)
    }
}

struct StationDepartureSwift: Identifiable {
    let id = UUID()
    let symbol: String
    let routeName: String
    let stops: [TimetableStopSwift]
}

private extension Optional where Wrapped == String {
    var nilIfEmpty: String? {
        guard let s = self, !s.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return s
    }
}
