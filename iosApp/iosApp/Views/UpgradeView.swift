import SwiftUI
import StoreKit

struct UpgradeView: View {
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss
    @State private var isPurchasing = false

    private let features: [(icon: String, title: String, desc: String)] = [
        ("cpu",                   "AI Symbol Decoder",       "Decode any freight or passenger train symbol with detailed route and consist info."),
        ("sparkles",              "AI Loco Identifier",      "Photograph any locomotive and get instant model, railroad, and feature identification."),
        ("person.3.fill",         "Community Reports",       "Share sightings and get real-time alerts from nearby railfans."),
        ("bookmark.fill",         "Saved Locations",         "Save unlimited railfan spots with proximity alerts."),
        ("arrow.triangle.2.circlepath", "Live Updates",      "Auto-refresh trains every 60 seconds."),
        ("sun.max.fill",          "Golden Hour Calculator",  "Know the best light times for photography at any location."),
    ]

    var body: some View {
        ZStack {
            Color.bgPrimary.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 24) {
                    // Hero
                    VStack(spacing: 10) {
                        Image(systemName: "star.circle.fill")
                            .font(.system(size: 64))
                            .foregroundColor(.yellow)
                        Text("Railfan Copilot Premium")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.textPrimary)
                        Text("Everything a serious railfan needs in one app.")
                            .font(.system(size: 15))
                            .foregroundColor(.textMuted)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 32)

                    // Features
                    VStack(spacing: 2) {
                        ForEach(features, id: \.title) { feat in
                            HStack(alignment: .top, spacing: 14) {
                                Image(systemName: feat.icon)
                                    .font(.system(size: 20))
                                    .foregroundColor(.railBlue)
                                    .frame(width: 28)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(feat.title)
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.textPrimary)
                                    Text(feat.desc)
                                        .font(.system(size: 13))
                                        .foregroundColor(.textSecondary)
                                        .lineSpacing(3)
                                }
                            }
                            .padding(.vertical, 12)
                            .padding(.horizontal, 16)
                            if feat.title != features.last?.title {
                                Divider().background(Color.border).padding(.leading, 58)
                            }
                        }
                    }
                    .background(Color.bgCard)
                    .cornerRadius(14)
                    .overlay(RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.border, lineWidth: 0.5))
                    .padding(.horizontal)

                    if vm.isPremium {
                        // Already premium
                        VStack(spacing: 8) {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.system(size: 36))
                                .foregroundColor(.green)
                            Text("Premium Active")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.textPrimary)
                            Text("Thank you for supporting Railfan Copilot!")
                                .font(.system(size: 14))
                                .foregroundColor(.textMuted)
                        }
                        .padding(24)
                    } else {
                        // Purchase
                        VStack(spacing: 12) {
                            Button {
                                purchase()
                            } label: {
                                Group {
                                    if isPurchasing {
                                        ProgressView().tint(.white)
                                    } else {
                                        Text("Upgrade — $4.99")
                                            .font(.system(size: 17, weight: .bold))
                                    }
                                }
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(Color.railBlueMid)
                                .cornerRadius(14)
                            }
                            .disabled(isPurchasing)

                            Button { restorePurchases() } label: {
                                Text("Restore Purchases")
                                    .font(.system(size: 14))
                                    .foregroundColor(.railBlue)
                            }

                            Text("One-time purchase. No subscription.")
                                .font(.system(size: 12))
                                .foregroundColor(.textMuted)
                        }
                        .padding(.horizontal)
                    }

                    Spacer(minLength: 40)
                }
            }
        }
        .navigationTitle("Upgrade")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.bgPrimary, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
    }

    private func purchase() {
        isPurchasing = true
        // TODO: integrate StoreKit 2 with your product ID
        // For now, simulate unlock for testing
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            vm.unlockPremium()
            isPurchasing = false
        }
    }

    private func restorePurchases() {
        Task {
            try? await AppStore.sync()
            // Re-check entitlements
        }
    }
}
