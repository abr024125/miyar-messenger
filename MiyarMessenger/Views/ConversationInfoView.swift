import SwiftUI

@MainActor
struct ConversationInfoView: View {
    @EnvironmentObject private var store: ChatStore
    @Environment(\.dismiss) private var dismiss
    let conversation: ChatConversation
    let onChanged: () -> Void
    let onSearch: () -> Void
    let onLeave: () -> Void
    @State private var title = ""
    @State private var busy = false
    @State private var error: String?
    @State private var confirmBlock = false
    @State private var confirmLeave = false
    @State private var group = false
    var body: some View {
        NavigationStack {
            List {
                VStack(spacing: 12) {
                    AvatarView(name: conversation.title, size: 100, group: conversation.kind == "group")
                    Text(conversation.title).font(MiyarTheme.font(24, weight: .bold, relativeTo: .title2)).multilineTextAlignment(.center)
                    Text(conversation.kind == "group" ? "\(conversation.participants.count) أعضاء" : "مِعيار").font(MiyarTheme.font(14)).foregroundStyle(.secondary)
                }.frame(maxWidth: .infinity).padding(.vertical, 18).listRowSeparator(.hidden)
                Section("المحادثة") {
                    Button { dismiss(); onSearch() } label: { Label("بحث في المحادثة", systemImage: "magnifyingglass") }
                    Button { update("preferences", ["muted": !conversation.muted]) } label: { Label(conversation.muted ? "إلغاء كتم المحادثة" : "كتم المحادثة", systemImage: conversation.muted ? "bell" : "bell.slash") }
                    Button { update("preferences", ["archived": !conversation.archived]) } label: { Label(conversation.archived ? "إلغاء الأرشفة" : "أرشفة المحادثة", systemImage: "archivebox") }
                    if conversation.kind == "group" && conversation.createdBy == store.me?.id {
                        TextField("اسم المجموعة", text: $title)
                        Button("حفظ الاسم") { update("rename", ["title": title]) }.disabled(title.trimmingCharacters(in: .whitespaces).isEmpty || title == conversation.title)
                    }
                }
                Section("الأعضاء") {
                    ForEach(conversation.participants) { person in HStack(spacing: 10) { AvatarView(name: person.name, size: 36); Text(person.name); Spacer(); Text(person.roleName).font(MiyarTheme.font(12)).foregroundStyle(.secondary) } }
                }
                if conversation.kind == "direct", let peer = conversation.peer(excluding: store.me?.id ?? "") {
                    Section("الخصوصية") {
                        Button("حظر الحساب", systemImage: "hand.raised", role: .destructive) { confirmBlock = true }
                        if !conversation.canSend { Button("إلغاء الحظر من جهتي") { update("block", ["participantId": peer.id, "blocked": false]) } }
                    }
                } else if conversation.kind == "group" { Section { Button("مغادرة المجموعة", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) { confirmLeave = true } } }
                if let error { InlineError(text: error) }
            }.listStyle(.plain).disabled(busy).navigationTitle("معلومات المحادثة").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("تم", systemImage: "checkmark") { dismiss() } } }
                .onAppear { title = conversation.title }
                .confirmationDialog("حظر هذا الحساب وإيقاف المحادثات الفردية معه؟", isPresented: $confirmBlock, titleVisibility: .visible) { Button("حظر", role: .destructive) { if let peer = conversation.peer(excluding: store.me?.id ?? "") { update("block", ["participantId": peer.id, "blocked": true]) } } }
                .confirmationDialog("مغادرة المجموعة؟", isPresented: $confirmLeave, titleVisibility: .visible) { Button("مغادرة", role: .destructive) { update("leave", [:]) } }
        }
    }
    private func update(_ action: String, _ values: [String: Any]) {
        busy = true
        Task { defer { busy = false }; do { try await store.action(action, conversation: conversation.id, values: values); try await store.refresh(); if action == "leave" { onLeave() } else { onChanged() }; dismiss() } catch { self.error = error.localizedDescription } }
    }
}
@MainActor
struct ConversationSearchView: View {
    @EnvironmentObject private var store: ChatStore
    @Environment(\.dismiss) private var dismiss
    let conversation: String
    let onSelect: (ChatMessage) -> Void
    @State private var query = ""
    @State private var found: [ChatMessage] = []
    @State private var error: String?
    var body: some View {
        NavigationStack {
            List {
                ForEach(found) { message in Button { onSelect(message); dismiss() } label: { VStack(alignment: .leading, spacing: 5) { Text(message.senderName).font(MiyarTheme.font(13, weight: .semibold)); Text(message.text).foregroundStyle(.primary); Text(message.date, style: .date).font(.caption).foregroundStyle(.secondary) } } }
                if !query.isEmpty && found.isEmpty { ContentUnavailableView.search(text: query) }
                if let error { InlineError(text: error) }
            }.listStyle(.plain).navigationTitle("بحث في المحادثة").navigationBarTitleDisplayMode(.inline).searchable(text: $query, prompt: "كلمة من الرسالة")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("إغلاق", systemImage: "xmark") { dismiss() } } }
                .task(id: query) {
                    guard !query.trimmingCharacters(in: .whitespaces).isEmpty, let branch = store.branchId else { found = []; return }
                    let requested = query
                    do { try await Task.sleep(for: .milliseconds(300)); let result: ThreadResponse = try await store.api.get(query: ["conversation": conversation, "q": requested, "branch": String(branch)]); guard !Task.isCancelled && requested == query else { return }; found = result.messages }
                    catch { if !Task.isCancelled { self.error = error.localizedDescription } }
                }
        }
    }
}
