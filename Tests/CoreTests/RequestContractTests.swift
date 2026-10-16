import XCTest
@testable import MiyarMessengerContracts

final class RequestContractTests: XCTestCase {
    private func fixture<T: Decodable>(_ name: String, as type: T.Type) throws -> T {
        let url = try XCTUnwrap(Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures"))
        return try JSONDecoder().decode(type, from: Data(contentsOf: url))
    }

    func testPasswordChangeActionAndFields() throws {
        let request = try RequestContracts.systemRequest(.studentChangePassword, values: ["currentPassword": "previous", "password": "new-password", "confirmation": "new-password"])
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(request.httpBody)) as? [String: String])
        XCTAssertEqual(body["action"], "studentChangePassword")
        XCTAssertEqual(body["currentPassword"], "previous")
        XCTAssertEqual(body["password"], body["confirmation"])
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.url?.absoluteString, "https://deanju.tech/api/system")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Origin"), "https://deanju.tech")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/json")
    }

    func testAllAuthenticationActions() throws {
        for (action, expected) in [(PlatformAction.login, "login"), (.verifyMfaLogin, "verifyMfaLogin"), (.logout, "logout")] {
            let request = try RequestContracts.systemRequest(action)
            let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(request.httpBody)) as? [String: String])
            XCTAssertEqual(body["action"], expected)
        }
    }

    func testAttachmentQuerySurvivesURLConstruction() throws {
        let url = try RequestContracts.url("/api/messenger/media?id=sample&branch=2")
        let items = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems)
        XCTAssertEqual(url.path, "/api/messenger/media")
        XCTAssertEqual(items.first { $0.name == "id" }?.value, "sample")
        XCTAssertEqual(items.first { $0.name == "branch" }?.value, "2")
    }

    func testArabicFileNameRemainsOneQueryValue() throws {
        let name = "ملف الطالبة & مستواها + 1?.pdf"
        let url = try RequestContracts.url("/api/messenger/media", query: ["name": name, "branch": "2"])
        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        XCTAssertEqual(components.queryItems?.count, 2)
        XCTAssertEqual(components.queryItems?.first { $0.name == "name" }?.value, name)
    }

    func testRejectsForeignAndNonAPIURLs() {
        for path in ["https://example.com/api/system", "//example.com/api/system", "/api/system#fragment", "/account", "api/system"] {
            XCTAssertThrowsError(try RequestContracts.url(path))
        }
    }

    func testDecodesActualInboxResponse() throws {
        let inbox = try fixture("inbox", as: InboxResponse.self)
        XCTAssertFalse(inbox.me.branches.isEmpty)
        XCTAssertEqual(inbox.me.branchId, inbox.me.branches.first?.id)
        let chat = try XCTUnwrap(inbox.conversations.first)
        XCTAssertEqual(chat.kind, "direct")
        XCTAssertEqual(chat.participants.count, 2)
        XCTAssertTrue(chat.participants.contains { $0.id.hasPrefix("student:") })
        XCTAssertNotNil(chat.lastMessage)
    }

    func testDecodesActualThreadWithReplyReactionsAndImage() throws {
        let thread = try fixture("thread", as: ThreadResponse.self)
        XCTAssertTrue(thread.messages.contains { $0.reply != nil })
        XCTAssertTrue(thread.messages.contains { !$0.reactions.isEmpty })
        let message = try XCTUnwrap(thread.messages.first { $0.attachment != nil })
        let attachment = try XCTUnwrap(message.attachment)
        XCTAssertEqual(attachment.mime, "image/png")
        XCTAssertGreaterThan(attachment.bytes, 0)
        XCTAssertEqual(message.conversationId, thread.conversation.id)
        XCTAssertEqual(message.kind, "image")
        XCTAssertEqual(message.date.timeIntervalSince1970, message.createdAt / 1000, accuracy: 0.001)
    }

    func testDecodesActualContactsAndLoginResponses() throws {
        let contacts = try fixture("contacts", as: ContactsResponse.self)
        XCTAssertFalse(contacts.contacts.isEmpty)
        XCTAssertTrue(contacts.contacts.contains { $0.id.hasPrefix("student:") })
        let login = try fixture("login", as: LoginResponse.self)
        XCTAssertEqual(login.user?.requiresPasswordChange, false)
    }
}
