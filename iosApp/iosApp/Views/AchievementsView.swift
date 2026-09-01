import SwiftUI

struct AchievementsView: View {
    let achievements: [AchievementRecord]

    var earnedCount: Int { achievements.filter { $0.earned }.count }

    var body: some View {
        ZStack {
            Color.bgPrimary.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 16) {

                    // Progress header
                    VStack(spacing: 8) {
                        Text("\(earnedCount) / \(achievements.count)")
                            .font(.system(size: 36, weight: .bold)).foregroundColor(.railBlue)
                        Text("Achievements Unlocked")
                            .font(.system(size: 14)).foregroundColor(.textMuted)
                        ProgressView(value: Double(earnedCount), total: Double(max(achievements.count, 1)))
                            .tint(.railBlue).padding(.horizontal, 40).padding(.top, 4)
                    }
                    .padding(.vertical, 20).frame(maxWidth: .infinity)
                    .background(Color.bgCard).cornerRadius(16).padding(.horizontal)

                    // Achievement grid
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        ForEach(achievements) { a in
                            AchievementCell(
                                icon: a.emoji, title: a.title,
                                description: a.description, earned: a.earned,
                                earnedDate: a.earnedDate
                            )
                        }
                    }
                    .padding(.horizontal)

                    Spacer(minLength: 40)
                }
                .padding(.top)
            }
        }
        .navigationTitle("Achievements")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.bgPrimary, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
    }
}

struct AchievementCell: View {
    let icon: String; let title: String; let description: String
    let earned: Bool; let earnedDate: Date?

    var body: some View {
        VStack(spacing: 8) {
            Text(icon).font(.system(size: 36)).opacity(earned ? 1.0 : 0.3)

            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(earned ? .textPrimary : .textMuted)
                .multilineTextAlignment(.center)

            Text(description)
                .font(.system(size: 11)).foregroundColor(.textMuted)
                .multilineTextAlignment(.center).lineLimit(2)

            if earned {
                VStack(spacing: 2) {
                    Text("Earned")
                        .font(.system(size: 10, weight: .medium)).foregroundColor(.railBlue)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(Color.railBlue.opacity(0.15)).cornerRadius(6)
                    if let date = earnedDate {
                        Text(date, style: .date)
                            .font(.system(size: 9)).foregroundColor(.textMuted)
                    }
                }
            } else {
                Text("Locked")
                    .font(.system(size: 10)).foregroundColor(.textMuted)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Color.bgInput).cornerRadius(6)
            }
        }
        .padding(14).frame(maxWidth: .infinity)
        .background(Color.bgCard).cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14)
            .stroke(earned ? Color.railBlue.opacity(0.4) : Color.border, lineWidth: 0.5))
    }
}
