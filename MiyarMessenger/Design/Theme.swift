import SwiftUI

enum MiyarTheme {
    static let accent = Color("AccentColor")
    static let received = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.18, alpha: 1) : UIColor(white: 0.94, alpha: 1) })
    static let surface = Color(.systemBackground)
    static let soft = Color(.secondarySystemBackground)
    static func font(_ size: CGFloat = 17, weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom(weight == .bold ? "Cairo-Bold" : weight == .semibold ? "Cairo-SemiBold" : "Cairo-Regular", size: size, relativeTo: style)
    }
}
extension Date {
    var messageTime: String { formatted(.dateTime.hour().minute().locale(Locale(identifier: "ar_JO"))) }
}
