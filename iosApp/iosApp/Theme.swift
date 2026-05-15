import SwiftUI

// Matches the Android dark theme exactly
extension Color {
    static let bgPrimary    = Color(red: 0.051, green: 0.051, blue: 0.110) // #0D0D1C
    static let bgCard       = Color(red: 0.090, green: 0.090, blue: 0.157) // #171728
    static let bgInput      = Color(red: 0.118, green: 0.118, blue: 0.196) // #1E1E32
    static let railBlue     = Color(red: 0.129, green: 0.588, blue: 0.953) // #2196F3
    static let railBlueDark = Color(red: 0.039, green: 0.176, blue: 0.353) // #0A2D5A
    static let railBlueMid  = Color(red: 0.082, green: 0.349, blue: 0.647) // #155995
    static let textPrimary  = Color(red: 0.941, green: 0.941, blue: 0.961) // #F0F0F5
    static let textSecondary = Color(red: 0.698, green: 0.698, blue: 0.773) // #B2B2C5
    static let textMuted    = Color(red: 0.435, green: 0.435, blue: 0.537) // #6F6F89
    static let border       = Color(red: 0.176, green: 0.176, blue: 0.275) // #2D2D46
    static let borderLight  = Color(red: 0.216, green: 0.216, blue: 0.314) // #373750
}

// Reusable card modifier
struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Color.bgCard)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.border, lineWidth: 0.5))
    }
}

extension View {
    func cardStyle() -> some View { modifier(CardStyle()) }
}

struct SectionHeader: View {
    let title: String
    var body: some View {
        Text(title)
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(.textMuted)
            .textCase(.uppercase)
            .tracking(0.8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.top, 20)
            .padding(.bottom, 6)
    }
}
