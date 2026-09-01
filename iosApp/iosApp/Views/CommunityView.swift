import SwiftUI
import PhotosUI
import Speech
import AVFoundation

// ── Holds AVAudioEngine as a reference type so it survives SwiftUI re-renders ──
final class AudioEngineHolder: ObservableObject {
    let engine = AVAudioEngine()
}

struct CommunityView: View {
    @ObservedObject var vm: RailFanViewModel
    @StateObject private var firestore = FirestoreManager.shared
    @State private var distanceFilter: Double = 0   // initialised from vm.reportRadiusMiles in onAppear
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
                                FirestoreSightingCard(sighting: sighting, vm: vm)
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
                if distanceFilter == 0 { distanceFilter = Double(vm.reportRadiusMiles) }
                let lat = vm.userLocation?.latitude ?? 0.0
                let lon = vm.userLocation?.longitude ?? 0.0
                // Always fetch at max radius; slider does client-side filtering only
                firestore.startListening(lat: lat, lon: lon, radiusMiles: 200)
            }
            .onChange(of: vm.userLocation) { newLocation in
                // Restart listener with real coordinates once GPS becomes available
                guard let loc = newLocation else { return }
                firestore.startListening(lat: loc.latitude, lon: loc.longitude, radiusMiles: 200)
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
    let vm: RailFanViewModel
    @StateObject private var firestore = FirestoreManager.shared
    @State private var showDetail = false
    @State private var showDeleteConfirm = false

    private var isOwner: Bool {
        guard let uid = firestore.currentUserId,
              let authorId = sighting.authorId else { return false }
        return uid == authorId
    }

    var body: some View {
        Button { showDetail = true } label: {
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

                    // Comment count
                    HStack(spacing: 4) {
                        Image(systemName: "bubble.left").font(.system(size: 12))
                        Text("\(sighting.commentCount)")
                            .font(.system(size: 12))
                    }
                    .foregroundColor(.textMuted)
                    .padding(.trailing, 6)

                    // Upvote
                    Button {
                        if let id = sighting.id { firestore.upvote(sightingId: id) }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "hand.thumbsup").font(.system(size: 12))
                            Text("\(sighting.upvotes)").font(.system(size: 12))
                        }
                        .foregroundColor(.railBlue)
                    }
                    .buttonStyle(.plain)

                    // Share
                    Button {
                        let text = "🚂 \(sighting.railroad) \(sighting.trainSymbol) spotted at \(sighting.location)! #RailfanCopilot"
                        let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
                        UIApplication.shared.connectedScenes
                            .compactMap { $0 as? UIWindowScene }
                            .first?.windows.first?.rootViewController?
                            .present(av, animated: true)
                    } label: {
                        Image(systemName: "square.and.arrow.up").font(.system(size: 12))
                            .foregroundColor(.railBlue)
                    }
                    .buttonStyle(.plain)

                    // Delete (own sightings only)
                    if isOwner {
                        Button { showDeleteConfirm = true } label: {
                            Image(systemName: "trash").font(.system(size: 12))
                                .foregroundColor(.red.opacity(0.7))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(14)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
        }
        .buttonStyle(.plain)
        .confirmationDialog("Delete this sighting?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                if let id = sighting.id {
                    Task { try? await firestore.deleteSighting(sightingId: id) }
                }
            }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showDetail) {
            SightingDetailSheet(sighting: sighting, vm: vm)
        }
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

// ── Sighting Detail Sheet (comments) ─────────────────────────────────────────
struct SightingDetailSheet: View {
    let sighting: FirestoreSighting
    let vm: RailFanViewModel
    @StateObject private var firestore = FirestoreManager.shared
    @Environment(\.dismiss) private var dismiss
    @State private var commentText = ""
    @State private var isSubmitting = false

    private var comments: [SightingComment] {
        guard let id = sighting.id else { return [] }
        return firestore.commentsBySighting[id] ?? []
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                VStack(spacing: 0) {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 14) {

                            // Sighting header
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(sighting.railroad)
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 8).padding(.vertical, 4)
                                        .background(Color.railBlueMid).cornerRadius(6)
                                    Text(sighting.trainSymbol)
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(.textPrimary)
                                    Spacer()
                                    Text("\(sighting.minutesAgo)m ago")
                                        .font(.system(size: 12)).foregroundColor(.textMuted)
                                }
                                HStack(spacing: 4) {
                                    Image(systemName: "mappin.circle.fill")
                                        .foregroundColor(.railBlue).font(.system(size: 13))
                                    Text(sighting.location)
                                        .font(.system(size: 14)).foregroundColor(.textSecondary)
                                }
                                if !sighting.notes.isEmpty {
                                    Text(sighting.notes)
                                        .font(.system(size: 14)).foregroundColor(.textSecondary)
                                }
                                if let url = sighting.photoUrl.flatMap(URL.init) {
                                    AsyncImage(url: url) { phase in
                                        switch phase {
                                        case .success(let img):
                                            img.resizable().scaledToFill()
                                                .frame(maxWidth: .infinity).frame(height: 180)
                                                .clipped().cornerRadius(10)
                                        default: EmptyView()
                                        }
                                    }
                                }
                                Text("By \(sighting.reporterName)")
                                    .font(.system(size: 12)).foregroundColor(.textMuted)
                            }
                            .padding(14)
                            .background(Color.bgCard)
                            .cornerRadius(14)
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))

                            // Comments section
                            Text("Comments (\(comments.count))")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.textMuted)

                            if comments.isEmpty {
                                Text("No comments yet — be the first!")
                                    .font(.system(size: 13))
                                    .foregroundColor(.textMuted)
                                    .padding(.vertical, 8)
                            } else {
                                ForEach(comments) { comment in
                                    CommentRow(comment: comment)
                                }
                            }

                            Spacer(minLength: 80)
                        }
                        .padding()
                    }

                    // Comment input bar
                    HStack(spacing: 10) {
                        TextField("Add a comment…", text: $commentText)
                            .textFieldStyle(.plain)
                            .foregroundColor(.textPrimary)
                            .padding(10)
                            .background(Color.bgCard)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))

                        Button {
                            submitComment()
                        } label: {
                            Image(systemName: isSubmitting ? "ellipsis" : "paperplane.fill")
                                .foregroundColor(.white)
                                .frame(width: 40, height: 40)
                                .background(commentText.isEmpty ? Color.railBlueDark : Color.railBlueMid)
                                .cornerRadius(10)
                        }
                        .disabled(commentText.trimmingCharacters(in: .whitespaces).isEmpty || isSubmitting)
                    }
                    .padding(12)
                    .background(Color.bgPrimary)
                }
            }
            .navigationTitle("Sighting")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }.foregroundColor(.railBlue)
                }
            }
            .onAppear {
                if let id = sighting.id { firestore.fetchComments(sightingId: id) }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func submitComment() {
        let text = commentText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty, let id = sighting.id else { return }
        isSubmitting = true
        Task {
            try? await firestore.addComment(
                sightingId: id,
                text: text,
                authorName: vm.userName
            )
            commentText = ""
            isSubmitting = false
        }
    }
}

struct CommentRow: View {
    let comment: SightingComment
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "person.circle.fill")
                .font(.system(size: 28))
                .foregroundColor(.railBlueDark)
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(comment.authorName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.textPrimary)
                    Spacer()
                    Text("\(comment.minutesAgo)m ago")
                        .font(.system(size: 11))
                        .foregroundColor(.textMuted)
                }
                Text(comment.text)
                    .font(.system(size: 13))
                    .foregroundColor(.textSecondary)
            }
        }
        .padding(12)
        .background(Color.bgCard)
        .cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
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
    @State private var selectedPhoto: PhotosPickerItem? = nil
    @State private var photoData: Data? = nil

    // Photo
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    @State private var selectedImage: UIImage? = nil
    @State private var isListening = false
    @State private var showPhotoAttach = false
    @State private var attachedPhoto: TaggedPhotoRecord? = nil

    private let railroads = ["BNSF", "UP", "CSX", "NS", "CN", "CP", "Amtrak", "Metra", "MBTA", "LIRR", "Metro-North", "SEPTA", "Caltrain", "Sound Transit", "Other"]

    // Speech recognition — audioEngine must survive re-renders so it's in a StateObject
    private let speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    @State private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    @State private var recognitionTask: SFSpeechRecognitionTask?
    @StateObject private var audioHolder = AudioEngineHolder()

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
                            HStack {
                                Text("Notes").font(.system(size: 13)).foregroundColor(.textMuted)
                                Spacer()
                                Button { toggleListening() } label: {
                                    HStack(spacing: 4) {
                                        Image(systemName: isListening ? "stop.circle.fill" : "mic.fill")
                                            .foregroundColor(isListening ? .red : .railBlue)
                                            .font(.system(size: 14))
                                        Text(isListening ? "Stop" : "Voice")
                                            .font(.system(size: 12))
                                            .foregroundColor(isListening ? .red : .railBlue)
                                    }
                                    .padding(.horizontal, 10).padding(.vertical, 5)
                                    .background((isListening ? Color.red : Color.railBlue).opacity(0.15))
                                    .cornerRadius(8)
                                }
                            }
                            TextEditor(text: $notes)
                                .foregroundColor(.textPrimary)
                                .frame(height: 100)
                                .padding(8)
                                .background(Color.bgCard)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                        }

                        // Photo attachment
                        PhotosPicker(selection: $selectedPhoto, matching: .images) {
                            HStack {
                                Image(systemName: photoData == nil ? "photo.badge.plus" : "photo.fill.on.rectangle.fill")
                                    .foregroundColor(.railBlue)
                                Text(photoData == nil ? "Attach Photo (optional)" : "Photo attached ✓")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(photoData == nil ? .textSecondary : .railBlue)
                                Spacer()
                                if photoData != nil {
                                    Button {
                                        photoData = nil
                                        selectedPhoto = nil
                                    } label: {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundColor(.textMuted)
                                    }
                                }
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

                        // Attach a previously-tagged photo (from Decoder / Photo history) instead
                        if !vm.taggedPhotos.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Attach Tagged Photo").font(.system(size: 13)).foregroundColor(.textMuted)
                                if let photo = attachedPhoto {
                                    HStack(spacing: 10) {
                                        Image(systemName: "photo.fill")
                                            .foregroundColor(.railBlue).font(.system(size: 16))
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(photo.symbol.isEmpty ? photo.railroad : photo.symbol)
                                                .font(.system(size: 13, weight: .medium)).foregroundColor(.textPrimary)
                                            Text(photo.locationName)
                                                .font(.system(size: 11)).foregroundColor(.textMuted)
                                        }
                                        Spacer()
                                        Button { attachedPhoto = nil } label: {
                                            Image(systemName: "xmark.circle.fill").foregroundColor(.textMuted)
                                        }
                                    }
                                    .padding(12).background(Color.bgCard).cornerRadius(10)
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.railBlue.opacity(0.4), lineWidth: 1))
                                } else {
                                    Button { showPhotoAttach = true } label: {
                                        Label("Choose from gallery", systemImage: "photo.badge.plus")
                                            .font(.system(size: 13))
                                            .foregroundColor(.railBlue)
                                            .padding(10).frame(maxWidth: .infinity)
                                            .background(Color.bgCard).cornerRadius(10)
                                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
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
                    Button("Cancel") { stopListening(); isPresented = false }
                        .foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showPhotoAttach) {
            PhotoPickerSheet(photos: vm.taggedPhotos, onSelect: { attachedPhoto = $0 })
        }
    }

    // ── Voice transcription ───────────────────────────────────────────────
    private func toggleListening() {
        if isListening { stopListening() } else { startListening() }
    }

    private func startListening() {
        SFSpeechRecognizer.requestAuthorization { status in
            guard status == .authorized else { return }
            Task { @MainActor in
                do {
                    try AVAudioSession.sharedInstance().setCategory(.record, mode: .measurement, options: .duckOthers)
                    try AVAudioSession.sharedInstance().setActive(true, options: .notifyOthersOnDeactivation)
                    recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
                    guard let req = recognitionRequest else { return }
                    req.shouldReportPartialResults = true
                    let node = audioHolder.engine.inputNode
                    recognitionTask = speechRecognizer?.recognitionTask(with: req) { result, error in
                        if let r = result {
                            notes = r.bestTranscription.formattedString
                            if r.isFinal { stopListening() }
                        }
                        if error != nil { stopListening() }
                    }
                    let format = node.outputFormat(forBus: 0)
                    node.installTap(onBus: 0, bufferSize: 1024, format: format) { buf, _ in
                        req.append(buf)
                    }
                    audioHolder.engine.prepare()
                    try audioHolder.engine.start()
                    isListening = true
                } catch { isListening = false }
            }
        }
    }

    private func stopListening() {
        audioHolder.engine.stop()
        audioHolder.engine.inputNode.removeTap(onBus: 0)
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        recognitionTask?.cancel()
        recognitionTask = nil
        try? AVAudioSession.sharedInstance().setActive(false)
        isListening = false
    }

    private func submit() {
        isSubmitting = true
        errorMessage = nil
        let lat = vm.userLocation?.latitude ?? 0.0
        let lon = vm.userLocation?.longitude ?? 0.0
        let name = effectiveReporterName
        // Use photoData state var (loaded via PhotosPicker); selectedImage is unused.
        let submitPhoto = photoData
        var finalNotes = notes
        if let photo = attachedPhoto {
            finalNotes += "\n[Photo: \(photo.symbol) at \(photo.locationName)]"
        }

        Task {
            do {
                try await FirestoreManager.shared.submitSighting(
                    railroad: railroad,
                    trainSymbol: trainSymbol.isEmpty ? "Unknown" : trainSymbol,
                    location: location.isEmpty ? "Unknown location" : location,
                    notes: finalNotes,
                    lat: lat,
                    lon: lon,
                    reporterName: name,
                    photoData: submitPhoto
                )
                stopListening()
                isPresented = false
            } catch {
                errorMessage = "Failed to submit: \(error.localizedDescription)"
                isSubmitting = false
            }
        }
    }
}

// ── Photo picker sheet for attaching a tagged photo ───────────────────────
struct PhotoPickerSheet: View {
    let photos: [TaggedPhotoRecord]
    let onSelect: (TaggedPhotoRecord) -> Void
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                List(photos) { photo in
                    Button {
                        onSelect(photo)
                        dismiss()
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "photo.fill").foregroundColor(.railBlue).font(.system(size: 20))
                            VStack(alignment: .leading, spacing: 3) {
                                Text(photo.symbol.isEmpty ? photo.railroad : "\(photo.railroad) · \(photo.symbol)")
                                    .font(.system(size: 14, weight: .medium)).foregroundColor(.textPrimary)
                                Text(photo.locationName)
                                    .font(.system(size: 12)).foregroundColor(.textMuted)
                                Text(photo.timestamp, style: .relative)
                                    .font(.system(size: 11)).foregroundColor(.textMuted)
                            }
                            Spacer()
                        }
                        .padding(.vertical, 2)
                    }
                    .listRowBackground(Color.bgCard)
                    .listRowSeparatorTint(Color.border)
                }
                .listStyle(.plain).scrollContentBackground(.hidden)
            }
            .navigationTitle("Choose Photo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cancel") { dismiss() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
