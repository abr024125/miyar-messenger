import SwiftUI

@MainActor
struct ComposeView: View {
    @EnvironmentObject private var store: ChatStore
    @Environment(\.dismiss) private var dismiss
    var onOpen: (ChatConversation) -> Void
    @State private var query = ""
    @State private var people: [Person] = []
    @State private var page = 1
    @State private var hasMore = false
    @State private var group = false
    @State private var title = ""
    @State private var selected: Set<String> = []
    @State private var busy = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            List {
                if store.me?.role != "student" {
                    Section {
                        if group { TextField("اسم المجموعة", text: $title) }
                        else { Button { group = true } label: { Label("إنشاء مجموعة جديدة", systemImage: "person.3.fill") }.foregroundStyle(.primary) }
                    }
                }
                Section("الأشخاص") {
                    ForEach(people) { person in
                        Button { group ? toggle(person.id) : start(person) } label: {
                            HStack(spacing: 12) { AvatarView(name: person.name, size: 42, online: person.online == true); VStack(alignment: .leading, spacing: 2) { Text(person.name).foregroundStyle(.primary).font(MiyarTheme.font(16, weight: .semibold)); Text(person.roleName).font(MiyarTheme.font(12, relativeTo: .caption)).foregroundStyle(.secondary) }; Spacer(); if group { Image(systemName: selected.contains(person.id) ? "checkmark.circle.fill" : "circle").foregroundStyle(selected.contains(person.id) ? MiyarTheme.accent : .secondary) } }
                        }.disabled(busy)
                    }
                    if hasMore { Button("عرض المزيد") { Task { await load(more: true) } } }
                    if people.isEmpty && !busy { Text("لا توجد جهات اتصال مطابقة").foregroundStyle(.secondary) }
                }
                if let error { InlineError(text: error) }
            }.listStyle(.plain).searchable(text: $query, prompt: "الاسم")
                .navigationTitle(group ? "مجموعة جديدة" : "رسالة جديدة").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("إلغاء", systemImage: "xmark") { dismiss() } }
                    if group { ToolbarItem(placement: .confirmationAction) { Button { createGroup() } label: { if busy { ProgressView() } else { Image(systemName: "checkmark") } }.buttonStyle(.borderedProminent).disabled(title.trimmingCharacters(in: .whitespaces).isEmpty || selected.isEmpty || busy).accessibilityLabel("إنشاء المجموعة") } }
                }
                .task(id: query) { do { try await Task.sleep(for: .milliseconds(250)); await load() } catch {} }
        }.presentationDragIndicator(.visible)
    }
    private func toggle(_ id: String) { if selected.contains(id) { selected.remove(id) } else if selected.count < 31 { selected.insert(id) } }
    private func load(more: Bool = false) async {
        let requested = query; let target = more ? page + 1 : 1
        do { let result = try await store.contacts(query: requested, page: target); guard !Task.isCancelled && query == requested else { return }; people = more ? people + result.contacts : result.contacts; page = target; hasMore = result.hasMore }
        catch { if !Task.isCancelled { self.error = error.localizedDescription } }
    }
    private func start(_ person: Person) { busy = true; Task { defer { busy = false }; do { let chat = try await store.direct(person); dismiss(); onOpen(chat) } catch { self.error = error.localizedDescription } } }
    private func createGroup() { busy = true; Task { defer { busy = false }; do { let chat = try await store.group(title: title, people: selected); dismiss(); onOpen(chat) } catch { self.error = error.localizedDescription } } }
}
