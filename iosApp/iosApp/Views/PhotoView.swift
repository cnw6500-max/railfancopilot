import SwiftUI
import PhotosUI

struct PhotoView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var selectedPhoto: PhotosPickerItem? = nil
    @State private var selectedImage: UIImage? = nil
    @State private var showCamera = false
    @State private var showHistory = false
    @State private var showEnhancer = false

    // Auto-tag fields (pre-filled from nearest train + GPS)
    @State private var tagSymbol = ""
    @State private var tagRailroad = ""
    @State private var tagLocation = ""
    @State private var tagNotes = ""
    @State private var showSaveTagDialog = false

    var body: some View {
        NavigationView {
            ZStack(alignment: .top) {
                Color.bgPrimary.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {

                        // ── Image preview ─────────────────────────────────────
                        ZStack {
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.bgCard)
                                .overlay(RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.border, lineWidth: 0.5))
                                .frame(height: 220)
                            if let img = selectedImage {
                                Image(uiImage: img)
                                    .resizable().scaledToFill()
                                    .frame(height: 220)
                                    .clipShape(RoundedRectangle(cornerRadius: 16))
                            } else {
                                VStack(spacing: 10) {
                                    Image(systemName: "camera.aperture")
                                        .font(.system(size: 48)).foregroundColor(.railBlueDark)
                                    Text("Take or select a photo")
                                        .font(.system(size: 14)).foregroundColor(.textMuted)
                                }
                            }
                        }
                        .padding(.horizontal)

                        // ── Camera / Gallery buttons ──────────────────────────
                        HStack(spacing: 12) {
                            Button { showCamera = true } label: {
                                Label("Camera", systemImage: "camera.fill")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color.railBlueMid).cornerRadius(10)
                            }
                            PhotosPicker(selection: $selectedPhoto, matching: .images, photoLibrary: .shared()) {
                                Label("Gallery", systemImage: "photo.on.rectangle")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.textPrimary)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color.bgCard).cornerRadius(10)
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                            }
                        }
                        .padding(.horizontal)

                        // ── Photo Enhancer button ─────────────────────────────
                        Button { showEnhancer = true } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "wand.and.stars")
                                    .font(.system(size: 16))
                                Text("Photo Enhancer")
                                    .font(.system(size: 15, weight: .semibold))
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12)).foregroundColor(.textMuted)
                            }
                            .foregroundColor(.textPrimary)
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.railBlue.opacity(0.4), lineWidth: 1))
                        }
                        .padding(.horizontal)

                        // ── Identify button ───────────────────────────────────
                        if selectedImage != nil {
                            Button { identify() } label: {
                                HStack {
                                    if vm.isIdentifying {
                                        ProgressView().tint(.white).scaleEffect(0.8)
                                    } else {
                                        Image(systemName: "sparkles")
                                    }
                                    Text(vm.isIdentifying ? "Identifying…" : "Identify Locomotive")
                                        .font(.system(size: 15, weight: .semibold))
                                }
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 14)
                                .background(Color.railBlueMid).cornerRadius(12)
                            }
                            .disabled(vm.isIdentifying)
                            .padding(.horizontal)
                        }

                        // ── Error ─────────────────────────────────────────────
                        if let err = vm.locoIdError {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill").foregroundColor(.orange)
                                Text(err).font(.system(size: 13)).foregroundColor(.textSecondary)
                            }
                            .padding(12).cardStyle().padding(.horizontal)
                        }

                        // ── AI Result + auto-tag ──────────────────────────────
                        if let result = vm.locoIdResult {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Image(systemName: "sparkles").foregroundColor(.railBlue)
                                    Text("AI Identification")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.textPrimary)
                                }
                                Text(result)
                                    .font(.system(size: 14)).foregroundColor(.textSecondary).lineSpacing(5)
                            }
                            .padding(16).cardStyle().padding(.horizontal)

                            // Auto-tag card
                            AutoTagCard(
                                symbol: $tagSymbol,
                                railroad: $tagRailroad,
                                location: $tagLocation,
                                notes: $tagNotes
                            ) {
                                showSaveTagDialog = true
                            }
                            .padding(.horizontal)
                        }

                        // ── Empty hint ────────────────────────────────────────
                        if selectedImage == nil {
                            VStack(spacing: 8) {
                                Image(systemName: "train.side.front.car")
                                    .font(.system(size: 40)).foregroundColor(.railBlueDark)
                                Text("AI Locomotive Identifier")
                                    .font(.system(size: 16, weight: .medium)).foregroundColor(.textPrimary)
                                Text("Photograph a locomotive and the AI will identify the model, railroad, and notable features.")
                                    .font(.system(size: 13)).foregroundColor(.textMuted)
                                    .multilineTextAlignment(.center)
                            }
                            .padding(24)
                        }

                        // ── Sun Angle Predictor ───────────────────────────────
                        SunAngleCard(
                            elevation: vm.sunInfo.map { String(format: "%.1f°", $0.elevationDegrees) },
                            azimuth: vm.sunInfo.map { String(format: "%.1f°", $0.azimuthDegrees) },
                            goldenStart: vm.sunInfo?.goldenHourStart,
                            goldenEnd: vm.sunInfo?.goldenHourEnd,
                            isGoldenHour: vm.sunInfo?.isGoldenHour ?? false
                        )
                        .padding(.horizontal)

                        // ── Achievements card ─────────────────────────────────
                        if !vm.achievements.isEmpty {
                            AchievementsProgressCard(achievements: vm.achievements)
                                .padding(.horizontal)
                        }

                        // ── Loco ID History ───────────────────────────────────
                        if !vm.locoIDHistory.isEmpty {
                            LocoHistoryCard(history: vm.locoIDHistory)
                                .padding(.horizontal)
                        }

                        Spacer(minLength: 40)
                    }
                    .padding(.top)
                }

                // ── Achievement unlock banner ─────────────────────────────────
                if let achievement = vm.newAchievement {
                    VStack {
                        AchievementBanner(achievement: achievement) {
                            vm.consumeNewAchievement()
                        }
                        Spacer()
                    }
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .animation(.spring(), value: vm.newAchievement?.id)
                }
            }
            .navigationTitle("Photo ID")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if !vm.locoIDHistory.isEmpty {
                        Button { showHistory = true } label: {
                            Image(systemName: "clock.arrow.circlepath")
                                .foregroundColor(.railBlue)
                        }
                    }
                }
            }
        }
        .onChange(of: selectedPhoto) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    selectedImage = img
                    vm.locoIdResult = nil
                    vm.locoIdError = nil
                    prefillTagFields()
                }
            }
        }
        .sheet(isPresented: $showCamera) {
            CameraSheet { img in
                selectedImage = img
                vm.locoIdResult = nil
                vm.locoIdError = nil
                showCamera = false
                prefillTagFields()
            }
        }
        .sheet(isPresented: $showHistory) {
            LocoHistorySheet(history: vm.locoIDHistory)
        }
        .sheet(isPresented: $showEnhancer) {
            PhotoEnhancerSheet()
        }
        .alert("Save Tagged Photo", isPresented: $showSaveTagDialog) {
            TextField("Symbol", text: $tagSymbol)
            TextField("Railroad", text: $tagRailroad)
            Button("Save") { saveTaggedPhoto() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Save this identification to your gallery?")
        }
        .onChange(of: vm.locoIdResult) { result in
            if result != nil { prefillTagFieldsFromResult(result!) }
        }
    }

    private func identify() {
        guard let img = selectedImage, let jpeg = img.jpegData(compressionQuality: 0.7) else { return }
        vm.identifyLoco(jpegData: jpeg)
    }

    private func prefillTagFields() {
        tagLocation = vm.locationName
        if let train = vm.nearestTrain {
            tagSymbol   = train.symbol
            tagRailroad = train.railroad.displayName
        }
    }

    private func prefillTagFieldsFromResult(_ result: String) {
        let railroads = ["BNSF","Union Pacific","CSX","Norfolk Southern","CN","CPKC","Amtrak","Metra","MBTA","SEPTA"]
        for rr in railroads {
            if result.localizedCaseInsensitiveContains(rr) && tagRailroad.isEmpty {
                tagRailroad = rr; break
            }
        }
        let thumb: Data? = selectedImage.flatMap { img -> Data? in
            let size: CGFloat = 120
            let scale = size / min(img.size.width, img.size.height)
            let drawSize = CGSize(width: img.size.width * scale, height: img.size.height * scale)
            let offset = CGPoint(x: (size - drawSize.width) / 2, y: (size - drawSize.height) / 2)
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
            return renderer.image { _ in
                img.draw(in: CGRect(origin: offset, size: drawSize))
            }.jpegData(compressionQuality: 0.5)
        }
        vm.saveLocoID(result: result, locationName: tagLocation, railroad: tagRailroad, thumbnailData: thumb)
    }

    private func saveTaggedPhoto() {
        let lat = vm.userLocation?.latitude  ?? 0
        let lon = vm.userLocation?.longitude ?? 0
        vm.saveTaggedPhoto(
            symbol: tagSymbol.isEmpty ? "Unknown" : tagSymbol,
            railroad: tagRailroad.isEmpty ? "Unknown" : tagRailroad,
            location: tagLocation.isEmpty ? vm.locationName : tagLocation,
            lat: lat,
            lon: lon,
            notes: tagNotes
        )
    }
}

// ── Auto-tag card ─────────────────────────────────────────────────────────────
struct AutoTagCard: View {
    @Binding var symbol: String
    @Binding var railroad: String
    @Binding var location: String
    @Binding var notes: String
    let onSave: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Image(systemName: "tag.fill").foregroundColor(.railBlue)
                Text("Auto-Tag Photo")
                    .font(.system(size: 15, weight: .semibold)).foregroundColor(.textPrimary)
            }

            VStack(spacing: 10) {
                TagField(label: "Train Symbol", placeholder: "e.g. QCHILA-01", text: $symbol)
                TagField(label: "Railroad",     placeholder: "e.g. BNSF",       text: $railroad)
                TagField(label: "Location",     placeholder: "Auto-filled",      text: $location)
                TagField(label: "Notes",        placeholder: "Optional notes…",  text: $notes)
            }

            Button(action: onSave) {
                Label("Save to Gallery", systemImage: "photo.badge.plus")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Color.railBlueMid)
                    .cornerRadius(8)
            }
        }
        .padding(16).cardStyle()
    }
}

struct TagField: View {
    let label: String; let placeholder: String
    @Binding var text: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: 11)).foregroundColor(.textMuted)
            TextField(placeholder, text: $text)
                .font(.system(size: 13)).foregroundColor(.textPrimary)
                .padding(8).background(Color.bgInput).cornerRadius(8)
        }
    }
}

// ── Achievements progress card ────────────────────────────────────────────────
struct AchievementsProgressCard: View {
    let achievements: [AchievementRecord]
    var earned: Int { achievements.filter { $0.earned }.count }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "trophy.fill").foregroundColor(.yellow)
                Text("Achievements")
                    .font(.system(size: 15, weight: .semibold)).foregroundColor(.textPrimary)
                Spacer()
                Text("\(earned)/\(achievements.count)")
                    .font(.system(size: 13)).foregroundColor(.textMuted)
            }
            ProgressView(value: Double(earned), total: Double(achievements.count))
                .tint(.railBlue)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(achievements) { a in
                        Text(a.emoji)
                            .font(.system(size: 24))
                            .opacity(a.earned ? 1.0 : 0.25)
                            .overlay(
                                a.earned ? nil : Image(systemName: "lock.fill")
                                    .font(.system(size: 10))
                                    .foregroundColor(.textMuted)
                                    .offset(x: 8, y: 8),
                                alignment: .bottomTrailing
                            )
                    }
                }
            }
        }
        .padding(16).background(Color.bgCard).cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
    }
}

// ── Loco ID history (compact card) ───────────────────────────────────────────
struct LocoHistoryCard: View {
    let history: [LocoIDRecord]
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "clock.arrow.circlepath").foregroundColor(.railBlue)
                Text("Recent Identifications")
                    .font(.system(size: 15, weight: .semibold)).foregroundColor(.textPrimary)
            }
            ForEach(history.prefix(3)) { record in
                HStack(alignment: .top, spacing: 10) {
                    if let b64 = record.thumbnailJpegBase64,
                       let data = Data(base64Encoded: b64),
                       let img = UIImage(data: data) {
                        Image(uiImage: img)
                            .resizable().scaledToFill()
                            .frame(width: 44, height: 44)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                    } else {
                        Image(systemName: "train.side.front.car")
                            .foregroundColor(.railBlueDark).font(.system(size: 13))
                            .frame(width: 44, height: 44)
                            .background(Color.bgInput).cornerRadius(6)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(record.railroadGuess.isEmpty ? "Unknown Railroad" : record.railroadGuess)
                            .font(.system(size: 13, weight: .medium)).foregroundColor(.textPrimary)
                        Text(record.resultText.prefix(60) + (record.resultText.count > 60 ? "…" : ""))
                            .font(.system(size: 11)).foregroundColor(.textMuted).lineLimit(2)
                    }
                    Spacer()
                    Text(record.timestamp, style: .relative)
                        .font(.system(size: 10)).foregroundColor(.textMuted)
                }
            }
        }
        .padding(16).background(Color.bgCard).cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
    }
}

// ── Loco history full sheet ───────────────────────────────────────────────────
struct LocoHistorySheet: View {
    let history: [LocoIDRecord]
    @Environment(\.dismiss) var dismiss
    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                List(history) { record in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(record.railroadGuess.isEmpty ? "Unknown" : record.railroadGuess)
                                .font(.system(size: 14, weight: .semibold)).foregroundColor(.textPrimary)
                            Spacer()
                            Text(record.timestamp, style: .relative)
                                .font(.system(size: 11)).foregroundColor(.textMuted)
                        }
                        if !record.locationName.isEmpty {
                            Label(record.locationName, systemImage: "mappin.circle")
                                .font(.system(size: 11)).foregroundColor(.textMuted)
                        }
                        Text(record.resultText.prefix(120))
                            .font(.system(size: 12)).foregroundColor(.textSecondary).lineLimit(3)
                    }
                    .padding(.vertical, 4)
                    .listRowBackground(Color.bgCard)
                    .listRowSeparatorTint(Color.border)
                }
                .listStyle(.plain).scrollContentBackground(.hidden)
            }
            .navigationTitle("ID History")
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
}

// ── Achievement unlock banner ─────────────────────────────────────────────────
struct AchievementBanner: View {
    let achievement: AchievementRecord
    let onDismiss: () -> Void
    var body: some View {
        HStack(spacing: 14) {
            Text(achievement.emoji).font(.system(size: 28))
            VStack(alignment: .leading, spacing: 2) {
                Text("Achievement unlocked!")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color(red: 0.3, green: 0.9, blue: 0.4))
                Text(achievement.title)
                    .font(.system(size: 15, weight: .bold)).foregroundColor(.textPrimary)
                Text(achievement.description)
                    .font(.system(size: 12)).foregroundColor(.textSecondary)
            }
            Spacer()
        }
        .padding(14)
        .background(Color(red: 0.07, green: 0.13, blue: 0.07))
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12)
            .stroke(Color(red: 0.3, green: 0.9, blue: 0.4).opacity(0.5), lineWidth: 1))
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .onTapGesture { onDismiss() }
        .onAppear {
            Task {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                onDismiss()
            }
        }
    }
}

// ── Sun Angle Card ────────────────────────────────────────────────────────────
struct SunAngleCard: View {
    let elevation: String?; let azimuth: String?
    let goldenStart: String?; let goldenEnd: String?
    let isGoldenHour: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "sun.max.fill").foregroundColor(.yellow)
                Text("Sun Angle Predictor")
                    .font(.system(size: 15, weight: .semibold)).foregroundColor(.textPrimary)
            }
            if let elev = elevation, let az = azimuth, let gs = goldenStart {
                HStack(spacing: 0) {
                    SunStatCell(value: elev, label: "Elevation")
                    Divider().background(Color.border).frame(height: 36)
                    SunStatCell(value: az, label: "Azimuth")
                    Divider().background(Color.border).frame(height: 36)
                    SunStatCell(value: gs, label: "Golden Start")
                }
                if isGoldenHour {
                    HStack(spacing: 6) {
                        Image(systemName: "sparkles").foregroundColor(.yellow)
                        Text("Golden hour — perfect lighting!")
                            .font(.system(size: 12)).foregroundColor(.yellow)
                    }
                } else if let ge = goldenEnd {
                    Text("Golden hour: \(gs) – \(ge)")
                        .font(.system(size: 12)).foregroundColor(.textMuted)
                }
            } else {
                Text("Enable location to see sun angle data")
                    .font(.system(size: 13)).foregroundColor(.textMuted)
            }
        }
        .padding(16).background(Color.bgCard).cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
    }
}

struct SunStatCell: View {
    let value: String; let label: String
    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 14, weight: .semibold)).foregroundColor(.railBlue)
            Text(label).font(.system(size: 11)).foregroundColor(.textMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

// ── Camera sheet ──────────────────────────────────────────────────────────────
struct CameraSheet: UIViewControllerRepresentable {
    let onCapture: (UIImage) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onCapture: onCapture) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController(); p.sourceType = .camera; p.delegate = context.coordinator; return p
    }
    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}
    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onCapture: (UIImage) -> Void
        init(onCapture: @escaping (UIImage) -> Void) { self.onCapture = onCapture }
        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage { onCapture(img) }
            picker.dismiss(animated: true)
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { picker.dismiss(animated: true) }
    }
}
