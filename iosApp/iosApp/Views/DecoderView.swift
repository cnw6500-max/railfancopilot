import SwiftUI
import shared

struct DecoderView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var symbolInput = ""
    @State private var locoInput = ""
    @FocusState private var inputFocused: Bool

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {

                        // Input card
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Enter Train Symbol")
                                .font(.system(size: 13))
                                .foregroundColor(.textMuted)

                            HStack(spacing: 10) {
                                TextField("e.g. QCHILA, TOMNK-01", text: $symbolInput)
                                    .font(.system(size: 16, weight: .medium, design: .monospaced))
                                    .foregroundColor(.textPrimary)
                                    .textInputAutocapitalization(.characters)
                                    .autocorrectionDisabled()
                                    .focused($inputFocused)
                                    .onSubmit { decodeIfNeeded() }

                                if !symbolInput.isEmpty {
                                    Button { symbolInput = "" } label: {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundColor(.textMuted)
                                    }
                                }
                            }
                            .padding(12)
                            .background(Color.bgInput)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.borderLight, lineWidth: 0.5))

                            Button(action: decodeIfNeeded) {
                                HStack {
                                    if vm.isDecoding {
                                        ProgressView().tint(.white).scaleEffect(0.8)
                                    } else {
                                        Image(systemName: "cpu")
                                    }
                                    Text(vm.isDecoding ? "Decoding…" : "Decode Symbol")
                                        .font(.system(size: 15, weight: .semibold))
                                }
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(symbolInput.isEmpty ? Color.railBlueDark : Color.railBlueMid)
                                .cornerRadius(12)
                            }
                            .disabled(symbolInput.isEmpty || vm.isDecoding)
                        }
                        .padding(16)
                        .cardStyle()
                        .padding(.horizontal)

                        // Error
                        if let err = vm.decoderError {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(.orange)
                                Text(err)
                                    .font(.system(size: 13))
                                    .foregroundColor(.textSecondary)
                            }
                            .padding(12)
                            .cardStyle()
                            .padding(.horizontal)
                        }

                        // Result
                        if let result = vm.decoderResult {
                            DecoderResultCard(result: result)
                                .padding(.horizontal)
                        }

                        // Hint
                        if vm.decoderResult == nil && !vm.isDecoding {
                            VStack(spacing: 8) {
                                Image(systemName: "cpu")
                                    .font(.system(size: 40))
                                    .foregroundColor(.railBlueDark)
                                Text("AI Symbol Decoder")
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundColor(.textPrimary)
                                Text("Enter a freight or passenger train symbol to decode its origin, destination, type, and typical consist.")
                                    .font(.system(size: 13))
                                    .foregroundColor(.textMuted)
                                    .multilineTextAlignment(.center)
                            }
                            .padding(24)
                        }

                        // ── Decode history ─────────────────────────────────
                        if !vm.decoderHistory.isEmpty {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    Text("Recent Decodes")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(.textMuted)
                                    Spacer()
                                    Button("Clear") { vm.clearDecoderHistory() }
                                        .font(.system(size: 12))
                                        .foregroundColor(.textMuted)
                                }
                                .padding(.horizontal)

                                ForEach(vm.decoderHistory) { entry in
                                    Button {
                                        symbolInput = entry.symbol
                                        decodeIfNeeded()
                                    } label: {
                                        HStack(spacing: 12) {
                                            Image(systemName: "clock.arrow.circlepath")
                                                .font(.system(size: 14))
                                                .foregroundColor(.railBlue)
                                                .frame(width: 28)

                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(entry.symbol)
                                                    .font(.system(size: 14, weight: .semibold, design: .monospaced))
                                                    .foregroundColor(.textPrimary)
                                                Text("\(entry.origin) → \(entry.destination)")
                                                    .font(.system(size: 12))
                                                    .foregroundColor(.textSecondary)
                                                    .lineLimit(1)
                                            }

                                            Spacer()

                                            Text(entry.dateLabel)
                                                .font(.system(size: 11))
                                                .foregroundColor(.textMuted)
                                        }
                                        .padding(12)
                                        .background(Color.bgCard)
                                        .cornerRadius(10)
                                        .overlay(RoundedRectangle(cornerRadius: 10)
                                            .stroke(Color.border, lineWidth: 0.5))
                                    }
                                    .buttonStyle(.plain)
                                    .padding(.horizontal)
                                }
                            }
                        }
                    }
                    // ── Loco Number Lookup ────────────────────────────────────
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Locomotive Number Lookup")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.textPrimary)
                        Text("Type a road number — no photo needed")
                            .font(.system(size: 12)).foregroundColor(.textMuted)

                        HStack(spacing: 10) {
                            TextField("e.g. BNSF 3751, UP 4141", text: $locoInput)
                                .font(.system(size: 15, design: .monospaced))
                                .foregroundColor(.textPrimary)
                                .textInputAutocapitalization(.characters)
                                .autocorrectionDisabled()
                                .padding(12).background(Color.bgInput).cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.borderLight, lineWidth: 0.5))

                            Button {
                                let n = locoInput.trimmingCharacters(in: .whitespaces)
                                guard !n.isEmpty else { return }
                                inputFocused = false
                                vm.lookupLocoNumber(n)
                            } label: {
                                if vm.isLocoNumberLoading {
                                    ProgressView().tint(.white).scaleEffect(0.8)
                                        .frame(width: 60, height: 44)
                                } else {
                                    Text("Lookup").font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(.white).frame(width: 60, height: 44)
                                }
                            }
                            .background(locoInput.isEmpty ? Color.railBlueDark : Color.railBlueMid)
                            .cornerRadius(10)
                            .disabled(locoInput.isEmpty || vm.isLocoNumberLoading)
                        }

                        if let err = vm.locoNumberError {
                            Text(err).font(.system(size: 13)).foregroundColor(.red)
                        }
                        if let result = vm.locoNumberResult {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Image(systemName: "train.side.front.car")
                                        .foregroundColor(.railBlue)
                                    Text("Identified").font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(.railBlue)
                                    Spacer()
                                    Button { vm.locoNumberResult = nil } label: {
                                        Image(systemName: "xmark.circle.fill").foregroundColor(.textMuted)
                                    }
                                }
                                Text(result).font(.system(size: 13)).foregroundColor(.textSecondary)
                                    .lineSpacing(4)
                            }
                            .padding(12).background(Color.bgCard)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.borderLight, lineWidth: 0.5))
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 80)

                    .padding(.vertical)
                }
            }
            .navigationTitle("Decoder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .onTapGesture { inputFocused = false }
        }
    }

    // ── Loco number lookup section appended inside body's ScrollView ──────────
    // (Called from body's VStack via a helper view)

    private func decodeIfNeeded() {
        let s = symbolInput.trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty else { return }
        inputFocused = false
        vm.decodeSymbol(s)
    }
}

struct DecoderResultCard: View {
    let result: TrainSymbolDecodeResult

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header
            HStack {
                Text(result.symbol)
                    .font(.system(size: 22, weight: .bold, design: .monospaced))
                    .foregroundColor(.textPrimary)
                Spacer()
                PriorityBadge(priority: result.priority)
            }
            Text(result.type)
                .font(.system(size: 14))
                .foregroundColor(.textMuted)

            Divider().background(Color.border)

            // Route
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Origin").font(.system(size: 11)).foregroundColor(.textMuted)
                    Text(result.origin).font(.system(size: 14, weight: .medium)).foregroundColor(.textPrimary)
                }
                Spacer()
                Image(systemName: "arrow.right").foregroundColor(.railBlue)
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("Destination").font(.system(size: 11)).foregroundColor(.textMuted)
                    Text(result.destination).font(.system(size: 14, weight: .medium)).foregroundColor(.textPrimary)
                }
            }

            Divider().background(Color.border)

            // Details
            DetailRow(label: "Railroad",  value: result.railroad.displayName)
            DetailRow(label: "Schedule",  value: result.schedule)

            if !result.typicalConsist.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Typical Consist").font(.system(size: 12)).foregroundColor(.textMuted)
                    Text(result.typicalConsist.joined(separator: " · "))
                        .font(.system(size: 13))
                        .foregroundColor(.textSecondary)
                }
            }

            if !result.notes.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Notes").font(.system(size: 12)).foregroundColor(.textMuted)
                    Text(result.notes)
                        .font(.system(size: 13))
                        .foregroundColor(.textSecondary)
                        .lineSpacing(4)
                }
            }
        }
        .padding(16)
        .cardStyle()
    }
}

struct PriorityBadge: View {
    let priority: String
    var color: Color {
        switch priority.lowercased() {
        case "high":   return .orange
        case "medium": return .railBlue
        default:       return .textMuted
        }
    }
    var body: some View {
        Text(priority)
            .font(.system(size: 11, weight: .semibold))
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.15))
            .cornerRadius(6)
    }
}
