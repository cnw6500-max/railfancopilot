import SwiftUI

// ── Loco data model ───────────────────────────────────────────────────────────
struct LocoEntry: Identifiable {
    let id: String
    let model: String
    let manufacturer: String
    let introduced: Int
    let horsepower: Int
    let traction: String
    let wheelArrangement: String
    let railroads: [String]
    let notes: String
    let imageUrl: String?
}

private let locoDatabase: [LocoEntry] = [
    // ── GE / Wabtec GEVO ─────────────────────────────────────────────────────
    LocoEntry(id:"l1",  model:"ES44AC",       manufacturer:"GE Transportation", introduced:2004, horsepower:4400, traction:"AC", wheelArrangement:"C-C", railroads:["BNSF","UP","CSX"],        notes:"GEVO series flagship. BNSF's primary Transcon power; UP and CSX also operate large fleets.",                                                                                          imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_7452_Barstow.jpg?width=480"),
    LocoEntry(id:"l2",  model:"ES44C4",       manufacturer:"GE Transportation", introduced:2012, horsepower:4400, traction:"AC", wheelArrangement:"C-C", railroads:["BNSF"],                    notes:"Controlled Tractive Effort variant — two of six axle motors cut out at speed to reduce wheel slip. Externally identical to ES44AC.",                                                    imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_6071_Victorville.jpg?width=480"),
    LocoEntry(id:"l3",  model:"ES44DC",       manufacturer:"GE Transportation", introduced:2003, horsepower:4400, traction:"DC", wheelArrangement:"C-C", railroads:["NS","CSX","KCS"],          notes:"DC-traction GEVO. NS and CSX preferred DC for maintenance simplicity.",                                                                                                                imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/NS_7614_ES44DC.jpg?width=480"),
    LocoEntry(id:"l4",  model:"ET44AC",       manufacturer:"GE Transportation", introduced:2015, horsepower:4400, traction:"AC", wheelArrangement:"C-C", railroads:["BNSF","CSX"],              notes:"Tier 4 emission-compliant GEVO with exhaust aftertreatment. BNSF and CSX are primary operators.",                                                                                      imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_3890_ET44AC.jpg?width=480"),
    LocoEntry(id:"l5",  model:"ET44C4",       manufacturer:"GE Transportation", introduced:2015, horsepower:4400, traction:"AC", wheelArrangement:"C-C", railroads:["BNSF"],                    notes:"Tier 4 Controlled Tractive Effort variant. BNSF ordered these exclusively for heavy mountain helper service.",                                                                          imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_3820_ET44C4.jpg?width=480"),
    // ── GE Legacy ─────────────────────────────────────────────────────────────
    LocoEntry(id:"l6",  model:"AC4400CW",     manufacturer:"GE Transportation", introduced:1993, horsepower:4400, traction:"AC", wheelArrangement:"C-C", railroads:["UP","CSX","CN"],           notes:"GE's first widely successful AC traction locomotive. The wide-nose 'C' cab became the template for all modern GE road units.",                                                          imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/UP_AC4400CW_6761.jpg?width=480"),
    LocoEntry(id:"l7",  model:"AC6000CW",     manufacturer:"GE Transportation", introduced:1995, horsepower:6000, traction:"AC", wheelArrangement:"C-C", railroads:["UP","CSX"],                notes:"World's most powerful diesel when introduced. Engine reliability issues led to most being downrated to 4400 hp in service.",                                                            imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/UP_AC6000CW_7558.jpg?width=480"),
    LocoEntry(id:"l8",  model:"C44-9W",       manufacturer:"GE Transportation", introduced:1994, horsepower:4400, traction:"DC", wheelArrangement:"C-C", railroads:["UP","NS","CSX","CN"],      notes:"Dash 9-44CW. GE's bestseller of the 1990s. Thousands still in daily service.",                                                                                                        imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/NS_9176_C44-9W.jpg?width=480"),
    LocoEntry(id:"l9",  model:"C40-8W",       manufacturer:"GE Transportation", introduced:1989, horsepower:4000, traction:"DC", wheelArrangement:"C-C", railroads:["UP","CSX","NS"],           notes:"Dash 8-40CW. GE's first wide-nose locomotive. Introduced microprocessor control systems now standard across the industry.",                                                            imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/CSX_7513_C40-8W.jpg?width=480"),
    // ── GE Passenger ──────────────────────────────────────────────────────────
    LocoEntry(id:"l10", model:"P42DC",        manufacturer:"GE Transportation", introduced:1996, horsepower:4200, traction:"DC", wheelArrangement:"B-B", railroads:["Amtrak"],                  notes:"Amtrak's Genesis series workhorse. Leads the California Zephyr, Southwest Chief, and most long-distance trains.",                                                                       imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_P42DC_822.jpg?width=480"),
    LocoEntry(id:"l11", model:"ALC-42 Charger", manufacturer:"Siemens",         introduced:2021, horsepower:4200, traction:"AC", wheelArrangement:"B-B", railroads:["Amtrak"],                  notes:"Amtrak's newest passenger locomotive replacing aging P42s. Tier 4 compliant, 125 mph capable.",                                                                                        imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_ALC-42_301.jpg?width=480"),
    LocoEntry(id:"l12", model:"SC-44 Charger", manufacturer:"Siemens",          introduced:2016, horsepower:4400, traction:"AC", wheelArrangement:"B-B", railroads:["Amtrak","Metra","Other"],  notes:"State-corridor Charger. Operates Pacific Surfliner, Hiawatha, and state-supported routes. 110 mph top speed.",                                                                        imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_SC44_2101.jpg?width=480"),
    // ── EMD SD70 ──────────────────────────────────────────────────────────────
    LocoEntry(id:"l13", model:"SD70ACe",      manufacturer:"EMD",               introduced:2004, horsepower:4300, traction:"AC", wheelArrangement:"C-C", railroads:["UP","NS","CSX"],           notes:"EMD's answer to the GEVO. UP's primary road locomotive. 16-710 prime mover defines modern EMD power.",                                                                                 imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/UP_8444_SD70ACe.jpg?width=480"),
    LocoEntry(id:"l14", model:"SD70ACe-T4",   manufacturer:"EMD/Progress Rail", introduced:2015, horsepower:4300, traction:"AC", wheelArrangement:"C-C", railroads:["NS","UP"],                 notes:"Tier 4 compliant SD70ACe with exhaust gas recirculation. NS was launch customer.",                                                                                                     imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/NS_SD70ACe-T4.jpg?width=480"),
    LocoEntry(id:"l15", model:"SD70MAC",      manufacturer:"EMD",               introduced:1993, horsepower:4300, traction:"AC", wheelArrangement:"C-C", railroads:["BNSF","CSX"],              notes:"EMD's first production AC traction locomotive. BN and Santa Fe were launch customers.",                                                                                                 imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_9616_SD70MAC.jpg?width=480"),
    LocoEntry(id:"l16", model:"SD70M",        manufacturer:"EMD",               introduced:1993, horsepower:4000, traction:"DC", wheelArrangement:"C-C", railroads:["UP"],                      notes:"UP's DC-traction workhorse of the 1990s. The 'Whisker' cab features full-width nose with angled side windows. UP operated over 1,000 units.",                                          imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/UP_4141_SD70M.jpg?width=480"),
    LocoEntry(id:"l17", model:"SD70M-2",      manufacturer:"EMD",               introduced:2010, horsepower:4300, traction:"DC", wheelArrangement:"C-C", railroads:["UP","BNSF"],               notes:"Updated DC-traction SD70 with isolated cab. UP's last large DC purchase before transitioning fully to AC.",                                                                             imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/UP_SD70M-2.jpg?width=480"),
    // ── EMD High-HP ──────────────────────────────────────────────────────────
    LocoEntry(id:"l18", model:"SD90MAC",      manufacturer:"EMD",               introduced:1995, horsepower:6000, traction:"AC", wheelArrangement:"C-C", railroads:["UP","CP"],                 notes:"EMD's 6000-hp contender using the new 265H engine — which proved troublesome. Many rebuilt to SD70ACe spec.",                                                                          imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/UP_SD90MAC_8102.jpg?width=480"),
    LocoEntry(id:"l19", model:"SD80MAC",      manufacturer:"EMD",               introduced:1995, horsepower:5000, traction:"AC", wheelArrangement:"C-C", railroads:["Conrail","NS","CSX"],      notes:"Conrail's unique 5000-hp AC units. One of the most powerful 16-cylinder diesels built.",                                                                                               imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/Conrail_SD80MAC.jpg?width=480"),
    // ── EMD SD60 ─────────────────────────────────────────────────────────────
    LocoEntry(id:"l20", model:"SD60M",        manufacturer:"EMD",               introduced:1989, horsepower:3800, traction:"DC", wheelArrangement:"C-C", railroads:["UP","CN","BNSF"],          notes:"First EMD with wide-nose safety cab in North America. Still widespread in secondary service.",                                                                                          imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/UP_SD60M_2290.jpg?width=480"),
    LocoEntry(id:"l21", model:"SD60MAC",      manufacturer:"EMD",               introduced:1994, horsepower:4000, traction:"AC", wheelArrangement:"C-C", railroads:["BNSF"],                    notes:"AC traction retrofit of the SD60. Burlington Northern ordered 250 for Powder River Basin coal service.",                                                                                imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_SD60MAC.jpg?width=480"),
    // ── EMD SD40 ─────────────────────────────────────────────────────────────
    LocoEntry(id:"l22", model:"SD40-2",       manufacturer:"EMD",               introduced:1972, horsepower:3000, traction:"DC", wheelArrangement:"C-C", railroads:["BNSF","UP","NS","CSX","CN","CP"], notes:"The most successful diesel ever built — over 4,000 produced. The 16-645E3 engine set the standard for an entire generation.",                                              imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_6851_SD40-2.jpg?width=480"),
    LocoEntry(id:"l23", model:"SD40-2T",      manufacturer:"EMD",               introduced:1974, horsepower:3000, traction:"DC", wheelArrangement:"C-C", railroads:["CN","SP"],                 notes:"Tunnel Motor variant with under-frame cooling for low-clearance tunnels. Built for Southern Pacific mountain operations.",                                                               imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/CN_SD40-2T_5377.jpg?width=480"),
    LocoEntry(id:"l24", model:"SD45",         manufacturer:"EMD",               introduced:1965, horsepower:3600, traction:"DC", wheelArrangement:"C-C", railroads:["BNSF","NS"],               notes:"20-cylinder 645 prime mover. Famous 'flared radiator' fins. High maintenance led most roads to favor the SD40-2.",                                                                      imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/SP_SD45.jpg?width=480"),
    // ── EMD Four-axle ────────────────────────────────────────────────────────
    LocoEntry(id:"l25", model:"GP38-2",       manufacturer:"EMD",               introduced:1972, horsepower:2000, traction:"DC", wheelArrangement:"B-B", railroads:["BNSF","UP","CSX","NS","CN","CP"], notes:"Standard four-axle branch line locomotive of the 1970s–80s. Ideal for light rail and industrial switching.",                                                              imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/NS_GP38-2_5083.jpg?width=480"),
    LocoEntry(id:"l26", model:"GP60",         manufacturer:"EMD",               introduced:1985, horsepower:3800, traction:"DC", wheelArrangement:"B-B", railroads:["BNSF","UP"],               notes:"EMD's high-HP four-axle answer to GE's Dash 8. Santa Fe and Southern Pacific were primary buyers.",                                                                                    imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_GP60_4017.jpg?width=480"),
    LocoEntry(id:"l27", model:"GP9",          manufacturer:"EMD",               introduced:1954, horsepower:1750, traction:"DC", wheelArrangement:"B-B", railroads:["BNSF","CN","CP"],          notes:"The locomotive that modernized American railroading. Over 4,000 built. Many survive in tourist and commuter service.",                                                                   imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/CN_GP9_4102.jpg?width=480"),
    // ── EMD Passenger ────────────────────────────────────────────────────────
    LocoEntry(id:"l28", model:"F59PHI",       manufacturer:"EMD",               introduced:1994, horsepower:3000, traction:"AC", wheelArrangement:"B-B", railroads:["Amtrak","Caltrain"],       notes:"Intercity passenger locomotive for state corridor services. Operates Amtrak California and Cascades.",                                                                                  imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_F59PHI_456.jpg?width=480"),
    LocoEntry(id:"l29", model:"F40PH",        manufacturer:"EMD",               introduced:1976, horsepower:3000, traction:"DC", wheelArrangement:"B-B", railroads:["Amtrak","Metra","MBTA"],   notes:"Amtrak's dominant power from the late 1970s through the 1990s. Many survive on commuter railroads.",                                                                                   imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_F40PH_349.jpg?width=480"),
    // ── EMD Switchers ────────────────────────────────────────────────────────
    LocoEntry(id:"l30", model:"MP15AC",       manufacturer:"EMD",               introduced:1975, horsepower:1500, traction:"AC", wheelArrangement:"B-B", railroads:["BNSF","UP","CSX","NS"],    notes:"Multi-Purpose 15 — EMD's primary switcher of the 1970s. Common in major classification yards.",                                                                                        imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/UP_MP15AC_1362.jpg?width=480"),
    LocoEntry(id:"l31", model:"SW1500",       manufacturer:"EMD",               introduced:1966, horsepower:1500, traction:"DC", wheelArrangement:"B-B", railroads:["BNSF","UP","CSX","NS"],    notes:"Standard yard switcher of the 1960s–70s. The 8-645E prime mover and trademark short hood made it ubiquitous in American yards.",                                                       imageUrl:"https://commons.wikimedia.org/wiki/Special:FilePath/UP_SW1500_1254.jpg?width=480"),
]

// ── Encyclopedia view ─────────────────────────────────────────────────────────
struct EncyclopediaView: View {
    @State private var selectedTab = 0
    @State private var searchText = ""
    @State private var selectedLoco: LocoEntry? = nil
    private let tabLabels = ["Loco Specs", "Roster", "Signals"]

    var filteredLocos: [LocoEntry] {
        if searchText.isEmpty { return locoDatabase }
        return locoDatabase.filter {
            $0.model.localizedCaseInsensitiveContains(searchText) ||
            $0.manufacturer.localizedCaseInsensitiveContains(searchText) ||
            $0.railroads.joined(separator: " ").localizedCaseInsensitiveContains(searchText)
        }
    }

    var filteredRoster: [(name: String, short: String, url: String, color: Color)] {
        guard !searchText.isEmpty else { return rosterLinks }
        return rosterLinks.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.short.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                VStack(spacing: 0) {
                    // Tab bar
                    HStack(spacing: 0) {
                        ForEach(tabLabels.indices, id: \.self) { i in
                            Button {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    selectedTab = i
                                    searchText = ""
                                }
                            } label: {
                                Text(tabLabels[i])
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(selectedTab == i ? .railBlue : .textMuted)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .overlay(Rectangle().fill(selectedTab == i ? Color.railBlue : .clear).frame(height: 2), alignment: .bottom)
                            }
                        }
                    }
                    .background(Color.bgPrimary)
                    .overlay(Divider().background(Color.border), alignment: .bottom)

                    // Search bar (shared)
                    HStack {
                        Image(systemName: "magnifyingglass").foregroundColor(.textMuted).font(.system(size: 14))
                        TextField(selectedTab == 0 ? "Model, manufacturer, railroad…" : "Search railroads…", text: $searchText)
                            .foregroundColor(.textPrimary).font(.system(size: 14))
                        if !searchText.isEmpty {
                            Button { searchText = "" } label: {
                                Image(systemName: "xmark.circle.fill").foregroundColor(.textMuted)
                            }
                        }
                    }
                    .padding(10).background(Color.bgInput).cornerRadius(10)
                    .padding(.horizontal, 12).padding(.vertical, 8)

                    if selectedTab == 0 { locoSpecsTab }
                    else if selectedTab == 1 { rosterTab }
                    else { signalsTab }
                }
            }
            .navigationTitle("Encyclopedia")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .sheet(item: $selectedLoco) { loco in
            LocoDetailSheet(loco: loco)
        }
    }

    // ── Loco specs tab ────────────────────────────────────────────────────────
    private var locoSpecsTab: some View {
        List(filteredLocos) { loco in
            Button { selectedLoco = loco } label: {
                LocoCard(loco: loco)
            }
            .listRowBackground(Color.bgCard)
            .listRowSeparatorTint(Color.border)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    // ── Roster tab ────────────────────────────────────────────────────────────
    private var rosterTab: some View {
        List {
            Section(header: HStack(spacing: 8) {
                Image(systemName: "safari").foregroundColor(.railBlue).font(.system(size: 13))
                Text("Powered by DieselShop.us · tap to open in browser")
                    .font(.system(size: 12)).foregroundColor(.textMuted)
            }.padding(.leading, 4)) {
                ForEach(filteredRoster, id: \.name) { roster in
                    Button {
                        if let url = URL(string: roster.url) { UIApplication.shared.open(url) }
                    } label: {
                        HStack(spacing: 14) {
                            Circle().fill(roster.color).frame(width: 10, height: 10)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(roster.name).font(.system(size: 14, weight: .medium)).foregroundColor(.textPrimary)
                                Text(roster.short).font(.system(size: 12)).foregroundColor(.textMuted)
                            }
                            Spacer()
                            Image(systemName: "arrow.up.right.square").foregroundColor(.railBlue).font(.system(size: 15))
                        }
                    }
                    .listRowBackground(Color.bgCard)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    // ── Signals tab ───────────────────────────────────────────────────────────
    private var signalsTab: some View {
        List(signals, id: \.abbr) { sig in
            HStack(alignment: .top, spacing: 12) {
                Text(sig.abbr)
                    .font(.system(size: 11, weight: .semibold)).foregroundColor(.railBlue)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Color.railBlueDark).cornerRadius(4)
                Text(sig.desc)
                    .font(.system(size: 13)).foregroundColor(.textSecondary).lineSpacing(3)
            }
            .listRowBackground(Color.bgCard)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    // ── Static data ───────────────────────────────────────────────────────────
    private let rosterLinks: [(name: String, short: String, url: String, color: Color)] = [
        ("BNSF Railway",                 "BNSF",  "https://www.dieselshop.us/BNSF.HTML",    Color(red:1.0, green:0.4, blue:0.0)),
        ("Union Pacific",                "UP",    "https://www.dieselshop.us/UP.HTML",       Color(red:1.0, green:0.8, blue:0.0)),
        ("CSX Transportation",           "CSX",   "https://www.dieselshop.us/CSX.HTML",      Color(red:0.0, green:0.34, blue:0.66)),
        ("Norfolk Southern",             "NS",    "http://www.nsdash9.com/roster.html",      Color(red:0.6, green:0.6, blue:0.6)),
        ("Canadian National",            "CN",    "https://www.dieselshop.us/CN.HTML",       Color(red:0.8, green:0.0, blue:0.0)),
        ("Canadian Pacific Kansas City", "CPKC",  "https://www.dieselshop.us/CP.HTML",       Color(red:0.55, green:0.0, blue:0.0)),
        ("Amtrak",                       "AMTK",  "https://www.dieselshop.us/AMTRAK.HTML",   Color(red:0.12, green:0.23, blue:0.54)),
        ("Metra",                        "METRA", "https://www.dieselshop.us/METRA.HTML",    Color(red:0.0, green:0.36, blue:0.67)),
        ("LIRR",                         "LIRR",  "https://www.dieselshop.us/LIRR.HTML",     Color(red:0.0, green:0.29, blue:0.53)),
        ("Metro North",                  "MNR",   "https://www.dieselshop.us/MNR.HTML",      Color(red:0.0, green:0.41, blue:0.24)),
        ("MBTA",                         "MBTA",  "https://www.dieselshop.us/MBTA.HTML",     Color(red:0.0, green:0.24, blue:0.65)),
        ("SEPTA",                        "SEPTA", "https://www.dieselshop.us/SEPTA.HTML",    Color(red:0.0, green:0.34, blue:0.66)),
        ("Caltrain",                     "CT",    "https://www.dieselshop.us/CALTRAIN.HTML", Color(red:0.55, green:0.0, blue:0.0)),
        ("Browse all rosters",           "All",   "https://www.dieselshop.us",               Color.railBlue),
    ]

    private let signals: [(abbr: String, desc: String)] = [
        ("CTC", "Centralized Traffic Control — dispatcher controls all signals/switches remotely"),
        ("ABS", "Automatic Block Signals — train presence triggers signals automatically"),
        ("PTC", "Positive Train Control — GPS-based system that can stop trains automatically"),
        ("DTC", "Direct Traffic Control — used on single-track lines without signals"),
        ("TWC", "Track Warrant Control — dispatcher issues paper/verbal authority"),
        ("ETMS","Electronic Train Management System — PTC variant used by BNSF and UP"),
        ("I-ETMS","Interoperable ETMS — cross-railroad PTC standard mandated by FRA"),
    ]
}

// ── Loco card (list row) ──────────────────────────────────────────────────────
struct LocoCard: View {
    let loco: LocoEntry
    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .center, spacing: 2) {
                Text(loco.wheelArrangement)
                    .font(.system(size: 11, weight: .bold)).foregroundColor(.railBlue)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Color.railBlueDark).cornerRadius(4)
                Text(loco.traction + " traction")
                    .font(.system(size: 9)).foregroundColor(.textMuted)
            }
            .frame(width: 58)

            VStack(alignment: .leading, spacing: 4) {
                Text(loco.model)
                    .font(.system(size: 15, weight: .bold)).foregroundColor(.textPrimary)
                Text("\(loco.manufacturer) · \(loco.introduced)")
                    .font(.system(size: 12)).foregroundColor(.textMuted)
                HStack(spacing: 6) {
                    Label("\(loco.horsepower) hp", systemImage: "bolt.fill")
                        .font(.system(size: 11)).foregroundColor(.textSecondary)
                    Text("·")
                        .foregroundColor(.textMuted)
                    Text(loco.railroads.prefix(3).joined(separator: ", "))
                        .font(.system(size: 11)).foregroundColor(.textMuted)
                        .lineLimit(1)
                }
            }
            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 12)).foregroundColor(.textMuted)
        }
        .padding(.vertical, 6)
    }
}

// ── Loco detail sheet ─────────────────────────────────────────────────────────
struct LocoDetailSheet: View {
    let loco: LocoEntry
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {

                    // Image
                    if let urlStr = loco.imageUrl, let url = URL(string: urlStr) {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .success(let img):
                                img.resizable().scaledToFill()
                                    .frame(maxWidth: .infinity).frame(height: 200)
                                    .clipped().cornerRadius(12)
                            case .failure(_):
                                imagePlaceholder
                            default:
                                ProgressView().frame(maxWidth: .infinity).frame(height: 200)
                                    .background(Color.bgInput).cornerRadius(12)
                            }
                        }
                        .padding(.horizontal)
                    } else {
                        imagePlaceholder.padding(.horizontal)
                    }

                    // Header
                    VStack(alignment: .leading, spacing: 6) {
                        Text(loco.model)
                            .font(.system(size: 24, weight: .bold)).foregroundColor(.textPrimary)
                        Text("\(loco.manufacturer) · Introduced \(loco.introduced)")
                            .font(.system(size: 14)).foregroundColor(.textMuted)
                    }
                    .padding(.horizontal)

                    // Specs card
                    VStack(spacing: 0) {
                        DetailRow(label: "Horsepower",       value: "\(loco.horsepower) hp")
                        DetailRow(label: "Wheel Arrangement", value: loco.wheelArrangement)
                        DetailRow(label: "Traction",          value: loco.traction + " traction")
                        DetailRow(label: "Manufacturer",      value: loco.manufacturer)
                        DetailRow(label: "Introduced",        value: "\(loco.introduced)")
                    }
                    .cardStyle().padding(.horizontal)

                    // Railroads
                    VStack(alignment: .leading, spacing: 10) {
                        Label("Operated by", systemImage: "tram.fill")
                            .font(.system(size: 13, weight: .semibold)).foregroundColor(.textMuted)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(loco.railroads, id: \.self) { rr in
                                    Text(rr)
                                        .font(.system(size: 12, weight: .medium)).foregroundColor(.white)
                                        .padding(.horizontal, 10).padding(.vertical, 5)
                                        .background(Color.railBlueMid).cornerRadius(8)
                                }
                            }
                        }
                    }
                    .padding(14).cardStyle().padding(.horizontal)

                    // Notes
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Notes", systemImage: "doc.text")
                            .font(.system(size: 13, weight: .semibold)).foregroundColor(.textMuted)
                        Text(loco.notes)
                            .font(.system(size: 14)).foregroundColor(.textSecondary).lineSpacing(5)
                    }
                    .padding(14).cardStyle().padding(.horizontal)

                    Spacer(minLength: 40)
                }
                .padding(.top)
            }
            .background(Color.bgPrimary)
            .navigationTitle(loco.model)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var imagePlaceholder: some View {
        HStack {
            Spacer()
            Image(systemName: "train.side.front.car")
                .font(.system(size: 48)).foregroundColor(.railBlueDark)
            Spacer()
        }
        .frame(height: 120).background(Color.bgCard).cornerRadius(12)
    }
}
