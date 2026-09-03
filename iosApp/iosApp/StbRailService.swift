import Foundation
import CoreLocation
import MapKit
import SwiftUI

// ─────────────────────────────────────────────────────────────────────────────
// Surface Transportation Board "Railroad Map Depot" (public ArcGIS feature
// services, no auth).  Mirrors Android's StbRailFetcher.
//
//  • NTAD North American Rail Network — owner (reporting mark), subdivision,
//    track count, yard name.  Powers the "Rail Lines" overlay and the
//    railroad / subdivision auto-fill on new spots & saved locations.
//  • STB abandoned + railbanked (rails-to-trails) lines with docket metadata.
//
// STB disclaimer: data is supplied by railroad applicants and is not verified —
// informational only; does not establish the legal status of any rail line.
// ─────────────────────────────────────────────────────────────────────────────

struct RailSegment: Identifiable {
    let id: Int
    let coordinates: [CLLocationCoordinate2D]
    let ownerMark: String       // AAR reporting mark, e.g. "BNSF", "CSXT"
    let ownerName: String       // display name, e.g. "BNSF Railway"
    let subdivision: String
    let division: String
    let tracks: Int
    let yardName: String
    let passenger: Bool

    var label: String {
        var s = ownerName.isEmpty ? (ownerMark.isEmpty ? "Railway" : ownerMark) : ownerName
        if !subdivision.isEmpty { s += " · \(subdivision) Sub" }
        if !yardName.isEmpty { s += " · \(yardName) Yard" }
        if tracks >= 2 { s += " · \(tracks) tracks" }
        return s
    }
}

struct RailInfo {
    let ownerMark: String
    let ownerName: String
    let subdivision: String
    let division: String
    let yardName: String
    let tracks: Int
    let distanceM: Double
}

struct AbandonedRailLine: Identifiable {
    let id: String
    let coordinates: [CLLocationCoordinate2D]
    let railbanked: Bool
    let docket: String
    let railroad: String
    let state: String
    let county: String
    let filed: String
    let approved: String
    let completed: String
    let lengthMiles: Double
    let moreInfo: String
    let link: String

    var statusLabel: String { railbanked ? "Railbanked (rails-to-trails)" : "Abandoned" }
}

// MKPolyline subclasses so the map delegate can style / hit-test them.
final class RailLinePolyline: MKPolyline {
    var segment: RailSegment!
}
final class AbandonedPolyline: MKPolyline {
    var line: AbandonedRailLine!
}

/// Stroke color for a rail line by owner (reporting mark or display name).
func railLineUIColor(_ owner: String) -> UIColor {
    let op = owner.uppercased()
    func c(_ r: Double, _ g: Double, _ b: Double) -> UIColor { UIColor(red: r/255, green: g/255, blue: b/255, alpha: 1) }
    if op.contains("BNSF") { return c(255, 98, 0) }
    if op == "UP" || op.contains("UNION PACIFIC") { return c(255, 179, 0) }
    if op.contains("CSX") { return c(21, 101, 192) }
    if op == "NS" || op.contains("NORFOLK") { return c(117, 117, 117) }
    if op == "CN" || op.contains("CANADIAN NATIONAL") { return c(211, 47, 47) }
    if op.contains("CPKC") || op.contains("CANADIAN PACIFIC") || op == "CP" || op == "CPRS" { return c(136, 14, 79) }
    if op.contains("AMTRAK") || op == "AMTK" { return c(26, 35, 126) }
    if op.contains("KCS") || op.contains("KANSAS CITY") { return c(85, 139, 47) }
    if ["METRA", "METRO-NORTH", "LIRR", "LONG ISLAND", "NJ TRANSIT", "SEPTA", "CALTRAIN",
        "METROLINK", "MBTA", "VIRGINIA RAILWAY", "FRONTRUNNER", "TRINITY"].contains(where: { op.contains($0) }) {
        return c(0, 137, 123)
    }
    return c(84, 110, 122)
}

final class StbRailService {
    static let shared = StbRailService()

    private let base = "https://services3.arcgis.com/6rJKAjBRDRSfjCzV/arcgis/rest/services"
    private var narnLayer: String { "\(base)/NTAD_NARN_Other_Rail_Lines/FeatureServer/0/query" }
    private var abandonedLayer: String { "\(base)/Abandoned_rail_lines/FeatureServer/0/query" }
    private var railbankedLayer: String { "\(base)/Railbanked_rail_lines/FeatureServer/0/query" }

    private let pageSize = 2000
    private let maxPages = 3

    private var segmentCache: [String: [RailSegment]] = [:]
    private var segmentCacheOrder: [String] = []
    private var abandonedCache: [String: [AbandonedRailLine]] = [:]
    private var abandonedCacheOrder: [String] = []

    private let session: URLSession = {
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 30
        cfg.httpAdditionalHeaders = ["User-Agent": "RailfanCopilot/1.0 (iOS)"]
        return URLSession(configuration: cfg)
    }()

    // ── Reporting mark → display name ─────────────────────────────────────

    static func ownerDisplayName(_ mark: String) -> String {
        switch mark.trimmingCharacters(in: .whitespaces).uppercased() {
        case "BNSF": return "BNSF Railway"
        case "UP": return "Union Pacific"
        case "CSXT", "CSX": return "CSX Transportation"
        case "NS": return "Norfolk Southern"
        case "CN", "GTW", "IC", "WC": return "Canadian National"
        case "CPRS", "CP", "CPKC", "SOO", "DME": return "CPKC"
        case "KCS", "KCSM": return "CPKC (ex-KCS)"
        case "AMTK": return "Amtrak"
        case "NIRC", "METX": return "Metra"
        case "IAIS": return "Iowa Interstate"
        case "FXE": return "Ferromex"
        case "MRL": return "Montana Rail Link"
        case "PAS", "PAR": return "Pan Am / CSX"
        case "GWR": return "Genesee & Wyoming"
        case "WSOR": return "Wisconsin & Southern"
        case "CFE": return "Chicago, Fort Wayne & Eastern"
        case "BPRR": return "Buffalo & Pittsburgh"
        case "FEC": return "Florida East Coast"
        case "LIRR": return "Long Island Rail Road"
        case "MNCW", "MNCR": return "Metro-North"
        case "NJTR": return "NJ Transit"
        case "SEPA", "SEPTA": return "SEPTA"
        case "CALTRAIN", "PCJPB": return "Caltrain"
        case "SCAX": return "Metrolink"
        case "MBTA": return "MBTA"
        case "VREX": return "Virginia Railway Express"
        case "TRE": return "Trinity Railway Express"
        case "UTAH", "UTA": return "UTA FrontRunner"
        case "BCOL": return "BC Rail (CN)"
        case "VIA": return "VIA Rail"
        case "": return ""
        default: return mark.trimmingCharacters(in: .whitespaces)
        }
    }

    // ── Rail lines (NTAD) ─────────────────────────────────────────────────

    func fetchRailSegments(region: MKCoordinateRegion) async -> [RailSegment] {
        let (south, west, north, east) = bbox(region)
        let key = String(format: "%.2f,%.2f,%.2f,%.2f", south, west, north, east)
        if let cached = segmentCache[key] { return cached }

        let offset = max(0.00005, (east - west) / 2500.0)
        var out: [RailSegment] = []
        var page = 0
        while page < maxPages {
            var q = URLComponents(string: narnLayer)!
            q.queryItems = [
                .init(name: "f", value: "json"), .init(name: "where", value: "1=1"),
                .init(name: "geometry", value: "\(west),\(south),\(east),\(north)"),
                .init(name: "geometryType", value: "esriGeometryEnvelope"),
                .init(name: "inSR", value: "4326"), .init(name: "outSR", value: "4326"),
                .init(name: "spatialRel", value: "esriSpatialRelIntersects"),
                .init(name: "outFields", value: "FRAARCID,RROWNER1,RROWNER2,SUBDIV,DIVISION,TRACKS,YARDNAME,PASSNGR"),
                .init(name: "geometryPrecision", value: "5"),
                .init(name: "maxAllowableOffset", value: "\(offset)"),
                .init(name: "resultRecordCount", value: "\(pageSize)"),
                .init(name: "resultOffset", value: "\(page * pageSize)")
            ]
            guard let url = q.url, let json = await getJSON(url) else { break }
            if json["error"] != nil { break }
            guard let feats = json["features"] as? [[String: Any]] else { break }
            for f in feats { if let s = parseNarn(f) { out.append(s) } }
            let exceeded = (json["exceededTransferLimit"] as? Bool) ?? false
            if !exceeded || feats.count < pageSize { break }
            page += 1
        }
        if !out.isEmpty {
            segmentCache[key] = out
            segmentCacheOrder.append(key)
            while segmentCacheOrder.count > 20 { segmentCache.removeValue(forKey: segmentCacheOrder.removeFirst()) }
        }
        return out
    }

    private func parseNarn(_ f: [String: Any]) -> RailSegment? {
        guard let a = f["attributes"] as? [String: Any],
              let coords = parsePaths(f["geometry"] as? [String: Any]) else { return nil }
        let mark = str(a["RROWNER1"])
        let subdiv = titleCase(str(a["SUBDIV"]))
        return RailSegment(
            id: (a["FRAARCID"] as? Int) ?? (a["FID"] as? Int) ?? Int.random(in: 1...Int.max),
            coordinates: coords,
            ownerMark: mark,
            ownerName: Self.ownerDisplayName(mark),
            subdivision: subdiv,
            division: titleCase(str(a["DIVISION"])),
            tracks: (a["TRACKS"] as? Int) ?? 0,
            yardName: titleCase(str(a["YARDNAME"])),
            passenger: !str(a["PASSNGR"]).isEmpty
        )
    }

    /// Nearest rail segment to a point (≤ radiusM) — for spot / saved-location auto-fill.
    func lookupRailInfo(lat: Double, lon: Double, radiusM: Int = 800) async -> RailInfo? {
        var q = URLComponents(string: narnLayer)!
        q.queryItems = [
            .init(name: "f", value: "json"), .init(name: "where", value: "1=1"),
            .init(name: "geometry", value: "\(lon),\(lat)"),
            .init(name: "geometryType", value: "esriGeometryPoint"),
            .init(name: "inSR", value: "4326"), .init(name: "outSR", value: "4326"),
            .init(name: "distance", value: "\(radiusM)"), .init(name: "units", value: "esriSRUnit_Meter"),
            .init(name: "spatialRel", value: "esriSpatialRelIntersects"),
            .init(name: "outFields", value: "FRAARCID,RROWNER1,RROWNER2,SUBDIV,DIVISION,TRACKS,YARDNAME,PASSNGR"),
            .init(name: "geometryPrecision", value: "6"), .init(name: "resultRecordCount", value: "50")
        ]
        guard let url = q.url, let json = await getJSON(url),
              let feats = json["features"] as? [[String: Any]] else { return nil }
        let segs = feats.compactMap(parseNarn)
        guard !segs.isEmpty else { return nil }

        let here = CLLocationCoordinate2D(latitude: lat, longitude: lon)
        let scored = segs.map { ($0, Self.distanceToPolylineM(here, $0.coordinates)) }
            .sorted { $0.1 < $1.1 }
        let nearest = scored[0]
        let withSub = scored.first { !$0.0.subdivision.isEmpty && $0.1 <= nearest.1 * 1.5 + 50 }
        return RailInfo(
            ownerMark: nearest.0.ownerMark,
            ownerName: nearest.0.ownerName,
            subdivision: withSub?.0.subdivision ?? "",
            division: withSub?.0.division ?? nearest.0.division,
            yardName: scored.first { !$0.0.yardName.isEmpty }?.0.yardName ?? "",
            tracks: nearest.0.tracks,
            distanceM: nearest.1
        )
    }

    // ── Abandoned / railbanked (STB) ──────────────────────────────────────

    func fetchAbandonedLines(region: MKCoordinateRegion) async -> [AbandonedRailLine] {
        let (south, west, north, east) = bbox(region)
        let key = String(format: "%.1f,%.1f,%.1f,%.1f", south, west, north, east)
        if let cached = abandonedCache[key] { return cached }
        let offset = max(0.00005, (east - west) / 2500.0)
        var out: [AbandonedRailLine] = []
        for (layer, railbanked) in [(abandonedLayer, false), (railbankedLayer, true)] {
            var q = URLComponents(string: layer)!
            q.queryItems = [
                .init(name: "f", value: "json"), .init(name: "where", value: "1=1"),
                .init(name: "geometry", value: "\(west),\(south),\(east),\(north)"),
                .init(name: "geometryType", value: "esriGeometryEnvelope"),
                .init(name: "inSR", value: "4326"), .init(name: "outSR", value: "4326"),
                .init(name: "spatialRel", value: "esriSpatialRelIntersects"),
                .init(name: "outFields", value: "FID,ID,Docket,Railroad,State,County,Filed,Approved,Completed,Length,More_Info,Link"),
                .init(name: "geometryPrecision", value: "5"),
                .init(name: "maxAllowableOffset", value: "\(offset)"),
                .init(name: "resultRecordCount", value: "\(pageSize)")
            ]
            guard let url = q.url, let json = await getJSON(url),
                  let feats = json["features"] as? [[String: Any]] else { continue }
            for f in feats {
                guard let a = f["attributes"] as? [String: Any],
                      let coords = parsePaths(f["geometry"] as? [String: Any]) else { continue }
                let rawId = str(a["ID"]).isEmpty ? "\(a["FID"] ?? 0)" : str(a["ID"])
                out.append(AbandonedRailLine(
                    id: (railbanked ? "RB_" : "AB_") + rawId,
                    coordinates: coords,
                    railbanked: railbanked,
                    docket: str(a["Docket"]), railroad: str(a["Railroad"]),
                    state: str(a["State"]), county: str(a["County"]),
                    filed: str(a["Filed"]), approved: str(a["Approved"]), completed: str(a["Completed"]),
                    lengthMiles: (a["Length"] as? Double) ?? 0,
                    moreInfo: str(a["More_Info"]), link: str(a["Link"])
                ))
            }
        }
        if !out.isEmpty {
            abandonedCache[key] = out
            abandonedCacheOrder.append(key)
            while abandonedCacheOrder.count > 20 { abandonedCache.removeValue(forKey: abandonedCacheOrder.removeFirst()) }
        }
        return out
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private func bbox(_ r: MKCoordinateRegion) -> (Double, Double, Double, Double) {
        (r.center.latitude - r.span.latitudeDelta / 2, r.center.longitude - r.span.longitudeDelta / 2,
         r.center.latitude + r.span.latitudeDelta / 2, r.center.longitude + r.span.longitudeDelta / 2)
    }

    private func getJSON(_ url: URL) async -> [String: Any]? {
        do {
            let (data, resp) = try await session.data(from: url)
            guard (resp as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) ?? true else { return nil }
            return try JSONSerialization.jsonObject(with: data) as? [String: Any]
        } catch {
            print("StbRailService: \(error.localizedDescription)")
            return nil
        }
    }

    private func str(_ v: Any?) -> String {
        (v as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
    }

    private func titleCase(_ s: String) -> String {
        guard !s.isEmpty else { return "" }
        return s.lowercased().split(separator: " ").map { w -> String in
            let u = w.uppercased()
            if w.count <= 2 && ["UP", "NS", "CN", "CP", "OF"].contains(u) { return u }
            return w.prefix(1).uppercased() + w.dropFirst()
        }.joined(separator: " ")
    }

    /// Flattens esriGeometryPolyline `paths` into one coordinate list.
    private func parsePaths(_ geom: [String: Any]?) -> [CLLocationCoordinate2D]? {
        guard let paths = geom?["paths"] as? [[[Double]]] else { return nil }
        var pts: [CLLocationCoordinate2D] = []
        for path in paths { for xy in path where xy.count >= 2 {
            pts.append(CLLocationCoordinate2D(latitude: xy[1], longitude: xy[0]))
        } }
        return pts.count >= 2 ? pts : nil
    }

    /// Approximate point→polyline distance in metres (equirectangular; fine under ~5 km).
    static func distanceToPolylineM(_ p: CLLocationCoordinate2D, _ line: [CLLocationCoordinate2D]) -> Double {
        let kLat = 111_320.0
        let kLon = 111_320.0 * cos(p.latitude * .pi / 180)
        func xy(_ l: CLLocationCoordinate2D) -> (Double, Double) {
            ((l.longitude - p.longitude) * kLon, (l.latitude - p.latitude) * kLat)
        }
        var best = Double.greatestFiniteMagnitude
        guard line.count >= 2 else { return best }
        for i in 0..<(line.count - 1) {
            let (ax, ay) = xy(line[i]); let (bx, by) = xy(line[i + 1])
            let dx = bx - ax, dy = by - ay
            let len2 = dx * dx + dy * dy
            let t = len2 == 0 ? 0 : max(0, min(1, ((-ax) * dx + (-ay) * dy) / len2))
            let cx = ax + t * dx, cy = ay + t * dy
            best = min(best, (cx * cx + cy * cy).squareRoot())
        }
        return best
    }
}

// ── Abandoned / railbanked line detail sheet ──────────────────────────────────
struct AbandonedLineSheet: View {
    let line: AbandonedRailLine
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    private var accent: Color { line.railbanked ? Color(red: 0.29, green: 0.87, blue: 0.50) : Color(red: 0.69, green: 0.75, blue: 0.77) }

    var body: some View {
        ZStack {
            Color.bgPrimary.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 10) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10).fill(accent.opacity(0.18)).frame(width: 40, height: 40)
                            Image(systemName: line.railbanked ? "figure.hiking" : "road.lanes.curved.left")
                                .foregroundColor(accent).font(.system(size: 18, weight: .semibold))
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(line.railroad.isEmpty ? "Unknown railroad" : line.railroad)
                                .font(.system(size: 16, weight: .medium)).foregroundColor(.textPrimary).lineLimit(2)
                            Text(line.statusLabel).font(.system(size: 12)).foregroundColor(accent)
                        }
                        Spacer()
                        Button { dismiss() } label: {
                            Image(systemName: "xmark").foregroundColor(.textMuted).font(.system(size: 14, weight: .semibold))
                        }
                    }
                    .padding(.horizontal, 16).padding(.top, 20)

                    VStack(spacing: 0) {
                        if !line.docket.isEmpty { DetailRow(label: "Docket", value: line.docket) }
                        let loc = [line.county, line.state].filter { !$0.isEmpty }.joined(separator: ", ")
                        if !loc.isEmpty { DetailRow(label: "Location", value: loc) }
                        if line.lengthMiles > 0 { DetailRow(label: "Length", value: String(format: "%.2f mi", line.lengthMiles)) }
                        if !line.filed.isEmpty { DetailRow(label: "Filed", value: line.filed) }
                        if !line.approved.isEmpty { DetailRow(label: "Approved", value: line.approved) }
                        if !line.completed.isEmpty { DetailRow(label: line.railbanked ? "Railbanked" : "Completed", value: line.completed) }
                    }
                    .cardStyle()
                    .padding(.horizontal, 16)

                    if !line.moreInfo.isEmpty {
                        Text(line.moreInfo).font(.system(size: 12)).foregroundColor(.textSecondary)
                            .padding(.horizontal, 16)
                    }

                    if let url = URL(string: line.link), !line.link.isEmpty {
                        Button { openURL(url) } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "arrow.up.right.square")
                                Text("STB docket records").font(.system(size: 13, weight: .medium))
                            }
                            .frame(maxWidth: .infinity).padding(12)
                            .background(Color.railBlueDark).foregroundColor(.textPrimary).cornerRadius(10)
                        }
                        .padding(.horizontal, 16)
                    }

                    Text("Source: Surface Transportation Board, Office of Environmental Analysis. Informational only — does not establish the legal status of any rail line.")
                        .font(.system(size: 10)).foregroundColor(.textMuted)
                        .padding(.horizontal, 16).padding(.bottom, 24)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
