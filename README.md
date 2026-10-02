# Back Then

**See what this day looked like, every year.**

Back Then is an iPhone and Android app that opens on today's date and shows the
photos you took on this same day in past years, one year at a time. Pick any other
day from a calendar, keep the best shots in Favorites, and gather photos into albums
you can share.

Both apps are fully native: Swift and SwiftUI on iPhone, Kotlin and Jetpack Compose
on Android. They read photos from the phone's own library and, if you choose, from
Google Photos.

<table>
  <tr>
    <th>Today</th>
    <th>Pick a day</th>
    <th>Photo viewer</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/ios/today.png" width="240" alt="Today screen on iPhone: years with photos from this date, newest first"></td>
    <td><img src="docs/screenshots/ios/calendar.png" width="240" alt="Calendar sheet on iPhone, with dots on days that have photos"></td>
    <td><img src="docs/screenshots/ios/viewer.png" width="240" alt="Full-screen photo viewer on iPhone with heart, share and album buttons"></td>
  </tr>
  <tr>
    <th>Favorites</th>
    <th>An album</th>
    <th>First launch</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/ios/favorites.png" width="240" alt="Favorites tab on iPhone"></td>
    <td><img src="docs/screenshots/ios/album.png" width="240" alt="An album on iPhone with Share album button"></td>
    <td><img src="docs/screenshots/ios/1-welcome.png" width="240" alt="Welcome screen on iPhone"></td>
  </tr>
</table>

### On Android

<table>
  <tr>
    <td><img src="docs/screenshots/android/today.png" width="200" alt="Today screen on Android"></td>
    <td><img src="docs/screenshots/android/calendar.png" width="200" alt="Date picker dialog on Android"></td>
    <td><img src="docs/screenshots/android/viewer.png" width="200" alt="Photo viewer on Android"></td>
    <td><img src="docs/screenshots/android/album.png" width="200" alt="An album on Android"></td>
  </tr>
</table>

The photos in these screenshots are samples from [Lorem Picsum](https://picsum.photos),
stamped with past dates. The [Screenshots workflow](.github/workflows/screenshots.yml)
takes them from the real apps on an iPhone simulator and an Android emulator. (A
simulator can't be given photo access without a tap, so on iPhone the samples load
the same way Google Photos picks do.)

## What it does

- **Today.** Every past year that has photos on this date, newest first. Each year
  gets a large year number, how long ago it was, and a mosaic of that day's photos.
  Pull down to check the library for new photos.
- **Pick a day.** A month-and-day calendar (no year, since every year shows at once).
  Dots mark days with photos. A day with no photos offers the closest days that have some.
- **Photo viewer.** Swipe through a day's photos. Heart one, share it, or add it to an album.
- **Favorites.** Every photo you've hearted, from any day.
- **Albums.** On Today, tap Select, pick photos, then tap Album. Share an album
  through the share sheet, or save it as a real album in Photos (iPhone) or under
  Pictures/Back Then (Android).
- **Daily reminder.** One notification a day at a time you choose, and only on days
  that have photos from past years. Tapping it opens that day.
- **Looks native on each phone.** A floating tab bar and sheets on iPhone; a
  navigation bar and Android's own date dialog on Android. Light and dark mode.

## Where the photos come from

**The phone's library.** PhotoKit on iPhone, MediaStore on Android. On Android this
already includes Google Photos pictures that are stored on the phone. Nothing leaves
the phone.

**Google Photos.** Since March 31, 2025, Google no longer lets other apps read a
user's whole Google Photos library
([Google's notice](https://developers.google.com/photos/support/updates)). The
supported way in is the **Google Photos Picker**: you pick photos in a Google screen
and the app gets only those. Back Then keeps a copy of each picked photo on the
phone, adds it to the day-by-day view, and skips shots that are already in the
phone's library. You can pick more at any time from the "..." menu, or disconnect
Google Photos to delete the copies.

## Running the apps

| | iPhone | Android |
|---|---|---|
| Needs | Xcode 16, iOS 17 or later | Android Studio, Android 8 or later |
| Open | `ios/BackThen.xcodeproj` | the `android/` folder |
| Run | pick your team under Signing, then Run | Run, or `cd android && ./gradlew installDebug` |
| Tests | built in CI | `./gradlew testDebugUnitTest` |

GitHub Actions builds both apps on every push. The Android run also uploads a debug
APK you can install on a phone.

### Turning on Google Photos

The apps work without this, using the phone's library.

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project and
   enable the **Photos Picker API**.
2. Set up the OAuth consent screen with the scope
   `https://www.googleapis.com/auth/photospicker.mediaitems.readonly`.
3. **iPhone:** create an **iOS** OAuth client for bundle ID `com.njarrar.backthen`
   and paste its client ID into `ios/BackThen/GoogleConfig.swift`.
4. **Android:** create an **Android** OAuth client for package `com.njarrar.backthen`
   with your signing key's SHA-1. No code change is needed.

## How the code is laid out

The two apps share no code, but each has the same core logic, so they behave alike:
a day index that counts photos per day and year, the nearby-days search, the check
that drops Google copies of phone photos, and the mosaic patterns.

```
ios/BackThen/
  Core/      dates, day index, mosaic layout
  Data/      PhotoKit, Google sign-in and picker, reminders, app state
  Views/     Today, calendar, viewer, Favorites, Albums, onboarding
android/app/src/main/java/com/njarrar/backthen/
  core/      the same core logic, with unit tests
  data/      MediaStore, Google picker, reminders, saved state
  ui/        Compose screens
design/      the source design (open "On This Day.dc.html") and the Nunito font license
```

Debug builds accept a `scene` launch argument (`today`, `calendar`, `viewer`,
`favorites`, `album`) that fills the app with sample data. The Screenshots workflow
uses it; release builds ignore it.

## Not built yet

- **Share an album as a link.** The design shows `backthen.app/a/...` links. That
  needs a server to host the photos, so for now albums share as files.
- **Hidden dates** and the year-by-year "strip" layout from the design's options.
