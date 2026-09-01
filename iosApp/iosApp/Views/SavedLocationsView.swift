import SwiftUI
import MapKit
import shared

private let FREE_LOCATION_LIMIT = 3

extension SavedLocationShared: @retroactive Identifiable {}

// ── Main View ─────────────────────────────────────────────────────────────────
struct SavedLocationsView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var showAddSheet    = false
    @State private var selectedLocation: SavedLocationShared? = nil
    @State private var locationToDelete: SavedLocationShared? = nil
    @State private var showUpgrade     = false
    @State private var showSpots = false
    @State private var showTripLog = false

    private var atFreeLimit: Bool {
        !vm.isPremium && vm.savedLocations.count >= FREE_LOCATION_LIMIT
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                if vm.savedLocations.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "bookmark.slash")
                            .font(.system(size: 48))
                            .foregroundColor(.railBlueDark)
                        Text("No saved locations yet")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.textPrimary)
                        Text("Save your favourite railfan spots to get proximity alerts when trains approach.")
                            .font(.system(size: 13))
                            .foregroundColor(.textMuted)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                        Button { showAddSheet = true } label: {
                            Label("Add Location", systemImage: "plus")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 10)
                                .background(Color.railBlueMid)
                                .cornerRadius(10)
                        }
                        .padding(.top, 8)
                    }
                } else {
                    ScrollView {
                        // Count header
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Saved Locations")
                                    .font(.system(size: 18, weight: .medium))
                                    .foregroundColor(.textPrimary)
                                Text(vm.isPremium
                                     ? "\(vm.savedLocations.count) saved spot\(vm.savedLocations.count == 1 ? "" : "s")"
                                     : "\(vm.savedLocations.count) / \(FREE_LOCATION_LIMIT) spots (free)")
                                    .font(.system(size: 13))
                                    .foregroundColor(.textMuted)
                            }
                            Spacer()
                        }
                        .padding(.horizontal)
                        .padding(.top, 12)

                        // Location cards
                        VStack(spacing: 8) {
                            ForEach(vm.savedLocations, id: \.id) { loc in
                                SavedLocationCard(loc: loc,
                                    onTap: { selectedLocation = loc },
                                    onDelete: { locationToDelete = loc })
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 100)
                    }
                }
            }
            .navigationTitle("Saved Locations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    HStack(spacing: 14) {
                        Button { showSpots = true } label: {
                            Image(systemName: "camera.aperture").foregroundColor(.railBlue)
                        }
                        Button { showTripLog = true } label: {
                            Image(systemName: "book.fill").foregroundColor(.railBlue)
                        }
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        if atFreeLimit { showUpgrade = true }
                        else { showAddSheet = true }
                    } label: {
                        Image(systemName: atFreeLimit ? "lock.fill" : "plus")
                            .foregroundColor(atFreeLimit ? .textMuted : .railBlue)
                    }
                }
            }
        }
        .sheet(isPresented: $showSpots) { SpotsView(vm: vm) }
        .sheet(isPresented: $showTripLog) { TripLogView(vm: vm) }
        // Add sheet
        .sheet(isPresented: $showAddSheet) {
            AddLocationSheet(vm: vm)
        }
        // Detail sheet
        .sheet(item: $selectedLocation) { loc in
            LocationDetailSheet(loc: loc, vm: vm)
        }
        // Upgrade sheet
        .sheet(isPresented: $showUpgrade) {
            UpgradeView(vm: vm)
        }
        // Delete confirmation
        .confirmationDialog(
            "Delete \"\(locationToDelete?.name ?? "")\"?",
            isPresented: Binding(get: { locationToDelete != nil },
                                 set: { if !$0 { locationToDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let loc = locationToDelete { vm.deleteLocation(id: loc.id) }
                locationToDelete = nil
            }
            Button("Cancel", role: .cancel) { locationToDelete = nil }
        }
    }
}

// ── Location Card ─────────────────────────────────────────────────────────────
struct SavedLocationCard: View {
    let loc: SavedLocationShared
    let onTap: () -> Void
    let onDelete: () -> Void

    private var dateStr: String {
        let date = Date(timeIntervalSince1970: Double(loc.createdMs) / 1000)
        let fmt = DateFormatter()
        fmt.dateFormat = "MMM d, yyyy"
        return fmt.string(from: date)
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: "bookmark.fill")
                    .font(.system(size: 18))
                    .foregroundColor(.railBlue)

                VStack(alignment: .leading, spacing: 3) {
                    Text(loc.name)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.textPrimary)
                    Text(String(format: "%.4f°, %.4f°", loc.latitude, loc.longitude))
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(.textMuted)
                    if let sub = loc.subdivision, !sub.isEmpty {
                        Text(sub)
                            .font(.system(size: 12))
                            .foregroundColor(.railBlue)
                    }
                    if let freq = loc.scannerFrequency, !freq.isEmpty {
                        Text("Scanner: \(freq)")
                            .font(.system(size: 11))
                            .foregroundColor(.textMuted)
                    }
                    if let notes = loc.notes, !notes.isEmpty {
                        Text(notes)
                            .font(.system(size: 12))
                            .foregroundColor(.textSecondary)
                            .lineLimit(2)
                    }
                    Text(dateStr)
                        .font(.system(size: 11))
                        .foregroundColor(.textMuted)
                }

                Spacer()

                // Navigate button
                Button {
                    openInMaps(loc)
                } label: {
                    Image(systemName: "arrow.triangle.turn.up.right.circle.fill")
                        .font(.system(size: 22))
                        .foregroundColor(.railBlue)
                }
                .buttonStyle(.plain)

                // Delete button
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(.system(size: 16))
                        .foregroundColor(.textMuted)
                }
                .buttonStyle(.plain)
            }
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.border, lineWidth: 0.5))
        }
        .buttonStyle(.plain)
    }

    private func openInMaps(_ loc: SavedLocationShared) {
        let placemark = MKPlacemark(coordinate: CLLocationCoordinate2D(
            latitude: loc.latitude, longitude: loc.longitude))
        let mapItem = MKMapItem(placemark: placemark)
        mapItem.name = loc.name
        mapItem.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving])
    }
}

// ── Add Location Sheet ────────────────────────────────────────────────────────
struct AddLocationSheet: View {
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss
    @State private var name        = ""
    @State private var notes       = ""
    @State private var subdivision = ""
    @State private var frequency   = ""
    @State private var photoTips   = ""

    private var hasGps: Bool {
        vm.userLocation != nil
    }
    private var coordLabel: String {
        guard let loc = vm.userLocation else { return "No GPS fix yet" }
        return String(format: "%.4f°, %.4f°", loc.latitude, loc.longitude)
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 14) {
                        // GPS readout
                        HStack(spacing: 8) {
                            Image(systemName: hasGps ? "location.fill" : "location.slash")
                                .foregroundColor(hasGps ? .railBlue : .textMuted)
                                .font(.system(size: 14))
                            Text(coordLabel)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(hasGps ? .textPrimary : .textMuted)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color.bgCard)
                        .cornerRadius(10)

                        // Fields
                        LocationTextField(label: "Location name *",
                            placeholder: "e.g. MP 330 Overpass", text: $name)
                        LocationTextField(label: "Notes",
                            placeholder: "Lighting tips, access info…",
                            text: $notes, multiline: true)
                        LocationTextField(label: "Subdivision",
                            placeholder: "e.g. Transcon, Mains, Springfield",
                            text: $subdivision)
                        LocationTextField(label: "Scanner frequency",
                            placeholder: "e.g. 161.100",
                            text: $frequency,
                            keyboardType: .decimalPad)
                        LocationTextField(label: "Photo tips",
                            placeholder: "Best angle, time of day, access notes…",
                            text: $photoTips, multiline: true)

                        // Save button
                        Button { save() } label: {
                            Text("Save Location")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(name.isEmpty || !hasGps ? Color.railBlueDark : Color.railBlueMid)
                                .cornerRadius(12)
                        }
                        .disabled(name.isEmpty || !hasGps)

                        if !hasGps {
                            Text("Waiting for GPS fix…")
                                .font(.system(size: 12))
                                .foregroundColor(.textMuted)
                        }

                        Spacer(minLength: 40)
                    }
                    .padding()
                }
            }
            .navigationTitle("Save Location")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }.foregroundColor(.textMuted)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func save() {
        let lat = vm.userLocation?.latitude  ?? 0.0
        let lon = vm.userLocation?.longitude ?? 0.0
        let loc = SavedLocationShared(
            id: UUID().uuidString, name: name,
            latitude: lat, longitude: lon,
            notes: notes.isEmpty ? nil : notes,
            subdivision: subdivision.isEmpty ? nil : subdivision,
            scannerFrequency: frequency.isEmpty ? nil : frequency,
            photoTips: photoTips.isEmpty ? nil : photoTips,
            createdMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        vm.saveLocation(loc)
        dismiss()
    }
}

// ── Location Detail Sheet ─────────────────────────────────────────────────────
struct LocationDetailSheet: View {
    let loc: SavedLocationShared
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss
    @State private var showDeleteConfirm = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Mini map
                    Map(coordinateRegion: .constant(MKCoordinateRegion(
                        center: CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude),
                        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05))),
                        annotationItems: [loc]) { _ in
                        MapMarker(coordinate: CLLocationCoordinate2D(
                            latitude: loc.latitude, longitude: loc.longitude), tint: .railBlue)
                    }
                    .frame(height: 180)
                    .cornerRadius(12)
                    .padding(.horizontal)

                    // Detail rows
                    VStack(alignment: .leading, spacing: 0) {
                        if let sub = loc.subdivision, !sub.isEmpty {
                            SavedLocationDetailRow(label: "Subdivision", value: sub)
                        }
                        if let freq = loc.scannerFrequency, !freq.isEmpty {
                            SavedLocationDetailRow(label: "Frequency", value: "\(freq) MHz")
                        }
                        SavedLocationDetailRow(label: "Coordinates",
                                  value: String(format: "%.5f, %.5f", loc.latitude, loc.longitude))
                        if let notes = loc.notes, !notes.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Notes").font(.system(size: 13)).foregroundColor(.textMuted)
                                    .padding(.horizontal, 14).padding(.top, 10)
                                Text(notes).font(.system(size: 14)).foregroundColor(.textSecondary)
                                    .padding(.horizontal, 14).padding(.bottom, 12)
                            }
                        }
                        if let tips = loc.photoTips, !tips.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Photo Tips").font(.system(size: 13)).foregroundColor(.textMuted)
                                    .padding(.horizontal, 14).padding(.top, 10)
                                Text(tips).font(.system(size: 14)).foregroundColor(.textSecondary)
                                    .padding(.horizontal, 14).padding(.bottom, 12)
                            }
                        }
                    }
                    .background(Color.bgCard)
                    .cornerRadius(14)
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
                    .padding(.horizontal)

                    // Navigate button
                    Button { openInMaps() } label: {
                        Label("Navigate Here", systemImage: "arrow.triangle.turn.up.right.circle.fill")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.railBlueMid)
                            .cornerRadius(12)
                    }
                    .padding(.horizontal)

                    // Delete button
                    Button {
                        showDeleteConfirm = true
                    } label: {
                        Label("Delete Location", systemImage: "trash")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.red)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.red.opacity(0.3), lineWidth: 0.5))
                    }
                    .padding(.horizontal)
                    .confirmationDialog(
                        "Delete \"\(loc.name)\"?",
                        isPresented: $showDeleteConfirm,
                        titleVisibility: .visible
                    ) {
                        Button("Delete", role: .destructive) {
                            vm.deleteLocation(id: loc.id)
                            dismiss()
                        }
                        Button("Cancel", role: .cancel) {}
                    }

                    Spacer(minLength: 40)
                }
                .padding(.vertical)
            }
            .background(Color.bgPrimary)
            .navigationTitle(loc.name)
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

    private func openInMaps() {
        let placemark = MKPlacemark(coordinate: CLLocationCoordinate2D(
            latitude: loc.latitude, longitude: loc.longitude))
        let mapItem = MKMapItem(placemark: placemark)
        mapItem.name = loc.name
        mapItem.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving])
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────
private struct SavedLocationDetailRow: View {
    let label: String; let value: String
    var body: some View {
        HStack {
            Text(label).font(.system(size: 13)).foregroundColor(.textMuted)
            Spacer()
            Text(value).font(.system(size: 13)).foregroundColor(.textPrimary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        Divider().background(Color.border).padding(.horizontal, 14)
    }
}

private struct LocationTextField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    var multiline = false
    var keyboardType: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.system(size: 13)).foregroundColor(.textMuted)
            if multiline {
                TextEditor(text: $text)
                    .foregroundColor(.textPrimary)
                    .frame(height: 72)
                    .padding(8)
                    .background(Color.bgCard)
                    .cornerRadius(10)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
            } else {
                TextField(placeholder, text: $text)
                    .textFieldStyle(.plain)
                    .foregroundColor(.textPrimary)
                    .keyboardType(keyboardType)
                    .padding(12)
                    .background(Color.bgCard)
                    .cornerRadius(10)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
            }
        }
    }
}
