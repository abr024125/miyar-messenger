import SwiftUI
import QuickLook

@MainActor
struct AvatarView: View {
    let name: String
    var size: CGFloat = 52
    var online = false
    var group = false
    private var initials: String { String(name.split(separator: " ").prefix(2).compactMap { $0.first }) }
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle().fill(MiyarTheme.accent.opacity(0.12))
                .overlay {
                    if group { Image(systemName: "person.2.fill").font(.system(size: size * 0.35)).foregroundStyle(MiyarTheme.accent) }
                    else { Text(initials).font(MiyarTheme.font(size * 0.32, weight: .semibold)).foregroundStyle(MiyarTheme.accent).lineLimit(1) }
                }
            if online { Circle().fill(.green).frame(width: 13, height: 13).overlay(Circle().stroke(MiyarTheme.surface, lineWidth: 2)) }
        }.frame(width: size, height: size).accessibilityLabel(name + (online ? "، متصلة الآن" : ""))
    }
}
@MainActor
struct InlineError: View {
    let text: String
    var body: some View { Label(text, systemImage: "exclamationmark.circle").font(MiyarTheme.font(13, relativeTo: .footnote)).foregroundStyle(.red).multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading).accessibilityAddTraits(.updatesFrequently) }
}
@MainActor
struct ConversationRow: View {
    let conversation: ChatConversation
    let myId: String
    var body: some View {
        HStack(spacing: 12) {
            AvatarView(name: conversation.title, size: 60, group: conversation.kind == "group")
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(conversation.title).font(MiyarTheme.font(17, weight: conversation.unread > 0 ? .bold : .semibold)).lineLimit(1)
                    if conversation.muted { Image(systemName: "bell.slash.fill").font(.caption2).foregroundStyle(.secondary) }
                }
                HStack(spacing: 5) {
                    Text(preview).lineLimit(1)
                    if let last = conversation.lastMessage { Text("·"); Text(Date(timeIntervalSince1970: last.createdAt / 1000), style: .time).environment(\.layoutDirection, .leftToRight) }
                }.font(MiyarTheme.font(13, relativeTo: .footnote)).foregroundStyle(conversation.unread > 0 ? .primary : .secondary)
            }.frame(maxWidth: .infinity, alignment: .leading)
            if conversation.unread > 0 {
                Text(conversation.unread > 99 ? "99+" : String(conversation.unread)).font(.caption.weight(.bold)).foregroundStyle(.white).padding(6).background(MiyarTheme.accent, in: Circle()).accessibilityLabel("\(conversation.unread) رسالة غير مقروءة")
            } else if let last = conversation.lastMessage, last.senderId == myId {
                Image(systemName: conversation.participants.filter { $0.id != myId }.allSatisfy { ($0.lastReadId ?? 0) >= last.id } ? "checkmark.circle.fill" : "checkmark.circle").foregroundStyle(.tertiary).font(.system(size: 16)).accessibilityLabel("حالة الرسالة")
            }
        }.padding(.vertical, 6).contentShape(Rectangle())
    }
    private var preview: String {
        guard let last = conversation.lastMessage else { return "ابدئي المحادثة" }
        return (last.senderId == myId ? "أنتِ: " : "") + last.text
    }
}
@MainActor
struct DocumentPreview: UIViewControllerRepresentable {
    let url: URL
    func makeCoordinator() -> Coordinator { Coordinator(url: url) }
    func makeUIViewController(context: Context) -> QLPreviewController { let controller = QLPreviewController(); controller.dataSource = context.coordinator; return controller }
    func updateUIViewController(_ uiViewController: QLPreviewController, context: Context) {}
    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        let url: URL
        init(url: URL) { self.url = url }
        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem { url as NSURL }
    }
}
@MainActor
struct FileSheet: View {
    let file: SharedFile
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack { DocumentPreview(url: file.url).navigationTitle("المرفق").navigationBarTitleDisplayMode(.inline).toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("إغلاق", systemImage: "xmark") { dismiss() } }
            ToolbarItem(placement: .primaryAction) { ShareLink(item: file.url) { Image(systemName: "square.and.arrow.up") } }
        } }
    }
}
