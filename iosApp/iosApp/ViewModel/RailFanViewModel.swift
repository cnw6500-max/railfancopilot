import Foundation
import UIKit
import CoreLocation
import StoreKit
import UIKit
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
    @Published var selectedRailroad: String? = nil  // Railroad.name or nil = all
    @Published var lastRefreshDate: Date? = nil
    @Published var fetchError: String? = nil
    @Published var trainTrails: [String: [CLLocationCoordinate2D]] = [:]
    /// trainId → last 10 speed readings (mph), oldest first — used for sparkline
    @Published var speedHistory: [String: [Int]] = [:]

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
    private var reviewPromptedPurchase  = UserDefaults.standard.bool(forKey: "reviewPurchaseDone")
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
    /// Fires once, ever — covers both a live purchase completing this session AND
    /// an existing Pro subscriber's next launch after this update ships (call this
    /// anywhere isPurchased is confirmed true: unlockPremium() and the entitlement check).
    func maybeRequestReviewOnPurchase() {
        guard !reviewPromptedPurchase else { return }
        reviewPromptedPurchase = true
        UserDefaults.standard.set(true, forKey: "reviewPurchaseDone")
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

    // ── Trip logging (shared-KMM based; drives the live map indicator) ────────
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
        loadTrips()
        resetRefreshTimer()
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
            let isFirst = self.userLocation == nil
            self.userLocation = loc.coordinate
            self.updateSunInfo()
            // Only reverse-geocode if moved more than 500m since last geocode
            if self.lastGeocodedLocation == nil ||
               self.lastGeocodedLocation!.distance(from: loc) > 500 {
                self.lastGeocodedLocation = loc
                self.reverseGeocodeCurrentLocation()
            }
            if self.lastRefreshDate == nil { self.refreshTrains() } // initial fetch only
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
                    // Accumulate speed history for sparkline (last 10 readings per train)
                    var updated = self.speedHistory
                    for t in fetched {
                        var hist = updated[t.id] ?? []
                        hist.append(Int(t.speedMph))
                        updated[t.id] = Array(hist.suffix(10))
                    }
                    updated.keys.filter { id in !fetched.contains { $0.id == id } }
                        .forEach { updated.removeValue(forKey: $0) }
                    self.speedHistory = updated
                    // Accumulate active trip distance
                    self.accumulateTripDistance()
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
        isPurchased = true
        UserDefaults.standard.set(true, forKey: "isPremium")
        maybeRequestReviewOnPurchase()
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
        guard train.railroad == .amtrak else {
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
