import SwiftUI
import PhotosUI
import CoreImage
import CoreImage.CIFilterBuiltins

struct PhotoView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var selectedPhoto: PhotosPickerItem? = nil
    @State private var selectedImage: UIImage? = nil
    @State private var showCamera = false

    // Photo Enhancer
    @State private var enhancerPhoto: PhotosPickerItem? = nil
    @State private var enhancedImage: UIImage? = nil
    @State private var showEnhancerPicker = false
    @State private var isEnhancing = false
    @State private var enhanceSaveSuccess = false

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {

                        // Image preview / placeholder
                        ZStack {
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.bgCard)
                                .overlay(RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.border, lineWidth: 0.5))
                                .frame(height: 220)

                            if let img = selectedImage {
                                Image(uiImage: img)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(height: 220)
                                    .clipShape(RoundedRectangle(cornerRadius: 16))
                            } else {
                                VStack(spacing: 10) {
                                    Image(systemName: "camera.aperture")
                                        .font(.system(size: 48))
                                        .foregroundColor(.railBlueDark)
                                    Text("Take or select a photo")
                                        .font(.system(size: 14))
                                        .foregroundColor(.textMuted)
                                }
                            }
                        }
                        .padding(.horizontal)

                        // Buttons
                        HStack(spacing: 12) {
                            Button {
                                showCamera = true
                            } label: {
                                Label("Camera", systemImage: "camera.fill")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.railBlueMid)
                                    .cornerRadius(10)
                            }

                            PhotosPicker(
                                selection: $selectedPhoto,
                                matching: .images,
                                photoLibrary: .shared()
                            ) {
                                Label("Gallery", systemImage: "photo.on.rectangle")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.textPrimary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.bgCard)
                                    .cornerRadius(10)
                                    .overlay(RoundedRectangle(cornerRadius: 10)
                                        .stroke(Color.border, lineWidth: 0.5))
                            }
                        }
                        .padding(.horizontal)

                        // Identify button
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
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Color.railBlueMid)
                                .cornerRadius(12)
                            }
                            .disabled(vm.isIdentifying)
                            .padding(.horizontal)
                        }

                        // Error
                        if let err = vm.locoIdError {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(.orange)
                                Text(err).font(.system(size: 13)).foregroundColor(.textSecondary)
                            }
                            .padding(12).cardStyle().padding(.horizontal)
                        }

                        // Result
                        if let result = vm.locoIdResult {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Image(systemName: "sparkles")
                                        .foregroundColor(.railBlue)
                                    Text("AI Identification")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.textPrimary)
                                }
                                Text(result)
                                    .font(.system(size: 14))
                                    .foregroundColor(.textSecondary)
                                    .lineSpacing(5)
                            }
                            .padding(16)
                            .cardStyle()
                            .padding(.horizontal)
                        }

                        // Empty hint
                        if selectedImage == nil {
                            VStack(spacing: 8) {
                                Image(systemName: "train.side.front.car")
                                    .font(.system(size: 40))
                                    .foregroundColor(.railBlueDark)
                                Text("AI Locomotive Identifier")
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundColor(.textPrimary)
                                Text("Photograph a locomotive and the AI will identify the model, railroad, and notable features.")
                                    .font(.system(size: 13))
                                    .foregroundColor(.textMuted)
                                    .multilineTextAlignment(.center)
                            }
                            .padding(24)
                        }




                        // ── Photo Enhancer ────────────────────────────────
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Image(systemName: "wand.and.stars")
                                    .foregroundColor(.purple)
                                Text("Photo Enhancer")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(.textPrimary)
                            }
                            Text("Railfan preset — boosts saturation, contrast, and warmth to make paint schemes pop.")
                                .font(.system(size: 13))
                                .foregroundColor(.textMuted)

                            if let enhanced = enhancedImage {
                                Image(uiImage: enhanced)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(height: 180)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))

                                HStack(spacing: 10) {
                                    PhotosPicker(selection: $enhancerPhoto, matching: .images) {
                                        Label("New Photo", systemImage: "photo")
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(.textSecondary)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 10)
                                            .background(Color.bgCard)
                                            .cornerRadius(10)
                                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                                    }
                                    Button {
                                        saveEnhancedPhoto(enhanced)
                                    } label: {
                                        Label(enhanceSaveSuccess ? "Saved!" : "Save to Photos",
                                              systemImage: enhanceSaveSuccess ? "checkmark.circle.fill" : "square.and.arrow.down")
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(.white)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 10)
                                            .background(enhanceSaveSuccess ? Color.green : Color.railBlueMid)
                                            .cornerRadius(10)
                                    }
                                }
                            } else {
                                PhotosPicker(selection: $enhancerPhoto, matching: .images) {
                                    HStack {
                                        if isEnhancing {
                                            ProgressView().tint(.white).scaleEffect(0.8)
                                            Text("Enhancing…")
                                        } else {
                                            Image(systemName: "photo.badge.plus")
                                            Text("Select Photo to Enhance")
                                        }
                                    }
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.purple.opacity(0.8))
                                    .cornerRadius(12)
                                }
                                .disabled(isEnhancing)
                            }
                        }
                        .padding(16)
                        .background(Color.bgCard)
                        .cornerRadius(14)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
                        .padding(.horizontal)

                        // Sun Angle Predictor
                        SunAngleCard(
                            elevation: vm.sunInfo.map { String(format: "%.1f°", $0.elevationDegrees) },
                            azimuth: vm.sunInfo.map { String(format: "%.1f°", $0.azimuthDegrees) },
                            goldenStart: vm.sunInfo?.goldenHourStart,
                            goldenEnd: vm.sunInfo?.goldenHourEnd,
                            isGoldenHour: vm.sunInfo?.isGoldenHour ?? false
                        )
                        .padding(.horizontal)

                        Spacer(minLength: 40)
                    }
                    .padding(.top)
                }
            }
            .navigationTitle("Photo ID")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onChange(of: selectedPhoto) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self),
                   let img  = UIImage(data: data) {
                    selectedImage = img
                    vm.locoIdResult = nil
                    vm.locoIdError  = nil
                }
            }
        }
        .onChange(of: enhancerPhoto) { item in
            guard let item else { return }
            isEnhancing = true
            enhancedImage = nil
            enhanceSaveSuccess = false
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img  = UIImage(data: data) {
                    let result = applyRailfanEnhancement(img)
                    await MainActor.run {
                        enhancedImage = result
                        isEnhancing   = false
                    }
                } else {
                    await MainActor.run { isEnhancing = false }
                }
            }
        }
        .sheet(isPresented: $showCamera) {
            CameraSheet { img in
                selectedImage = img
                vm.locoIdResult = nil
                vm.locoIdError  = nil
                showCamera = false
            }
        }
    }

    private func identify() {
        guard let img = selectedImage,
              let jpeg = img.jpegData(compressionQuality: 0.7) else { return }
        vm.identifyLoco(jpegData: jpeg)
    }

    // ── Railfan Enhancement — matches Android preset exactly ─────────────────
    private func applyRailfanEnhancement(_ input: UIImage) -> UIImage {
        guard let ciImage = CIImage(image: input) else { return input }
        let context = CIContext()

        // 1. Saturation +35% and brightness lift
        let colorControls = CIFilter.colorControls()
        colorControls.inputImage  = ciImage
        colorControls.saturation  = 1.35
        colorControls.brightness  = 0.04   // ~+10/255
        colorControls.contrast    = 1.15

        // 2. Warmth — shift toward warm orange/red
        guard let afterControls = colorControls.outputImage else { return input }
        let tempTint = CIFilter.temperatureAndTint()
        tempTint.inputImage = afterControls
        tempTint.neutral    = CIVector(x: 6500, y: 0)   // warm target
        tempTint.targetNeutral = CIVector(x: 7200, y: 0)

        guard let output = tempTint.outputImage,
              let cgImage = context.createCGImage(output, from: ciImage.extent) else { return input }

        return UIImage(cgImage: cgImage, scale: input.scale, orientation: input.imageOrientation)
    }

    private func saveEnhancedPhoto(_ image: UIImage) {
        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
        withAnimation { enhanceSaveSuccess = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation { enhanceSaveSuccess = false }
        }
    }
}

// ── Sun Angle Card ────────────────────────────────────────────────────────────
struct SunAngleCard: View {
    let elevation: String?
    let azimuth: String?
    let goldenStart: String?
    let goldenEnd: String?
    let isGoldenHour: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "sun.max.fill")
                    .foregroundColor(.yellow)
                Text("Sun Angle Predictor")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.textPrimary)
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
        .padding(16)
        .background(Color.bgCard)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.border, lineWidth: 0.5))
    }
}

struct SunStatCell: View {
    let value: String
    let label: String
    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 14, weight: .semibold)).foregroundColor(.railBlue)
            Text(label).font(.system(size: 11)).foregroundColor(.textMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

// ── Camera sheet using UIImagePickerController ────────────────────────────────
struct CameraSheet: UIViewControllerRepresentable {
    let onCapture: (UIImage) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onCapture: onCapture) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onCapture: (UIImage) -> Void
        init(onCapture: @escaping (UIImage) -> Void) { self.onCapture = onCapture }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage {
                // Save to camera roll so photo persists in gallery
                UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
                onCapture(img)
            }
            picker.dismiss(animated: true)
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
        }
    }
}
