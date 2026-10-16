package tech.deanju.messenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val WireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable data class Person(val id: String, val name: String, val role: String, val online: Boolean = false, val lastReadId: Long = 0, val typingUntil: Long = 0) {
    val roleName get() = when (role) { "admin" -> "مشرف"; "teacher" -> "معلمة"; else -> "طالبة" }
}
@Serializable data class Branch(val id: Int, val name: String, val status: String)
@Serializable data class MessengerUser(val id: String, val name: String, val role: String, val branchId: Int, val branchName: String, val branches: List<Branch>)
@Serializable data class ChatSettings(val showOnline: Boolean = true)
@Serializable data class LastMessage(val id: Long, val text: String, val kind: String, val senderId: String, val senderName: String, val createdAt: Long)
@Serializable data class Conversation(val id: String, val kind: String, val title: String, val createdBy: String, val updatedAt: Long, val archived: Boolean = false, val muted: Boolean = false, val canSend: Boolean = true, val participants: List<Person>, val lastMessage: LastMessage? = null, val unread: Int = 0) {
    fun peer(me: String) = participants.firstOrNull { it.id != me }
}
@Serializable data class Attachment(val id: String, val name: String, val mime: String, val bytes: Int, val url: String)
@Serializable data class Reply(val id: Long, val text: String, val senderName: String)
@Serializable data class Reaction(val messageId: Long, val actorId: String, val value: String)
@Serializable data class Message(val id: Long, val conversationId: String, val senderId: String, val senderName: String, val clientId: String, val text: String, val createdAt: Long, val deleted: Boolean = false, val kind: String, val attachment: Attachment? = null, val reply: Reply? = null, val reactions: List<Reaction> = emptyList())
@Serializable data class Inbox(val me: MessengerUser, val settings: ChatSettings, val conversations: List<Conversation>)
@Serializable data class Contacts(val contacts: List<Person>, val hasMore: Boolean)
@Serializable data class Thread(val conversation: Conversation, val messages: List<Message>, val hasMore: Boolean)
@Serializable data class NewConversation(val conversation: Conversation)
@Serializable data class Upload(val attachment: Attachment)
@Serializable data class Login(val mfaRequired: Boolean = false, val user: LoginUser? = null)
@Serializable data class LoginUser(val requiresPasswordChange: Boolean = false)
class ApiFailure(val status: Int, override val message: String) : Exception(message)

enum class PlatformAction(val wireName: String) {
    LOGIN("login"), MFA("verifyMfaLogin"), CHANGE_PASSWORD("studentChangePassword"), LOGOUT("logout")
}

fun mergeMessages(existing: List<Message>, incoming: List<Message>): List<Message> =
    (existing.associateBy { it.id } + incoming.associateBy { it.id }).values.sortedBy { it.id }
