import SwiftUI
import PhotosUI

struct PhotoView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var selectedPhoto: PhotosPickerItem? = nil
    @State private var selectedImage: UIImage? = nil
    @State private var showCamera = false

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
            if let img = info[.originalImage] as? UIImage { onCapture(img) }
            picker.dismiss(animated: true)
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
        }
    }
}
