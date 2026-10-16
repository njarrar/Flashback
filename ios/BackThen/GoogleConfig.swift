import Foundation

/// Google OAuth settings for Google Photos import. See the README for how to make these.
/// Leave `iosClientID` empty to build without Google import; the phone library still works.
enum GoogleConfig {
    /// iOS OAuth client ID from Google Cloud Console, like "1234-abcd.apps.googleusercontent.com".
    static let iosClientID = "796199427308-0p0mcbmrobpmr91ip93i4p7vdudpe633.apps.googleusercontent.com"

    /// The client ID reversed, used as the sign-in callback scheme.
    static var redirectScheme: String {
        iosClientID.split(separator: ".").reversed().joined(separator: ".")
    }

    static var isConfigured: Bool { !iosClientID.isEmpty }
}
