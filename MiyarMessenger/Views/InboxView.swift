import SwiftUI

@MainActor
struct InboxView: View {
    @EnvironmentObject private var store: ChatStore
    @Environment(\.scenePhase) private var phase
    @State private var search = ""
    @State private var compose = false
    @State private var settings = false
    @State private var conversation: ChatConversation?
    @State private var people: [Person] = []
    @State private var camera = false
    @State private var pendingImage: Data?
    private var filtered: [ChatConversation] { store.conversations.filter { search.isEmpty || $0.title.localizedStandardContains(search) || ($0.lastMessage?.text.localizedStandardContains(search) ?? false) } }
    var body: some View {
        NavigationStack {
            List {
                if !store.archived && search.isEmpty && !people.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        LazyHStack(spacing: 20) {
                            Button { compose = true } label: {
                                VStack(spacing: 7) { Image(systemName: "plus").font(.title2).frame(width: 52, height: 52).background(MiyarTheme.soft, in: Circle()); Text("رسالة جديدة").font(MiyarTheme.font(11, relativeTo: .caption)) }
                            }.buttonStyle(.plain)
                            ForEach(people.prefix(12)) { person in
                                Button { Task { await open(person) } } label: {
                                    VStack(spacing: 7) { AvatarView(name: person.name, online: person.online == true); Text(person.name.split(separator: " ").first.map(String.init) ?? person.name).font(MiyarTheme.font(12, relativeTo: .caption)).lineLimit(1) }
                                }.buttonStyle(.plain)
                            }
                        }.padding(.vertical, 8)
                    }.listRowSeparator(.hidden).listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                }
                if let error = store.error { InlineError(text: error).listRowSeparator(.hidden) }
                ForEach(filtered) { chat in
                    Button { pendingImage = nil; conversation = chat } label: { ConversationRow(conversation: chat, myId: store.me?.id ?? "") }.buttonStyle(.plain)
                        .listRowSeparator(.hidden)
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button { Task { await preference(chat, values: ["archived": !chat.archived]) } } label: { Label(chat.archived ? "إلغاء الأرشفة" : "أرشفة", systemImage: "archivebox") }.tint(.indigo)
                            Button { Task { await preference(chat, values: ["muted": !chat.muted]) } } label: { Label(chat.muted ? "إلغاء الكتم" : "كتم", systemImage: chat.muted ? "bell" : "bell.slash") }.tint(.gray)
                        }
                }
                if filtered.isEmpty {
                    ContentUnavailableView(search.isEmpty ? store.archived ? "لا توجد محادثات مؤرشفة" : "محادثاتك تبدأ هنا" : "لا توجد نتائج", systemImage: "bubble.left.and.bubble.right", description: Text(search.isEmpty ? "ابدئي رسالة مع إحدى جهات الاتصال المتاحة لكِ." : "جرّبي اسمًا آخر.")).listRowSeparator(.hidden)
                }
            }.listStyle(.plain)
                .searchable(text: $search, prompt: "بحث في المحادثات")
                .navigationTitle(store.archived ? "المؤرشفة" : "المحادثات")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) { Button { settings = true } label: { AvatarView(name: store.me?.name ?? "معيار", size: 36) }.accessibilityLabel("حسابي") }
                    ToolbarItemGroup(placement: .topBarTrailing) {
                        Button { camera = true } label: { Image(systemName: "camera.fill") }.accessibilityLabel("التقاط صورة وإرسالها")
                        Button { pendingImage = nil; compose = true } label: { Image(systemName: "square.and.pencil") }.accessibilityLabel("رسالة جديدة")
                        Menu { Button(store.archived ? "المحادثات" : "المؤرشفة", systemImage: "archivebox") { store.archived.toggle(); Task { do { try await store.refresh() } catch { store.handle(error) } } } } label: { Image(systemName: "ellipsis") }.accessibilityLabel("خيارات المحادثات")
                    }
                }
                .refreshable { do { try await store.refresh() } catch { store.handle(error) } }
                .sheet(isPresented: $compose) { ComposeView { chat in conversation = chat } }
                .sheet(isPresented: $settings) { SettingsView() }
                .sheet(isPresented: $camera, onDismiss: { if pendingImage != nil { compose = true } }) { CameraCapture(onFinish: { data in pendingImage = data; camera = false }, onFailure: { store.error = $0.localizedDescription }) }
                .navigationDestination(item: $conversation) { chat in ConversationView(initial: chat, initialImage: pendingImage) }
                .task(id: phase) {
                    guard phase == .active else { return }
                    while !Task.isCancelled && phase == .active {
                        do { try await store.refresh(); people = try await store.contacts().contacts; try await Task.sleep(for: .seconds(12)) }
                        catch is CancellationError { return }
                        catch { store.handle(error); try? await Task.sleep(for: .seconds(12)) }
                    }
                }
        }
    }
    private func open(_ person: Person) async { do { conversation = try await store.direct(person) } catch { store.handle(error) } }
    private func preference(_ chat: ChatConversation, values: [String: Any]) async { do { try await store.action("preferences", conversation: chat.id, values: values); try await store.refresh() } catch { store.handle(error) } }
}
