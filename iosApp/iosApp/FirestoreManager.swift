import Foundation
import FirebaseFirestore
import FirebaseAuth
import FirebaseStorage

// ── Sighting model ────────────────────────────────────────────────────────────
struct FirestoreSighting: Identifiable, Codable {
    @DocumentID var id: String?
    var railroad: String
    var trainSymbol: String
    var location: String
    var notes: String
    var latitude: Double
    var longitude: Double
    var reporterName: String
    var timestampMs: Double
    var upvotes: Int
    var photoUrl: String?

    var minutesAgo: Int {
        let ms = Date().timeIntervalSince1970 * 1000 - timestampMs
        return max(0, Int(ms / 60000))
    }

    // Client-side only — excluded from Firestore encoding
    var distanceMiles: Double = 0

    enum CodingKeys: String, CodingKey {
        case id, railroad, trainSymbol, location, notes
        case latitude, longitude, reporterName, timestampMs, upvotes, photoUrl
    }
}

// ── Watchlist models ──────────────────────────────────────────────────────────
enum WatchlistType: String, Codable {
    case symbol = "SYMBOL"
    case loco   = "LOCO"
}

struct WatchlistEntry: Identifiable, Codable {
    var id: String
    var type: WatchlistType
    var value: String
    var railroad: String
    var label: String
    var addedMs: Double
}

// ── Railfan Spot model ────────────────────────────────────────────────────────
struct RailfanSpot: Identifiable, Codable {
    @DocumentID var id: String?
    var name: String
    var latitude: Double
    var longitude: Double
    var submittedBy: String
    var railroad: String
    var subdivision: String
    var notes: String
    var safetyNotes: String
    var parkingNotes: String
    var scannerFrequency: String
    var seasonalNotes: String
    var trainFrequency: String
    var isPublicProperty: Bool
    var hasParking: Bool
    var hasRestrooms: Bool
    var hasFood: Bool
    var hasShade: Bool
    var upvotes: Int
    var photoUrls: [String]
    var timestampMs: Double

    var distanceMiles: Double = 0
}

// ── Firestore manager ─────────────────────────────────────────────────────────
@MainActor
class FirestoreManager: ObservableObject {
    static let shared = FirestoreManager()

    private let db = Firestore.firestore()
    private let storage = Storage.storage()

    // ── Auth ──────────────────────────────────────────────────────────────────
    @Published var currentUserId: String? = nil

    // ── Sightings ─────────────────────────────────────────────────────────────
    @Published var sightings: [FirestoreSighting] = []
    @Published var isLoading = false
    @Published var errorMessage: String? = nil
    private var sightingListener: ListenerRegistration?

    // ── Watchlist ─────────────────────────────────────────────────────────────
    @Published var watchlist: [WatchlistEntry] = []
    private var watchlistListener: ListenerRegistration?

    // ── Spots ─────────────────────────────────────────────────────────────────
    @Published var communitySpots: [RailfanSpot] = []
    @Published var isLoadingSpots = false
    @Published var isSubmittingSpot = false
    @Published var spotSubmitError: String? = nil
    private var spotsListener: ListenerRegistration?

    // ── Anonymous auth ────────────────────────────────────────────────────────
    func ensureAuth() async {
        if let user = Auth.auth().currentUser {
            currentUserId = user.uid
            return
        }
        do {
            let result = try await Auth.auth().signInAnonymously()
            currentUserId = result.user.uid
            startWatchlistListener(uid: result.user.uid)
        } catch {
            print("Anonymous auth failed: \(error)")
        }
    }

    // ── Sightings listener ────────────────────────────────────────────────────
    func startListening(lat: Double, lon: Double, radiusMiles: Double = 100) {
        isLoading = true
        sightingListener?.remove()

        sightingListener = db.collection("sightings")
            .order(by: "timestampMs", descending: true)
            .limit(to: 50)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self else { return }
                isLoading = false
                if let error = error { errorMessage = error.localizedDescription; return }
                guard let documents = snapshot?.documents else { return }

                var results: [FirestoreSighting] = []
                for doc in documents {
                    if var s = try? doc.data(as: FirestoreSighting.self) {
                        let dLat = s.latitude - lat
                        let dLon = s.longitude - lon
                        s.distanceMiles = sqrt(dLat * dLat + dLon * dLon) * 69.0
                        if s.distanceMiles <= radiusMiles { results.append(s) }
                    }
                }
                self.sightings = results.sorted { $0.timestampMs > $1.timestampMs }
            }
    }

    func stopListening() {
        sightingListener?.remove()
        sightingListener = nil
    }

    func submitSighting(
        railroad: String, trainSymbol: String, location: String,
        notes: String, lat: Double, lon: Double, reporterName: String = "Railfan"
    ) async throws {
        let sighting = FirestoreSighting(
            railroad: railroad, trainSymbol: trainSymbol, location: location,
            notes: notes, latitude: lat, longitude: lon,
            reporterName: reporterName,
            timestampMs: Date().timeIntervalSince1970 * 1000, upvotes: 0
        )
        try db.collection("sightings").addDocument(from: sighting)
    }

    func upvote(sightingId: String) {
        db.collection("sightings").document(sightingId)
            .updateData(["upvotes": FieldValue.increment(Int64(1))])
    }

    // ── Watchlist ─────────────────────────────────────────────────────────────
    func startWatchlistListener(uid: String) {
        watchlistListener?.remove()
        watchlistListener = db.collection("users").document(uid)
            .collection("watchlist")
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let self, let docs = snapshot?.documents else { return }
                self.watchlist = docs.compactMap { try? $0.data(as: WatchlistEntry.self) }
                    .sorted { $0.addedMs > $1.addedMs }
            }
    }

    func addWatchlistEntry(_ entry: WatchlistEntry) async {
        guard let uid = currentUserId else { return }
        try? db.collection("users").document(uid)
            .collection("watchlist").document(entry.id)
            .setData(from: entry)
    }

    func removeWatchlistEntry(id: String) async {
        guard let uid = currentUserId else { return }
        try? await db.collection("users").document(uid)
            .collection("watchlist").document(id).delete()
    }

    // ── Spots ─────────────────────────────────────────────────────────────────
    func startSpotsListener(lat: Double, lon: Double, radiusMiles: Double = 150) {
        isLoadingSpots = true
        spotsListener?.remove()

        spotsListener = db.collection("railfan_spots")
            .order(by: "timestampMs", descending: true)
            .limit(to: 100)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self else { return }
                isLoadingSpots = false
                guard let docs = snapshot?.documents else { return }

                var results: [RailfanSpot] = []
                for doc in docs {
                    if var spot = try? doc.data(as: RailfanSpot.self) {
                        let dLat = spot.latitude - lat
                        let dLon = spot.longitude - lon
                        spot.distanceMiles = sqrt(dLat * dLat + dLon * dLon) * 69.0
                        if spot.distanceMiles <= radiusMiles { results.append(spot) }
                    }
                }
                self.communitySpots = results.sorted { $0.distanceMiles < $1.distanceMiles }
            }
    }

    func submitSpot(_ spot: RailfanSpot, photoData: Data?) async {
        isSubmittingSpot = true
        spotSubmitError = nil
        do {
            var mutableSpot = spot
            if let photoData {
                let spotId = UUID().uuidString
                let ref = storage.reference().child("spots/\(spotId)/\(UUID().uuidString).jpg")
                _ = try await ref.putDataAsync(photoData, metadata: StorageMetadata().then { $0.contentType = "image/jpeg" })
                let url = try await ref.downloadURL()
                mutableSpot.photoUrls = [url.absoluteString]
            }
            try db.collection("railfan_spots").addDocument(from: mutableSpot)
        } catch {
            spotSubmitError = error.localizedDescription
        }
        isSubmittingSpot = false
    }

    func upvoteSpot(spotId: String) {
        db.collection("railfan_spots").document(spotId)
            .updateData(["upvotes": FieldValue.increment(Int64(1))])
    }
}

// ── StorageMetadata convenience ───────────────────────────────────────────────
private extension StorageMetadata {
    func then(_ block: (StorageMetadata) -> Void) -> StorageMetadata {
        block(self); return self
    }
}
