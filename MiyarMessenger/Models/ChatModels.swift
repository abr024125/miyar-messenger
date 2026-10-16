import Foundation

struct Person: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let role: String
    var online: Bool? = nil
    var lastReadId: Int? = nil
    var typingUntil: Double? = nil
    var roleName: String { role == "admin" ? "مشرف" : role == "teacher" ? "معلمة" : "طالبة" }
}
struct Branch: Codable, Identifiable, Hashable { let id: Int; let name: String; let status: String }
struct MessengerUser: Codable, Identifiable {
    let id: String; let name: String; let role: String
    let branchId: Int; let branchName: String; let branches: [Branch]
}
struct ChatSettings: Codable { var showOnline: Bool }
struct LastMessage: Codable { let id: Int; let text: String; let kind: String; let senderId: String; let senderName: String; let createdAt: Double }
struct ChatConversation: Codable, Identifiable, Hashable {
    let id: String; let kind: String; var title: String; let createdBy: String
    var updatedAt: Double; var archived: Bool; var muted: Bool; var canSend: Bool
    var participants: [Person]; var lastMessage: LastMessage?; var unread: Int
    static func == (a: Self, b: Self) -> Bool { a.id == b.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
    func peer(excluding id: String) -> Person? { participants.first { $0.id != id } }
}
struct Attachment: Codable, Identifiable { let id: String; let name: String; let mime: String; let bytes: Int; let url: String }
struct Reply: Codable { let id: Int; let text: String; let senderName: String }
struct Reaction: Codable, Hashable { let messageId: Int; let actorId: String; let value: String }
struct ChatMessage: Codable, Identifiable {
    let id: Int; let conversationId: String; let senderId: String; let senderName: String
    let clientId: String; let text: String; let createdAt: Double; let deleted: Bool; let kind: String
    let attachment: Attachment?; let reply: Reply?; let reactions: [Reaction]
    var date: Date { Date(timeIntervalSince1970: createdAt / 1000) }
}
struct InboxResponse: Decodable { let me: MessengerUser; let settings: ChatSettings; let conversations: [ChatConversation] }
struct ContactsResponse: Decodable { let contacts: [Person]; let hasMore: Bool }
struct ThreadResponse: Decodable { let conversation: ChatConversation; let messages: [ChatMessage]; let hasMore: Bool }
struct ConversationResponse: Decodable { let conversation: ChatConversation }
struct UploadResponse: Decodable { let attachment: Attachment }
struct ActionResponse: Decodable { let ok: Bool; let id: Int? }
struct LoginResponse: Decodable { let mfaRequired: Bool?; let user: LoginUser? }
struct LoginUser: Decodable { let requiresPasswordChange: Bool? }
struct APIError: LocalizedError {
    let status: Int; let message: String
    var errorDescription: String? { message }
}
struct SharedFile: Identifiable { let id = UUID(); let url: URL }
