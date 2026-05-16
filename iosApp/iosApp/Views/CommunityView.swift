import SwiftUI

// ── Sample data model ─────────────────────────────────────────────────────────
struct Sighting: Identifiable {
    let id = UUID()
    let railroad: String
    let trainSymbol: String
    let location: String
    let notes: String
    let distanceMiles: Double
    let minutesAgo: Int
    let reporterName: String
}

// ── Main View ─────────────────────────────────────────────────────────────────
struct CommunityView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var distanceFilter: Double = 50
    @State private var showAddSighting = false

    // Sample sightings — will be replaced by backend data
    private let sampleSightings: [Sighting] = [
        Sighting(railroad: "BNSF",   trainSymbol: "QCHILA",    location: "Galesburg, IL",       notes: "ES44AC leading, 180 cars",         distanceMiles: 12,  minutesAgo: 5,   reporterName: "TrainFan_IL"),
        Sighting(railroad: "UP",     trainSymbol: "MSKCC-12",  location: "North Platte, NE",     notes: "Heritage unit on point!",           distanceMiles: 24,  minutesAgo: 18,  reporterName: "NebraskaRails"),
        Sighting(railroad: "Amtrak", trainSymbol: "California Zephyr", location: "Denver, CO",  notes: "On time, Superliner consist",       distanceMiles: 38,  minutesAgo: 32,  reporterName: "AmtrakWatcher"),
        Sighting(railroad: "CSX",    trainSymbol: "Q410",      location: "Cincinnati, OH",       notes: "Double stack intermodal, 2 units",  distanceMiles: 45,  minutesAgo: 47,  reporterName: "OhioRailfan"),
        Sighting(railroad: "NS",     trainSymbol: "19G",        location: "Harrisburg, PA",      notes: "Manifest freight, SD70ACe",         distanceMiles: 62,  minutesAgo: 61,  reporterName: "PennRailsPA"),
    ]

    var filteredSightings: [Sighting] {
        sampleSightings.filter { $0.distanceMiles <= distanceFilter }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 16) {

                        // Distance filter
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text("Distance Filter")
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(.textMuted)
                                Spacer()
                                Text("\(Int(distanceFilter)) miles")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.railBlue)
                            }
                            Slider(value: $distanceFilter, in: 10...200, step: 10)
                                .tint(.railBlue)
                            HStack {
                                Text("10 mi").font(.system(size: 11)).foregroundColor(.textMuted)
                                Spacer()
                                Text("200 mi").font(.system(size: 11)).foregroundColor(.textMuted)
                            }
                        }
                        .padding(16)
                        .background(Color.bgCard)
                        .cornerRadius(14)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
                        .padding(.horizontal)

                        // Sighting count
                        HStack {
                            Text("\(filteredSightings.count) sightings within \(Int(distanceFilter)) miles")
                                .font(.system(size: 13))
                                .foregroundColor(.textMuted)
                            Spacer()
                        }
                        .padding(.horizontal)

                        // Sighting list
                        if filteredSightings.isEmpty {
                            VStack(spacing: 12) {
                                Image(systemName: "binoculars")
                                    .font(.system(size: 40))
                                    .foregroundColor(.railBlueDark)
                                Text("No sightings nearby")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(.textPrimary)
                                Text("Increase the distance filter or be the first to report a sighting!")
                                    .font(.system(size: 13))
                                    .foregroundColor(.textMuted)
                                    .multilineTextAlignment(.center)
                            }
                            .padding(40)
                        } else {
                            ForEach(filteredSightings) { sighting in
                                SightingCard(sighting: sighting)
                                    .padding(.horizontal)
                            }
                        }

                        Spacer(minLength: 40)
                    }
                    .padding(.top)
                }
            }
            .navigationTitle("Community")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showAddSighting = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(.railBlue)
                            .font(.system(size: 20))
                    }
                }
            }
            .sheet(isPresented: $showAddSighting) {
                AddSightingView(isPresented: $showAddSighting)
            }
        }
    }
}

// ── Sighting Card ─────────────────────────────────────────────────────────────
struct SightingCard: View {
    let sighting: Sighting

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(sighting.railroad)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(railroadColor(sighting.railroad))
                    .cornerRadius(6)

                Text(sighting.trainSymbol)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.textPrimary)

                Spacer()

                Text("\(sighting.minutesAgo)m ago")
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
            }

            HStack(spacing: 4) {
                Image(systemName: "mappin.circle.fill")
                    .foregroundColor(.railBlue)
                    .font(.system(size: 12))
                Text(sighting.location)
                    .font(.system(size: 13))
                    .foregroundColor(.textSecondary)
                Spacer()
                Text(String(format: "%.0f mi", sighting.distanceMiles))
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
            }

            Text(sighting.notes)
                .font(.system(size: 13))
                .foregroundColor(.textSecondary)
                .lineLimit(2)

            HStack {
                Image(systemName: "person.circle")
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
                Text(sighting.reporterName)
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
                Spacer()
                Button {
                    // Share action
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 12))
                        Text("Share")
                            .font(.system(size: 12))
                    }
                    .foregroundColor(.railBlue)
                }
            }
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
    }

    private func railroadColor(_ name: String) -> Color {
        switch name {
        case "BNSF":   return Color(red: 1.0,  green: 0.4,  blue: 0.0)
        case "UP":     return Color(red: 0.6,  green: 0.4,  blue: 0.0)
        case "CSX":    return Color(red: 0.0,  green: 0.34, blue: 0.66)
        case "NS":     return Color(red: 0.4,  green: 0.4,  blue: 0.4)
        case "Amtrak": return Color(red: 0.12, green: 0.23, blue: 0.54)
        default:       return Color.railBlueMid
        }
    }
}

// ── Add Sighting Sheet ────────────────────────────────────────────────────────
struct AddSightingView: View {
    @Binding var isPresented: Bool
    @State private var railroad = ""
    @State private var trainSymbol = ""
    @State private var location = ""
    @State private var notes = ""

    private let railroads = ["BNSF", "UP", "CSX", "NS", "CN", "CP", "Amtrak", "Metra", "MBTA", "LIRR", "Metro-North", "SEPTA", "Other"]

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 16) {

                        // Railroad picker
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Railroad").font(.system(size: 13)).foregroundColor(.textMuted)
                            Picker("Railroad", selection: $railroad) {
                                Text("Select Railroad").tag("")
                                ForEach(railroads, id: \.self) { rr in
                                    Text(rr).tag(rr)
                                }
                            }
                            .pickerStyle(.menu)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(Color.bgCard)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                        }

                        // Train symbol
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Train Symbol (optional)").font(.system(size: 13)).foregroundColor(.textMuted)
                            TextField("e.g. QCHILA, California Zephyr", text: $trainSymbol)
                                .textFieldStyle(.plain)
                                .foregroundColor(.textPrimary)
                                .padding(12)
                                .background(Color.bgCard)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                        }

                        // Location
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Location").font(.system(size: 13)).foregroundColor(.textMuted)
                            TextField("e.g. Galesburg, IL", text: $location)
                                .textFieldStyle(.plain)
                                .foregroundColor(.textPrimary)
                                .padding(12)
                                .background(Color.bgCard)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                        }

                        // Notes
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Notes").font(.system(size: 13)).foregroundColor(.textMuted)
                            TextEditor(text: $notes)
                                .foregroundColor(.textPrimary)
                                .frame(height: 100)
                                .padding(8)
                                .background(Color.bgCard)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                        }

                        // Submit button
                        Button {
                            // Submit sighting
                            isPresented = false
                        } label: {
                            Text("Submit Sighting")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(railroad.isEmpty ? Color.railBlueDark : Color.railBlueMid)
                                .cornerRadius(12)
                        }
                        .disabled(railroad.isEmpty)

                        Spacer(minLength: 40)
                    }
                    .padding()
                }
            }
            .navigationTitle("Report Sighting")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { isPresented = false }
                        .foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
