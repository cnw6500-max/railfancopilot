import Foundation
import CoreLocation
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

    // ── Sun info ──────────────────────────────────────────────────────────────
    @Published var sunInfo: SunInfo? = nil

    // ── Saved locations ───────────────────────────────────────────────────────
    @Published var savedLocations: [SavedLocationShared] = []

    // ── Settings ──────────────────────────────────────────────────────────────
    @Published var showAmtrak    = true
    @Published var showCommuter  = true
    @Published var showFreight   = true
    @Published var isPremium     = false

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
        isPremium = UserDefaults.standard.bool(forKey: "isPremium")
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
                    self.trains = trains as? [TrainLocation] ?? []
                    self.isLoadingTrains = false
                    self.checkApproachNotifications()
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
    }

    func deleteLocation(id: String) {
        savedLocations.removeAll { $0.id == id }
        persistLocations()
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
        isPremium = true
        UserDefaults.standard.set(true, forKey: "isPremium")
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
}
