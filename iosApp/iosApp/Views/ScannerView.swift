import SwiftUI
import WebKit
import shared

struct ScannerView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var selectedChannel: RadioChannel? = nil
    @State private var showWebPlayer = false
    @State private var webURL = ""
    @State private var searchText = ""

    var filteredChannels: [RadioChannel] {
        let channels = vm.radioChannels as? [RadioChannel] ?? []
        if searchText.isEmpty { return channels }
        return channels.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.railroad.displayName.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Search
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.textMuted)
                            .font(.system(size: 15))
                        TextField("Search channels...", text: $searchText)
                            .foregroundColor(.textPrimary)
                            .font(.system(size: 15))
                    }
                    .padding(10)
                    .background(Color.bgInput)
                    .cornerRadius(10)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)

                    // Web player buttons
                    VStack(spacing: 8) {
                        ScannerLinkButton(
                            title: "Broadcastify — Live Rail Feeds",
                            subtitle: "Largest railroad scanner network",
                            icon: "antenna.radiowaves.left.and.right",
                            url: "https://www.broadcastify.com/listen/ctid/2",
                            webURL: $webURL, showWebPlayer: $showWebPlayer
                        )
                        ScannerLinkButton(
                            title: "Railroad Radio",
                            subtitle: "railroadradio.net live streams",
                            icon: "radio",
                            url: "https://railroadradio.net",
                            webURL: $webURL, showWebPlayer: $showWebPlayer
                        )
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)

                    // AAR Frequency list
                    List(filteredChannels, id: \.id) { channel in
                        ChannelRow(channel: channel)
                            .listRowBackground(Color.bgCard)
                            .listRowSeparatorTint(Color.border)
                    }
                    .listStyle(.plain)
                    .background(Color.bgPrimary)
                    .scrollContentBackground(.hidden)
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
    }
}

struct ChannelRow: View {
    let channel: RadioChannel
    var body: some View {
        HStack(spacing: 12) {
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
        }
        .padding(.vertical, 4)
    }
}

struct WebPlayerSheet: View {
    let url: String
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            Group {
                if let parsedURL = URL(string: url) {
                    WebView(url: parsedURL)
                        .ignoresSafeArea(edges: .bottom)
                } else {
                    VStack(spacing: 16) {
                        Image(systemName: "antenna.radiowaves.left.and.right.slash")
                            .font(.system(size: 40))
                            .foregroundColor(.textMuted)
                        Text("Could not load scanner")
                            .foregroundColor(.textMuted)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.bgPrimary)
                }
            }
            .navigationTitle("Live Streams")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

struct ScannerLinkButton: View {
    let title: String
    let subtitle: String
    let icon: String
    let url: String
    @Binding var webURL: String
    @Binding var showWebPlayer: Bool

    var body: some View {
        Button {
            webURL = url
            showWebPlayer = true
        } label: {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .foregroundColor(.railBlue)
                    .font(.system(size: 18))
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.textPrimary)
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(.textMuted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12))
                    .foregroundColor(.textMuted)
            }
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12)
                .stroke(Color.railBlueMid, lineWidth: 0.5))
        }
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
        wv.scrollView.backgroundColor = .clear
        wv.load(URLRequest(url: url))
        return wv
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
