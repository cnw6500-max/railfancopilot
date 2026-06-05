import SwiftUI

// Mirrors Android EncyclopediaScreen — DieselShop.us links + Signal Systems reference
struct EncyclopediaView: View {
    @State private var searchText = ""
    @State private var rrFilter: String? = nil   // short name of selected railroad filter

    private let railroadFilters = ["BNSF","UP","CSX","NS","CN","CPKC","AMTK","METRA","LIRR","MNR","MBTA","SEPTA","CT"]

    private let rosterLinks: [(name: String, short: String, url: String, color: Color)] = [
        ("BNSF Railway",                 "BNSF",  "https://www.dieselshop.us/BNSF.HTML",     Color(red: 1.0,  green: 0.4,  blue: 0.0)),
        ("Union Pacific",                "UP",    "https://www.dieselshop.us/UP.HTML",        Color(red: 1.0,  green: 0.8,  blue: 0.0)),
        ("CSX Transportation",           "CSX",   "https://www.dieselshop.us/CSX.HTML",       Color(red: 0.0,  green: 0.34, blue: 0.66)),
        ("Norfolk Southern",             "NS",    "http://www.nsdash9.com/roster.html",       Color(red: 0.6,  green: 0.6,  blue: 0.6)),
        ("Canadian National",            "CN",    "https://www.dieselshop.us/CN.HTML",        Color(red: 0.8,  green: 0.0,  blue: 0.0)),
        ("Canadian Pacific Kansas City", "CPKC",  "https://www.dieselshop.us/CP.HTML",        Color(red: 0.55, green: 0.0,  blue: 0.0)),
        ("Amtrak",                       "AMTK",  "https://www.dieselshop.us/AMTRAK.HTML",    Color(red: 0.12, green: 0.23, blue: 0.54)),
        ("Metra",                        "METRA", "https://www.dieselshop.us/METRA.HTML",     Color(red: 0.0,  green: 0.36, blue: 0.67)),
        ("LIRR",                         "LIRR",  "https://www.dieselshop.us/LIRR.HTML",      Color(red: 0.0,  green: 0.29, blue: 0.53)),
        ("Metro North",                  "MNR",   "https://www.dieselshop.us/MNR.HTML",       Color(red: 0.0,  green: 0.41, blue: 0.24)),
        ("MBTA",                         "MBTA",  "https://www.dieselshop.us/MBTA.HTML",      Color(red: 0.0,  green: 0.24, blue: 0.65)),
        ("SEPTA",                        "SEPTA", "https://www.dieselshop.us/SEPTA.HTML",     Color(red: 0.0,  green: 0.34, blue: 0.66)),
        ("Caltrain",                     "CT",    "https://www.dieselshop.us/CALTRAIN.HTML",  Color(red: 0.55, green: 0.0,  blue: 0.0)),
        ("Browse all rosters",           "All",   "https://www.dieselshop.us",                Color.railBlue),
    ]

    private let signals: [(abbr: String, desc: String)] = [
        ("CTC", "Centralized Traffic Control — dispatcher controls all signals/switches remotely"),
        ("ABS", "Automatic Block Signals — train presence triggers signals automatically"),
        ("PTC", "Positive Train Control — GPS-based system that can stop trains automatically"),
        ("DTC", "Direct Traffic Control — used on single-track lines without signals"),
        ("TWC", "Track Warrant Control — dispatcher issues paper/verbal authority"),
    ]

    var filteredLinks: [(name: String, short: String, url: String, color: Color)] {
        rosterLinks.filter {
            let matchesSearch = searchText.isEmpty ||
                $0.name.localizedCaseInsensitiveContains(searchText) ||
                $0.short.localizedCaseInsensitiveContains(searchText)
            let matchesRr = rrFilter == nil || $0.short == rrFilter
            return matchesSearch && matchesRr
        }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                List {
                    // Search bar
                    Section {
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.textMuted).font(.system(size: 15))
                            TextField("Search railroads...", text: $searchText)
                                .foregroundColor(.textPrimary)
                        }
                        .listRowBackground(Color.bgInput)
                    }

                    // Railroad filter chips
                    Section {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                FilterChipView(label: "All", selected: rrFilter == nil) { rrFilter = nil }
                                ForEach(railroadFilters, id: \.self) { rr in
                                    FilterChipView(label: rr, selected: rrFilter == rr) {
                                        rrFilter = rrFilter == rr ? nil : rr
                                    }
                                }
                            }
                            .padding(.vertical, 4)
                        }
                        .listRowBackground(Color.bgPrimary)
                        .listRowInsets(EdgeInsets(top: 0, leading: 8, bottom: 0, trailing: 8))
                    }

                    // Signal Systems
                    if searchText.isEmpty {
                        Section(header: SectionHeader(title: "Signal Systems")) {
                            ForEach(signals, id: \.abbr) { sig in
                                HStack(alignment: .top, spacing: 12) {
                                    Text(sig.abbr)
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(.railBlue)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.railBlueDark)
                                        .cornerRadius(4)
                                    Text(sig.desc)
                                        .font(.system(size: 13))
                                        .foregroundColor(.textSecondary)
                                        .lineSpacing(3)
                                }
                                .listRowBackground(Color.bgCard)
                            }
                        }
                    }

                    // Railroad Rosters
                    Section(header:
                        VStack(alignment: .leading, spacing: 4) {
                            SectionHeader(title: "Railroad Rosters")
                            HStack(spacing: 8) {
                                Image(systemName: "safari")
                                    .foregroundColor(.railBlue)
                                    .font(.system(size: 13))
                                Text("Powered by DieselShop.us · tap to open in browser")
                                    .font(.system(size: 12))
                                    .foregroundColor(.textMuted)
                            }
                            .padding(.horizontal, 16)
                            .padding(.bottom, 4)
                        }
                    ) {
                        ForEach(filteredLinks, id: \.name) { roster in
                            Button {
                                if let url = URL(string: roster.url) {
                                    UIApplication.shared.open(url)
                                }
                            } label: {
                                HStack(spacing: 14) {
                                    Circle()
                                        .fill(roster.color)
                                        .frame(width: 10, height: 10)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(roster.name)
                                            .font(.system(size: 14, weight: .medium))
                                            .foregroundColor(.textPrimary)
                                        Text(roster.short)
                                            .font(.system(size: 12))
                                            .foregroundColor(.textMuted)
                                    }
                                    Spacer()
                                    Image(systemName: "arrow.up.right.square")
                                        .foregroundColor(.railBlue)
                                        .font(.system(size: 15))
                                }
                            }
                            .listRowBackground(Color.bgCard)
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
                .background(Color.bgPrimary)
            }
            .navigationTitle("Encyclopedia")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}
