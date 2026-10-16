import Foundation

@MainActor
final class APIClient {
    static let shared = APIClient()
    let baseURL = RequestContracts.baseURL
    private let session: URLSession
    private init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpCookieStorage = SessionVault.cookieStorage
        configuration.httpShouldSetCookies = true
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.timeoutIntervalForRequest = 25
        session = URLSession(configuration: configuration)
    }
    private func url(_ path: String, query: [String: String] = [:]) throws -> URL {
        try RequestContracts.url(path, query: query)
    }
    private func execute(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await session.data(for: request)
        SessionVault.save()
        guard let http = response as? HTTPURLResponse else { throw APIError(status: 503, message: "تعذر الاتصال بالخادم") }
        guard 200..<300 ~= http.statusCode else {
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            throw APIError(status: http.statusCode, message: object?["error"] as? String ?? "تعذر إتمام الطلب")
        }
        return data
    }
    func get<T: Decodable>(_ path: String = "/api/messenger", query: [String: String] = [:]) async throws -> T {
        let request = URLRequest(url: try url(path, query: query))
        let data = try await execute(request)
        return try JSONDecoder().decode(T.self, from: data)
    }
    func post<T: Decodable>(_ body: [String: Any], branch: Int? = nil, path: String = "/api/messenger") async throws -> T {
        var request = URLRequest(url: try url(path, query: branch.map { ["branch": String($0)] } ?? [:]))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(baseURL.absoluteString, forHTTPHeaderField: "Origin")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let data = try await execute(request)
        return try JSONDecoder().decode(T.self, from: data)
    }
    func system<T: Decodable>(_ action: PlatformAction, values: [String: Any] = [:]) async throws -> T {
        let data = try await execute(RequestContracts.systemRequest(action, values: values))
        return try JSONDecoder().decode(T.self, from: data)
    }
    func upload(_ data: Data, name: String, mime: String, conversation: String, branch: Int) async throws -> Attachment {
        guard data.count <= 10 * 1024 * 1024 else { throw APIError(status: 413, message: "الحد الأعلى للمرفق 10 ميجابايت") }
        var request = URLRequest(url: try url("/api/messenger/media", query: ["conversation": conversation, "name": name, "branch": String(branch)]))
        request.httpMethod = "POST"; request.httpBody = data
        request.setValue(mime, forHTTPHeaderField: "Content-Type")
        request.setValue(baseURL.absoluteString, forHTTPHeaderField: "Origin")
        let response = try await execute(request)
        return try JSONDecoder().decode(UploadResponse.self, from: response).attachment
    }
    func bytes(_ attachment: Attachment) async throws -> Data {
        let endpoint = try url(attachment.url)
        return try await execute(URLRequest(url: endpoint))
    }
    func localFile(_ attachment: Attachment) async throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("miyar-messenger", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let safeName = attachment.name.replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "\\", with: "_")
        let file = directory.appendingPathComponent(attachment.id + "-" + safeName)
        if !FileManager.default.fileExists(atPath: file.path) {
            let data = try await bytes(attachment)
            try data.write(to: file, options: [.atomic, .completeFileProtection])
        }
        return file
    }
    func clearPrivateFiles() { try? FileManager.default.removeItem(at: FileManager.default.temporaryDirectory.appendingPathComponent("miyar-messenger")) }
}
