import SwiftUI
import WebKit

struct RailWebcam: Identifiable {
    let id = UUID()
    let name: String
    let location: String
    let railroad: String
    let description: String
    let url: String
    let category: WebcamCategory
}

enum WebcamCategory: String, CaseIterable {
    case crossing  = "Crossings"
    case yard      = "Yards"
    case mountain  = "Mountain Grades"
    case station   = "Stations"
    case all       = "All"
}

// Confirmed permanent VirtualRailfan YouTube stream IDs (mirrors Android)
let curated: [RailWebcam] = [
    // ── Crossings ─────────────────────────────────────────────────────────────
    RailWebcam(name: "Rochelle Railroad Park",
               location: "Rochelle, IL",
               railroad: "BNSF / UP",
               description: "World-famous diamond crossing of BNSF and UP mainlines. 100+ trains/day.",
               url: "https://www.youtube.com/watch?v=LhNpn9L5ndM",
               category: .crossing),
    RailWebcam(name: "Fostoria Iron Triangle",
               location: "Fostoria, OH",
               railroad: "CSX / NS / CR",
               description: "Three-railroad triangle with a dedicated railfan park and webcam.",
               url: "https://www.youtube.com/watch?v=23tmCNeFh7A",
               category: .crossing),
    RailWebcam(name: "Streator Diamond",
               location: "Streator, IL",
               railroad: "BNSF / CN",
               description: "Active diamond crossing of BNSF Chillicothe Sub and CN.",
               url: "https://www.youtube.com/watch?v=7xdHH9KMSVk",
               category: .crossing),

    // ── Mountain Grades ───────────────────────────────────────────────────────
    RailWebcam(name: "Cajon Pass",
               location: "San Bernardino, CA",
               railroad: "BNSF / UP",
               description: "Iconic desert mountain pass with helper operations.",
               url: "https://www.youtube.com/watch?v=-Q9VQJdqIlk",
               category: .mountain),
    RailWebcam(name: "Donner Pass",
               location: "Truckee, CA",
               railroad: "UP",
               description: "Sierra Nevada crossing on the historic Overland Route.",
               url: "https://www.youtube.com/watch?v=xKUkjFJkKgc",
               category: .mountain),
    RailWebcam(name: "Horseshoe Curve",
               location: "Altoona, PA",
               railroad: "NS",
               description: "National Historic Landmark. One of the most-watched rail cams in the US.",
               url: "https://www.youtube.com/@VirtualRailfan/search?query=Altoona",
               category: .mountain),
    RailWebcam(name: "Moffat Tunnel",
               location: "Winter Park, CO",
               railroad: "UP",
               description: "6-mile tunnel through the Continental Divide.",
               url: "https://www.youtube.com/@VirtualRailfan/search?query=Marias",
               category: .mountain),

    // ── Yards ─────────────────────────────────────────────────────────────────
    RailWebcam(name: "Barstow Yard (BNSF)",
               location: "Barstow, CA",
               railroad: "BNSF",
               description: "Major BNSF intermodal hub in the Mojave Desert.",
               url: "https://www.youtube.com/@VirtualRailfan/search?query=Barstow",
               category: .yard),
    RailWebcam(name: "Chattanooga",
               location: "Chattanooga, TN",
               railroad: "NS / CSX",
               description: "Tennessee Gateway — heavy manifest and coal traffic.",
               url: "https://www.youtube.com/@VirtualRailfan/search?query=Chattanooga",
               category: .yard),
    RailWebcam(name: "Helper, UT (UP)",
               location: "Helper, UT",
               railroad: "UP",
               description: "UP's famous Utah mountain grade with helper operations.",
               url: "https://www.youtube.com/@VirtualRailfan/search?query=Helper",
               category: .yard),

    // ── Stations ──────────────────────────────────────────────────────────────
    RailWebcam(name: "Laramie Station (UP)",
               location: "Laramie, WY",
               railroad: "UP",
               description: "UP's Sherman Hill crossing — high-altitude mountain railroading.",
               url: "https://www.youtube.com/@VirtualRailfan/search?query=Laramie",
               category: .station),
    RailWebcam(name: "Waycross (CSX)",
               location: "Waycross, GA",
               railroad: "CSX",
               description: "CSX's busiest Southeast yard and the Folkston Funnel approach.",
               url: "https://www.youtube.com/@VirtualRailfan/search?query=Waycross",
               category: .station),

    // ── All ───────────────────────────────────────────────────────────────────
    RailWebcam(name: "VirtualRailfan — All Streams",
               location: "YouTube",
               railroad: "All railroads",
               description: "Browse all live and recent VirtualRailfan streams.",
               url: "https://www.youtube.com/@VirtualRailfan/streams",
               category: .all),
    RailWebcam(name: "Railstream Directory",
               location: "railstream.net",
               railroad: "All railroads",
               description: "Full library of live and recorded railroad webcams.",
               url: "https://www.railstream.net",
               category: .all),
]

// ── WebcamsView ───────────────────────────────────────────────────────────────
struct WebcamsView: View {
    @State private var selectedCategory: WebcamCategory = .all
    @State private var webURL = ""
    @State private var webName = ""
    @State private var showWebPlayer = false

    var filtered: [RailWebcam] {
        selectedCategory == .all ? curated : curated.filter { $0.category == selectedCategory }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Category filter chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(WebcamCategory.allCases, id: \.self) { cat in
                                FilterChipView(label: cat.rawValue,
                                               selected: selectedCategory == cat) {
                                    selectedCategory = cat
                                }
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                    }
                    .background(Color.bgPrimary)
                    .overlay(Divider().background(Color.border), alignment: .bottom)

                    List(filtered) { cam in
                        WebcamRow(cam: cam, webURL: $webURL, webName: $webName, showWebPlayer: $showWebPlayer)
                            .listRowBackground(Color.bgCard)
                            .listRowSeparatorTint(Color.border)
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("Webcams")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .sheet(isPresented: $showWebPlayer) {
            WebcamPlayerSheet(url: webURL, camName: webName)
        }
    }
}

// ── Webcam row ────────────────────────────────────────────────────────────────
struct WebcamRow: View {
    let cam: RailWebcam
    @Binding var webURL: String
    @Binding var webName: String
    @Binding var showWebPlayer: Bool

    var body: some View {
        Button {
            webURL = cam.url
            webName = cam.name
            showWebPlayer = true
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.bgInput)
                        .frame(width: 50, height: 50)
                    Image(systemName: categoryIcon(cam.category))
                        .font(.system(size: 22))
                        .foregroundColor(.railBlue)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(cam.name)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.textPrimary)
                        .lineLimit(1)
                    Text("\(cam.location) · \(cam.railroad)")
                        .font(.system(size: 12))
                        .foregroundColor(.textMuted)
                    Text(cam.description)
                        .font(.system(size: 11))
                        .foregroundColor(.textSecondary)
                        .lineLimit(2)
                }

                Spacer()
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 22))
                    .foregroundColor(.railBlue)
            }
            .padding(.vertical, 6)
        }
    }

    private func categoryIcon(_ cat: WebcamCategory) -> String {
        switch cat {
        case .crossing:  return "xmark.circle"
        case .yard:      return "building.2"
        case .mountain:  return "mountain.2"
        case .station:   return "building.columns"
        case .all:       return "video"
        }
    }
}

// ── Webcam player sheet ───────────────────────────────────────────────────────
struct WebcamPlayerSheet: View {
    let url: String
    let camName: String
    @Environment(\.dismiss) var dismiss
    @State private var loadFailed = false
    @State private var isLoading = true

    var parsedURL: URL? { URL(string: url) }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                if let validURL = parsedURL {
                    if loadFailed {
                        blockedView(validURL)
                    } else {
                        WebViewWithFallback(
                            url: validURL,
                            onLoadFailed: { loadFailed = true },
                            onLoadFinished: { isLoading = false }
                        )
                        .ignoresSafeArea(edges: .bottom)
                        .overlay {
                            if isLoading {
                                ProgressView("Loading…")
                                    .tint(.railBlue)
                                    .foregroundColor(.textMuted)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                                    .background(Color.bgPrimary)
                            }
                        }
                    }
                } else {
                    blockedView(nil)
                }
            }
            .navigationTitle(camName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") { dismiss() }.foregroundColor(.railBlue)
                }
                if let validURL = parsedURL {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button {
                            UIApplication.shared.open(validURL)
                        } label: {
                            Image(systemName: "safari").foregroundColor(.railBlue)
                        }
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    @ViewBuilder
    private func blockedView(_ validURL: URL?) -> some View {
        VStack(spacing: 20) {
            Image(systemName: "video.slash.fill")
                .font(.system(size: 48)).foregroundColor(.textMuted)
            Text("Can't play inline")
                .font(.system(size: 18, weight: .semibold)).foregroundColor(.textPrimary)
            Text("This webcam doesn't allow embedding.\nTap below to open it in Safari.")
                .font(.system(size: 14)).foregroundColor(.textMuted)
                .multilineTextAlignment(.center)
            if let validURL {
                Button {
                    UIApplication.shared.open(validURL)
                } label: {
                    Label("Open in Safari", systemImage: "safari")
                        .font(.system(size: 15, weight: .semibold)).foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 14)
                        .background(Color.railBlueMid).cornerRadius(12)
                }
                .padding(.horizontal, 40)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// ── WebView with failure detection ────────────────────────────────────────────
struct WebViewWithFallback: UIViewRepresentable {
    let url: URL
    var onLoadFailed: () -> Void
    var onLoadFinished: () -> Void

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        let wv = WKWebView(frame: .zero, configuration: config)
        wv.navigationDelegate = context.coordinator
        wv.backgroundColor = UIColor(red: 0.05, green: 0.1, blue: 0.18, alpha: 1)
        wv.isOpaque = false
        wv.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        wv.load(URLRequest(url: url))
        return wv
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(self) }

    class Coordinator: NSObject, WKNavigationDelegate {
        let parent: WebViewWithFallback
        init(_ p: WebViewWithFallback) { parent = p }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.onLoadFinished()
        }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            parent.onLoadFailed()
        }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            parent.onLoadFailed()
        }
    }
}
