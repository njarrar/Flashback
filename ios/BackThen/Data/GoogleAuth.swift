import AuthenticationServices
import CryptoKit
import Foundation
import Security
import UIKit

/// Signs in with Google (OAuth with PKCE in a system browser sheet) for the Photos Picker scope.
/// Keeps the refresh token in the Keychain so later imports skip the sign-in screen.
@MainActor
final class GoogleAuth: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = GoogleAuth()
    static let scope = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
    private let keychainKey = "google-refresh-token"
    private var currentSession: ASWebAuthenticationSession?

    private var redirectURI: String { "\(GoogleConfig.redirectScheme):/oauth2redirect" }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
    }

    /// A fresh access token, signing in if needed. Nil if the user backs out.
    func accessToken() async throws -> String? {
        if let refresh = Keychain.read(keychainKey) {
            do {
                return try await refreshToken(refresh)
            } catch is GooglePhotosError {
                Keychain.delete(keychainKey)
            }
        }
        return try await signIn()
    }

    func signOut() {
        Keychain.delete(keychainKey)
    }

    private func signIn() async throws -> String? {
        let verifier = Self.randomString()
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64URL
        var url = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        url.queryItems = [
            .init(name: "client_id", value: GoogleConfig.iosClientID),
            .init(name: "redirect_uri", value: redirectURI),
            .init(name: "response_type", value: "code"),
            .init(name: "scope", value: Self.scope),
            .init(name: "code_challenge", value: challenge),
            .init(name: "code_challenge_method", value: "S256"),
        ]
        let callback: URL? = try await withCheckedThrowingContinuation { cont in
            let session = ASWebAuthenticationSession(url: url.url!, callbackURLScheme: GoogleConfig.redirectScheme) { [weak self] url, error in
                self?.currentSession = nil
                if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                    cont.resume(returning: nil)
                } else if let error {
                    cont.resume(throwing: error)
                } else {
                    cont.resume(returning: url)
                }
            }
            session.presentationContextProvider = self
            self.currentSession = session
            session.start()
        }
        guard let callback,
              let code = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "code" })?.value
        else { return nil }
        let token = try await tokenRequest([
            "code": code,
            "client_id": GoogleConfig.iosClientID,
            "redirect_uri": redirectURI,
            "code_verifier": verifier,
            "grant_type": "authorization_code",
        ])
        if let refresh = token.refresh_token { Keychain.write(keychainKey, refresh) }
        return token.access_token
    }

    private func refreshToken(_ refresh: String) async throws -> String {
        try await tokenRequest([
            "refresh_token": refresh,
            "client_id": GoogleConfig.iosClientID,
            "grant_type": "refresh_token",
        ]).access_token
    }

    private struct TokenResponse: Decodable {
        let access_token: String
        let refresh_token: String?
    }

    private func tokenRequest(_ form: [String: String]) async throws -> TokenResponse {
        var req = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var body = URLComponents()
        body.queryItems = form.map { URLQueryItem(name: $0.key, value: $0.value) }
        req.httpBody = body.percentEncodedQuery?.data(using: .utf8)
        let (data, response) = try await URLSession.shared.data(for: req)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw GooglePhotosError.http(String(decoding: data, as: UTF8.self)) }
        return try JSONDecoder().decode(TokenResponse.self, from: data)
    }

    private static func randomString() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64URL
    }
}

private extension Data {
    var base64URL: String {
        base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
}

/// A tiny Keychain wrapper for one string per key.
enum Keychain {
    private static func query(_ key: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "BackThen", kSecAttrAccount as String: key]
    }

    static func read(_ key: String) -> String? {
        var q = query(key)
        q[kSecReturnData as String] = true
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func write(_ key: String, _ value: String) {
        delete(key)
        var q = query(key)
        q[kSecValueData as String] = Data(value.utf8)
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(q as CFDictionary, nil)
    }

    static func delete(_ key: String) {
        SecItemDelete(query(key) as CFDictionary)
    }
}
