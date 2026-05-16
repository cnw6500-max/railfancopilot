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

    var minutesAgo: Int {
        let ms = Date().timeIntervalSince1970 * 1000 - timestampMs
        return max(0, Int(ms / 60000))
    }

    var distanceMiles: Double = 0
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
    func startListening(lat: Double, lon: Double, radiusMiles: Double = 100) {
        isLoading = true
        listener?.remove()

        listener = db.collection(collection)
            .order(by: "timestampMs", descending: true)
            .limit(to: 50)
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
                        // Calculate distance
                        let dLat = sighting.latitude - lat
                        let dLon = sighting.longitude - lon
                        let distDeg = sqrt(dLat * dLat + dLon * dLon)
                        sighting.distanceMiles = distDeg * 69.0
                        if sighting.distanceMiles <= radiusMiles {
                            results.append(sighting)
                        }
                    }
                }
                self.sightings = results.sorted { $0.timestampMs > $1.timestampMs }
            }
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
