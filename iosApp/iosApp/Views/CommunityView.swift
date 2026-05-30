import SwiftUI
import PhotosUI

struct CommunityView: View {
    @ObservedObject var vm: RailFanViewModel
    @StateObject private var firestore = FirestoreManager.shared
    @State private var distanceFilter: Double = 100
    @State private var showAddSighting = false

    var filteredSightings: [FirestoreSighting] {
        // When location is unknown the distances are calculated from (0,0) and are meaningless;
        // show all sightings so the list isn't empty while waiting for a GPS fix.
        guard vm.userLocation != nil else { return firestore.sightings }
        return firestore.sightings.filter { $0.distanceMiles <= distanceFilter }
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

                        // Count
                        HStack {
                            Text("\(filteredSightings.count) sightings within \(Int(distanceFilter)) miles")
                                .font(.system(size: 13))
                                .foregroundColor(.textMuted)
                            Spacer()
                            if firestore.isLoading {
                                ProgressView().scaleEffect(0.7)
                            }
                        }
                        .padding(.horizontal)

                        // Error
                        if let err = firestore.errorMessage {
                            Text(err)
                                .font(.system(size: 13))
                                .foregroundColor(.orange)
                                .padding(.horizontal)
                        }

                        // Sightings
                        if filteredSightings.isEmpty && !firestore.isLoading {
                            VStack(spacing: 12) {
                                Image(systemName: "binoculars")
                                    .font(.system(size: 40))
                                    .foregroundColor(.railBlueDark)
                                Text("No sightings nearby")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(.textPrimary)
                                Text("Be the first to report a sighting!")
                                    .font(.system(size: 13))
                                    .foregroundColor(.textMuted)
                                    .multilineTextAlignment(.center)
                            }
                            .padding(40)
                        } else {
                            ForEach(filteredSightings) { sighting in
                                FirestoreSightingCard(sighting: sighting)
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
            .onAppear {
                let lat = vm.userLocation?.latitude ?? 0.0
                let lon = vm.userLocation?.longitude ?? 0.0
                firestore.startListening(lat: lat, lon: lon, radiusMiles: distanceFilter)
            }
            .onDisappear {
                firestore.stopListening()
            }
            .onChange(of: vm.userLocation) { newLocation in
                // Restart listener with real coordinates once GPS becomes available
                guard let loc = newLocation else { return }
                firestore.startListening(lat: loc.latitude, lon: loc.longitude, radiusMiles: distanceFilter)
            }
            .sheet(isPresented: $showAddSighting) {
                AddSightingView(vm: vm, isPresented: $showAddSighting)
            }
        }
    }
}

// ── Firestore Sighting Card ───────────────────────────────────────────────────
struct FirestoreSightingCard: View {
    let sighting: FirestoreSighting
    @StateObject private var firestore = FirestoreManager.shared

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

            if !sighting.notes.isEmpty {
                Text(sighting.notes)
                    .font(.system(size: 13))
                    .foregroundColor(.textSecondary)
                    .lineLimit(2)
            }

            if let photoUrl = sighting.photoUrl, let url = URL(string: photoUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                            .frame(maxWidth: .infinity).frame(height: 160)
                            .clipped().cornerRadius(8)
                    case .failure:
                        EmptyView()
                    default:
                        Color.bgCard.frame(height: 160).cornerRadius(8)
                            .overlay(ProgressView())
                    }
                }
            }

            HStack {
                Image(systemName: "person.circle")
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
                Text(sighting.reporterName)
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
                Spacer()

                // Upvote button
                Button {
                    if let id = sighting.id {
                        firestore.upvote(sightingId: id)
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "hand.thumbsup")
                            .font(.system(size: 12))
                        Text("\(sighting.upvotes)")
                            .font(.system(size: 12))
                    }
                    .foregroundColor(.railBlue)
                }

                // Share button
                Button {
                    let text = "🚂 \(sighting.railroad) \(sighting.trainSymbol) spotted at \(sighting.location)! #RailfanCopilot"
                    let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
                    UIApplication.shared.connectedScenes
                        .compactMap { $0 as? UIWindowScene }
                        .first?.windows.first?.rootViewController?
                        .present(av, animated: true)
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
    @ObservedObject var vm: RailFanViewModel
    @Binding var isPresented: Bool
    @State private var railroad = ""
    @State private var trainSymbol = ""
    @State private var location = ""
    @State private var notes = ""
    @State private var reporterName = ""
    private var effectiveReporterName: String { reporterName.isEmpty ? vm.userName : reporterName }
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil

    // Photo
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    @State private var selectedImage: UIImage? = nil

    private let railroads = ["BNSF", "UP", "CSX", "NS", "CN", "CP", "Amtrak", "Metra", "MBTA", "LIRR", "Metro-North", "SEPTA", "Caltrain", "Sound Transit", "Other"]

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 16) {

                        // Reporter name
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Your Name / Handle").font(.system(size: 13)).foregroundColor(.textMuted)
                            TextField("e.g. TrainFan_IL", text: $reporterName)
                                .textFieldStyle(.plain)
                                .foregroundColor(.textPrimary)
                                .padding(12)
                                .background(Color.bgCard)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                        }

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

                        // Photo picker
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Photo (optional)").font(.system(size: 13)).foregroundColor(.textMuted)
                            if let image = selectedImage {
                                ZStack(alignment: .topTrailing) {
                                    Image(uiImage: image)
                                        .resizable()
                                        .scaledToFill()
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 160)
                                        .clipped()
                                        .cornerRadius(10)
                                    Button {
                                        selectedImage = nil
                                        selectedPhotoItem = nil
                                    } label: {
                                        Image(systemName: "xmark.circle.fill")
                                            .font(.system(size: 22))
                                            .foregroundColor(.white)
                                            .shadow(radius: 2)
                                            .padding(6)
                                    }
                                }
                            } else {
                                PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                                    HStack {
                                        Image(systemName: "photo.badge.plus")
                                        Text("Add Photo")
                                    }
                                    .font(.system(size: 14))
                                    .foregroundColor(.railBlue)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.bgCard)
                                    .cornerRadius(10)
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                                }
                                .onChange(of: selectedPhotoItem) { item in
                                    Task {
                                        if let data = try? await item?.loadTransferable(type: Data.self),
                                           let uiImage = UIImage(data: data) {
                                            selectedImage = uiImage
                                        }
                                    }
                                }
                            }
                        }

                        if let err = errorMessage {
                            Text(err).font(.system(size: 13)).foregroundColor(.orange)
                        }

                        // Submit
                        Button {
                            submit()
                        } label: {
                            Group {
                                if isSubmitting {
                                    ProgressView().tint(.white)
                                } else {
                                    Text("Submit Sighting")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(.white)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(railroad.isEmpty ? Color.railBlueDark : Color.railBlueMid)
                            .cornerRadius(12)
                        }
                        .disabled(railroad.isEmpty || isSubmitting)

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

    private func submit() {
        isSubmitting = true
        errorMessage = nil
        let lat = vm.userLocation?.latitude ?? 0.0
        let lon = vm.userLocation?.longitude ?? 0.0
        let name = effectiveReporterName
        let photoData = selectedImage.flatMap {
            $0.jpegData(compressionQuality: 0.8)
        }

        Task {
            do {
                try await FirestoreManager.shared.submitSighting(
                    railroad: railroad,
                    trainSymbol: trainSymbol.isEmpty ? "Unknown" : trainSymbol,
                    location: location.isEmpty ? "Unknown location" : location,
                    notes: notes,
                    lat: lat,
                    lon: lon,
                    reporterName: name,
                    photoData: photoData
                )
                isPresented = false
            } catch {
                errorMessage = "Failed to submit: \(error.localizedDescription)"
                isSubmitting = false
            }
        }
    }
}
