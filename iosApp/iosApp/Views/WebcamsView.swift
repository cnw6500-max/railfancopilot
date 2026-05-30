import SwiftUI

// ── Data Model ────────────────────────────────────────────────────────────────
struct RailcamEntry: Identifiable {
    let id: String
    let name: String
    let location: String
    let railroad: String
    let subdivision: String
    let description: String
    let url: String
}

let ALL_RAILCAMS: [RailcamEntry] = [
    RailcamEntry(id: "rochelle_il", name: "Rochelle Railroad Park", location: "Rochelle, IL",
                 railroad: "BNSF / UP", subdivision: "BNSF Chillicothe Sub / UP Overland",
                 description: "Live crossing of BNSF and UP mainlines — one of the busiest rail intersections in the US.",
                 url: "https://www.youtube.com/results?search_query=rochelle+railroad+park+live+cam"),
    RailcamEntry(id: "cajon_pass", name: "Cajon Pass", location: "Cajon, CA",
                 railroad: "BNSF / UP", subdivision: "BNSF Transcon / UP LA Sub",
                 description: "Fan-operated cameras at the summit of Cajon Pass watching heavy freights climb the grade.",
                 url: "https://www.youtube.com/results?search_query=cajon+pass+live+train+cam"),
    RailcamEntry(id: "tehachapi_loop", name: "Tehachapi Loop", location: "Tehachapi, CA",
                 railroad: "BNSF / UP", subdivision: "BNSF Mojave Sub",
                 description: "Classic spiral loop where trains visually pass over themselves. Stunning mountain scenery.",
                 url: "https://www.youtube.com/results?search_query=tehachapi+loop+live+train+cam"),
    RailcamEntry(id: "donner_pass", name: "Donner Pass", location: "Donner Summit, CA",
                 railroad: "UP", subdivision: "UP Overland Route",
                 description: "UP's dramatic crossing of the Sierra Nevada — snow sheds, helpers, and spectacular mountain scenery year-round.",
                 url: "https://www.youtube.com/results?search_query=donner+pass+union+pacific+live+train+cam"),
    RailcamEntry(id: "horseshoe_curve", name: "Horseshoe Curve", location: "Altoona, PA",
                 railroad: "NS", subdivision: "NS Pittsburgh Line",
                 description: "Historic NS horseshoe curve cutting through the Allegheny Mountains. NPS visitor center on-site.",
                 url: "https://www.youtube.com/results?search_query=horseshoe+curve+altoona+live+train"),
    RailcamEntry(id: "fostoria_oh", name: "Fostoria Iron Triangle", location: "Fostoria, OH",
                 railroad: "CSX / NS", subdivision: "Multiple mainlines",
                 description: "Three mainlines converge at Fostoria — watch CSX and NS freights roll through the triangle.",
                 url: "https://www.youtube.com/results?search_query=fostoria+ohio+iron+triangle+live+cam"),
    RailcamEntry(id: "up_north_platte", name: "UP Bailey Yard — Golden Spike Tower", location: "North Platte, NE",
                 railroad: "UP", subdivision: "UP Overland Route",
                 description: "World's largest rail yard. Golden Spike Tower offers live webcam views of over 10,000 cars per day.",
                 url: "https://www.goldenspike.org/webcam/"),
    RailcamEntry(id: "raton_pass", name: "Raton Pass", location: "Raton, NM",
                 railroad: "BNSF", subdivision: "BNSF Raton Sub",
                 description: "Steep 3.5% grade crossing the Sangre de Cristo Mountains — helpers required on almost every train.",
                 url: "https://www.youtube.com/results?search_query=raton+pass+bnsf+live+train+cam"),
    RailcamEntry(id: "marias_pass", name: "Marias Pass", location: "Essex, MT",
                 railroad: "BNSF", subdivision: "BNSF Havre Sub",
                 description: "Lowest pass through the Rockies — BNSF's northern Transcon through glacier country.",
                 url: "https://www.youtube.com/results?search_query=marias+pass+bnsf+montana+trains"),
    RailcamEntry(id: "bnsf_galesburg", name: "BNSF Galesburg", location: "Galesburg, IL",
                 railroad: "BNSF", subdivision: "BNSF Chillicothe Sub",
                 description: "Major BNSF yard and mainline. Heavy intermodal, coal, and manifest traffic throughout the day.",
                 url: "https://www.youtube.com/results?search_query=bnsf+galesburg+illinois+trains+live"),
    RailcamEntry(id: "kansas_city", name: "Kansas City", location: "Kansas City, MO",
                 railroad: "BNSF / UP / NS / CSX", subdivision: "Multiple mainlines",
                 description: "One of the largest rail hubs in North America — nearly every Class I railroad passes through.",
                 url: "https://www.youtube.com/results?search_query=kansas+city+railroad+live+train+cam"),
    RailcamEntry(id: "ogden_ut", name: "Ogden", location: "Ogden, UT",
                 railroad: "UP", subdivision: "UP Overland Route",
                 description: "Historic transcontinental junction where the Golden Spike was driven in 1869 — heavy UP mainline traffic.",
                 url: "https://www.youtube.com/results?search_query=ogden+utah+union+pacific+trains+live"),
    RailcamEntry(id: "csx_cincinnati", name: "CSX Cincinnati", location: "Cincinnati, OH",
                 railroad: "CSX", subdivision: "CSX Cincinnati Hub",
                 description: "CSX gateway hub — intermodal, automotive, and mixed-freight trains through the Queen City.",
                 url: "https://www.youtube.com/results?search_query=csx+cincinnati+live+train+cam"),
    RailcamEntry(id: "selkirk_yard", name: "Selkirk Yard", location: "Selkirk, NY",
                 railroad: "CSX", subdivision: "CSX Boston Line",
                 description: "Largest rail yard in the northeast — CSX's main classification hub for New England traffic.",
                 url: "https://www.youtube.com/results?search_query=selkirk+yard+csx+new+york+trains"),
    RailcamEntry(id: "colton_crossing", name: "Colton Crossing", location: "Colton, CA",
                 railroad: "BNSF / UP", subdivision: "BNSF Transcon / UP Sunset Route",
                 description: "One of the busiest at-grade rail diamonds in the US — BNSF Transcon crosses UP's Sunset Route with trains every few minutes.",
                 url: "https://www.youtube.com/results?search_query=colton+crossing+bnsf+up+live+train+cam"),
    RailcamEntry(id: "horseshoe_curve", name: "Horseshoe Curve", location: "Altoona, PA",
                 railroad: "NS", subdivision: "NS Pittsburgh Line",
                 description: "Historic NS horseshoe curve cutting through the Allegheny Mountains.",
                 url: "https://www.youtube.com/results?search_query=horseshoe+curve+altoona+live+train"),
    RailcamEntry(id: "up_cheyenne", name: "UP Cheyenne", location: "Cheyenne, WY",
                 railroad: "UP", subdivision: "UP Overland Route",
                 description: "Union Pacific's historic Cheyenne terminal — gateway to Sherman Hill and the Rocky Mountain climb.",
                 url: "https://www.youtube.com/results?search_query=union+pacific+cheyenne+wyoming+live+trains"),
    RailcamEntry(id: "strasburg_rr", name: "Strasburg Railroad", location: "Strasburg, PA",
                 railroad: "Strasburg RR", subdivision: "Strasburg Branch",
                 description: "America's oldest operating short line — live steam locomotives hauling passenger trains through Pennsylvania Dutch Country.",
                 url: "https://www.strasburgrailroad.com"),
    RailcamEntry(id: "kingman_az", name: "Kingman", location: "Kingman, AZ",
                 railroad: "BNSF", subdivision: "BNSF Transcon / Seligman Sub",
                 description: "High-desert BNSF Transcon — constant intermodal and manifest traffic through the Mojave.",
                 url: "https://www.youtube.com/results?search_query=kingman+arizona+bnsf+live+train+cam"),
    RailcamEntry(id: "cn_memphis", name: "CN Memphis Bridge", location: "Memphis, TN",
                 railroad: "CN", subdivision: "CN Memphis Sub",
                 description: "CN's crossing over the Mississippi River — a constant parade of manifest and intermodal trains.",
                 url: "https://www.youtube.com/results?search_query=cn+railroad+memphis+bridge+trains"),
]

// ── Main View ─────────────────────────────────────────────────────────────────
struct WebcamsView: View {
    @State private var filter = ""

    var filtered: [RailcamEntry] {
        if filter.isEmpty { return ALL_RAILCAMS }
        let q = filter.lowercased()
        return ALL_RAILCAMS.filter {
            $0.name.lowercased().contains(q) ||
            $0.railroad.lowercased().contains(q) ||
            $0.location.lowercased().contains(q)
        }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Search bar
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.textMuted)
                            .font(.system(size: 15))
                        TextField("Filter by railroad or location…", text: $filter)
                            .foregroundColor(.textPrimary)
                            .font(.system(size: 14))
                        if !filter.isEmpty {
                            Button { filter = "" } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.textMuted)
                                    .font(.system(size: 15))
                            }
                        }
                    }
                    .padding(12)
                    .background(Color.bgCard)
                    .cornerRadius(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.border, lineWidth: 0.5))
                    .padding(.horizontal)
                    .padding(.vertical, 10)

                    Divider().background(Color.border)

                    if filtered.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "video.slash")
                                .font(.system(size: 40))
                                .foregroundColor(.railBlueDark)
                            Text("No cams match \"\(filter)\"")
                                .font(.system(size: 15))
                                .foregroundColor(.textMuted)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        ScrollView {
                            VStack(spacing: 10) {
                                ForEach(filtered) { cam in
                                    WebcamCard(cam: cam)
                                }
                                Spacer(minLength: 40)
                            }
                            .padding(.horizontal)
                            .padding(.top, 10)
                        }
                    }
                }
            }
            .navigationTitle("Railroad Webcams")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}

// ── Webcam Card ───────────────────────────────────────────────────────────────
private struct WebcamCard: View {
    let cam: RailcamEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header
            HStack(spacing: 10) {
                Image(systemName: "video.fill")
                    .foregroundColor(.railBlue)
                    .font(.system(size: 18))
                VStack(alignment: .leading, spacing: 2) {
                    Text(cam.name)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.textPrimary)
                    Text(cam.location)
                        .font(.system(size: 12))
                        .foregroundColor(.textMuted)
                }
            }

            // Tags
            HStack(spacing: 6) {
                RailcamTag(text: cam.railroad, primary: true)
                RailcamTag(text: String(cam.subdivision.prefix(30)) + (cam.subdivision.count > 30 ? "…" : ""),
                           primary: false)
            }

            // Description
            Text(cam.description)
                .font(.system(size: 13))
                .foregroundColor(.textSecondary)
                .lineSpacing(3)

            // Open button
            Link(destination: URL(string: cam.url)!) {
                HStack {
                    Image(systemName: "safari.fill")
                        .font(.system(size: 14))
                    Text("Open Webcam")
                        .font(.system(size: 14, weight: .medium))
                }
                .foregroundColor(.railBlue)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(Color.railBlueDark)
                .cornerRadius(10)
            }
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
    }
}

private struct RailcamTag: View {
    let text: String
    let primary: Bool

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .medium))
            .foregroundColor(primary ? .railBlue : .textMuted)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(primary ? Color.railBlueDark : Color.bgCard)
            .cornerRadius(6)
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.border, lineWidth: 0.5))
    }
}
