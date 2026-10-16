import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

@MainActor
struct ConversationView: View {
    @EnvironmentObject private var store: ChatStore
    @Environment(\.scenePhase) private var phase
    @Environment(\.dismiss) private var dismiss
    let initial: ChatConversation
    var initialImage: Data? = nil
    @State private var chat: ChatConversation?
    @State private var messages: [ChatMessage] = []
    @State private var hasMore = false
    @State private var text = ""
    @State private var attachment: Attachment?
    @State private var reply: ChatMessage?
    @State private var error: String?
    @State private var sending = false
    @State private var uploading = false
    @State private var recording = false
    @State private var camera = false
    @State private var importer = false
    @State private var showPhotos = false
    @State private var photo: PhotosPickerItem?
    @State private var info = false
    @State private var search = false
    @State private var followLatest = true
    @State private var focusMessage: Int?
    @State private var deleting: ChatMessage?
    @State private var lastRead = 0
    @State private var lastTyped = Date.distantPast
    @AppStorage("messenger.haptics") private var haptics = true
    @State private var initialImageHandled = false
    @State private var retryKey: String?
    @State private var retryId: String?
    @FocusState private var focused: Bool
    private var current: ChatConversation { chat ?? initial }
    private var typing: [Person] { current.participants.filter { $0.id != store.me?.id && ($0.typingUntil ?? 0) > Date().timeIntervalSince1970 * 1000 } }
    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 10) {
                    if hasMore { Button("رسائل أقدم") { Task { followLatest = false; await load(older: true) } }.padding(.top) }
                    ForEach(Array(messages.enumerated()), id: \.element.id) { index, message in
                        if index == 0 || message.createdAt - messages[index - 1].createdAt > 300000 {
                            Text(message.date.formatted(.dateTime.day().month().hour().minute())).font(MiyarTheme.font(11, relativeTo: .caption)).foregroundStyle(.tertiary).padding(.vertical, 8)
                        }
                        bubble(message).id(message.id)
                    }
                    if messages.isEmpty && error == nil { ContentUnavailableView("ابدئي المحادثة", systemImage: "bubble.left.and.bubble.right", description: Text("أرسلي رسالة إلى \(current.title)")).padding(.top, 60) }
                    if !typing.isEmpty { Text(typing.map(\.name).joined(separator: "، ") + " تكتب الآن…").font(MiyarTheme.font(12, relativeTo: .caption)).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal) }
                }.padding(.vertical, 12)
            }.scrollDismissesKeyboard(.interactively)
                .onChange(of: messages.last?.id) { _, id in if followLatest, let id { withAnimation { proxy.scrollTo(id, anchor: .bottom) } } }
                .onChange(of: focusMessage) { _, id in if let id { followLatest = false; withAnimation { proxy.scrollTo(id, anchor: .center) } } }
                .overlay(alignment: .bottomTrailing) { if !followLatest { Button { followLatest = true; if let last = messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } } } label: { Image(systemName: "arrow.down.circle.fill").font(.title).padding() }.accessibilityLabel("آخر الرسائل") } }
        }
        .background(MiyarTheme.surface).navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Button { info = true } label: { HStack(spacing: 9) { AvatarView(name: current.title, size: 36, group: current.kind == "group"); VStack(alignment: .leading, spacing: 0) { Text(current.title).font(MiyarTheme.font(16, weight: .semibold)).foregroundStyle(.primary).lineLimit(1); Text(current.kind == "group" ? "\(current.participants.count) أعضاء" : current.peer(excluding: store.me?.id ?? "")?.roleName ?? "مِعيار").font(MiyarTheme.font(11, relativeTo: .caption)).foregroundStyle(.secondary) } } }.accessibilityLabel("معلومات المحادثة")
            }
            ToolbarItem(placement: .topBarTrailing) { Button { search = true } label: { Image(systemName: "magnifyingglass") }.accessibilityLabel("البحث داخل المحادثة") }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) { composer }
        .sheet(isPresented: $info) { ConversationInfoView(conversation: current, onChanged: { Task { await load() } }, onSearch: { info = false; search = true }, onLeave: { dismiss() }) }
        .sheet(isPresented: $search) { ConversationSearchView(conversation: initial.id) { message in merge([message]); focusMessage = message.id } }
        .sheet(isPresented: $recording) { RecordingSheet { file in Task { defer { try? FileManager.default.removeItem(at: file) }; do { await upload(try Data(contentsOf: file), name: "تسجيل.m4a", mime: "audio/mp4") } catch { self.error = error.localizedDescription } } } }
        .sheet(isPresented: $camera) { CameraCapture(onFinish: { data in camera = false; if let data { Task { await upload(data, name: "صورة.jpg", mime: "image/jpeg") } } }, onFailure: { error = $0.localizedDescription }) }
        .photosPicker(isPresented: $showPhotos, selection: $photo, matching: .images)
        .onChange(of: photo) { _, selected in
            guard let selected else { return }
            Task {
                do {
                    guard let data = try await selected.loadTransferable(type: Data.self) else { throw APIError(status: 415, message: "تعذر قراءة الصورة المختارة") }
                    await upload(try PhotoPreparation.jpeg(from: data), name: "صورة.jpg", mime: "image/jpeg")
                } catch { self.error = error.localizedDescription }
                photo = nil
            }
        }
        .fileImporter(isPresented: $importer, allowedContentTypes: [.pdf]) { result in
            Task { do { let file = try result.get(); guard file.startAccessingSecurityScopedResource() else { throw APIError(status: 403, message: "تعذر فتح الملف") }; defer { file.stopAccessingSecurityScopedResource() }; let values = try file.resourceValues(forKeys: [.fileSizeKey]); guard (values.fileSize ?? 0) <= 10 * 1024 * 1024 else { throw APIError(status: 413, message: "الحد الأعلى 10 ميجابايت") }; await upload(try Data(contentsOf: file), name: file.lastPathComponent, mime: "application/pdf") } catch { self.error = error.localizedDescription } }
        }
        .alert("حذف رسالتك للجميع؟", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button("حذف الرسالة", role: .destructive) { if let deleting { perform("deleteMessage", message: deleting, values: [:]) }; deleting = nil }
            Button("إلغاء", role: .cancel) { deleting = nil }
        }
        .task(id: phase) {
            guard phase == .active else { return }
            if let initialImage, !initialImageHandled { initialImageHandled = true; await upload(initialImage, name: "صورة.jpg", mime: "image/jpeg") }
            while !Task.isCancelled && phase == .active { await load(); do { try await Task.sleep(for: .seconds(4)) } catch { return } }
        }
        .onChange(of: text) { _, value in
            guard Date().timeIntervalSince(lastTyped) > 4 else { return }; lastTyped = Date()
            Task { try? await store.action("typing", conversation: initial.id, values: ["active": !value.isEmpty]) }
        }
        .onDisappear { Task { try? await store.action("typing", conversation: initial.id, values: ["active": false]) } }
    }
    private var composer: some View {
        VStack(spacing: 8) {
            if let error { HStack { InlineError(text: error); Button("إغلاق", systemImage: "xmark") { self.error = nil }.labelStyle(.iconOnly) }.padding(.horizontal) }
            if let reply { HStack { Image(systemName: "arrowshape.turn.up.left"); VStack(alignment: .leading) { Text(reply.senderName).fontWeight(.semibold); Text(reply.text.isEmpty ? "مرفق" : reply.text).lineLimit(1) }; Spacer(); Button("إلغاء الرد", systemImage: "xmark.circle.fill") { self.reply = nil }.labelStyle(.iconOnly) }.font(MiyarTheme.font(12)).padding(.horizontal) }
            if let attachment { HStack { Image(systemName: attachment.mime.hasPrefix("audio/") ? "waveform" : attachment.mime.hasPrefix("image/") ? "photo" : "doc"); Text(attachment.name).font(MiyarTheme.font(13)).lineLimit(1); Spacer(); Button("إزالة المرفق", systemImage: "xmark.circle.fill") { self.attachment = nil }.labelStyle(.iconOnly) }.padding(.horizontal) }
            if current.canSend {
                HStack(alignment: .bottom, spacing: 9) {
                    Menu { Button("الصور", systemImage: "photo") { showPhotos = true }; Button("الكاميرا", systemImage: "camera") { camera = true }; Button("ملف PDF", systemImage: "doc") { importer = true } } label: { Image(systemName: "plus.circle.fill").font(.title2).frame(minWidth: 36, minHeight: 44) }.disabled(uploading || sending).accessibilityLabel("إضافة مرفق")
                    TextField("رسالة…", text: $text, axis: .vertical).lineLimit(1...5).padding(.horizontal, 12).padding(.vertical, 9).background(MiyarTheme.soft, in: RoundedRectangle(cornerRadius: 20)).focused($focused).disabled(sending)
                    if uploading { ProgressView().frame(width: 36, height: 44) }
                    else if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || attachment != nil {
                        Button { Task { await send() } } label: { if sending { ProgressView() } else { Image(systemName: "arrow.up.circle.fill").font(.title) } }.frame(minWidth: 36, minHeight: 44).disabled(sending).accessibilityLabel("إرسال الرسالة")
                    } else {
                        Button { recording = true } label: { Image(systemName: "mic.fill").font(.title3).frame(minWidth: 36, minHeight: 44) }.accessibilityLabel("تسجيل رسالة صوتية")
                        Button { text = "👍"; Task { await send() } } label: { Image(systemName: "hand.thumbsup.fill").font(.title2).frame(minWidth: 32, minHeight: 44) }.accessibilityLabel("إرسال إعجاب")
                    }
                }.padding(.horizontal, 10)
            } else { Text("التواصل مع هذا الحساب موقوف").font(MiyarTheme.font(14)).foregroundStyle(.secondary).padding() }
        }.padding(.vertical, 8).background(.bar)
    }
    private func bubble(_ message: ChatMessage) -> some View {
        let mine = message.senderId == store.me?.id
        return HStack(alignment: .bottom, spacing: 6) {
            if mine { Spacer(minLength: 44) }
            if !mine { AvatarView(name: message.senderName, size: 28) }
            VStack(alignment: .leading, spacing: 4) {
                if !mine && current.kind == "group" { Text(message.senderName).font(MiyarTheme.font(11, weight: .semibold)).foregroundStyle(.secondary) }
                VStack(alignment: .leading, spacing: 6) {
                    if let reply = message.reply { VStack(alignment: .leading, spacing: 2) { Text(reply.senderName).fontWeight(.semibold); Text(reply.text).lineLimit(2) }.font(MiyarTheme.font(11)).padding(7).frame(maxWidth: .infinity, alignment: .leading).background(Color.primary.opacity(0.07), in: RoundedRectangle(cornerRadius: 8)) }
                    if let attachment = message.attachment { MessageAttachmentView(attachment: attachment, mine: mine) }
                    if !message.text.isEmpty { Text(message.text).font(message.deleted ? MiyarTheme.font(16).italic() : MiyarTheme.font(16)).textSelection(.enabled) }
                }.padding(message.attachment?.mime.hasPrefix("image/") == true ? 3 : 11)
                    .foregroundStyle(mine ? Color.white : Color.primary)
                    .background(mine ? MiyarTheme.accent : MiyarTheme.received, in: RoundedRectangle(cornerRadius: 18))
                if !message.reactions.isEmpty { Text(message.reactions.map(\.value).joined(separator: " ")).font(.caption).padding(.horizontal, 7).padding(.vertical, 3).background(MiyarTheme.soft, in: Capsule()) }
            }
            if mine { Image(systemName: current.participants.filter { $0.id != store.me?.id }.allSatisfy { ($0.lastReadId ?? 0) >= message.id } ? "checkmark.circle.fill" : "checkmark.circle").font(.system(size: 13)).foregroundStyle(MiyarTheme.accent).accessibilityLabel("حالة القراءة") }
            if !mine { Spacer(minLength: 44) }
        }.padding(.horizontal, 12)
            .contextMenu {
                if !message.deleted {
                    Button("رد", systemImage: "arrowshape.turn.up.left") { reply = message; focused = true }
                    if !message.text.isEmpty { Button("نسخ", systemImage: "doc.on.doc") { UIPasteboard.general.string = message.text } }
                    ForEach(["👍", "❤️", "😂", "😮", "😢"], id: \.self) { value in Button(value) { perform("react", message: message, values: ["value": value]) } }
                    if message.reactions.contains(where: { $0.actorId == store.me?.id }) { Button("إزالة تفاعلي") { perform("react", message: message, values: ["value": ""]) } }
                    if mine { Button("حذف للجميع", systemImage: "trash", role: .destructive) { deleting = message } }
                }
            }
    }
    private func merge(_ incoming: [ChatMessage]) { var indexed = Dictionary(uniqueKeysWithValues: messages.map { ($0.id, $0) }); for message in incoming { indexed[message.id] = message }; messages = indexed.values.sorted { $0.id < $1.id } }
    private func load(older: Bool = false) async {
        guard let branchId = store.branchId else { return }
        var query = ["conversation": initial.id, "branch": String(branchId)]
        if older, let first = messages.first { query["before"] = String(first.id) }
        do {
            let result: ThreadResponse = try await store.api.get(query: query)
            guard !Task.isCancelled else { return }; chat = result.conversation; merge(result.messages)
            if older || messages.count <= 50 { hasMore = result.hasMore }
            if !older, let id = messages.last?.id, id > lastRead { try await store.action("read", conversation: initial.id, values: ["lastReadId": id]); lastRead = id }
        } catch { if !Task.isCancelled { self.error = error.localizedDescription; if (error as? APIError)?.status == 401 { store.handle(error) } } }
    }
    private func upload(_ data: Data, name: String, mime: String) async {
        guard let branchId = store.branchId else { return }; uploading = true; error = nil; defer { uploading = false }
        do { attachment = try await store.api.upload(data, name: name, mime: mime, conversation: initial.id, branch: branchId) } catch { self.error = error.localizedDescription }
    }
    private func send() async {
        guard !sending && !uploading && current.canSend else { return }
        let content = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty || attachment != nil else { return }
        sending = true; error = nil; defer { sending = false }
        let fingerprint = content + "|" + (attachment?.id ?? "") + "|" + String(reply?.id ?? 0)
        if fingerprint != retryKey { retryKey = fingerprint; retryId = UUID().uuidString }
        var body: [String: Any] = ["action": "send", "conversationId": initial.id, "clientId": retryId ?? UUID().uuidString, "text": content]
        if let attachment { body["attachmentId"] = attachment.id }; if let reply { body["replyTo"] = reply.id }
        do { let _: ActionResponse = try await store.api.post(body, branch: store.branchId); if haptics { UINotificationFeedbackGenerator().notificationOccurred(.success) }; text = ""; attachment = nil; reply = nil; retryKey = nil; retryId = nil; followLatest = true; await load(); try await store.refresh() }
        catch { self.error = error.localizedDescription }
    }
    private func perform(_ action: String, message: ChatMessage, values: [String: Any]) { Task { do { var values = values; values["messageId"] = message.id; try await store.action(action, conversation: initial.id, values: values); await load() } catch { self.error = error.localizedDescription } } }
}
