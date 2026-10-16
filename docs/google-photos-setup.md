# Turning on Google Photos

Back Then reads the phone's own photos with no setup. To let people add pictures from
Google Photos, Google needs to know the app. You do this once, in Google Cloud.

Since March 2025 Google only lets apps use the **Photos Picker**: the user picks
photos in a Google screen and the app gets just those. That is what Back Then uses.

What you need:

- A Google account to own the Google Cloud project.
- About 20 minutes.
- For Android, the SHA-1 fingerprints of your signing keys (step 6 says where).

## 1. Make a project

1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Click the project picker at the top, then **New project**.
3. Name it `Back Then` and click **Create**. Make sure it is the selected project.

## 2. Turn on the Photos Picker API

1. Go to **APIs & Services > Library**.
2. Search for **Photos Picker API**, open it and click **Enable**.

## 3. Set up the consent screen

This is the screen people see when they sign in. Go to **Google Auth Platform**
(also under **APIs & Services > OAuth consent screen**) and click **Get started**.

1. **App information:** app name `Back Then`, and your email as support email.
2. **Audience:** choose **External**.
3. **Contact information:** your email.
4. Agree to the policy and click **Create**.

Then fill in the other pages in the left menu:

- **Branding:** leave the logo, home page, privacy policy and authorized domains
  empty for now. They are optional while the app is in testing, and a logo makes
  Google review the app before anyone can sign in. You fill them in before a public
  launch (see the last section).
- **Data Access:** click **Add or remove scopes**, paste this in **Manually add
  scopes**, click **Add to table**, then **Update** and **Save**:

  ```
  https://www.googleapis.com/auth/photospicker.mediaitems.readonly
  ```

## 4. Add testers

While the app is in testing, only Google accounts on this list can sign in.

1. Go to **Audience > Test users > Add users**.
2. Add your own Google account and the Google account of each Play tester (up to 100).
   Use the account they will pick in the Google sign-in screen, which may differ from
   the one they use for Google Play.

What testers will see while the app is in testing:

- A "Google hasn't verified this app" screen. They tap **Continue**.
- The sign-in lasts 7 days. After that, Add Google Photos asks them to sign in again.
  Photos they already added stay, since the app keeps its own copies.

## 5. iPhone client

1. Go to **Clients > Create client**.
2. Application type: **iOS**. Name: `Back Then iOS`.
3. Bundle ID: `com.njarrar.backthen`. Leave the rest empty.
4. Click **Create** and copy the **Client ID**. It looks like
   `1234567890-abc123.apps.googleusercontent.com`.

The client ID goes in `ios/BackThen/GoogleConfig.swift` as `iosClientID`. It is not a
secret: Google gives iOS clients no secret, and the ID ships inside every copy of the
app. Then pull the change on your Mac and run the app from Xcode again.

## 6. Android clients

Android has no client ID in the code. Google matches the app by its package name and
the SHA-1 fingerprint of the key that signed it. Each way you install the app uses a
different key, so make one Android client for each.

For each row below: **Clients > Create client**, type **Android**, package name
`com.njarrar.backthen`, paste the SHA-1, click **Create**.

| Name | Used by | Where to find the SHA-1 |
| --- | --- | --- |
| `Back Then Play` | Anyone who installs from Google Play, testers included | Play Console > your app > **Test and release > App integrity > App signing**. Copy the SHA-1 under **App signing key certificate**. It shows after your first upload. |
| `Back Then upload key` | The APK in the repo's build/ folder | `C5:1C:2E:DB:70:45:29:7D:B7:99:78:0E:41:19:A9:E1:61:9A:A4:63` |
| `Back Then debug` (optional) | Builds you run from Android Studio on your own computer | Run the command below. |

Debug key SHA-1, on a Mac:

```
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

The Play row is the one testers need. Google Play re-signs the app with its own key,
so the upload key's SHA-1 does not match what lands on their phones.

The app already on the Play test track needs no new upload for this. Once the Play
client exists, Add Google Photos works in it. Allow a few minutes for Google to catch up.

The upload key row only works for APKs that the **Build apps** workflow made after
version 1.8, since earlier runs used a throwaway key each time.

## 7. Check it

- **iPhone:** tap the Google button in Back Then. You should get the Google sign-in
  sheet, not "This build has no Google client ID yet".
- **Android:** same button. If you see "Google sign-in is not set up for this build",
  the SHA-1 for the way you installed the app is missing or wrong, or your Google
  account is not a test user.

## Later: opening it to everyone

Testing mode is fine for the closed test. Before a public launch:

1. Go to **Audience** and click **Publish app**.
2. On **Branding**, add the 120 x 120 logo, a home page, the privacy policy and
   your domain under **Authorized domains**.
3. Google requires a review for apps that use the Google Photos APIs. Go to
   **Verification Center** and submit. Google will ask for:
   - a home page and privacy policy on a domain you own and have verified in
     [Google Search Console](https://search.google.com/search-console) (a github.com
     page can't be verified as yours, so you will need your own domain, which can
     point at GitHub Pages);
   - a short video showing the sign-in and how the app uses the photos.
4. Until the review passes, users see the "unverified app" screen and Google caps
   sign-ins at 100 users.
