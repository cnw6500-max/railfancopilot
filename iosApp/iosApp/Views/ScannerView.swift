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

                    // Web player button
                    Button {
                        webURL = "https://www.railroadradio.net/mobile.php"
                        showWebPlayer = true
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "antenna.radiowaves.left.and.right")
                                .foregroundColor(.railBlue)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Live Scanner Streams")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.textPrimary)
                                Text("Powered by RailroadRadio.net · tap to open")
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
                        .padding(.horizontal, 12)
                        .padding(.bottom, 8)
                    }

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
            WebView(url: URL(string: url)!)
                .ignoresSafeArea(edges: .bottom)
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

struct WebView: UIViewRepresentable {
    let url: URL
    func makeUIView(context: Context) -> WKWebView {
        let wv = WKWebView()
        wv.load(URLRequest(url: url))
        return wv
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
