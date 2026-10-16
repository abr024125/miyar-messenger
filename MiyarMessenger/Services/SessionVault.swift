import Foundation
import Security

/// The login cookie is an authentication secret: persist it only in Keychain.
enum SessionVault {
    private static let service = "tech.deanju.messenger.session"
    // Ephemeral cookie storage keeps the URLSession cookie jar off disk.
    static let cookieStorage = URLSessionConfiguration.ephemeral.httpCookieStorage!
    private struct StoredCookie: Codable {
        let name: String; let value: String; let domain: String; let path: String
        let expires: Date?; let secure: Bool
    }
    static func save() {
        let cookies = (cookieStorage.cookies ?? []).filter { $0.name == "miyar_session" || $0.name == "miyar_mfa" }
        let stored = cookies.map { StoredCookie(name: $0.name, value: $0.value, domain: $0.domain, path: $0.path, expires: $0.expiresDate, secure: $0.isSecure) }
        guard let data = try? JSONEncoder().encode(stored) else { return }
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: "cookies"]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }
    static func restore() -> Bool {
        clearLegacyCookies()
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: "cookies", kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data, let cookies = try? JSONDecoder().decode([StoredCookie].self, from: data) else { return false }
        for cookie in cookies where cookie.expires.map({ $0 > Date() }) ?? true {
            var properties: [HTTPCookiePropertyKey: Any] = [.name: cookie.name, .value: cookie.value, .domain: cookie.domain, .path: cookie.path, .secure: cookie.secure ? "TRUE" : "FALSE"]
            if let expires = cookie.expires { properties[.expires] = expires }
            if let restored = HTTPCookie(properties: properties) { cookieStorage.setCookie(restored) }
        }
        return (cookieStorage.cookies ?? []).contains { $0.name == "miyar_session" && ($0.expiresDate.map { $0 > Date() } ?? true) }
    }
    static func clear() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service] as CFDictionary)
        for cookie in cookieStorage.cookies ?? [] where cookie.name.hasPrefix("miyar_") { cookieStorage.deleteCookie(cookie) }
        clearLegacyCookies()
    }
    private static func clearLegacyCookies() {
        for cookie in HTTPCookieStorage.shared.cookies ?? [] where cookie.name.hasPrefix("miyar_") { HTTPCookieStorage.shared.deleteCookie(cookie) }
    }
}
