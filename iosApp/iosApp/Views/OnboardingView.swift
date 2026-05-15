import SwiftUI

struct OnboardingView: View {
    let onFinish: () -> Void
    @State private var page = 0

    private let pages: [(icon: String, title: String, body: String)] = [
        ("train.side.front.car",
         "Welcome to\nRailfan Copilot",
         "Your all-in-one companion for tracking trains, decoding symbols, and discovering rail history."),
        ("map.fill",
         "Live Train Map",
         "See Amtrak, Metra, MBTA, LIRR, Metro-North, SEPTA, Caltrain, and freight trains plotted in real time nationwide."),
        ("cpu",
         "AI Decoder & Identifier",
         "Decode any train symbol to learn its route and consist. Photograph a locomotive for instant AI identification."),
        ("antenna.radiowaves.left.and.right",
         "Scanner & Frequencies",
         "Browse AAR radio frequencies and tune in to live railroad scanner streams from RailroadRadio.net."),
        ("location.fill",
         "Location & Privacy",
         "Railfan Copilot uses your location to show nearby trains and sort scanner feeds by distance.\n\nYour location is never stored or shared."),
    ]

    var body: some View {
        ZStack {
            Color.bgPrimary.ignoresSafeArea()

            VStack(spacing: 0) {
                TabView(selection: $page) {
                    ForEach(pages.indices, id: \.self) { i in
                        OnboardingPage(page: pages[i])
                            .tag(i)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .always))
                .indexViewStyle(.page(backgroundDisplayMode: .always))

                // Buttons
                VStack(spacing: 12) {
                    if page == pages.count - 1 {
                        Button(action: onFinish) {
                            Text("Get Started")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(Color.railBlueMid)
                                .cornerRadius(14)
                        }
                    } else {
                        Button { withAnimation { page += 1 } } label: {
                            Text("Next")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(Color.railBlueMid)
                                .cornerRadius(14)
                        }
                        Button { onFinish() } label: {
                            Text("Skip")
                                .font(.system(size: 15))
                                .foregroundColor(.textMuted)
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 40)
            }
        }
    }
}

struct OnboardingPage: View {
    let page: (icon: String, title: String, body: String)
    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: page.icon)
                .font(.system(size: 72))
                .foregroundColor(.railBlue)
                .shadow(color: Color.railBlue.opacity(0.4), radius: 20)

            Text(page.title)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.textPrimary)
                .multilineTextAlignment(.center)

            Text(page.body)
                .font(.system(size: 16))
                .foregroundColor(.textSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(5)
                .padding(.horizontal, 32)

            Spacer()
            Spacer()
        }
    }
}
