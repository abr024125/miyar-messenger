package tech.deanju.messenger

import kotlinx.serialization.json.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ContractTests {
    private inline fun <reified T> fixture(name: String): T = WireJson.decodeFromString(javaClass.classLoader!!.getResource("fixtures/$name.json")!!.readText())
    @Test fun realInboxPreservesBranchPermissions() {
        val inbox = fixture<Inbox>("inbox")
        assertEquals("teacher", inbox.me.role)
        assertEquals(1, inbox.me.branchId)
        assertTrue(inbox.settings.showOnline)
        assertTrue(inbox.conversations.single().canSend)
        assertEquals("student:S00000001", inbox.conversations.single().peer(inbox.me.id)!!.id)
    }
    @Test fun realThreadDecodesRepliesReactionsAndPrivateMedia() {
        val thread = fixture<Thread>("thread")
        assertEquals(3, thread.messages.size)
        assertEquals("👍", thread.messages[0].reactions.single().value)
        assertEquals(thread.messages[0].id, thread.messages[1].reply!!.id)
        val url = RequestContracts.url(thread.messages.last().attachment!!.url)
        assertEquals("deanju.tech", url.host)
        assertEquals("1", url.queryParameter("branch"))
        assertFalse(thread.hasMore)
    }
    @Test fun directoryAndLoginSupportExistingAccounts() {
        val contacts = fixture<Contacts>("contacts")
        assertEquals(4, contacts.contacts.size)
        assertTrue(contacts.contacts.any { it.online })
        val login = fixture<Login>("login")
        assertFalse(login.mfaRequired)
        assertFalse(login.user!!.requiresPasswordChange)
        assertTrue(WireJson.decodeFromString<Login>("{\"mfaRequired\":true}").mfaRequired)
        assertTrue(WireJson.decodeFromString<Login>("{\"user\":{\"requiresPasswordChange\":true}}").user!!.requiresPasswordChange)
    }
    @Test fun refreshMergesUpdatedMessagesWithoutLosingHistory() {
        val messages = fixture<Thread>("thread").messages
        val deleted = messages[0].copy(deleted = true, text = "حُذفت الرسالة", reactions = emptyList())
        val next = messages.last().copy(id = 4)
        val merged = mergeMessages(messages, listOf(deleted, next))
        assertEquals(listOf(1L, 2L, 3L, 4L), merged.map { it.id })
        assertTrue(merged.first().deleted)
        assertEquals(messages[1], merged[1])
    }
    @Test fun urlRetainsArabicAndExistingAttachmentQuery() {
        val url = RequestContracts.url("/api/messenger/media?id=example&branch=1", mapOf("name" to "ملف الدرس.pdf", "conversation" to "a/b?c"))
        assertEquals("ملف الدرس.pdf", url.queryParameter("name"))
        assertEquals("a/b?c", url.queryParameter("conversation"))
        assertEquals("example", url.queryParameter("id"))
        assertEquals("https://deanju.tech/api/messenger", RequestContracts.url().toString())
    }
    @Test fun urlNeverSendsSessionToExternalOrUnexpectedEndpoints() {
        listOf("https://outside.invalid/api/messenger", "//outside.invalid/api/messenger", "/api/messenger#fragment", "/api/not-messenger", "/api/messenger\\evil", "/api/messenger/media/extra").forEach { path ->
            assertThrows("Rejected endpoint: $path", IllegalArgumentException::class.java) { RequestContracts.url(path) }
        }
    }
    @Test fun passwordChangeUsesActualPlatformActionAndOrigin() {
        val body = RequestContracts.systemBody(PlatformAction.CHANGE_PASSWORD, buildJsonObject { put("action", "incorrect"); put("currentPassword", "old"); put("password", "new"); put("confirmation", "new") })
        val request = RequestContracts.jsonRequest(body, path = "/api/system")
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        val sent = WireJson.parseToJsonElement(buffer.readUtf8()).jsonObject
        assertEquals("studentChangePassword", sent["action"]!!.jsonPrimitive.content)
        assertEquals("old", sent["currentPassword"]!!.jsonPrimitive.content)
        assertEquals("https://deanju.tech", request.header("Origin"))
        assertEquals("application/json; charset=utf-8", request.body!!.contentType().toString())
    }
    @Test fun sendRequestPreservesRetryIdentifierAndScope() {
        val request = RequestContracts.jsonRequest(buildJsonObject { put("action", "send"); put("clientId", "11111111-1111-4111-8111-111111111111"); put("conversationId", "conversation"); put("text", "رسالة"); put("replyTo", 3) }, branch = 2)
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        val sent = WireJson.parseToJsonElement(buffer.readUtf8()).jsonObject
        assertEquals("2", request.url.queryParameter("branch"))
        assertEquals("11111111-1111-4111-8111-111111111111", sent["clientId"]!!.jsonPrimitive.content)
        assertEquals(3, sent["replyTo"]!!.jsonPrimitive.int)
    }
}
