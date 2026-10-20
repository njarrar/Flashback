# Publishing Flashback on the App Store

Everything App Store Connect asks for is in this folder or made by the
[App Store workflow](../.github/workflows/app-store.yml). Work through the steps in order.
Steps 1 to 6 happen once.

| What | Where |
|---|---|
| Bundle ID | `com.njarrar.backthen` (widget: `com.njarrar.backthen.widget`) |
| App Group | `group.com.njarrar.backthen` |
| Version | 1.16 (build 18) |
| Store text, four languages | [`listing/`](listing) |
| iPhone screenshots, four languages | [`screenshots/`](screenshots), made by the workflow |
| Privacy policy | [`../play-store/privacy-policy.md`](../play-store/privacy-policy.md) (covers both apps) |

## 1. Join the Apple Developer Program

The free Apple ID you use to run the app from Xcode can't publish. Publishing needs
the paid program.

1. Go to [developer.apple.com/programs/enroll](https://developer.apple.com/programs/enroll)
   and sign in with your Apple ID. It needs two-factor sign-in turned on.
2. Enroll as an **Individual**. It costs 99 US dollars a year. Apple checks your ID,
   which can take from a few minutes to two days.
3. As an individual, the App Store shows your legal name as the seller.

## 2. Move the project to your paid team (once, on your Mac)

1. In Xcode, **Settings > Accounts**. Your paid team now shows next to your Personal Team.
2. Open `ios/BackThen.xcodeproj`. For each of the targets **BackThen** and
   **BackThenWidget**, open **Signing & Capabilities** and pick the paid team (not
   "Personal Team"). Leave "Automatically manage signing" on.
3. Xcode registers both bundle IDs and the App Group for you. If it says the App Group
   is missing, click the **+** under App Groups and pick `group.com.njarrar.backthen`.
4. Run the app on your iPhone once to check that signing works. The home screen widget
   now shares hidden days with the app, which a free account could not do.

If Xcode says `com.njarrar.backthen` isn't available, it is still held by your Personal
Team. Delete the app from your iPhone, wait a day and try again.

## 3. Make the signing certificates

1. In Xcode, **Settings > Accounts**, pick the paid team, click **Manage Certificates**.
2. Click **+** and add **Apple Development** (skip if it's there), then **+** and
   **Apple Distribution**.
3. Open **Keychain Access**, pick **login** and **My Certificates**. Select both
   "Apple Development: ..." and "Apple Distribution: ..." (Command click), right click,
   **Export 2 items**. Save as `flashback-certs.p12` and set a password.
4. Copy it as text: `base64 -i flashback-certs.p12 | pbcopy`. Keep this for step 6.

## 4. Create the app in App Store Connect

In [App Store Connect](https://appstoreconnect.apple.com), **Apps > + > New App**:

- Platform: **iOS**
- Name: **Flashback: On This Day**. Names are unique across the App Store. If it's
  taken, try `Flashback: Photos of This Day`, and paste the same name in
  `listing/en-US/name.txt`.
- Primary language: **English (U.S.)**
- Bundle ID: **com.njarrar.backthen**
- SKU: `flashback`
- User access: Full access

## 5. Make an App Store Connect API key

This key lets GitHub sign and send builds for you.

1. **Users and Access > Integrations > App Store Connect API > Team Keys**. Click
   **Generate API Key** (the first time, accept the terms first).
2. Name `github`, access **Admin**. Admin is needed so Xcode can make signing profiles;
   a lower role uploads but fails at signing.
3. Click **Download API Key**. Apple lets you download the `.p8` file only once.
4. Note the **Key ID** (next to the key) and the **Issuer ID** (above the list).

Your **Team ID** is on [developer.apple.com/account](https://developer.apple.com/account)
under Membership details. It's ten letters and digits.

## 6. Give GitHub the secrets

In the repo, **Settings > Secrets and variables > Actions > New repository secret**.
Add six:

| Name | Value |
|---|---|
| `APPLE_TEAM_ID` | your Team ID (step 5) |
| `ASC_KEY_ID` | the Key ID (step 5) |
| `ASC_ISSUER_ID` | the Issuer ID (step 5) |
| `ASC_KEY_P8` | the whole text of the `.p8` file: `pbcopy < ~/Downloads/AuthKey_XXXX.p8` |
| `APPLE_CERTS_P12` | the text from step 3 |
| `APPLE_CERTS_PASSWORD` | the password from step 3 |

Then delete the `.p8` and `.p12` files, or keep them in your password manager. Never
commit them.

## 7. Send a build to TestFlight

**Actions > App Store > Run workflow** on `main`. Two choices:

- **Send the build to App Store Connect**: on by default.
- **Take new App Store screenshots**: on by default. They land in `screenshots/`.

The run's summary says what it built and whether it went to Apple. After 10 to 30
minutes the build shows in App Store Connect under **TestFlight**. Add yourself under
**Internal Testing**, install the **TestFlight** app on your iPhone, and test the real
App Store build. Friends need **External Testing**, which Apple reviews first (often
a day).

The workflow uses Xcode 26: since April 2026 Apple takes only builds made with it.

## 8. Fill in the App Store page

Under **Apps > Flashback > App Store**, on the **1.16 Prepare for Submission** page:

- **Screenshots, 6.9" Display:** all six from `screenshots/en/`. They are 1320 x 2868,
  which Apple asks for, and Apple scales them for smaller iPhones.
- **Promotional Text, Description, Keywords:** copy from `listing/en-US/`.
- **Support URL:** `https://github.com/njarrar/PicturesFromthisday`
- **Version:** 1.16. **Copyright:** `2026 your name`.
- **Build:** click **+** and pick the build from step 7.
- **App Review Information:** sign-in required **off**. Notes:

  > Flashback has no account and no server. It shows photos from the device library
  > taken on today's date in past years. Please allow photo access when asked; with an
  > empty library the app shows an empty state. Places (Settings) is optional and uses
  > coarse location while the app is open, only on the device.

- **Release:** "Manually release this version" lets you pick the day.

Other languages: open the language menu at the top right, add **French (France)**,
**Spanish (Spain)** and **Arabic**, and paste `name.txt`, `subtitle.txt`,
`promotional-text.txt`, `description.txt` and `keywords.txt` from each folder in
`listing/`, plus the screenshots from `screenshots/fr`, `es` and `ar`.

Under **App Information**:

- **Subtitle:** from `listing/en-US/subtitle.txt` (and each language's file).
- **Category:** Photo & Video. Second: Lifestyle.
- **Age Rating:** answer None or No to every question. Expect 4+.
- **Content Rights:** the app shows no third party content.
- **Digital Services Act (EU):** say whether you are a trader. A free app that earns
  nothing is usually "not a trader".

Under **App Privacy**:

- **Privacy Policy URL:**
  `https://github.com/njarrar/PicturesFromthisday/blob/main/play-store/privacy-policy.md`
- **Data collection:** No, we do not collect data from this app. Photos and location
  are read on the iPhone only and never leave it, and Apple only counts data that
  leaves the device.

Under **Pricing and Availability:** price Free, all countries.

**Export compliance:** the build already answers this (the app uses no encryption of
its own), so App Store Connect won't ask.

Then click **Add for Review** and **Submit**. Reviews often take one or two days.

## 9. Later updates

1. Raise `MARKETING_VERSION` (like 1.17) and `CURRENT_PROJECT_VERSION` (like 18) in
   `ios/BackThen.xcodeproj/project.pbxproj`. Apple refuses a build number it has seen
   for that version.
2. Run the App Store workflow.
3. In App Store Connect click **+** next to iOS App, type the new version, paste the
   text from `listing/<lang>/release-notes.txt` into **What's New**, pick the build and
   submit.

Notes:
- A push to GitHub never sends anything to Apple. Only the Run workflow button does.
- Without the secrets, the workflow still builds the app and takes screenshots, and
  skips the upload.
- The certificates last a year. When they run out, redo step 3 and update the two
  `APPLE_CERTS` secrets. The API key lasts until you revoke it.
