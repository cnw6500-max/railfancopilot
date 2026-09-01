import Foundation

// ── Persistent model types ────────────────────────────────────────────────────

struct LocoIDRecord: Codable, Identifiable {
    let id: String
    let timestamp: Date
    let resultText: String
    let railroadGuess: String
    let locationName: String
    var thumbnailJpegBase64: String?  // compressed 120×120 thumbnail
}

struct DecodeHistoryRecord: Codable, Identifiable {
    let id: String
    let timestamp: Date
    let symbol: String
    let railroadName: String
    let origin: String
    let destination: String
    let type: String
}

struct AchievementRecord: Codable, Identifiable {
    let id: String
    let title: String
    let description: String
    let emoji: String
    var earned: Bool
    var earnedDate: Date?
}

struct TaggedPhotoRecord: Codable, Identifiable {
    let id: String
    let timestamp: Date
    let symbol: String
    let railroad: String
    let locationName: String
    let latitude: Double
    let longitude: Double
    var notes: String
}

struct TripLogSession: Codable, Identifiable {
    let id: String
    var date: Date
    var locationName: String
    var railroad: String
    var notes: String
    var trainsSeen: [TripLogTrain]
}

struct TripLogTrain: Codable, Identifiable {
    let id: String
    var symbol: String
    var railroad: String
    var type: String      // e.g. "Intermodal", "Coal", "Passenger"
    var speedMph: Int
    var notes: String
}

struct StationDeparture: Identifiable {
    let id = UUID()
    let trainName: String
    let routeName: String
    let scheduledDep: String
    let estimatedDep: String?
    let status: String
}

struct TransmissionEntry: Identifiable {
    let id: String
    let channelName: String
    let frequencyMhz: Double
    let note: String
    let trainSymbol: String?
    let timestamp: Date
}

struct ClassificationYard: Identifiable {
    let id: String
    let name: String
    let railroad: String
    let latitude: Double
    let longitude: Double
    let description: String
    let warning: String
    let frequency: String
}

struct CustomRailfanSpot: Codable, Identifiable {
    let id: String
    var name: String
    var location: String
    var state: String
    var railroad: String
    var subdivision: String
    var description: String
    var photoTips: String
    var bestTime: String
    var access: String     // "Public", "Park / Preserve", "Roadside"
    var latitude: Double
    var longitude: Double
    var scannerFreq: String
    var createdDate: Date
}

// ── PersistenceManager ────────────────────────────────────────────────────────

final class PersistenceManager {
    static let shared = PersistenceManager()
    private let docsURL: URL

    private init() {
        docsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    private func fileURL(_ name: String) -> URL { docsURL.appendingPathComponent(name) }

    private func load<T: Decodable>(_ name: String) -> T? {
        guard let data = try? Data(contentsOf: fileURL(name)) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private func save<T: Encodable>(_ value: T, to name: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: fileURL(name), options: .atomic)
    }

    // ── Loco ID history ───────────────────────────────────────────────────────
    func loadLocoIDHistory() -> [LocoIDRecord] { load("locoIDHistory.json") ?? [] }
    func saveLocoIDHistory(_ r: [LocoIDRecord]) { save(r, to: "locoIDHistory.json") }

    // ── Decode history ────────────────────────────────────────────────────────
    func loadDecodeHistory() -> [DecodeHistoryRecord] { load("decodeHistory.json") ?? [] }
    func saveDecodeHistory(_ r: [DecodeHistoryRecord]) { save(r, to: "decodeHistory.json") }

    // ── Achievements ──────────────────────────────────────────────────────────
    func loadAchievements() -> [AchievementRecord] {
        load("achievements.json") ?? defaultAchievements
    }
    func saveAchievements(_ r: [AchievementRecord]) { save(r, to: "achievements.json") }

    // ── Tagged photos ─────────────────────────────────────────────────────────
    func loadTaggedPhotos() -> [TaggedPhotoRecord] { load("taggedPhotos.json") ?? [] }
    func saveTaggedPhotos(_ r: [TaggedPhotoRecord]) { save(r, to: "taggedPhotos.json") }

    // ── Trip log ──────────────────────────────────────────────────────────────
    func loadTripLog() -> [TripLogSession] { load("tripLog.json") ?? [] }
    func saveTripLog(_ sessions: [TripLogSession]) { save(sessions, to: "tripLog.json") }

    // ── Custom spots ──────────────────────────────────────────────────────────
    func loadCustomSpots() -> [CustomRailfanSpot] { load("customSpots.json") ?? [] }
    func saveCustomSpots(_ r: [CustomRailfanSpot]) { save(r, to: "customSpots.json") }

    // ── Decode count (for App Store review prompt) ────────────────────────────
    func loadDecodeCount() -> Int { UserDefaults.standard.integer(forKey: "decodeCount") }
    func incrementDecodeCount() -> Int {
        let n = loadDecodeCount() + 1
        UserDefaults.standard.set(n, forKey: "decodeCount")
        return n
    }

    private let defaultAchievements: [AchievementRecord] = [
        AchievementRecord(id: "a1", title: "Heritage Spotter", description: "Photograph a heritage unit",               emoji: "⭐", earned: false, earnedDate: nil),
        AchievementRecord(id: "a2", title: "Night Owl",        description: "Capture a night shot after dark",         emoji: "🌙", earned: false, earnedDate: nil),
        AchievementRecord(id: "a3", title: "Grain Rush",       description: "Spot a train during grain rush season",   emoji: "🌾", earned: false, earnedDate: nil),
        AchievementRecord(id: "a4", title: "Double Stack",     description: "Photograph a double-stack intermodal",    emoji: "📦", earned: false, earnedDate: nil),
        AchievementRecord(id: "a5", title: "Speed Demon",      description: "See a train exceed 79 mph",               emoji: "⚡", earned: false, earnedDate: nil),
        AchievementRecord(id: "a6", title: "Yard Master",      description: "Visit 5 classification yards",            emoji: "🚂", earned: false, earnedDate: nil),
    ]
}
