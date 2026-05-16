import SwiftUI
import MapKit
import shared

extension SavedLocationShared: @retroactive Identifiable {}

struct SavedLocationsView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var showAddSheet = false
    @State private var selectedLocation: SavedLocationShared? = nil

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
                    List {
                        ForEach(vm.savedLocations, id: \.id) { loc in
                            SavedLocationRow(loc: loc)
                                .contentShape(Rectangle())
                                .onTapGesture { selectedLocation = loc }
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    Button(role: .destructive) {
                                        vm.deleteLocation(id: loc.id)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                                .listRowBackground(Color.bgCard)
                                .listRowSeparatorTint(Color.border)
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("Saved Locations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showAddSheet = true } label: {
                        Image(systemName: "plus")
                            .foregroundColor(.railBlue)
                    }
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            AddLocationSheet(vm: vm)
        }
        .sheet(item: $selectedLocation) { loc in
            LocationDetailSheet(loc: loc, vm: vm)
        }
    }
}

struct SavedLocationRow: View {
    let loc: SavedLocationShared
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "mappin.circle.fill")
                .font(.system(size: 28))
                .foregroundColor(.railBlue)
            VStack(alignment: .leading, spacing: 3) {
                Text(loc.name)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.textPrimary)
                Text(String(format: "%.4f, %.4f", loc.latitude, loc.longitude))
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(.textMuted)
                if let sub = loc.subdivision, !sub.isEmpty {
                    Text(sub)
                        .font(.system(size: 12))
                        .foregroundColor(.textSecondary)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 12))
                .foregroundColor(.textMuted)
        }
        .padding(.vertical, 4)
    }
}

struct AddLocationSheet: View {
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss
    @State private var name = ""
    @State private var notes = ""
    @State private var subdivision = ""
    @State private var frequency = ""

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Location Name").foregroundColor(.textMuted)) {
                    TextField("e.g. Rochelle Observation Park", text: $name)
                        .foregroundColor(.textPrimary)
                }
                .listRowBackground(Color.bgCard)

                Section(header: Text("Details").foregroundColor(.textMuted)) {
                    TextField("Subdivision", text: $subdivision)
                        .foregroundColor(.textPrimary)
                    TextField("Scanner Frequency (MHz)", text: $frequency)
                        .foregroundColor(.textPrimary)
                        .keyboardType(.decimalPad)
                    TextField("Notes / Photo Tips", text: $notes, axis: .vertical)
                        .foregroundColor(.textPrimary)
                        .lineLimit(3...6)
                }
                .listRowBackground(Color.bgCard)

                Section {
                    Text("Current location will be used automatically.")
                        .font(.system(size: 12))
                        .foregroundColor(.textMuted)
                }
                .listRowBackground(Color.bgPrimary)
            }
            .scrollContentBackground(.hidden)
            .background(Color.bgPrimary)
            .navigationTitle("Add Location")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }.foregroundColor(.textMuted)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") { save() }
                        .foregroundColor(name.isEmpty ? .textMuted : .railBlue)
                        .disabled(name.isEmpty)
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
            photoTips: nil,
            createdMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        vm.saveLocation(loc)
        dismiss()
    }
}

struct LocationDetailSheet: View {
    let loc: SavedLocationShared
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Mini map
                    Map(coordinateRegion: .constant(MKCoordinateRegion(
                        center: CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude),
                        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05))),
                        annotationItems: [loc]) { _ in
                        MapMarker(coordinate: CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude),
                                  tint: .railBlue)
                    }
                    .frame(height: 180)
                    .cornerRadius(12)
                    .padding(.horizontal)

                    VStack(alignment: .leading, spacing: 0) {
                        if let sub = loc.subdivision, !sub.isEmpty {
                            DetailRow(label: "Subdivision",    value: sub)
                        }
                        if let freq = loc.scannerFrequency, !freq.isEmpty {
                            DetailRow(label: "Frequency",      value: "\(freq) MHz")
                        }
                        DetailRow(label: "Coordinates",
                                  value: String(format: "%.5f, %.5f", loc.latitude, loc.longitude))
                        if let notes = loc.notes, !notes.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Notes").font(.system(size: 13)).foregroundColor(.textMuted)
                                    .padding(.horizontal, 14).padding(.top, 10)
                                Text(notes).font(.system(size: 14)).foregroundColor(.textSecondary)
                                    .padding(.horizontal, 14).padding(.bottom, 12)
                            }
                        }
                    }
                    .cardStyle()
                    .padding(.horizontal)

                    Button(role: .destructive) {
                        vm.deleteLocation(id: loc.id)
                        dismiss()
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
                }
                .padding(.vertical)
            }
            .background(Color.bgPrimary)
            .navigationTitle(loc.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
