import SwiftUI

@MainActor
struct SettingsView: View {
    @EnvironmentObject private var store: ChatStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.isPresented) private var isPresented
    @AppStorage("messenger.appearance") private var appearance = "system"
    @AppStorage("messenger.haptics") private var haptics = true
    @State private var showLogout = false
    @State private var busy = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(spacing: 12) {
                        AvatarView(name: store.me?.name ?? "مِعيار", size: 104)
                        Text(store.me?.name ?? "").font(MiyarTheme.font(24, weight: .bold, relativeTo: .title2))
                        Text(store.me?.branchName ?? "").font(MiyarTheme.font(14)).foregroundStyle(.secondary)
                    }.frame(maxWidth: .infinity).padding(.vertical, 24)
                }.listRowSeparator(.hidden)
                Section {
                    Picker(selection: $appearance) { Text("حسب الجهاز").tag("system"); Text("فاتح").tag("light"); Text("داكن").tag("dark") } label: { Label("المظهر", systemImage: "moon.fill") }
                    Toggle(isOn: Binding(get: { store.showOnline }, set: { value in
                        busy = true
                        Task { defer { busy = false }; do { try await store.action("profile", values: ["showOnline": value]); store.showOnline = value } catch { self.error = error.localizedDescription } }
                    })) { Label("إظهار حالة الاتصال", systemImage: "circle.fill") }.disabled(busy)
                    LabeledContent { Text(store.me?.id.split(separator: ":").last.map(String.init) ?? "").foregroundStyle(.secondary) } label: { Label("رقم الحساب", systemImage: "at") }
                }
                Section("التفضيلات") {
                    Toggle(isOn: $haptics) { Label("اهتزاز عند الإرسال", systemImage: "iphone.radiowaves.left.and.right") }
                    if let me = store.me, me.branches.count > 1 {
                        Picker(selection: Binding(get: { store.branchId ?? me.branchId }, set: { id in Task { await store.selectBranch(id) } })) { ForEach(me.branches) { branch in Text(branch.name + (branch.status == "active" ? "" : " · للقراءة")).tag(branch.id) } } label: { Label("الفرع", systemImage: "building.2") }
                    }
                    Link(destination: URL(string: "https://deanju.tech")!) { Label("منصة معيار", systemImage: "arrow.up.right.square") }
                }
                Section {
                    Text("يستخدم التطبيق حساب معيار وصلاحيات فرعك. تُتاح المرفقات لأعضاء المحادثة فقط، ويُحفظ تسجيل الدخول في سلسلة مفاتيح الآيفون.").font(MiyarTheme.font(13, relativeTo: .footnote)).foregroundStyle(.secondary)
                    Button("تسجيل الخروج", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) { showLogout = true }
                }
                if let error { InlineError(text: error) }
            }.listStyle(.plain).navigationTitle("حسابي").navigationBarTitleDisplayMode(.inline)
                .toolbar { if isPresented { ToolbarItem(placement: .confirmationAction) { Button("تم", systemImage: "checkmark") { dismiss() } } } }
                .confirmationDialog("تسجيل الخروج من هذا الجهاز؟", isPresented: $showLogout, titleVisibility: .visible) { Button("تسجيل الخروج", role: .destructive) { Task { await store.logout() } } }
        }
    }
}
