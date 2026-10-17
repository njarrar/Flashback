# Publishing Back Then on Google Play

Everything the Play Console asks for is in this folder or made by the
[Play Store workflow](../.github/workflows/play.yml). Work through the steps in order.

| What | Where |
|---|---|
| Package name | `com.njarrar.backthen` |
| Version | 1.9.0 (version code 10) |
| Release bundle | [`build/BackThen-Android.aab`](../build/) |
| Store text, four languages | [`listing/`](listing) |
| App icon, feature graphics | [`graphics/`](graphics) |
| Phone screenshots, four languages | [`screenshots/`](screenshots) |
| Privacy policy | [`privacy-policy.md`](privacy-policy.md) |

## 1. Package name

The app's package name is **`com.njarrar.backthen`**. It is the app's ID on Google
Play and can never change after the first upload. It doesn't need a website behind
it; it only has to be unique on Play, and the Console tells you on the first
upload if it is taken. The iPhone app uses the same
ID.

## 2. Make an upload key (once, on your Mac)

Play needs every upload signed with your own key. Google then signs the copies
people download with its own key (Play App Signing), so if you ever lose the upload
key, Google can reset it. Never commit the key to the repo.

In Terminal (Android Studio ships the `keytool` program):

```sh
KEYTOOL="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"
"$KEYTOOL" -genkeypair -v -keystore ~/backthen-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

It asks for a password and your name. Use the same password when it asks for the
key password. Keep `~/backthen-upload.jks` and the password in a safe place, such as
your password manager.

## 3. Let GitHub sign the bundle

1. Copy the key as text: `base64 -i ~/backthen-upload.jks | pbcopy`
2. In GitHub, open the repo's **Settings > Secrets and variables > Actions** and add
   four repository secrets:

   | Name | Value |
   |---|---|
   | `UPLOAD_KEYSTORE_BASE64` | paste (from step 1) |
   | `UPLOAD_KEYSTORE_PASSWORD` | your password |
   | `UPLOAD_KEY_ALIAS` | `upload` |
   | `UPLOAD_KEY_PASSWORD` | your password |

3. Open **Actions > Play Store > Run workflow** on `main`. It builds the bundle,
   takes fresh screenshots, and commits them. The run's summary says whether the
   bundle is signed.

Without the secrets the workflow still builds the bundle, but unsigned, and Play
won't take it.

## 4. Developer account

1. Sign up at [play.google.com/console](https://play.google.com/console) as a
   personal account. It costs a one-time 25 US dollars, and Google checks your ID.
2. **New personal accounts must run a closed test first:** at least 12 testers who
   keep the app installed for 14 days in a row before you can apply for production.
   Plan for this. Friends and family with Android phones and a Gmail address work.

## 5. Create the app

**Home > Create app:** name `Back Then`, default language English (United States),
App, Free. Tick the declarations.

## 6. App content (Policy > App content)

- **Privacy policy:**
  `https://github.com/njarrar/PicturesFromthisday/blob/main/play-store/privacy-policy.md`
- **App access:** All functions are available without special access. There is no
  sign-in.
- **Ads:** No, the app has no ads.
- **Content rating:** start the questionnaire, enter your email, category "All
  other app types". Answer No to every question: no violence, fear, sex, language,
  drugs, gambling, no user to user chat or content sharing inside the app, no
  location sharing, no purchases. Expect Everyone / PEGI 3.
- **Target audience:** 13 to 15, 16 to 17, and 18 and over. Leave the under 13
  ages unticked, so the stricter Families rules don't apply. "Appeals to children":
  No.
- **News app:** No.
- **Data safety:**
  - Does your app collect or share any of the required user data types? **No.**
  - Reason: photos are read and shown on the phone only, nothing goes to a server,
    there are no analytics or ads. Sharing a photo is a user's own action through
    the share screen, which Play doesn't count as sharing. The app has no internet
    permission.
- **Advertising ID:** No. The app removes that permission on purpose.
- **Government app, financial features, health:** No to all.
- **Photo and video permissions** (Play asks because the app uses
  `READ_MEDIA_IMAGES`). Pick "core functionality" and paste:

  > Back Then's core feature is showing the photos a user took on today's date in
  > every past year. To find them, the app must read the capture date of every photo
  > in the library each day, including new photos, so a one-time photo picker can't
  > do the job. The app shows the photos on screen only and never uploads them.
  > Users on Android 14 and later can still choose to share only some photos.

## 7. Store listing (Grow users > Store presence > Main store listing)

- **App name, short description, full description:** copy from
  `listing/en-US/`. Then **Manage translations > Add your own translations** for
  French (France) `fr-FR`, Spanish (Spain) `es-ES` and Arabic `ar`, and paste each
  folder's text.
- **App icon:** `graphics/icon-512.png`
- **Feature graphic:** `graphics/en/feature-graphic.png` (and the `fr`, `es`, `ar`
  ones for each translation)
- **Phone screenshots:** all six from `screenshots/en/` (and each language's
  folder for its translation). They are 1080 x 1920, which Play asks for.
- **Category:** Photography. **Tags:** Photography, Photo gallery, Memories.
- **Contact details:** your email. A website is optional; the GitHub page works.

Tablet screenshots are optional and can wait.

## 8. Release

1. **Test and release > Testing > Closed testing > Create track.** Add your testers'
   emails, upload `build/BackThen-Android.aab`, paste the release notes from each
   `listing/<lang>/release-notes.txt`, and roll out.
2. Share the opt-in link with testers. After 14 days with 12 or more testers,
   **Apply for production** from the Dashboard.
3. Then **Production > Create new release**, pick the same bundle from the library,
   and send it for review. Reviews often take a few days for a new app.

For later updates: raise `versionCode` and `versionName` in
`android/app/build.gradle.kts`, run the Play Store workflow, and upload the new
bundle.
