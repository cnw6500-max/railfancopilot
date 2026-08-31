import Foundation
import FirebaseFirestore

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

    // Display-only — never written to Firestore
    var distanceMiles: Double = 0

    var minutesAgo: Int {
        let ms = Date().timeIntervalSince1970 * 1000 - timestampMs
        return max(0, Int(ms / 60000))
    }

    // Exclude distanceMiles from Codable encoding so it is never uploaded to Firestore
    enum CodingKeys: String, CodingKey {
        case id, railroad, trainSymbol, location, notes
        case latitude, longitude, reporterName, timestampMs, upvotes
    }
}

// ── Firestore manager ─────────────────────────────────────────────────────────
@MainActor
class FirestoreManager: ObservableObject {
    static let shared = FirestoreManager()

    private let db = Firestore.firestore()
    private let collection = "sightings"

    @Published var sightings: [FirestoreSighting] = []
    @Published var isLoading = false
    @Published var errorMessage: String? = nil

    private var listener: ListenerRegistration?

    // ── Listen for real-time sightings ────────────────────────────────────────
    // Fetches the 100 most recent sightings globally. Distance is stamped onto
    // each sighting for display; radius filtering is done in the UI so the
    // slider works without restarting the listener.
    func startListening(lat: Double, lon: Double) {
        // Don't restart if already listening — just update distances
        if listener != nil {
            updateLocation(lat: lat, lon: lon)
            return
        }
        isLoading = true
        userLat = lat
        userLon = lon

        listener = db.collection(collection)
            .order(by: "timestampMs", descending: true)
            .limit(to: 100)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self else { return }
                isLoading = false

                if let error = error {
                    errorMessage = error.localizedDescription
                    return
                }

                guard let documents = snapshot?.documents else { return }

                var results: [FirestoreSighting] = []
                for doc in documents {
                    if var sighting = try? doc.data(as: FirestoreSighting.self) {
                        sighting.distanceMiles = haversineMiles(
                            lat1: self.userLat, lon1: self.userLon,
                            lat2: sighting.latitude, lon2: sighting.longitude)
                        results.append(sighting)
                    }
                }
                self.sightings = results.sorted { $0.timestampMs > $1.timestampMs }
            }
    }

    // Update distances when user location changes without restarting the listener
    func updateLocation(lat: Double, lon: Double) {
        userLat = lat
        userLon = lon
        sightings = sightings.map {
            var s = $0
            s.distanceMiles = haversineMiles(lat1: lat, lon1: lon,
                                             lat2: s.latitude, lon2: s.longitude)
            return s
        }
    }

    private var userLat: Double = 41.8781
    private var userLon: Double = -87.6298

    private func haversineMiles(lat1: Double, lon1: Double,
                                lat2: Double, lon2: Double) -> Double {
        let R = 3958.8
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat/2)*sin(dLat/2) +
                cos(lat1 * .pi/180)*cos(lat2 * .pi/180)*sin(dLon/2)*sin(dLon/2)
        return R * 2 * atan2(sqrt(a), sqrt(1-a))
    }

    func stopListening() {
        listener?.remove()
        listener = nil
    }

    // ── Submit a new sighting ─────────────────────────────────────────────────
    func submitSighting(
        railroad: String,
        trainSymbol: String,
        location: String,
        notes: String,
        lat: Double,
        lon: Double,
        reporterName: String = "Railfan"
    ) async throws {
        let sighting = FirestoreSighting(
            railroad: railroad,
            trainSymbol: trainSymbol,
            location: location,
            notes: notes,
            latitude: lat,
            longitude: lon,
            reporterName: reporterName,
            timestampMs: Date().timeIntervalSince1970 * 1000,
            upvotes: 0
        )

        try db.collection(collection).addDocument(from: sighting)
    }

    // ── Upvote a sighting ─────────────────────────────────────────────────────
    func upvote(sightingId: String) {
        db.collection(collection).document(sightingId)
            .updateData(["upvotes": FieldValue.increment(Int64(1))])
    }
}
