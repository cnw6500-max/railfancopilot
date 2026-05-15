import SwiftUI

struct CommunityView: View {
    @ObservedObject var vm: RailFanViewModel

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                VStack(spacing: 24) {
                    Spacer()

                    Image(systemName: "person.3.sequence.fill")
                        .font(.system(size: 56))
                        .foregroundColor(.railBlueDark)

                    VStack(spacing: 8) {
                        Text("Community Reports")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.textPrimary)
                        Text("Share train sightings, track conditions, and event alerts with nearby railfans.")
                            .font(.system(size: 14))
                            .foregroundColor(.textMuted)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }

                    // Coming soon card
                    VStack(spacing: 12) {
                        HStack(spacing: 10) {
                            Image(systemName: "clock.badge")
                                .foregroundColor(.railBlue)
                                .font(.system(size: 18))
                            Text("Coming Soon")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.textPrimary)
                        }
                        Text("Community features are in active development. Share sightings, get train alerts, and connect with local railfans.")
                            .font(.system(size: 13))
                            .foregroundColor(.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(20)
                    .cardStyle()
                    .padding(.horizontal, 24)

                    Spacer()
                }
            }
            .navigationTitle("Community")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}
