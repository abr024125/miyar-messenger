import SwiftUI

@MainActor
struct PeopleView: View {
    @EnvironmentObject private var store: ChatStore
    @State private var query = ""
    @State private var contacts: [Person] = []
    @State private var onlineOnly = false
    @State private var page = 1
    @State private var hasMore = false
    @State private var error: String?
    @State private var conversation: ChatConversation?
    var body: some View {
        NavigationStack {
            List {
                Picker("العرض", selection: $onlineOnly) { Text("الجميع").tag(false); Text("متصلون").tag(true) }.pickerStyle(.segmented).listRowSeparator(.hidden)
                ForEach(contacts.filter { !onlineOnly || $0.online == true }) { person in
                    Button { Task { do { conversation = try await store.direct(person) } catch { self.error = error.localizedDescription } } } label: {
                        HStack(spacing: 12) { AvatarView(name: person.name, size: 52, online: person.online == true); VStack(alignment: .leading) { Text(person.name).font(MiyarTheme.font(16, weight: .semibold)).foregroundStyle(.primary); Text(person.roleName).font(MiyarTheme.font(13)).foregroundStyle(.secondary) }; Spacer() }
                    }.listRowSeparator(.hidden)
                }
                if hasMore { Button("عرض المزيد") { Task { await load(more: true) } } }
                if let error { InlineError(text: error) }
                if contacts.isEmpty { ContentUnavailableView("الأشخاص", systemImage: "person.2", description: Text("تظهر هنا الحسابات المتاحة للتواصل ضمن فرعك وإسناداتك.")).listRowSeparator(.hidden) }
            }.listStyle(.plain).navigationTitle("الأشخاص").searchable(text: $query, prompt: "البحث عن شخص")
                .task(id: query) { do { try await Task.sleep(for: .milliseconds(250)); await load() } catch {} }
                .refreshable { await load() }
                .navigationDestination(item: $conversation) { ConversationView(initial: $0) }
        }
    }
    private func load(more: Bool = false) async {
        let requested = query, target = more ? page + 1 : 1
        do { let result = try await store.contacts(query: requested, page: target); guard requested == query && !Task.isCancelled else { return }; contacts = more ? contacts + result.contacts : result.contacts; page = target; hasMore = result.hasMore }
        catch { if !Task.isCancelled { self.error = error.localizedDescription } }
    }
}
