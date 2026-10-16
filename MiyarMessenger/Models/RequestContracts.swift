import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

enum PlatformAction: String {
    case login
    case verifyMfaLogin
    case studentChangePassword
    case logout
}

enum RequestContracts {
    static let baseURL = URL(string: "https://deanju.tech")!

    static func url(_ path: String, query: [String: String] = [:]) throws -> URL {
        guard let relative = URLComponents(string: path), relative.scheme == nil,
              relative.host == nil, relative.fragment == nil,
              ["/api/system", "/api/messenger", "/api/messenger/media"].contains(relative.path),
              var destination = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else {
            throw APIError(status: 400, message: "رابط الخدمة غير صالح")
        }
        destination.percentEncodedPath = relative.percentEncodedPath
        let items = (relative.queryItems ?? []) + query.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        destination.queryItems = items.isEmpty ? nil : items
        guard let url = destination.url else { throw APIError(status: 400, message: "رابط الخدمة غير صالح") }
        return url
    }

    static func systemRequest(_ action: PlatformAction, values: [String: Any] = [:]) throws -> URLRequest {
        var body = values
        body["action"] = action.rawValue
        var request = URLRequest(url: try url("/api/system"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(baseURL.absoluteString, forHTTPHeaderField: "Origin")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        return request
    }
}
