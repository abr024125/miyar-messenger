import SwiftUI

@main
@MainActor
struct MiyarMessengerApp: App {
    @StateObject private var store = ChatStore()
    @AppStorage("messenger.appearance") private var appearance = "system"
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(store)
                .environment(\.layoutDirection, .rightToLeft)
                .environment(\.locale, Locale(identifier: "ar_JO"))
                .font(MiyarTheme.font())
                .tint(MiyarTheme.accent)
                .preferredColorScheme(appearance == "dark" ? .dark : appearance == "light" ? .light : nil)
        }
    }
}
