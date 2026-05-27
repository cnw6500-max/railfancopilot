import SwiftUI
import PhotosUI
import shared

struct SpotsView: View {
    @ObservedObject var vm: RailFanViewModel
    @StateObject private var firestore = FirestoreManager.shared
    @State private var selectedTab = 0
    @State private var showSubmitSheet = false
    @State private var selectedSpot: RailfanSpot? = nil

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Tab selector
                    HStack(spacing: 0) {
                        ForEach(["Nearby", "My Spots"], id: \.self) { tab in
                            let idx = tab == "Nearby" ? 0 : 1
                            Button {
                                selectedTab = idx
                            } label: {
                                Text(tab)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(selectedTab == idx ? .railBlue : .textMuted)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .overlay(alignment: .bottom) {
                                        if selectedTab == idx {
                                            Rectangle().fill(Color.railBlue).frame(height: 2)
                                        }
                                    }
                            }
                        }
                    }
                    .background(Color.bgCard)
                    .overlay(alignment: .bottom) {
                        Divider().background(Color.border)
                    }

                    if selectedTab == 0 {
                        CommunitySpotsList(spots: firestore.communitySpots,
                                          isLoading: firestore.isLoadingSpots,
                                          onSelect: { selectedSpot = $0 })
                    } else {
                        PrivateSpotsList(vm: vm)
                    }
                }
            }
            .navigationTitle("Railfan Spots")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showSubmitSheet = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(.railBlue)
                            .font(.system(size: 20))
                    }
                }
            }
            .sheet(isPresented: $showSubmitSheet) {
                SubmitSpotSheet(vm: vm, isPresented: $showSubmitSheet)
            }
            .sheet(item: $selectedSpot) { spot in
                SpotDetailSheet(spot: spot)
            }
        }
    }
}

// ── Community Spots List ──────────────────────────────────────────────────────
struct CommunitySpotsList: View {
    let spots: [RailfanSpot]
    let isLoading: Bool
    let onSelect: (RailfanSpot) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                if isLoading && spots.isEmpty {
                    ProgressView().padding(40)
                } else if spots.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "mappin.slash")
                            .font(.system(size: 40))
                            .foregroundColor(.railBlueDark)
                        Text("No spots nearby")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.textPrimary)
                        Text("Be the first to submit a railfan spot in your area!")
                            .font(.system(size: 13))
                            .foregroundColor(.textMuted)
                            .multilineTextAlignment(.center)
                    }
                    .padding(40)
                } else {
                    ForEach(spots) { spot in
                        CommunitySpotCard(spot: spot, onTap: { onSelect(spot) })
                            .padding(.horizontal)
                    }
                }
                Spacer(minLength: 40)
            }
            .padding(.top)
        }
    }
}

// ── Community Spot Card ───────────────────────────────────────────────────────
struct CommunitySpotCard: View {
    let spot: RailfanSpot
    let onTap: () -> Void
    @StateObject private var firestore = FirestoreManager.shared

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 10) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(spot.name)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.textPrimary)
                        if !spot.railroad.isEmpty {
                            Text(spot.railroad + (spot.subdivision.isEmpty ? "" : " · \(spot.subdivision)"))
                                .font(.system(size: 12))
                                .foregroundColor(.textMuted)
                        }
                    }
                    Spacer()
                    Text(String(format: "%.0f mi", spot.distanceMiles))
                        .font(.system(size: 12))
                        .foregroundColor(.textMuted)
                }

                if !spot.notes.isEmpty {
                    Text(spot.notes)
                        .font(.system(size: 13))
                        .foregroundColor(.textSecondary)
                        .lineLimit(2)
                }

                // Amenity chips
                let amenities = amenityList(spot)
                if !amenities.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(amenities, id: \.self) { chip in
                                Text(chip)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.railBlue)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.railBlueDark)
                                    .cornerRadius(6)
                            }
                        }
                    }
                }

                // Footer
                HStack {
                    if !spot.trainFrequency.isEmpty && spot.trainFrequency != "UNKNOWN" {
                        Label(freqLabel(spot.trainFrequency), systemImage: "waveform")
                            .font(.system(size: 11))
                            .foregroundColor(.textMuted)
                    }
                    Spacer()
                    Button {
                        if let id = spot.id { firestore.upvoteSpot(spotId: id) }
                    } label: {
                        Label("\(spot.upvotes)", systemImage: "hand.thumbsup")
                            .font(.system(size: 12))
                            .foregroundColor(.railBlue)
                    }
                }
            }
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(14)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
        }
        .buttonStyle(.plain)
    }

    private func amenityList(_ s: RailfanSpot) -> [String] {
        var list: [String] = []
        if s.hasParking   { list.append("🅿 Parking") }
        if s.hasRestrooms { list.append("🚻 Restrooms") }
        if s.hasFood      { list.append("🍔 Food") }
        if s.hasShade     { list.append("🌳 Shade") }
        return list
    }

    private func freqLabel(_ freq: String) -> String {
        switch freq {
        case "LIGHT":    return "Light traffic"
        case "MODERATE": return "Moderate traffic"
        case "HEAVY":    return "Heavy traffic"
        default:         return freq
        }
    }
}

// ── Private Spots List (saved locations) ──────────────────────────────────────
struct PrivateSpotsList: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var locationToDelete: SavedLocationShared? = nil

    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                if vm.savedLocations.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "bookmark.slash")
                            .font(.system(size: 40))
                            .foregroundColor(.railBlueDark)
                        Text("No saved spots")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.textPrimary)
                        Text("Save locations from the Map screen to see them here.")
                            .font(.system(size: 13))
                            .foregroundColor(.textMuted)
                            .multilineTextAlignment(.center)
                    }
                    .padding(40)
                } else {
                    ForEach(vm.savedLocations, id: \.id) { loc in
                        SavedSpotCard(location: loc, onDelete: { locationToDelete = loc })
                            .padding(.horizontal)
                    }
                }
                Spacer(minLength: 40)
            }
            .padding(.top)
        }
        .confirmationDialog(
            "Delete \"\(locationToDelete?.name ?? "")\"?",
            isPresented: Binding(get: { locationToDelete != nil }, set: { if !$0 { locationToDelete = nil } }),
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

struct SavedSpotCard: View {
    let location: SavedLocationShared
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "mappin.circle.fill")
                .font(.system(size: 22))
                .foregroundColor(.railBlue)

            VStack(alignment: .leading, spacing: 2) {
                Text(location.name)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.textPrimary)
                if let notes = location.notes, !notes.isEmpty {
                    Text(notes)
                        .font(.system(size: 13))
                        .foregroundColor(.textMuted)
                        .lineLimit(1)
                }
            }

            Spacer()

            Button(action: onDelete) {
                Image(systemName: "trash")
                    .foregroundColor(.textMuted)
                    .font(.system(size: 16))
            }
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
    }
}

// ── Spot Detail Sheet ─────────────────────────────────────────────────────────
struct SpotDetailSheet: View {
    let spot: RailfanSpot
    @StateObject private var firestore = FirestoreManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        // Header
                        VStack(alignment: .leading, spacing: 4) {
                            Text(spot.name)
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.textPrimary)
                            if !spot.railroad.isEmpty {
                                Text(spot.railroad + (spot.subdivision.isEmpty ? "" : " · \(spot.subdivision)"))
                                    .font(.system(size: 14))
                                    .foregroundColor(.textMuted)
                            }
                        }

                        Divider().background(Color.border)

                        SpotDetailRow(icon: "note.text", label: "Notes", value: spot.notes)
                        SpotDetailRow(icon: "exclamationmark.triangle", label: "Safety", value: spot.safetyNotes)
                        SpotDetailRow(icon: "p.square", label: "Parking", value: spot.parkingNotes)
                        SpotDetailRow(icon: "antenna.radiowaves.left.and.right", label: "Scanner", value: spot.scannerFrequency)
                        SpotDetailRow(icon: "leaf", label: "Seasonal", value: spot.seasonalNotes)

                        // Amenities
                        HStack(spacing: 10) {
                            AmenityBadge(icon: "p.square.fill", label: "Parking", active: spot.hasParking)
                            AmenityBadge(icon: "toilet", label: "Restrooms", active: spot.hasRestrooms)
                            AmenityBadge(icon: "fork.knife", label: "Food", active: spot.hasFood)
                            AmenityBadge(icon: "tree", label: "Shade", active: spot.hasShade)
                        }

                        // Upvote
                        Button {
                            if let id = spot.id { firestore.upvoteSpot(spotId: id) }
                        } label: {
                            Label("\(spot.upvotes) upvotes", systemImage: "hand.thumbsup.fill")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(Color.railBlueMid)
                                .cornerRadius(12)
                        }

                        Spacer(minLength: 40)
                    }
                    .padding()
                }
            }
            .navigationTitle("Spot Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Close") { dismiss() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

private struct SpotDetailRow: View {
    let icon: String; let label: String; let value: String
    var body: some View {
        if !value.isEmpty {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: icon).foregroundColor(.railBlue).frame(width: 20)
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(.system(size: 11)).foregroundColor(.textMuted)
                    Text(value).font(.system(size: 14)).foregroundColor(.textPrimary)
                }
            }
        }
    }
}

private struct AmenityBadge: View {
    let icon: String; let label: String; let active: Bool
    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(active ? .railBlue : .textMuted)
            Text(label)
                .font(.system(size: 10))
                .foregroundColor(active ? .textSecondary : .textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(active ? Color.railBlueDark : Color.bgCard)
        .cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
    }
}

// ── Submit Spot Sheet ─────────────────────────────────────────────────────────
struct SubmitSpotSheet: View {
    @ObservedObject var vm: RailFanViewModel
    @Binding var isPresented: Bool
    @StateObject private var firestore = FirestoreManager.shared

    @State private var name = ""
    @State private var railroad = ""
    @State private var subdivision = ""
    @State private var notes = ""
    @State private var safetyNotes = ""
    @State private var parkingNotes = ""
    @State private var scannerFreq = ""
    @State private var seasonalNotes = ""
    @State private var trainFreq = "UNKNOWN"
    @State private var hasParking = false
    @State private var hasRestrooms = false
    @State private var hasFood = false
    @State private var hasShade = false
    @State private var isPublic = true
    @State private var selectedPhoto: PhotosPickerItem? = nil
    @State private var photoData: Data? = nil

    private let freqOptions = ["UNKNOWN", "LIGHT", "MODERATE", "HEAVY"]
    private var isValid: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {

                        // GPS readout
                        if let loc = vm.userLocation {
                            HStack(spacing: 6) {
                                Image(systemName: "location.fill").foregroundColor(.railBlue).font(.system(size: 13))
                                Text(String(format: "%.5f, %.5f", loc.latitude, loc.longitude))
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(.textSecondary)
                            }
                            .padding(10)
                            .background(Color.bgCard)
                            .cornerRadius(10)
                        }

                        SpotTextField(label: "Spot Name *", placeholder: "e.g. Galesburg Tower", text: $name)
                        SpotTextField(label: "Railroad", placeholder: "e.g. BNSF", text: $railroad)
                        SpotTextField(label: "Subdivision", placeholder: "e.g. Chillicothe Sub", text: $subdivision)
                        SpotTextField(label: "Notes", placeholder: "What makes this spot great?", text: $notes, multiline: true)
                        SpotTextField(label: "Safety Notes", placeholder: "Trespassing concerns, sight lines…", text: $safetyNotes, multiline: true)
                        SpotTextField(label: "Parking Notes", placeholder: "Where to park…", text: $parkingNotes)
                        SpotTextField(label: "Scanner Frequency", placeholder: "e.g. 161.550 MHz", text: $scannerFreq)
                        SpotTextField(label: "Seasonal Notes", placeholder: "Best season, foliage…", text: $seasonalNotes)

                        // Train frequency
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Train Frequency").font(.system(size: 13)).foregroundColor(.textMuted)
                            Picker("", selection: $trainFreq) {
                                ForEach(freqOptions, id: \.self) { Text($0).tag($0) }
                            }
                            .pickerStyle(.segmented)
                        }

                        // Amenities
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Amenities").font(.system(size: 13)).foregroundColor(.textMuted)
                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                                AmenityToggle(label: "Parking",   icon: "p.square.fill",  on: $hasParking)
                                AmenityToggle(label: "Restrooms", icon: "toilet",          on: $hasRestrooms)
                                AmenityToggle(label: "Food",      icon: "fork.knife",      on: $hasFood)
                                AmenityToggle(label: "Shade",     icon: "tree",            on: $hasShade)
                            }
                        }

                        // Public toggle
                        Toggle(isOn: $isPublic) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Share with community")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.textPrimary)
                                Text("Visible to all app users")
                                    .font(.system(size: 12))
                                    .foregroundColor(.textMuted)
                            }
                        }
                        .tint(.railBlue)
                        .padding(14)
                        .background(Color.bgCard)
                        .cornerRadius(12)

                        // Photo picker
                        PhotosPicker(selection: $selectedPhoto, matching: .images) {
                            HStack {
                                Image(systemName: photoData == nil ? "photo.badge.plus" : "photo.fill")
                                    .foregroundColor(.railBlue)
                                Text(photoData == nil ? "Add Photo" : "Photo selected")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(photoData == nil ? .textSecondary : .railBlue)
                                Spacer()
                            }
                            .padding(14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.border, lineWidth: 0.5))
                        }
                        .onChange(of: selectedPhoto) { item in
                            Task {
                                photoData = try? await item?.loadTransferable(type: Data.self)
                            }
                        }

                        if let err = firestore.spotSubmitError {
                            Text(err).font(.system(size: 13)).foregroundColor(.orange)
                        }

                        // Submit
                        Button {
                            submit()
                        } label: {
                            Group {
                                if firestore.isSubmittingSpot {
                                    ProgressView().tint(.white)
                                } else {
                                    Text("Submit Spot")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(.white)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(isValid ? Color.railBlueMid : Color.railBlueDark)
                            .cornerRadius(12)
                        }
                        .disabled(!isValid || firestore.isSubmittingSpot)

                        Spacer(minLength: 40)
                    }
                    .padding()
                }
            }
            .navigationTitle("Submit Spot")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { isPresented = false }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func submit() {
        let lat = vm.userLocation?.latitude ?? 0.0
        let lon = vm.userLocation?.longitude ?? 0.0
        let spot = RailfanSpot(
            name: name, latitude: lat, longitude: lon,
            submittedBy: "iOS User",
            railroad: railroad, subdivision: subdivision,
            notes: notes, safetyNotes: safetyNotes, parkingNotes: parkingNotes,
            scannerFrequency: scannerFreq, seasonalNotes: seasonalNotes,
            trainFrequency: trainFreq,
            isPublicProperty: isPublic,
            hasParking: hasParking, hasRestrooms: hasRestrooms,
            hasFood: hasFood, hasShade: hasShade,
            upvotes: 0, photoUrls: [],
            timestampMs: Date().timeIntervalSince1970 * 1000
        )
        Task {
            await firestore.submitSpot(spot, photoData: photoData)
            if firestore.spotSubmitError == nil { isPresented = false }
        }
    }
}

private struct SpotTextField: View {
    let label: String; let placeholder: String
    @Binding var text: String
    var multiline = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.system(size: 13)).foregroundColor(.textMuted)
            if multiline {
                TextEditor(text: $text)
                    .foregroundColor(.textPrimary)
                    .frame(height: 80)
                    .padding(8)
                    .background(Color.bgCard)
                    .cornerRadius(10)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
            } else {
                TextField(placeholder, text: $text)
                    .textFieldStyle(.plain)
                    .foregroundColor(.textPrimary)
                    .padding(12)
                    .background(Color.bgCard)
                    .cornerRadius(10)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
            }
        }
    }
}

private struct AmenityToggle: View {
    let label: String; let icon: String
    @Binding var on: Bool

    var body: some View {
        Button { on.toggle() } label: {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 14))
                    .foregroundColor(on ? .railBlue : .textMuted)
                Text(label)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(on ? .textPrimary : .textMuted)
                Spacer()
                if on { Image(systemName: "checkmark").font(.system(size: 11)).foregroundColor(.railBlue) }
            }
            .padding(12)
            .background(on ? Color.railBlueDark : Color.bgCard)
            .cornerRadius(10)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? Color.railBlue.opacity(0.4) : Color.border, lineWidth: 0.5))
        }
        .buttonStyle(.plain)
    }
}
