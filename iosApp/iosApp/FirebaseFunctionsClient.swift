import Foundation
import FirebaseFunctions

// Replaces direct KMP/Anthropic calls for AI features.
// Requires FirebaseFunctions Swift package — add via Xcode:
//   File > Add Package Dependencies > https://github.com/firebase/firebase-ios-sdk
//   Add: FirebaseFunctions target

@MainActor
class FirebaseFunctionsClient {
    static let shared = FirebaseFunctionsClient()

    private let functions = Functions.functions()

    func decodeTrainSymbol(symbol: String, localContext: String) async throws -> String {
        let data: [String: Any] = ["symbol": symbol, "localContext": localContext]
        let result = try await functions.httpsCallable("decodeTrainSymbol").call(data)
        guard let map = result.data as? [String: Any],
              let text = map["text"] as? String else {
            throw NSError(domain: "FirebaseFunctionsClient", code: 0,
                          userInfo: [NSLocalizedDescriptionKey: "Unexpected response from decodeTrainSymbol"])
        }
        return text
    }

    func identifyLocomotive(jpegData: Data) async throws -> String {
        let b64 = jpegData.base64EncodedString()
        let data: [String: Any] = ["base64Image": b64]
        let result = try await functions.httpsCallable("identifyLocomotive").call(data)
        guard let map = result.data as? [String: Any],
              let text = map["text"] as? String else {
            throw NSError(domain: "FirebaseFunctionsClient", code: 0,
                          userInfo: [NSLocalizedDescriptionKey: "Unexpected response from identifyLocomotive"])
        }
        return text
    }

    func analyzeConsist(jpegData: Data) async throws -> String {
        let b64 = jpegData.base64EncodedString()
        let data: [String: Any] = ["base64Image": b64]
        let result = try await functions.httpsCallable("analyzeConsist").call(data)
        guard let map = result.data as? [String: Any],
              let text = map["text"] as? String else {
            throw NSError(domain: "FirebaseFunctionsClient", code: 0,
                          userInfo: [NSLocalizedDescriptionKey: "Unexpected response from analyzeConsist"])
        }
        return text
    }

    func lookupLocoNumber(roadNumber: String) async throws -> String {
        let data: [String: Any] = ["roadNumber": roadNumber]
        let result = try await functions.httpsCallable("lookupLocoNumber").call(data)
        guard let map = result.data as? [String: Any], let text = map["text"] as? String else {
            throw NSError(domain: "FirebaseFunctionsClient", code: -1,
                          userInfo: [NSLocalizedDescriptionKey: "Unexpected response from lookupLocoNumber"])
        }
        return text
    }

    func getStationDepartures(stationCode: String) async throws -> String {
        let data: [String: Any] = ["stationCode": stationCode]
        let result = try await functions.httpsCallable("getStationDepartures").call(data)
        if let map = result.data as? [String: Any], let json = map["departures"] as? String {
            return json
        }
        // Fallback: encode whatever came back as JSON
        if let json = try? JSONSerialization.data(withJSONObject: result.data as Any),
           let str = String(data: json, encoding: .utf8) { return str }
        return "[]"
    }
}
