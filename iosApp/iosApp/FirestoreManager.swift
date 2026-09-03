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
    var authorId: String?          // Firebase Auth UID for delete ownership
    var timestampMs: Double
    var upvotes: Int
    var photoUrl: String?
    var commentCount: Int = 0

    var minutesAgo: Int {
        let ms = Date().timeIntervalSince1970 * 1000 - timestampMs
        return max(0, Int(ms / 60000))
    }

    // Client-side only — excluded from Firestore encoding
    var distanceMiles: Double = 0

    enum CodingKeys: String, CodingKey {
        case id, railroad, trainSymbol, location, notes
        case latitude, longitude, reporterName, authorId, reporterUid
        case timestampMs, upvotes, photoUrl, commentCount
    }

    init(id: String? = nil, railroad: String, trainSymbol: String, location: String,
         notes: String, latitude: Double, longitude: Double, reporterName: String,
         authorId: String? = nil, timestampMs: Double, upvotes: Int,
         photoUrl: String? = nil, commentCount: Int = 0) {
        self.id = id
        self.railroad = railroad
        self.trainSymbol = trainSymbol
        self.location = location
        self.notes = notes
        self.latitude = latitude
        self.longitude = longitude
        self.reporterName = reporterName
        self.authorId = authorId
        self.timestampMs = timestampMs
        self.upvotes = upvotes
        self.photoUrl = photoUrl
        self.commentCount = commentCount
    }

    // Custom decoder: tolerates documents written by the Android app, which
    // don't include commentCount and use "reporterUid" instead of "authorId".
    // Swift's auto-synthesized Decodable treats a missing non-optional key
    // (like commentCount) as a hard failure, silently dropping the whole
    // document — this decodes every field leniently instead.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // @DocumentID would normally be populated by Firestore's decoder via
        // decoder.userInfo, but that key (documentRefUserInfoKey) turned out to
        // be `internal` in this Firebase SDK version, and DocumentID<String?>
        // (from:) fails a DocumentIDWrappable conformance check on top of that
        // — both routes are effectively version-locked, non-public API. There
        // is no field named "id" in the document body either, so this decodes
        // to nil here; callers (see startListening below) set the real id from
        // the public, version-stable `document.documentID` after decoding.
        // Leaving `id` nil at this point is fine as long as every call site
        // fills it in — forgetting to is exactly what caused every sighting
        // card to collapse onto the last-decoded sighting (ForEach keys rows
        // by `id`; every row sharing nil collapses to one identity).
        id = try c.decodeIfPresent(String.self, forKey: .id)
        railroad = try c.decodeIfPresent(String.self, forKey: .railroad) ?? "Unknown"
        trainSymbol = try c.decodeIfPresent(String.self, forKey: .trainSymbol) ?? ""
        location = try c.decodeIfPresent(String.self, forKey: .location) ?? "Unknown location"
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        latitude = try c.decodeIfPresent(Double.self, forKey: .latitude) ?? 0
        longitude = try c.decodeIfPresent(Double.self, forKey: .longitude) ?? 0
        reporterName = try c.decodeIfPresent(String.self, forKey: .reporterName) ?? "Railfan"
        authorId = try c.decodeIfPresent(String.self, forKey: .authorId)
            ?? c.decodeIfPresent(String.self, forKey: .reporterUid)
        timestampMs = try c.decodeIfPresent(Double.self, forKey: .timestampMs) ?? 0
        upvotes = try c.decodeIfPresent(Int.self, forKey: .upvotes) ?? 0
        photoUrl = try c.decodeIfPresent(String.self, forKey: .photoUrl)
        commentCount = try c.decodeIfPresent(Int.self, forKey: .commentCount) ?? 0
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(id, forKey: .id)
        try c.encode(railroad, forKey: .railroad)
        try c.encode(trainSymbol, forKey: .trainSymbol)
        try c.encode(location, forKey: .location)
        try c.encode(notes, forKey: .notes)
        try c.encode(latitude, forKey: .latitude)
        try c.encode(longitude, forKey: .longitude)
        try c.encode(reporterName, forKey: .reporterName)
        try c.encodeIfPresent(authorId, forKey: .authorId)
        try c.encode(timestampMs, forKey: .timestampMs)
        try c.encode(upvotes, forKey: .upvotes)
        try c.encodeIfPresent(photoUrl, forKey: .photoUrl)
        try c.encode(commentCount, forKey: .commentCount)
    }
}

// ── Sighting comment model ────────────────────────────────────────────────────
struct SightingComment: Identifiable, Codable {
    @DocumentID var id: String?
    var authorName: String
    var authorId: String
    var text: String
    var timestampMs: Double

    var minutesAgo: Int {
        let ms = Date().timeIntervalSince1970 * 1000 - timestampMs
        return max(0, Int(ms / 60000))
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

    // ── Comments ──────────────────────────────────────────────────────────────
    @Published var commentsBySighting: [String: [SightingComment]] = [:]

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
                        s.id = doc.documentID   // public API — see the decoder comment above
                        s.distanceMiles = FirestoreManager.haversineMiles(
                            lat1: lat, lon1: lon, lat2: s.latitude, lon2: s.longitude)
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
        notes: String, lat: Double, lon: Double,
        reporterName: String = "Railfan",
        photoData: Data? = nil
    ) async throws {
        // Upload photo to Firebase Storage first if provided
        var photoUrl: String? = nil
        if let photoData {
            let ref = storage.reference()
                .child("sightings/\(UUID().uuidString)/photo.jpg")
            let meta = StorageMetadata()
            meta.contentType = "image/jpeg"
            _ = try await ref.putDataAsync(photoData, metadata: meta)
            photoUrl = try await ref.downloadURL().absoluteString
        }

        var sighting = FirestoreSighting(
            railroad: railroad, trainSymbol: trainSymbol, location: location,
            notes: notes, latitude: lat, longitude: lon,
            reporterName: reporterName,
            authorId: currentUserId,
            timestampMs: Date().timeIntervalSince1970 * 1000, upvotes: 0
        )
        sighting.photoUrl = photoUrl
        try db.collection("sightings").addDocument(from: sighting)
    }

    func upvote(sightingId: String) {
        db.collection("sightings").document(sightingId)
            .updateData(["upvotes": FieldValue.increment(Int64(1))])
    }

    func deleteSighting(sightingId: String) async throws {
        try await db.collection("sightings").document(sightingId).delete()
    }

    // ── Comments ──────────────────────────────────────────────────────────────
    func fetchComments(sightingId: String) {
        db.collection("sightings").document(sightingId)
            .collection("comments")
            .order(by: "timestampMs", descending: false)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let self, let docs = snapshot?.documents else { return }
                let comments = docs.compactMap { try? $0.data(as: SightingComment.self) }
                Task { @MainActor in
                    self.commentsBySighting[sightingId] = comments
                }
            }
    }

    func addComment(sightingId: String, text: String, authorName: String) async throws {
        guard let uid = currentUserId else { return }
        let comment = SightingComment(
            authorName: authorName, authorId: uid,
            text: text, timestampMs: Date().timeIntervalSince1970 * 1000
        )
        let ref = db.collection("sightings").document(sightingId).collection("comments")
        try ref.addDocument(from: comment)
        // Increment comment count on parent (fire-and-forget)
        try? await db.collection("sightings").document(sightingId)
            .updateData(["commentCount": FieldValue.increment(Int64(1))])
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
                        spot.distanceMiles = FirestoreManager.haversineMiles(
                            lat1: lat, lon1: lon, lat2: spot.latitude, lon2: spot.longitude)
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

    // Great-circle distance in miles (more accurate than a flat lat/lon approximation)
    static func haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let R = 3958.8
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat/2) * sin(dLat/2) +
                cos(lat1 * .pi/180) * cos(lat2 * .pi/180) * sin(dLon/2) * sin(dLon/2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}

// ── StorageMetadata convenience ───────────────────────────────────────────────
private extension StorageMetadata {
    func then(_ block: (StorageMetadata) -> Void) -> StorageMetadata {
        block(self); return self
    }
}
