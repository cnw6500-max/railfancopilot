import SwiftUI
import MapKit
import shared

struct RailfanSpot: Identifiable {
    let id = UUID()
    let name: String
    let location: String
    let state: String
    let railroad: String
    let subdivision: String
    let description: String
    let photoTips: String
    let bestTime: String
    let access: SpotAccess
    let latitude: Double
    let longitude: Double
    let scannerFreq: String?
}

enum SpotAccess: String {
    case public_  = "Public"
    case park     = "Park / Preserve"
    case roadside = "Roadside"
}

private let curatedSpots: [RailfanSpot] = [
    RailfanSpot(name: "Rochelle Railroad Park",
                location: "Rochelle", state: "IL",
                railroad: "BNSF / UP", subdivision: "Chillicothe Sub / Council Bluffs Sub",
                description: "The world's most-watched diamond crossing. A dedicated park with a pavilion, scanner hookups, and 100+ trains per day.",
                photoTips: "Shoot from the pavilion for a broadside. Use a 24–70mm. Best light is morning for eastbounds and late afternoon for westbounds.",
                bestTime: "Anytime — traffic is constant. Golden hour is spectacular.",
                access: .park, latitude: 41.9209, longitude: -89.0711,
                scannerFreq: "161.520 (BNSF Ch 7)"),

    RailfanSpot(name: "Horseshoe Curve",
                location: "Altoona", state: "PA",
                railroad: "NS", subdivision: "Pittsburgh Line",
                description: "National Historic Landmark on the former PRR main. A funicular takes you to the apex. Helpers cut in here going west.",
                photoTips: "Shoot from the top of the curve for dramatic curve shots. Telephoto for the horseshoe fill. Watch for helper sets on the rear.",
                bestTime: "Morning light hits the horseshoe best.",
                access: .park, latitude: 40.5152, longitude: -78.3869,
                scannerFreq: "160.410 (NS Ch 6)"),

    RailfanSpot(name: "Cajon Pass — Sullivan's Curve",
                location: "San Bernardino", state: "CA",
                railroad: "BNSF / UP", subdivision: "San Bernardino Sub",
                description: "Iconic S-curve on the desert grade. Helpers, intermodals, and autoracks grind up in dramatic desert scenery.",
                photoTips: "Wide angle for the curve with mountain backdrop. Arrive early — it gets hot. Bring water.",
                bestTime: "Early morning for soft desert light and cool temperatures.",
                access: .roadside, latitude: 34.3128, longitude: -117.4509,
                scannerFreq: "161.100 (BNSF Ch 72)"),

    RailfanSpot(name: "Tehachapi Loop",
                location: "Tehachapi", state: "CA",
                railroad: "BNSF / UP", subdivision: "Mojave Sub",
                description: "Legendary spiral loop where long trains literally cross over themselves. One of the engineering marvels of US railroading.",
                photoTips: "500mm or longer for the loop shot from the overlook. A 100-car train takes ~20 min to fully loop.",
                bestTime: "Midday light is flat but workable. Golden hour is exceptional.",
                access: .public_, latitude: 35.1419, longitude: -118.4447,
                scannerFreq: "160.590 (BNSF Ch 18)"),

    RailfanSpot(name: "Donner Pass — Norden",
                location: "Norden", state: "CA",
                railroad: "UP", subdivision: "Overland Route",
                description: "Historic Sierra Nevada crossing with snow sheds and dramatic granite scenery. Spectacular in winter.",
                photoTips: "Snow scenes in winter are world-class. Summer shows lush forest. Dress for cold — it's at 7,000 ft.",
                bestTime: "Winter for snow; summer for greenery. Avoid mid-day flat light.",
                access: .roadside, latitude: 39.3318, longitude: -120.3342,
                scannerFreq: "161.010 (UP Ch 52)"),

    RailfanSpot(name: "Marias Pass",
                location: "East Glacier", state: "MT",
                railroad: "BNSF", subdivision: "Marias Pass Sub",
                description: "Stunning Rocky Mountain crossing on the Hi-Line. Adjacent to Glacier National Park. Some of the most dramatic scenery on any US railroad.",
                photoTips: "The entire pass is photogenic. The Two Medicine bridge is iconic. Bears are real — stay aware.",
                bestTime: "Late summer for wildflowers plus trains. Early morning mist is magical.",
                access: .public_, latitude: 48.3119, longitude: -113.3566,
                scannerFreq: "160.410 (BNSF Ch 6)"),

    RailfanSpot(name: "Palmer Lake — BNSF Joint Line",
                location: "Palmer Lake", state: "CO",
                railroad: "BNSF / UP", subdivision: "Joint Line",
                description: "High-traffic corridor between Denver and Pueblo. Coal trains, intermodals, and helpers on a busy double-track mainline.",
                photoTips: "The lake reflection makes for stunning wedge shots. Use the Rocky Mountains as a backdrop.",
                bestTime: "Morning for northbounds against the mountains.",
                access: .public_, latitude: 39.1211, longitude: -104.9186,
                scannerFreq: "160.515 (BNSF Ch 12)"),

    RailfanSpot(name: "Galesburg, IL — BNSF Chillicothe Sub",
                location: "Galesburg", state: "IL",
                railroad: "BNSF", subdivision: "Chillicothe Sub",
                description: "Flat Midwest mainline with extremely high traffic. The railroad park at Seminary Street is excellent.",
                photoTips: "Broadside shots from Seminary St. overpass. Intermodal trains are frequent.",
                bestTime: "Anytime — traffic is heavy 24/7.",
                access: .park, latitude: 40.9478, longitude: -90.3712,
                scannerFreq: "161.520 (BNSF Ch 7)"),

    RailfanSpot(name: "Folkston Funnel",
                location: "Folkston", state: "GA",
                railroad: "CSX", subdivision: "Nahunta Sub",
                description: "Where CSX traffic funnels through Georgia. A dedicated railfan platform with scanners, chairs, and power outlets.",
                photoTips: "The platform is perfectly positioned. Long lens for meet shots at the north switch.",
                bestTime: "All day — 60–70 trains per day.",
                access: .park, latitude: 30.8352, longitude: -82.0124,
                scannerFreq: "160.800 (CSX Ch 42)"),

    RailfanSpot(name: "NS Rathole Division — Jellico",
                location: "Jellico", state: "TN",
                railroad: "NS", subdivision: "CNO&TP District",
                description: "Steep mountain grade with helper operations. Trains struggle over the Tennessee–Kentucky state line.",
                photoTips: "Chase helpers working hard on the grade. Fill flash helps in the tree shadows.",
                bestTime: "Overcast for even light in the forested cuts.",
                access: .roadside, latitude: 36.5854, longitude: -84.1294,
                scannerFreq: "160.920 (NS Ch 46)"),

    RailfanSpot(name: "UP Wasatch Grade — Echo Canyon",
                location: "Echo", state: "UT",
                railroad: "UP", subdivision: "Overland Route",
                description: "Dramatic red-rock canyon on the historic Overland Route. Dramatic helper operations on the grade.",
                photoTips: "The canyon walls are the backdrop — use them. Telephoto for trains emerging from the narrows.",
                bestTime: "Midday in winter for good light in the canyon.",
                access: .public_, latitude: 40.9747, longitude: -111.4387,
                scannerFreq: "161.130 (UP Ch 63)"),

    RailfanSpot(name: "Crestline — NS Chicago Line",
                location: "Crestline", state: "OH",
                railroad: "NS", subdivision: "Chicago Line",
                description: "Busy NS main at a junction town. Tower and several switches visible from public streets.",
                photoTips: "The grade crossing at Thoman St is classic. Watch for priority intermodals.",
                bestTime: "Afternoons for westbounds in good light.",
                access: .public_, latitude: 40.7937, longitude: -82.7357,
                scannerFreq: "160.515 (NS Ch 12)"),
]

// ── SpotsView ─────────────────────────────────────────────────────────────────
struct SpotsView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var selectedSpot: RailfanSpot? = nil
    @State private var selectedCustomSpot: CustomRailfanSpot? = nil
    @State private var searchText = ""
    @State private var showAddSheet = false
    @State private var customSpots: [CustomRailfanSpot] = PersistenceManager.shared.loadCustomSpots()

    var filteredCurated: [RailfanSpot] {
        guard !searchText.isEmpty else { return curatedSpots }
        return curatedSpots.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.state.localizedCaseInsensitiveContains(searchText) ||
            $0.railroad.localizedCaseInsensitiveContains(searchText) ||
            $0.location.localizedCaseInsensitiveContains(searchText)
        }
    }

    var filteredCustom: [CustomRailfanSpot] {
        guard !searchText.isEmpty else { return customSpots }
        return customSpots.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.state.localizedCaseInsensitiveContains(searchText) ||
            $0.railroad.localizedCaseInsensitiveContains(searchText) ||
            $0.location.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                VStack(spacing: 0) {
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.textMuted).font(.system(size: 14))
                        TextField("Search spots, states, railroads…", text: $searchText)
                            .foregroundColor(.textPrimary).font(.system(size: 14))
                        if !searchText.isEmpty {
                            Button { searchText = "" } label: {
                                Image(systemName: "xmark.circle.fill").foregroundColor(.textMuted)
                            }
                        }
                    }
                    .padding(10).background(Color.bgInput).cornerRadius(10)
                    .padding(.horizontal, 12).padding(.vertical, 8)

                    List {
                        if !filteredCustom.isEmpty {
                            Section {
                                ForEach(filteredCustom) { spot in
                                    Button { selectedCustomSpot = spot } label: {
                                        CustomSpotRow(spot: spot)
                                    }
                                    .listRowBackground(Color.bgCard)
                                    .listRowSeparatorTint(Color.border)
                                }
                                .onDelete { idxSet in
                                    // Map filtered-list indices back to original array indices
                                    let toDelete = Set(idxSet.map { filteredCustom[$0].id })
                                    customSpots.removeAll { toDelete.contains($0.id) }
                                    PersistenceManager.shared.saveCustomSpots(customSpots)
                                }
                            } header: {
                                Text("MY SPOTS").font(.system(size: 11)).foregroundColor(.textMuted)
                            }
                        }

                        Section {
                            ForEach(filteredCurated) { spot in
                                Button { selectedSpot = spot } label: {
                                    SpotRow(spot: spot)
                                }
                                .listRowBackground(Color.bgCard)
                                .listRowSeparatorTint(Color.border)
                            }
                        } header: {
                            Text("CURATED (\(filteredCurated.count))").font(.system(size: 11)).foregroundColor(.textMuted)
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("Spots")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showAddSheet = true } label: {
                        Image(systemName: "plus").foregroundColor(.railBlue)
                    }
                }
            }
        }
        .sheet(item: $selectedSpot) { spot in
            SpotDetailSheet(spot: spot, vm: vm)
        }
        .sheet(item: $selectedCustomSpot) { spot in
            CustomSpotDetailSheet(spot: spot, vm: vm, onDelete: {
                customSpots.removeAll { $0.id == spot.id }
                PersistenceManager.shared.saveCustomSpots(customSpots)
            })
        }
        .sheet(isPresented: $showAddSheet) {
            AddSpotSheet(vm: vm) { newSpot in
                customSpots.insert(newSpot, at: 0)
                PersistenceManager.shared.saveCustomSpots(customSpots)
            }
        }
    }
}

// ── Custom spot row ───────────────────────────────────────────────────────────
struct CustomSpotRow: View {
    let spot: CustomRailfanSpot
    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(spot.access)
                    .font(.system(size: 9, weight: .bold)).foregroundColor(.white)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Color.railBlueMid).cornerRadius(4)
                Image(systemName: "star.fill")
                    .font(.system(size: 20)).foregroundColor(.yellow)
            }
            .frame(width: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text(spot.name)
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(.textPrimary)
                Text("\(spot.location), \(spot.state) · \(spot.railroad)")
                    .font(.system(size: 12)).foregroundColor(.textMuted)
                if !spot.description.isEmpty {
                    Text(spot.description)
                        .font(.system(size: 11)).foregroundColor(.textSecondary).lineLimit(2)
                }
            }
            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 12)).foregroundColor(.textMuted)
        }
        .padding(.vertical, 6)
    }
}

// ── Add spot sheet ────────────────────────────────────────────────────────────
struct AddSpotSheet: View {
    @ObservedObject var vm: RailFanViewModel
    let onSave: (CustomRailfanSpot) -> Void
    @Environment(\.dismiss) var dismiss

    @State private var name = ""
    @State private var location = ""
    @State private var state = ""
    @State private var railroad = ""
    @State private var subdivision = ""
    @State private var description = ""
    @State private var photoTips = ""
    @State private var bestTime = ""
    @State private var access = "Public"
    @State private var latText = ""
    @State private var lonText = ""
    @State private var scannerFreq = ""
    @State private var useMyLocation = false

    private let accessOptions = ["Public", "Park / Preserve", "Roadside"]

    var canSave: Bool { !name.isEmpty && !location.isEmpty }

    var body: some View {
        NavigationView {
            Form {
                Section("Basic Info") {
                    TextField("Spot name *", text: $name)
                    TextField("City / town *", text: $location)
                    TextField("State (e.g. IL)", text: $state)
                        .autocorrectionDisabled()
                    TextField("Railroad(s)", text: $railroad)
                    TextField("Subdivision", text: $subdivision)
                    Picker("Access", selection: $access) {
                        ForEach(accessOptions, id: \.self) { Text($0) }
                    }
                }

                Section("Location") {
                    Toggle("Use my current location", isOn: $useMyLocation)
                        .tint(.railBlue)
                        .onChange(of: useMyLocation) { on in
                            if on, let loc = vm.userLocation {
                                latText = String(format: "%.6f", loc.latitude)
                                lonText = String(format: "%.6f", loc.longitude)
                            }
                        }
                    TextField("Latitude", text: $latText)
                        .keyboardType(.decimalPad)
                        .disabled(useMyLocation)
                    TextField("Longitude", text: $lonText)
                        .keyboardType(.decimalPad)
                        .disabled(useMyLocation)
                }

                Section("Details") {
                    TextField("Scanner frequency", text: $scannerFreq)
                    TextField("Best time to visit", text: $bestTime)
                }

                Section("Notes") {
                    TextEditor(text: $description)
                        .frame(minHeight: 80)
                        .foregroundColor(.textPrimary)
                        .overlay(alignment: .topLeading) {
                            if description.isEmpty {
                                Text("About this spot…")
                                    .foregroundColor(.textMuted)
                                    .font(.system(size: 14)).padding(4)
                            }
                        }
                    TextEditor(text: $photoTips)
                        .frame(minHeight: 60)
                        .foregroundColor(.textPrimary)
                        .overlay(alignment: .topLeading) {
                            if photoTips.isEmpty {
                                Text("Photo tips…").foregroundColor(.textMuted)
                                    .font(.system(size: 14)).padding(4)
                            }
                        }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.bgPrimary)
            .navigationTitle("Add Spot")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }.foregroundColor(.railBlue)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") { save() }
                        .foregroundColor(canSave ? .railBlue : .textMuted)
                        .disabled(!canSave)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func save() {
        let lat = Double(latText) ?? vm.userLocation?.latitude ?? 0
        let lon = Double(lonText) ?? vm.userLocation?.longitude ?? 0
        let spot = CustomRailfanSpot(
            id: UUID().uuidString,
            name: name,
            location: location,
            state: state,
            railroad: railroad,
            subdivision: subdivision,
            description: description,
            photoTips: photoTips,
            bestTime: bestTime,
            access: access,
            latitude: lat,
            longitude: lon,
            scannerFreq: scannerFreq,
            createdDate: Date()
        )
        onSave(spot)
        dismiss()
    }
}

// ── Custom spot detail sheet ───────────────────────────────────────────────────
struct CustomSpotDetailSheet: View {
    let spot: CustomRailfanSpot
    @ObservedObject var vm: RailFanViewModel
    let onDelete: () -> Void
    @Environment(\.dismiss) var dismiss
    @State private var saved = false
    @State private var confirmDelete = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Map(coordinateRegion: .constant(MKCoordinateRegion(
                        center: CLLocationCoordinate2D(latitude: spot.latitude, longitude: spot.longitude),
                        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05))),
                        annotationItems: [spot]) { s in
                        MapMarker(coordinate: CLLocationCoordinate2D(latitude: s.latitude, longitude: s.longitude), tint: .yellow)
                    }
                    .frame(height: 180).cornerRadius(14).padding(.horizontal)

                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(spot.name)
                                .font(.system(size: 20, weight: .bold)).foregroundColor(.textPrimary)
                            Spacer()
                            Image(systemName: "star.fill").foregroundColor(.yellow)
                        }
                        Label("\(spot.location), \(spot.state)", systemImage: "mappin.circle.fill")
                            .font(.system(size: 13)).foregroundColor(.textMuted)
                    }
                    .padding(.horizontal)

                    VStack(spacing: 0) {
                        if !spot.railroad.isEmpty { DetailRow(label: "Railroad", value: spot.railroad) }
                        if !spot.subdivision.isEmpty { DetailRow(label: "Subdivision", value: spot.subdivision) }
                        if !spot.scannerFreq.isEmpty { DetailRow(label: "Scanner", value: spot.scannerFreq) }
                        if !spot.bestTime.isEmpty { DetailRow(label: "Best Time", value: spot.bestTime) }
                        DetailRow(label: "Access", value: spot.access)
                    }
                    .cardStyle().padding(.horizontal)

                    if !spot.description.isEmpty {
                        spotInfoCard(icon: "binoculars.fill", title: "About", body: spot.description)
                    }
                    if !spot.photoTips.isEmpty {
                        spotInfoCard(icon: "camera.fill", title: "Photo Tips", body: spot.photoTips)
                    }

                    Button { saveSpot() } label: {
                        Label(saved ? "Saved to My Locations" : "Save to My Locations",
                              systemImage: saved ? "bookmark.fill" : "bookmark")
                            .font(.system(size: 15, weight: .semibold)).foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(saved ? Color.railBlueDark : Color.railBlueMid).cornerRadius(12)
                    }
                    .padding(.horizontal)

                    Button {
                        let url = URL(string: "maps://?ll=\(spot.latitude),\(spot.longitude)&q=\(spot.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")")!
                        UIApplication.shared.open(url)
                    } label: {
                        Label("Get Directions", systemImage: "arrow.triangle.turn.up.right.circle")
                            .font(.system(size: 15, weight: .medium)).foregroundColor(.railBlue)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(Color.bgCard).cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.border, lineWidth: 0.5))
                    }
                    .padding(.horizontal)

                    Button(role: .destructive) { confirmDelete = true } label: {
                        Label("Delete Spot", systemImage: "trash")
                            .font(.system(size: 14)).foregroundColor(.red)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color.bgCard).cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.red.opacity(0.3), lineWidth: 0.5))
                    }
                    .padding(.horizontal)

                    Spacer(minLength: 40)
                }
                .padding(.top)
            }
            .background(Color.bgPrimary)
            .navigationTitle("My Spot")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }.foregroundColor(.railBlue)
                }
            }
            .confirmationDialog("Delete this spot?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive) { onDelete(); dismiss() }
                Button("Cancel", role: .cancel) {}
            }
        }
        .preferredColorScheme(.dark)
    }

    @ViewBuilder
    private func spotInfoCard(icon: String, title: String, body: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: icon).foregroundColor(.railBlue)
                Text(title).font(.system(size: 14, weight: .semibold)).foregroundColor(.textPrimary)
            }
            Text(body).font(.system(size: 13)).foregroundColor(.textSecondary).lineSpacing(4)
        }
        .padding(14).cardStyle().padding(.horizontal)
    }

    private func saveSpot() {
        let loc = SavedLocationShared(
            id: UUID().uuidString, name: spot.name,
            latitude: spot.latitude, longitude: spot.longitude,
            notes: spot.description, subdivision: spot.subdivision,
            scannerFrequency: spot.scannerFreq.isEmpty ? nil : spot.scannerFreq,
            photoTips: spot.photoTips.isEmpty ? nil : spot.photoTips,
            createdMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        vm.saveLocation(loc)
        saved = true
    }
}

// ── Spot list row ─────────────────────────────────────────────────────────────
struct SpotRow: View {
    let spot: RailfanSpot
    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(spot.access.rawValue)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(accessColor(spot.access)).cornerRadius(4)
                Image(systemName: "camera.aperture")
                    .font(.system(size: 22)).foregroundColor(.railBlueDark)
            }
            .frame(width: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text(spot.name)
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(.textPrimary)
                Text("\(spot.location), \(spot.state) · \(spot.railroad)")
                    .font(.system(size: 12)).foregroundColor(.textMuted)
                Text(spot.description)
                    .font(.system(size: 11)).foregroundColor(.textSecondary).lineLimit(2)
            }

            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 12)).foregroundColor(.textMuted)
        }
        .padding(.vertical, 6)
    }

    private func accessColor(_ a: SpotAccess) -> Color {
        switch a {
        case .park:     return .green
        case .public_:  return Color.railBlueMid
        case .roadside: return .orange
        }
    }
}

// ── Spot detail sheet ─────────────────────────────────────────────────────────
struct SpotDetailSheet: View {
    let spot: RailfanSpot
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss
    @State private var saved = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {

                    // Mini map
                    Map(coordinateRegion: .constant(MKCoordinateRegion(
                        center: CLLocationCoordinate2D(latitude: spot.latitude, longitude: spot.longitude),
                        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05))),
                        annotationItems: [spot]) { s in
                        MapMarker(coordinate: CLLocationCoordinate2D(latitude: s.latitude, longitude: s.longitude),
                                  tint: .railBlue)
                    }
                    .frame(height: 180)
                    .cornerRadius(14)
                    .padding(.horizontal)

                    // Header
                    VStack(alignment: .leading, spacing: 6) {
                        Text(spot.name)
                            .font(.system(size: 20, weight: .bold)).foregroundColor(.textPrimary)
                        HStack(spacing: 8) {
                            Label("\(spot.location), \(spot.state)", systemImage: "mappin.circle.fill")
                                .font(.system(size: 13)).foregroundColor(.textMuted)
                            Spacer()
                            Text(spot.access.rawValue)
                                .font(.system(size: 11, weight: .semibold)).foregroundColor(.white)
                                .padding(.horizontal, 8).padding(.vertical, 3)
                                .background(Color.railBlueMid).cornerRadius(6)
                        }
                    }
                    .padding(.horizontal)

                    // Detail cards
                    VStack(spacing: 0) {
                        DetailRow(label: "Railroad",     value: spot.railroad)
                        DetailRow(label: "Subdivision",  value: spot.subdivision)
                        if let freq = spot.scannerFreq {
                            DetailRow(label: "Scanner",  value: freq)
                        }
                        DetailRow(label: "Best Time",    value: spot.bestTime)
                    }
                    .cardStyle().padding(.horizontal)

                    infoCard(icon: "binoculars.fill", title: "About", body: spot.description)
                    infoCard(icon: "camera.fill", title: "Photo Tips", body: spot.photoTips)

                    // Save button
                    Button {
                        saveSpot()
                    } label: {
                        Label(saved ? "Saved to My Locations" : "Save to My Locations",
                              systemImage: saved ? "bookmark.fill" : "bookmark")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(saved ? Color.railBlueDark : Color.railBlueMid)
                            .cornerRadius(12)
                    }
                    .padding(.horizontal)

                    // Apple Maps directions
                    Button {
                        let url = URL(string: "maps://?ll=\(spot.latitude),\(spot.longitude)&q=\(spot.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")")!
                        UIApplication.shared.open(url)
                    } label: {
                        Label("Get Directions", systemImage: "arrow.triangle.turn.up.right.circle")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.railBlue)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(Color.bgCard).cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.border, lineWidth: 0.5))
                    }
                    .padding(.horizontal)

                    Spacer(minLength: 40)
                }
                .padding(.top)
            }
            .background(Color.bgPrimary)
            .navigationTitle("Spot Details")
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

    @ViewBuilder
    private func infoCard(icon: String, title: String, body: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: icon).foregroundColor(.railBlue)
                Text(title).font(.system(size: 14, weight: .semibold)).foregroundColor(.textPrimary)
            }
            Text(body).font(.system(size: 13)).foregroundColor(.textSecondary).lineSpacing(4)
        }
        .padding(14).cardStyle().padding(.horizontal)
    }

    private func saveSpot() {
        let loc = SavedLocationShared(
            id: UUID().uuidString,
            name: spot.name,
            latitude: spot.latitude,
            longitude: spot.longitude,
            notes: spot.description,
            subdivision: spot.subdivision,
            scannerFrequency: spot.scannerFreq,
            photoTips: spot.photoTips,
            createdMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        vm.saveLocation(loc)
        saved = true
    }
}
