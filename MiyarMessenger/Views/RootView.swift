import SwiftUI

@MainActor
struct RootView: View {
    @EnvironmentObject private var store: ChatStore
    var body: some View {
        Group {
            if !store.ready { ProgressView("مِعيار").task { await store.restore() } }
            else if store.me == nil || store.needsPasswordChange { LoginView() }
            else {
                TabView {
                    InboxView().tabItem { Label("المحادثات", systemImage: "bubble.left.and.bubble.right.fill") }.badge(store.conversations.reduce(0) { $0 + $1.unread })
                    PeopleView().tabItem { Label("الأشخاص", systemImage: "person.2.fill") }
                    SettingsView().tabItem { Label("حسابي", systemImage: "person.crop.circle") }
                }.id(store.branchId)
            }
        }
    }
}
