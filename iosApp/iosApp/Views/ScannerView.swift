import SwiftUI
import WebKit
import CoreLocation
import shared

// ── Live radio feeds data (mirrors Android) ───────────────────────────────────
struct RRFeed: Identifiable {
    let id = UUID()
    let name: String
    let region: String
    let railroads: String
    let url: String
    let lat: Double?
    let lon: Double?

    func distanceMiles(from loc: CLLocationCoordinate2D?) -> Double? {
        guard let loc, let lat, let lon else { return nil }
        let a = CLLocation(latitude: loc.latitude, longitude: loc.longitude)
        let b = CLLocation(latitude: lat, longitude: lon)
        return a.distance(from: b) / 1609.34
    }
}

private let railroadRadioFeeds: [RRFeed] = [
    RRFeed(name: "CSX Baltimore MD",        region: "Baltimore, MD",    railroads: "CSX",         url: "http://www.railroadradio.net/content/view/199/241/", lat: 39.29,  lon: -76.61),
    RRFeed(name: "CSX | NS Elkhorn City KY", region: "Elkhorn City, KY", railroads: "CSX/NS",      url: "http://www.railroadradio.net/content/view/278/322/", lat: 37.30,  lon: -82.35),
    RRFeed(name: "Central New Jersey",       region: "New Jersey",       railroads: "CSX/NS/NJT",  url: "http://www.railroadradio.net/content/view/166/200/", lat: 40.29,  lon: -74.55),
    RRFeed(name: "Fort Wayne Area",          region: "Fort Wayne, IN",   railroads: "NS/CSX",      url: "http://www.railroadradio.net/content/view/33/160",   lat: 41.08,  lon: -85.14),
    RRFeed(name: "Greater Canton Ohio",      region: "Canton, OH",       railroads: "NS/CSX",      url: "http://www.railroadradio.net/content/view/298/344/", lat: 40.80,  lon: -81.38),
    RRFeed(name: "Spartanburg SC",           region: "Spartanburg, SC",  railroads: "CSX/NS",      url: "http://www.railroadradio.net/content/view/192/232/", lat: 34.95,  lon: -81.93),
    RRFeed(name: "BNSF/UP Ozark Region",     region: "St. Louis, MO",    railroads: "BNSF/UP",     url: "http://www.railroadradio.net/content/view/282/325/", lat: 38.63,  lon: -90.20),
    RRFeed(name: "South Central Virginia",   region: "Virginia",         railroads: "NS/CSX",      url: "http://www.railroadradio.net/content/view/178/215/", lat: 37.10,  lon: -79.39),
    RRFeed(name: "Vancouver BC",             region: "Vancouver, BC",    railroads: "CN/CPKC",     url: "http://www.railroadradio.net/content/view/22/130/",  lat: 49.25,  lon: -123.10),
    RRFeed(name: "CPKC Calgary Terminal",    region: "Calgary, AB",      railroads: "CPKC",        url: "http://www.railroadradio.net/content/view/272/315/", lat: 51.05,  lon: -114.07),
    RRFeed(name: "Browse all feeds",         region: "RailroadRadio.net", railroads: "All railroads", url: "http://www.railroadradio.net", lat: nil, lon: nil),
]

// ── ScannerView ───────────────────────────────────────────────────────────────
struct ScannerView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var selectedTab = 0
    @State private var showWebPlayer = false      // live feeds
    @State private var showWebcamPlayer = false   // webcam tab
    @State private var showLogSheet = false
    @State private var logChannel: RadioChannel? = nil
    @State private var webURL = ""
    @State private var webName = ""
    @State private var searchText = ""
    private let tabLabels = ["Live Feeds", "Frequencies", "Webcams"]

    var sortedFeeds: [RRFeed] {
        let withDistance = railroadRadioFeeds.dropLast()
        let sorted = withDistance.sorted {
            let da = $0.distanceMiles(from: vm.userLocation) ?? Double.greatestFiniteMagnitude
            let db = $1.distanceMiles(from: vm.userLocation) ?? Double.greatestFiniteMagnitude
            return da < db
        }
        return sorted + [railroadRadioFeeds.last!]
    }

    var filteredChannels: [RadioChannel] {
        if searchText.isEmpty { return vm.radioChannels }
        return vm.radioChannels.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.railroad.displayName.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Tab bar
                    HStack(spacing: 0) {
                        ForEach(tabLabels.indices, id: \.self) { i in
                            Button {
                                withAnimation(.easeInOut(duration: 0.2)) { selectedTab = i }
                            } label: {
                                Text(tabLabels[i])
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(selectedTab == i ? .railBlue : .textMuted)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .overlay(
                                        Rectangle()
                                            .fill(selectedTab == i ? Color.railBlue : Color.clear)
                                            .frame(height: 2),
                                        alignment: .bottom
                                    )
                            }
                        }
                    }
                    .background(Color.bgPrimary)
                    .overlay(Divider().background(Color.border), alignment: .bottom)

                    if selectedTab == 0 {
                        liveFeedsTab
                    } else if selectedTab == 1 {
                        frequenciesTab
                    } else {
                        webcamsTab
                    }
                }
            }
            .navigationTitle("Scanner")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .sheet(isPresented: $showWebPlayer) {
            WebPlayerSheet(url: webURL)
        }
        .sheet(isPresented: $showWebcamPlayer) {
            WebcamPlayerSheet(url: webURL, camName: webName)
        }
        .sheet(isPresented: $showLogSheet) {
            logSheet
        }
    }

    // ── Live Feeds tab ────────────────────────────────────────────────────────
    private var liveFeedsTab: some View {
        VStack(spacing: 0) {
            if vm.userLocation != nil {
                HStack(spacing: 6) {
                    Image(systemName: "location.fill").font(.system(size: 11)).foregroundColor(.railBlue)
                    Text("Sorted by distance from you")
                        .font(.system(size: 12)).foregroundColor(.textMuted)
                    Spacer()
                }
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(Color.bgCard.opacity(0.5))
            }

            List {
                ForEach(sortedFeeds) { feed in
                    FeedRow(feed: feed, userLocation: vm.userLocation,
                            webURL: $webURL, showWebPlayer: $showWebPlayer)
                        .listRowBackground(Color.bgCard)
                        .listRowSeparatorTint(Color.border)
                }
                Text("Powered by RailroadRadio.net")
                    .font(.system(size: 12)).foregroundColor(.railBlue)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .listRowBackground(Color.bgPrimary)
                    .listRowSeparator(.hidden)
            }
            .listStyle(.plain)
            .background(Color.bgPrimary)
            .scrollContentBackground(.hidden)
        }
    }

    // ── Webcams tab ───────────────────────────────────────────────────────────
    private var webcamsTab: some View {
        List(curated) { cam in
            WebcamRow(cam: cam, webURL: $webURL, webName: $webName, showWebPlayer: $showWebcamPlayer)
                .listRowBackground(Color.bgCard)
                .listRowSeparatorTint(Color.border)
        }
        .listStyle(.plain)
        .background(Color.bgPrimary)
        .scrollContentBackground(.hidden)
    }

    // ── Frequencies tab ───────────────────────────────────────────────────────
    private var frequenciesTab: some View {
        VStack(spacing: 0) {
            // Search bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.textMuted).font(.system(size: 15))
                TextField("Search channels…", text: $searchText)
                    .foregroundColor(.textPrimary).font(.system(size: 15))
            }
            .padding(10).background(Color.bgInput).cornerRadius(10)
            .padding(.horizontal, 12).padding(.vertical, 8)

            List(filteredChannels, id: \.id) { channel in
                ChannelRow(channel: channel, onLog: {
                    logChannel = channel
                    showLogSheet = true
                })
                .listRowBackground(Color.bgCard)
                .listRowSeparatorTint(Color.border)
            }
            .listStyle(.plain)
            .background(Color.bgPrimary)
            .scrollContentBackground(.hidden)
        }
    }
}
extension ScannerView {
    // Sheets defined outside body to avoid SwiftUI triple-sheet limitation
    var logSheet: some View {
        Group {
            if let ch = logChannel {
                LogTransmissionSheet(channel: ch, vm: vm, onDismiss: { showLogSheet = false })
            }
        }
    }
}

// ── Feed row ──────────────────────────────────────────────────────────────────
struct FeedRow: View {
    let feed: RRFeed
    let userLocation: CLLocationCoordinate2D?
    @Binding var webURL: String
    @Binding var showWebPlayer: Bool

    var body: some View {
        Button {
            webURL = feed.url
            showWebPlayer = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .foregroundColor(.railBlue)
                    .font(.system(size: 16))
                    .frame(width: 22)

                VStack(alignment: .leading, spacing: 3) {
                    Text(feed.name)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.textPrimary)
                    Text("\(feed.region) · \(feed.railroads)")
                        .font(.system(size: 12))
                        .foregroundColor(.textMuted)
                }
                Spacer()
                if let dist = feed.distanceMiles(from: userLocation) {
                    Text(String(format: "%.0f mi", dist))
                        .font(.system(size: 12))
                        .foregroundColor(.textMuted)
                }
                Image(systemName: "chevron.right")
                    .font(.system(size: 11))
                    .foregroundColor(.textMuted)
            }
            .padding(.vertical, 4)
        }
    }
}

// ── Channel row ───────────────────────────────────────────────────────────────
struct ChannelRow: View {
    let channel: RadioChannel
    var onLog: (() -> Void)? = nil
    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(rrColor(channel.railroad.name))
                .frame(width: 8, height: 8)
            VStack(alignment: .leading, spacing: 3) {
                Text(channel.name)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.textPrimary)
                Text(channel.railroad.displayName)
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
            }
            Spacer()
            Text(String(format: "%.4f MHz", channel.frequencyMhz))
                .font(.system(size: 13, weight: .medium, design: .monospaced))
                .foregroundColor(.railBlue)
            if let onLog {
                Button { onLog() } label: {
                    Text("Log")
                        .font(.system(size: 11, weight: .semibold)).foregroundColor(.white)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Color.railBlueMid).cornerRadius(6)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 4)
    }
}

// ── Web player sheet ──────────────────────────────────────────────────────────
struct WebPlayerSheet: View {
    let url: String
    @Environment(\.dismiss) var dismiss
    var body: some View {
        NavigationView {
            Group {
                if let parsedURL = URL(string: url) {
                    WebView(url: parsedURL).ignoresSafeArea(edges: .bottom)
                } else {
                    VStack(spacing: 16) {
                        Image(systemName: "antenna.radiowaves.left.and.right.slash")
                            .font(.system(size: 40)).foregroundColor(.textMuted)
                        Text("Could not load scanner").foregroundColor(.textMuted)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.bgPrimary)
                }
            }
            .navigationTitle("Live Streams")
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

struct WebView: UIViewRepresentable {
    let url: URL
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        let wv = WKWebView(frame: .zero, configuration: config)
        wv.backgroundColor = UIColor(red: 0.05, green: 0.1, blue: 0.18, alpha: 1)
        wv.isOpaque = false
        wv.load(URLRequest(url: url))
        return wv
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
