import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins
import PhotosUI

// ── Shared CIContext — expensive to create, reused across all enhancements ─────
private let sharedCIContext = CIContext(options: [.useSoftwareRenderer: false])

// ── Core Image enhancement pipeline (mirrors Android applyRailfanEnhancement) ─
// Applies the same three-stage pipeline as the Android ColorMatrix version:
//   1. Saturation ×1.35  — makes railroad paint schemes pop
//   2. Contrast ×1.15 + brightness +10/255 — lifts shadows and adds punch
//   3. Warmth (+8 red, -6 blue) — flatters locomotive paint and golden-hour sky
func applyRailfanEnhancement(to input: UIImage) -> UIImage? {
    guard let ciImage = CIImage(image: input) else { return nil }

    // Stage 1: saturation boost
    let sat = CIFilter.colorControls()
    sat.inputImage = ciImage
    sat.saturation = 1.35
    sat.brightness = 0
    sat.contrast   = 1.0
    guard let satOut = sat.outputImage else { return nil }

    // Stage 2: contrast ×1.15 + brightness lift (+10/255 ≈ 0.039 in CI's -1…1 range)
    let con = CIFilter.colorControls()
    con.inputImage = satOut
    con.saturation = 1.0
    con.brightness = Float(10.0 / 255.0)
    con.contrast   = 1.15
    guard let conOut = con.outputImage else { return nil }

    // Stage 3: warmth — bias vector replicates Android's +8 red, -6 blue
    let warm = CIFilter.colorMatrix()
    warm.inputImage  = conOut
    warm.biasVector  = CIVector(x: CGFloat(8.0/255.0), y: 0, z: CGFloat(-6.0/255.0), w: 0)
    guard let warmOut = warm.outputImage else { return nil }

    // Use warmOut.extent (not ciImage.extent) to avoid clipping if filter expands bounds
    guard let cgImage = sharedCIContext.createCGImage(warmOut, from: warmOut.extent) else { return nil }
    return UIImage(cgImage: cgImage, scale: input.scale, orientation: input.imageOrientation)
}

// ── Photo Enhancer Sheet ──────────────────────────────────────────────────────
struct PhotoEnhancerSheet: View {
    @Environment(\.dismiss) var dismiss
    @State private var selectedPhoto: PhotosPickerItem? = nil
    @State private var originalImage: UIImage? = nil
    @State private var enhancedImage: UIImage? = nil
    @State private var isEnhancing = false
    @State private var showOriginal = false
    @State private var showSaveSuccess = false
    @State private var enhanceError: String? = nil
    @State private var enhanceTask: Task<Void, Never>? = nil

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {

                        // ── Pick image (hidden while enhancing or result is shown) ──
                        if originalImage == nil && !isEnhancing {
                            PhotosPicker(selection: $selectedPhoto, matching: .images) {
                                VStack(spacing: 14) {
                                    Image(systemName: "photo.badge.plus")
                                        .font(.system(size: 52)).foregroundColor(.railBlueDark)
                                    Text("Choose a Photo to Enhance")
                                        .font(.system(size: 15, weight: .medium)).foregroundColor(.textPrimary)
                                    Text("Optimised for locomotive and railroad photography")
                                        .font(.system(size: 13)).foregroundColor(.textMuted)
                                        .multilineTextAlignment(.center)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(40)
                                .background(Color.bgCard)
                                .cornerRadius(16)
                                .overlay(RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.border, lineWidth: 0.5))
                            }
                            .padding(.horizontal)
                        }

                        // ── Enhancing spinner ─────────────────────────────────
                        if isEnhancing {
                            VStack(spacing: 14) {
                                ProgressView().scaleEffect(1.4).tint(.railBlue)
                                Text("Enhancing…")
                                    .font(.system(size: 14)).foregroundColor(.textMuted)
                            }
                            .frame(maxWidth: .infinity).padding(40)
                        }

                        // ── Enhancement error ─────────────────────────────────
                        if let err = enhanceError {
                            HStack(spacing: 10) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(.orange)
                                Text(err).font(.system(size: 13)).foregroundColor(.textSecondary)
                                Spacer()
                                Button { enhanceError = nil } label: {
                                    Image(systemName: "xmark").foregroundColor(.textMuted)
                                }
                            }
                            .padding(12).background(Color.bgCard).cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                            .padding(.horizontal)
                        }

                        // ── Before / After ────────────────────────────────────
                        if let original = originalImage, let enhanced = enhancedImage {
                            VStack(spacing: 12) {
                                // Toggle chips
                                HStack(spacing: 0) {
                                    ForEach(["Before", "After"], id: \.self) { label in
                                        let isSelected = (label == "Before") == showOriginal
                                        Button { showOriginal = (label == "Before") } label: {
                                            Text(label)
                                                .font(.system(size: 13, weight: .semibold))
                                                .foregroundColor(isSelected ? .white : .textMuted)
                                                .frame(maxWidth: .infinity)
                                                .padding(.vertical, 8)
                                                .background(isSelected ? Color.railBlueMid : Color.bgCard)
                                        }
                                    }
                                }
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                                .padding(.horizontal)

                                // Image
                                Image(uiImage: showOriginal ? original : enhanced)
                                    .resizable()
                                    .scaledToFit()
                                    .cornerRadius(14)
                                    .padding(.horizontal)
                                    .animation(.easeInOut(duration: 0.2), value: showOriginal)

                                // Enhancement summary
                                if !showOriginal {
                                    HStack(spacing: 16) {
                                        EnhanceBadge(label: "Saturation", value: "+35%")
                                        EnhanceBadge(label: "Contrast",   value: "+15%")
                                        EnhanceBadge(label: "Warmth",     value: "On")
                                    }
                                    .padding(.horizontal)
                                }

                                // Actions
                                VStack(spacing: 10) {
                                    Button { saveEnhanced(enhanced) } label: {
                                        Label(showSaveSuccess ? "Saved!" : "Save to Photos",
                                              systemImage: showSaveSuccess ? "checkmark.circle.fill" : "square.and.arrow.down")
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(.white)
                                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                                            .background(showSaveSuccess ? Color.green : Color.railBlueMid)
                                            .cornerRadius(12)
                                    }
                                    .disabled(showSaveSuccess)
                                    .padding(.horizontal)

                                    Button {
                                        shareImage(enhanced)
                                    } label: {
                                        Label("Share", systemImage: "square.and.arrow.up")
                                            .font(.system(size: 15, weight: .medium))
                                            .foregroundColor(.railBlue)
                                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                                            .background(Color.bgCard)
                                            .cornerRadius(12)
                                            .overlay(RoundedRectangle(cornerRadius: 12)
                                                .stroke(Color.border, lineWidth: 0.5))
                                    }
                                    .padding(.horizontal)

                                    Button {
                                        originalImage   = nil
                                        enhancedImage   = nil
                                        selectedPhoto   = nil
                                        showOriginal    = false
                                        showSaveSuccess = false
                                        enhanceError    = nil
                                        enhanceTask     = nil
                                    } label: {
                                        Text("Enhance Another Photo")
                                            .font(.system(size: 14)).foregroundColor(.textMuted)
                                    }
                                }
                            }
                        }

                        Spacer(minLength: 40)
                    }
                    .padding(.top)
                }
            }
            .navigationTitle("Photo Enhancer")
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
        .onChange(of: selectedPhoto) { item in
            guard let item else { return }
            // Cancel any in-flight enhancement before starting a new one
            enhanceTask?.cancel()
            isEnhancing     = true
            showOriginal    = false
            showSaveSuccess = false
            enhanceError    = nil
            originalImage   = nil
            enhancedImage   = nil

            enhanceTask = Task {
                guard let data = try? await item.loadTransferable(type: Data.self),
                      let img = UIImage(data: data) else {
                    if !Task.isCancelled {
                        isEnhancing  = false
                        enhanceError = "Could not load the selected photo."
                    }
                    return
                }
                guard !Task.isCancelled else { return }

                let enhanced = await Task.detached(priority: .userInitiated) {
                    applyRailfanEnhancement(to: img)
                }.value

                guard !Task.isCancelled else { return }

                if let enhanced {
                    originalImage = img
                    enhancedImage = enhanced
                } else {
                    enhanceError  = "Enhancement failed. The image format may not be supported."
                    selectedPhoto = nil   // reset so user can pick the same photo again
                }
                isEnhancing  = false
                showOriginal = false
            }
        }
    }

    private func saveEnhanced(_ image: UIImage) {
        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
        withAnimation { showSaveSuccess = true }
    }

    private func shareImage(_ image: UIImage) {
        guard let data = image.jpegData(compressionQuality: 0.9) else { return }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("railfan_enhanced_\(Int(Date().timeIntervalSince1970)).jpg")
        try? data.write(to: url)
        let av = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let root = scene.windows.first?.rootViewController {
            av.popoverPresentationController?.sourceView = root.view
            root.present(av, animated: true)
        }
    }
}

// ── Enhancement badge ─────────────────────────────────────────────────────────
struct EnhanceBadge: View {
    let label: String; let value: String
    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 13, weight: .bold)).foregroundColor(.railBlue)
            Text(label).font(.system(size: 10)).foregroundColor(.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(Color.bgCard)
        .cornerRadius(8)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.border, lineWidth: 0.5))
    }
}
