import Foundation
import Combine

@MainActor
final class ChatStore: ObservableObject {
    @Published var me: MessengerUser?
    @Published var conversations: [ChatConversation] = []
    @Published var branchId: Int?
    @Published var showOnline = true
    @Published var archived = false
    @Published var loading = false
    @Published var ready = false
    @Published var needsMFA = false
    @Published var needsPasswordChange = false
    @Published var canRetrySession = false
    @Published var error: String?
    let api = APIClient.shared
    private var sessionRevision = UUID()
    func restore() async {
        guard !ready else { return }
        if SessionVault.restore() {
            do { try await refresh() }
            catch { canRetrySession = (error as? APIError)?.status != 401 && (error as? APIError)?.status != 428; handle(error) }
        }
        ready = true
    }
    func retrySession() async {
        guard !loading else { return }
        loading = true; error = nil; defer { loading = false }
        do { try await refresh(); canRetrySession = false }
        catch { handle(error) }
    }
    func refresh() async throws {
        let selected = branchId
        let revision = sessionRevision
        var query = ["archived": archived ? "1" : "0"]
        if let selected { query["branch"] = String(selected) }
        let result: InboxResponse = try await api.get(query: query)
        guard branchId == selected, sessionRevision == revision else { return }
        me = result.me; branchId = result.me.branchId; conversations = result.conversations; showOnline = result.settings.showOnline; error = nil
    }
    func login(username: String, password: String, code: String) async {
        guard !loading else { return }
        sessionRevision = UUID()
        loading = true; error = nil
        defer { loading = false }
        do {
            let result: LoginResponse = try await api.system(needsMFA ? .verifyMfaLogin : .login, values: needsMFA ? ["code": code] : ["username": username, "password": password])
            canRetrySession = false
            needsMFA = result.mfaRequired == true
            if needsMFA { return }
            needsPasswordChange = result.user?.requiresPasswordChange == true
            if !needsPasswordChange { try await refresh() }
        } catch {
            // An incorrect factor must not erase the still-valid MFA challenge.
            if needsMFA, (error as? APIError)?.status == 401 { self.error = error.localizedDescription }
            else { handle(error) }
        }
    }
    func changePassword(current: String, next: String, confirmation: String) async {
        loading = true; error = nil; defer { loading = false }
        do {
            let _: LoginResponse = try await api.system(.studentChangePassword, values: ["currentPassword": current, "password": next, "confirmation": confirmation])
            needsPasswordChange = false; try await refresh()
        } catch { handle(error) }
    }
    func selectBranch(_ id: Int) async {
        guard branchId != id else { return }
        conversations = []; branchId = id; archived = false
        do { try await refresh() } catch { handle(error) }
    }
    func contacts(query: String = "", page: Int = 1) async throws -> ContactsResponse {
        guard let branchId else { throw APIError(status: 401, message: "سجلي الدخول أولًا") }
        return try await api.get(query: ["view": "contacts", "q": query, "page": String(page), "branch": String(branchId)])
    }
    func direct(_ person: Person) async throws -> ChatConversation {
        let result: ConversationResponse = try await api.post(["action": "direct", "recipientId": person.id], branch: branchId)
        try await refresh(); return result.conversation
    }
    func group(title: String, people: Set<String>) async throws -> ChatConversation {
        let result: ConversationResponse = try await api.post(["action": "group", "title": title, "participantIds": Array(people)], branch: branchId)
        try await refresh(); return result.conversation
    }
    func action(_ name: String, conversation: String? = nil, values: [String: Any] = [:]) async throws {
        var body = values; body["action"] = name
        if let conversation { body["conversationId"] = conversation }
        let _: ActionResponse = try await api.post(body, branch: branchId)
    }
    func logout() async {
        guard !loading else { return }
        sessionRevision = UUID(); loading = true
        me = nil; branchId = nil; conversations = []
        defer { loading = false }
        do { let _: ActionResponse = try await api.system(.logout) } catch { /* Always erase this device's authentication state. */ }
        SessionVault.clear(); api.clearPrivateFiles(); me = nil; branchId = nil; conversations = []
        needsMFA = false; needsPasswordChange = false; canRetrySession = false; archived = false; error = nil
    }
    func handle(_ failure: Error) {
        if failure is CancellationError || (failure as? URLError)?.code == .cancelled { return }
        if let failure = failure as? APIError {
            if failure.status == 401 { sessionRevision = UUID(); me = nil; branchId = nil; conversations = []; canRetrySession = false; SessionVault.clear(); api.clearPrivateFiles() }
            if failure.status == 428 { needsPasswordChange = true }
        }
        error = failure.localizedDescription
    }
}
