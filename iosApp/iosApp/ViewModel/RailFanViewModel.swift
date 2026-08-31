import Foundation
import UIKit
import CoreLocation
import StoreKit
import shared   // KMP XCFramework

struct NominatimPlace: Decodable, Identifiable {
    let place_id: Int
    let display_name: String
    let lat: String
    let lon: String
    var id: Int { place_id }
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: Double(lat) ?? 0, longitude: Double(lon) ?? 0)
    }
}

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
    @Published var selectedRailroad: String? = nil
    @Published var lastRefreshDate: Date? = nil
    @Published var fetchError: String? = nil
    @Published var trainTrails: [String: [CLLocationCoordinate2D]] = [:]

    // ── Map search ────────────────────────────────────────────────────────────
    @Published var searchResults: [NominatimPlace] = []
    @Published var isSearchingLocation = false

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
    private var pendingLocoJpeg: Data? = nil  // thumbnail held until result arrives

    // ── Sun info ──────────────────────────────────────────────────────────────
    @Published var sunInfo: SunInfo? = nil

    // ── Saved locations ───────────────────────────────────────────────────────
    @Published var savedLocations: [SavedLocationShared] = []

    // ── Persistence collections ───────────────────────────────────────────────
    @Published var locoIDHistory: [LocoIDRecord] = []
    @Published var decodeHistory: [DecodeHistoryRecord] = []
    @Published var achievements: [AchievementRecord] = []
    @Published var taggedPhotos: [TaggedPhotoRecord] = []
    @Published var newAchievement: AchievementRecord? = nil

    // ── Settings — agency toggles ─────────────────────────────────────────────
    @Published var showAmtrak    = true { didSet { guard !isLoadingSettings else { return }; saveBool(showAmtrak,    "showAmtrak")    } }
    @Published var showCommuter  = true { didSet { guard !isLoadingSettings else { return }; saveBool(showCommuter,  "showCommuter")  } }
    @Published var showFreight   = true { didSet { guard !isLoadingSettings else { return }; saveBool(showFreight,   "showFreight")   } }
    @Published var showMBTA      = true { didSet { guard !isLoadingSettings else { return }; saveBool(showMBTA,      "showMBTA")      } }
    @Published var showSEPTA     = true { didSet { guard !isLoadingSettings else { return }; saveBool(showSEPTA,     "showSEPTA")     } }
    @Published var showMetra     = true { didSet { guard !isLoadingSettings else { return }; saveBool(showMetra,     "showMetra")     } }
    @Published var showLIRR      = true { didSet { guard !isLoadingSettings else { return }; saveBool(showLIRR,      "showLIRR")      } }
    @Published var showMetroNorth = true { didSet { guard !isLoadingSettings else { return }; saveBool(showMetroNorth, "showMetroNorth") } }
    @Published var showCaltrain  = true { didSet { guard !isLoadingSettings else { return }; saveBool(showCaltrain,  "showCaltrain")  } }
    @Published var showSoundTransit = true { didSet { guard !isLoadingSettings else { return }; saveBool(showSoundTransit, "showSoundTransit") } }

    // ── Settings — map / refresh ──────────────────────────────────────────────
    @Published var refreshIntervalSec: Int = 30  { didSet { guard !isLoadingSettings else { return }; UserDefaults.standard.set(refreshIntervalSec, forKey: "refreshIntervalSec"); resetRefreshTimer() } }
    @Published var trainRadiusMiles: Int   = 500 { didSet { guard !isLoadingSettings else { return }; UserDefaults.standard.set(trainRadiusMiles,   forKey: "trainRadiusMiles")   } }
    @Published var railOverlayDefault: Bool = false { didSet { guard !isLoadingSettings else { return }; saveBool(railOverlayDefault, "railOverlayDefault") } }
    @Published var isPremium = false

    // ── Community ─────────────────────────────────────────────────────────────
    @Published var userName: String = UserDefaults.standard.string(forKey: "userName") ?? "Railfan" {
        didSet { UserDefaults.standard.set(userName, forKey: "userName") }
    }
    @Published var reportRadiusMiles: Int = 100 {
        didSet { guard !isLoadingSettings else { return }; UserDefaults.standard.set(reportRadiusMiles, forKey: "reportRadiusMiles") }
    }

    // ── Station Board ─────────────────────────────────────────────────────────
    @Published var stationDepartures: [StationDeparture] = []
    @Published var stationDeparturesLoading = false
    @Published var stationDeparturesError: String? = nil

    // ── Transmission Log ──────────────────────────────────────────────────────
    @Published var transmissionLog: [TransmissionEntry] = []

    // ── Trial ─────────────────────────────────────────────────────────────────
    var isInTrial: Bool {
        guard let start = UserDefaults.standard.object(forKey: "trialStartDate") as? Date else { return false }
        return Date().timeIntervalSince(start) < 7 * 86400
    }
    var trialDaysLeft: Int {
        guard let start = UserDefaults.standard.object(forKey: "trialStartDate") as? Date else { return 0 }
        let remaining = 7 - Int(Date().timeIntervalSince(start) / 86400)
        return max(0, remaining)
    }

    // ── Approach Notifications ────────────────────────────────────────────────
    @Published var approachEtaThreshold: Int = 10
    @Published var approachNotificationsEnabled: Bool = false
    private var notifiedTrainIds: Set<String> = []

    // ── Geocode throttle ──────────────────────────────────────────────────────
    private var lastGeocodedLocation: CLLocation? = nil

    // ── Init guard — suppresses didSet side effects during loadSettings() ─────
    private var isLoadingSettings = false

    // ── Auto-refresh timer ────────────────────────────────────────────────────
    private var refreshTimer: Timer?

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
        loadSettings()
        loadPersistence()
        resetRefreshTimer()
    }

    deinit { helper.cancel(); refreshTimer?.invalidate() }

    private func loadSettings() {
        isLoadingSettings = true
        defer { isLoadingSettings = false }
        let ud = UserDefaults.standard
        isPremium        = ud.bool(forKey: "isPremium")
        showAmtrak       = ud.object(forKey: "showAmtrak")       == nil ? true : ud.bool(forKey: "showAmtrak")
        showCommuter     = ud.object(forKey: "showCommuter")     == nil ? true : ud.bool(forKey: "showCommuter")
        showFreight      = ud.object(forKey: "showFreight")      == nil ? true : ud.bool(forKey: "showFreight")
        showMBTA         = ud.object(forKey: "showMBTA")         == nil ? true : ud.bool(forKey: "showMBTA")
        showSEPTA        = ud.object(forKey: "showSEPTA")        == nil ? true : ud.bool(forKey: "showSEPTA")
        showMetra        = ud.object(forKey: "showMetra")        == nil ? true : ud.bool(forKey: "showMetra")
        showLIRR         = ud.object(forKey: "showLIRR")         == nil ? true : ud.bool(forKey: "showLIRR")
        showMetroNorth   = ud.object(forKey: "showMetroNorth")   == nil ? true : ud.bool(forKey: "showMetroNorth")
        showCaltrain     = ud.object(forKey: "showCaltrain")     == nil ? true : ud.bool(forKey: "showCaltrain")
        showSoundTransit = ud.object(forKey: "showSoundTransit") == nil ? true : ud.bool(forKey: "showSoundTransit")
        railOverlayDefault = ud.bool(forKey: "railOverlayDefault")
        if ud.object(forKey: "refreshIntervalSec") != nil { refreshIntervalSec  = ud.integer(forKey: "refreshIntervalSec") }
        if ud.object(forKey: "trainRadiusMiles")   != nil { trainRadiusMiles    = ud.integer(forKey: "trainRadiusMiles")   }
        if ud.object(forKey: "reportRadiusMiles")  != nil { reportRadiusMiles   = ud.integer(forKey: "reportRadiusMiles")  }
    }

    private func loadPersistence() {
        locoIDHistory = PersistenceManager.shared.loadLocoIDHistory()
        decodeHistory = PersistenceManager.shared.loadDecodeHistory()
        achievements  = PersistenceManager.shared.loadAchievements()
        taggedPhotos  = PersistenceManager.shared.loadTaggedPhotos()
    }

    private func saveBool(_ v: Bool, _ key: String) { UserDefaults.standard.set(v, forKey: key) }

    // ── CLLocationManagerDelegate ─────────────────────────────────────────────
    nonisolated func locationManager(_ manager: CLLocationManager,
                                     didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        Task { @MainActor in
            self.userLocation = loc.coordinate
            self.updateSunInfo()
            // Only reverse-geocode if moved more than 500m since last geocode
            if self.lastGeocodedLocation == nil ||
               self.lastGeocodedLocation!.distance(from: loc) > 500 {
                self.lastGeocodedLocation = loc
                self.reverseGeocodeCurrentLocation()
            }
            if self.lastRefreshDate == nil { self.refreshTrains() } // initial fetch only
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse ||
           manager.authorizationStatus == .authorizedAlways {
            manager.startUpdatingLocation()
        }
    }

    // ── Auto-refresh timer ────────────────────────────────────────────────────
    private func resetRefreshTimer() {
        refreshTimer?.invalidate()
        let interval = TimeInterval(refreshIntervalSec)
        let t = Timer(timeInterval: interval, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in self?.refreshTrains() }
        }
        RunLoop.main.add(t, forMode: .common)
        refreshTimer = t
    }

    // ── Train refresh ─────────────────────────────────────────────────────────
    func refreshTrains() {
        guard let loc = userLocation else { return }
        isLoadingTrains = true
        fetchError = nil
        helper.getSelectedTrains(
            lat: loc.latitude, lon: loc.longitude,
            includeAmtrak: showAmtrak,
            includeMbta: showMBTA,
            includeSepta: showSEPTA,
            includeMetra: showMetra,
            includeLirr: showLIRR,
            includeMetroNorth: showMetroNorth,
            includeCaltrain: showCaltrain,
            includeSoundTransit: showSoundTransit,
            onSuccess: { [weak self] trains in
                guard let self else { return }
                Task { @MainActor in
                    let fetched = trains as? [TrainLocation] ?? []
                    // Update trails (keep last 10 positions per train)
                    for t in fetched {
                        let coord = CLLocationCoordinate2D(latitude: t.latitude, longitude: t.longitude)
                        var trail = self.trainTrails[t.id] ?? []
                        trail.append(coord)
                        if trail.count > 10 { trail.removeFirst(trail.count - 10) }
                        self.trainTrails[t.id] = trail
                    }
                    // Prune trails for trains no longer in the feed
                    let activeIds = Set(fetched.map { $0.id })
                    self.trainTrails = self.trainTrails.filter { activeIds.contains($0.key) }
                    self.trains = fetched
                    self.isLoadingTrains = false
                    self.lastRefreshDate = Date()
                    self.checkApproachNotifications()
                    self.checkSpeedDemonAchievement()
                    self.checkGrainRushAchievement()
                }
            },
            onError: { [weak self] msg in
                Task { @MainActor in
                    self?.isLoadingTrains = false
                    self?.fetchError = msg
                }
            }
        )
    }

    // ── Filtered trains (agency toggles + railroad chip + radius) ─────────────
    var filteredTrains: [TrainLocation] {
        trains.filter { t in
            // Railroad chip filter
            if let f = selectedRailroad, t.railroad.name != f { return false }
            // Agency toggles
            if !agencyAllowed(t.railroad.name) { return false }
            // Radius filter
            if let loc = userLocation {
                let userCL  = CLLocation(latitude: loc.latitude, longitude: loc.longitude)
                let trainCL = CLLocation(latitude: t.latitude,   longitude: t.longitude)
                let miles = userCL.distance(from: trainCL) / 1609.34
                if miles > Double(trainRadiusMiles) { return false }
            }
            return true
        }
    }

    private func agencyAllowed(_ name: String) -> Bool {
        switch name {
        case "AMTRAK":                             return showAmtrak
        case "BNSF", "UP", "CSX", "NS", "CN", "CP", "KCS": return showFreight
        default:                                   return showCommuter
        }
    }

    var nearestTrain: TrainLocation? {
        guard let loc = userLocation else { return nil }
        let userCL = CLLocation(latitude: loc.latitude, longitude: loc.longitude)
        return trains.min {
            let a = CLLocation(latitude: $0.latitude, longitude: $0.longitude)
            let b = CLLocation(latitude: $1.latitude, longitude: $1.longitude)
            return userCL.distance(from: a) < userCL.distance(from: b)
        }
    }

    func setRailroadFilter(_ name: String?) { selectedRailroad = name }

    // ── Location search (Nominatim) ───────────────────────────────────────────
    func searchLocations(query: String) {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            searchResults = []; return
        }
        isSearchingLocation = true
        Task {
            let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            let urlStr = "https://nominatim.openstreetmap.org/search?q=\(encoded)&format=json&limit=5"
            guard let url = URL(string: urlStr) else { isSearchingLocation = false; return }
            var req = URLRequest(url: url)
            req.setValue("RailfanCopilot/iOS", forHTTPHeaderField: "User-Agent")
            do {
                let (data, _) = try await URLSession.shared.data(for: req)
                let places = (try? JSONDecoder().decode([NominatimPlace].self, from: data)) ?? []
                searchResults = places
            } catch { searchResults = [] }
            isSearchingLocation = false
        }
    }

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
                if let result = helper.parseDecodeResult(json: json) {
                    decoderResult = result
                    saveDecodeResult(result)
                } else {
                    decoderError = "Could not parse decode result"
                }
            } catch {
                decoderError = error.localizedDescription
            }
            isDecoding = false
        }
    }

    private func saveDecodeResult(_ result: TrainSymbolDecodeResult) {
        let record = DecodeHistoryRecord(
            id: UUID().uuidString,
            timestamp: Date(),
            symbol: result.symbol,
            railroadName: result.railroad.displayName,
            origin: result.origin,
            destination: result.destination,
            type: result.type
        )
        decodeHistory.insert(record, at: 0)
        if decodeHistory.count > 100 { decodeHistory = Array(decodeHistory.prefix(100)) }
        PersistenceManager.shared.saveDecodeHistory(decodeHistory)
        // App Store review prompt at milestone counts
        let count = PersistenceManager.shared.incrementDecodeCount()
        if count == 5 || count == 25 {
            if let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene }).first {
                SKStoreReviewController.requestReview(in: scene)
            }
        }
    }

    // ── Loco identifier ───────────────────────────────────────────────────────
    func identifyLoco(jpegData: Data) {
        isIdentifying = true
        locoIdResult = nil
        locoIdError  = nil
        // Store a 120×120 thumbnail for history display
        pendingLocoJpeg = makeThumbnail(from: jpegData, size: 120)
        Task {
            do {
                let result = try await FirebaseFunctionsClient.shared.identifyLocomotive(jpegData: jpegData)
                locoIdResult = result
                checkAchievementsForLocoID(result: result)
            } catch {
                locoIdError = error.localizedDescription
            }
            isIdentifying = false
        }
    }

    private func makeThumbnail(from jpegData: Data, size: CGFloat) -> Data? {
        guard let src = UIImage(data: jpegData) else { return nil }
        let side = min(src.size.width, src.size.height)
        let origin = CGPoint(x: (src.size.width - side) / 2, y: (src.size.height - side) / 2)
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
        let thumb = renderer.image { _ in
            src.draw(in: CGRect(x: -origin.x * size / side, y: -origin.y * size / side,
                                width: src.size.width * size / side, height: src.size.height * size / side))
        }
        return thumb.jpegData(compressionQuality: 0.5)
    }

    func saveLocoID(result: String, locationName: String, railroad: String, thumbnailData: Data? = nil) {
        let thumb = thumbnailData.map { $0.base64EncodedString() }
        let record = LocoIDRecord(
            id: UUID().uuidString,
            timestamp: Date(),
            resultText: result,
            railroadGuess: railroad,
            locationName: locationName,
            thumbnailJpegBase64: thumb
        )
        locoIDHistory.insert(record, at: 0)
        if locoIDHistory.count > 100 { locoIDHistory = Array(locoIDHistory.prefix(100)) }
        PersistenceManager.shared.saveLocoIDHistory(locoIDHistory)
    }

    func saveTaggedPhoto(symbol: String, railroad: String, location: String,
                         lat: Double, lon: Double, notes: String) {
        let record = TaggedPhotoRecord(
            id: UUID().uuidString,
            timestamp: Date(),
            symbol: symbol,
            railroad: railroad,
            locationName: location,
            latitude: lat,
            longitude: lon,
            notes: notes
        )
        taggedPhotos.insert(record, at: 0)
        if taggedPhotos.count > 200 { taggedPhotos = Array(taggedPhotos.prefix(200)) }
        PersistenceManager.shared.saveTaggedPhotos(taggedPhotos)
    }

    // ── Achievement logic ─────────────────────────────────────────────────────
    func unlockAchievement(id: String) {
        guard let idx = achievements.firstIndex(where: { $0.id == id }),
              !achievements[idx].earned else { return }
        achievements[idx].earned = true
        achievements[idx].earnedDate = Date()
        newAchievement = achievements[idx]
        PersistenceManager.shared.saveAchievements(achievements)
    }

    func consumeNewAchievement() { newAchievement = nil }

    private func checkAchievementsForLocoID(result: String) {
        let lower = result.lowercased()
        let hour = Calendar.current.component(.hour, from: Date())
        if hour >= 21 || hour < 5 { unlockAchievement(id: "a2") }
        if lower.contains("heritage") || lower.contains("historic") || lower.contains("excursion") {
            unlockAchievement(id: "a1")
        }
        if lower.contains("intermodal") || lower.contains("double stack") || lower.contains("container") {
            unlockAchievement(id: "a4")
        }
    }

    private func checkSpeedDemonAchievement() {
        if trains.contains(where: { Int($0.speedMph) > 79 }) { unlockAchievement(id: "a5") }
    }

    private func checkGrainRushAchievement() {
        let month = Calendar.current.component(.month, from: Date())
        if (8...10).contains(month) && !trains.isEmpty { unlockAchievement(id: "a3") }
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

    // ── Saved locations ───────────────────────────────────────────────────────
    private func loadSavedLocations() {
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
                let miles = locCL.distance(from: trainCL) / 1609.34
                let speed = Double(train.speedMph)
                guard speed > 5 else { continue }
                let etaMinutes = Int((miles / speed) * 60)
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
        // Evict oldest half when the set grows large to avoid unbounded growth
        // without losing all state (which would re-trigger all notifications)
        if notifiedTrainIds.count > 200 {
            notifiedTrainIds = Set(notifiedTrainIds.dropFirst(100))
        }
    }

    // ── Station departures ────────────────────────────────────────────────────
    func loadStationDepartures(code: String) {
        let upper = code.trimmingCharacters(in: .whitespaces).uppercased()
        guard !upper.isEmpty else { return }
        stationDeparturesLoading = true
        stationDeparturesError   = nil
        stationDepartures        = []
        Task {
            do {
                let json = try await FirebaseFunctionsClient.shared.getStationDepartures(stationCode: upper)
                let deps = parseStationDepartures(json: json, stationCode: upper)
                if deps.isEmpty {
                    stationDeparturesError = "No trains found for \"\(upper)\""
                } else {
                    stationDepartures = deps
                }
            } catch {
                stationDeparturesError = "Couldn't load departures — check your connection"
            }
            stationDeparturesLoading = false
        }
    }

    func clearStationDepartures() {
        stationDepartures      = []
        stationDeparturesError = nil
    }

    private func parseStationDepartures(json: String, stationCode: String) -> [StationDeparture] {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return root.compactMap { d -> StationDeparture? in
            let trainFromName = d["trainName"] as? String
            let trainFromNum  = (d["trainNum"] as? Int).map { "Amtrak #\($0)" }
                             ?? (d["trainNum"] as? String).map { "Amtrak #\($0)" }
            guard let train = trainFromName ?? trainFromNum,
                  let route = d["routeName"] as? String ?? d["route"] as? String else { return nil }
            let schDep = d["scheduledDeparture"] as? String ?? d["schDep"] as? String ?? ""
            let estDep = d["estimatedDeparture"] as? String ?? d["estDep"] as? String
            let status = d["status"] as? String ?? ""
            return StationDeparture(trainName: train, routeName: route,
                                    scheduledDep: schDep, estimatedDep: estDep, status: status)
        }
    }

    // ── Transmission log ──────────────────────────────────────────────────────
    func logTransmission(channelName: String, frequencyMhz: Double, note: String, trainSymbol: String?) {
        let entry = TransmissionEntry(
            id: UUID().uuidString,
            channelName: channelName,
            frequencyMhz: frequencyMhz,
            note: note,
            trainSymbol: trainSymbol,
            timestamp: Date()
        )
        transmissionLog.insert(entry, at: 0)
        if transmissionLog.count > 20 { transmissionLog = Array(transmissionLog.prefix(20)) }
    }

    // ── Premium ───────────────────────────────────────────────────────────────
    func unlockPremium() {
        isPremium = true
        UserDefaults.standard.set(true, forKey: "isPremium")
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
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
