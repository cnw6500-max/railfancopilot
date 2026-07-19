import SwiftUI
import StoreKit

struct UpgradeView: View {
    @ObservedObject var vm: RailFanViewModel
    @StateObject private var store = StoreManager.shared
    @Environment(\.dismiss) var dismiss

    private let freeFeatures: [(icon: String, title: String)] = [
        ("map.fill",                          "Live Train Map"),
        ("antenna.radiowaves.left.and.right", "Railroad Scanner Feeds"),
        ("book.fill",                         "Locomotive Encyclopedia"),
        ("trophy.fill",                       "Achievements"),
    ]

    private let proFeatures: [(icon: String, title: String, desc: String)] = [
        ("sparkles",         "AI Loco Identifier",     "Photograph any locomotive for instant model, railroad, and era identification."),
        ("list.bullet.rectangle", "Consist Analyzer",  "Identify locomotive numbers, car types, and hazmat placards from a photo."),
        ("person.3.fill",    "Community Reports",      "Share sightings and browse real-time reports from nearby railfans."),
        ("bookmark.fill",    "Unlimited Saved Spots",  "Save unlimited railfan locations. Free tier includes 3."),
        ("bell.badge.fill",  "Approach Notifications", "Get alerted when trains approach your saved locations."),
        ("sun.max.fill",     "Sun Angle Predictor",    "Know the golden hour window for perfect rail photography."),
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
                            .shadow(color: Color.yellow.opacity(0.4), radius: 16)
                        Text("Railfan Copilot Pro")
                            .font(.system(size: 26, weight: .bold))
                            .foregroundColor(.textPrimary)
                        Text("Everything a serious railfan needs.")
                            .font(.system(size: 15))
                            .foregroundColor(.textMuted)
                    }
                    .padding(.top, 32)

                    // Free vs Pro comparison
                    HStack(alignment: .top, spacing: 12) {
                        // Free column
                        VStack(alignment: .leading, spacing: 0) {
                            Text("FREE")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.textMuted)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                            Divider().background(Color.border)
                            ForEach(freeFeatures, id: \.title) { f in
                                HStack(spacing: 8) {
                                    Image(systemName: f.icon)
                                        .foregroundColor(.railBlue)
                                        .font(.system(size: 13))
                                        .frame(width: 18)
                                    Text(f.title)
                                        .font(.system(size: 12))
                                        .foregroundColor(.textSecondary)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                Divider().background(Color.border)
                            }
                        }
                        .background(Color.bgCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.border, lineWidth: 0.5))

                        // Pro column
                        VStack(alignment: .leading, spacing: 0) {
                            Text("PRO")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.yellow)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                            Divider().background(Color.border)
                            ForEach(proFeatures, id: \.title) { f in
                                HStack(spacing: 8) {
                                    Image(systemName: f.icon)
                                        .foregroundColor(.yellow)
                                        .font(.system(size: 13))
                                        .frame(width: 18)
                                    Text(f.title)
                                        .font(.system(size: 12))
                                        .foregroundColor(.textSecondary)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                Divider().background(Color.border)
                            }
                        }
                        .background(Color.bgCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.yellow.opacity(0.5), lineWidth: 1))
                    }
                    .padding(.horizontal)

                    if vm.isPurchased {
                        // Already purchased permanently
                        VStack(spacing: 8) {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.system(size: 48))
                                .foregroundColor(.green)
                            Text("Pro Active")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.textPrimary)
                            Text("Thank you for supporting Railfan Copilot!")
                                .font(.system(size: 14))
                                .foregroundColor(.textMuted)
                        }
                        .padding(24)
                        .frame(maxWidth: .infinity)
                        .background(Color.bgCard)
                        .cornerRadius(16)
                        .padding(.horizontal)

                    } else {
                        // Trial banner
                        if vm.isInTrial {
                            HStack(spacing: 12) {
                                Image(systemName: "timer")
                                    .foregroundColor(.green)
                                    .font(.system(size: 18))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Free trial active")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(.green)
                                    Text(vm.trialDaysLeft > 1
                                         ? "\(vm.trialDaysLeft) days remaining"
                                         : vm.trialDaysLeft == 1
                                         ? "Last day — expires tomorrow"
                                         : "Expires today")
                                        .font(.system(size: 12))
                                        .foregroundColor(.textMuted)
                                }
                                Spacer()
                            }
                            .padding(14)
                            .background(Color.green.opacity(0.12))
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.green.opacity(0.3), lineWidth: 0.5))
                            .padding(.horizontal)
                        }

                        // Purchase section
                        VStack(spacing: 12) {

                            // Price badge
                            HStack {
                                Spacer()
                                VStack(spacing: 2) {
                                    Text(store.formattedPrice)
                                        .font(.system(size: 32, weight: .bold))
                                        .foregroundColor(.textPrimary)
                                    Text("One-time purchase · No subscription")
                                        .font(.system(size: 12))
                                        .foregroundColor(.textMuted)
                                }
                                Spacer()
                            }

                            // Error message
                            if let err = store.purchaseError {
                                Text(err)
                                    .font(.system(size: 13))
                                    .foregroundColor(.orange)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal)
                            }

                            // Buy button
                            Button {
                                Task {
                                    let success = await store.purchase()
                                    if success { vm.unlockPremium() }
                                }
                            } label: {
                                Group {
                                    if store.isPurchasing {
                                        ProgressView().tint(.white)
                                    } else {
                                        HStack(spacing: 8) {
                                            Image(systemName: "star.fill")
                                                .foregroundColor(.yellow)
                                            Text(vm.isInTrial
                                                 ? "Unlock Pro Permanently — \(store.formattedPrice)"
                                                 : "Upgrade to Pro — \(store.formattedPrice)")
                                                .font(.system(size: 16, weight: .bold))
                                        }
                                    }
                                }
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(
                                    LinearGradient(colors: [Color.railBlueMid, Color.railBlue],
                                                   startPoint: .leading, endPoint: .trailing)
                                )
                                .cornerRadius(14)
                            }
                            .disabled(store.isPurchasing)

                            // Restore button
                            Button {
                                Task {
                                    let restored = await store.restorePurchases()
                                    if restored { vm.unlockPremium() }
                                }
                            } label: {
                                Text("Restore Purchases")
                                    .font(.system(size: 14))
                                    .foregroundColor(.railBlue)
                            }

                            Text("Payment will be charged to your Apple ID account at confirmation of purchase.")
                                .font(.system(size: 11))
                                .foregroundColor(.textMuted)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal)
                        }
                        .padding(.horizontal)
                    }

                    Spacer(minLength: 40)
                }
            }
        }
        .navigationTitle("Upgrade to Pro")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.bgPrimary, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .task {
            await store.loadProducts()
            // Check if already purchased
            if await store.checkEntitlement() {
                vm.unlockPremium()
            }
        }
    }
}
