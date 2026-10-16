# Built apps

The [Build apps workflow](../.github/workflows/build.yml) puts these here. Run it
again from the Actions tab after app changes.

| File | What it is | Where it runs |
|---|---|---|
| `BackThen-Android.apk` | Debug build of the Android app | Any Android 8 or later phone. Open the file on the phone and allow installs from that source. |
| `BackThen-Android.aab` | Release bundle for Google Play, made by the [Play Store workflow](../.github/workflows/play.yml) | Upload to the Play Console. It can't be installed on a phone directly. See [play-store/README.md](../play-store/README.md). |
| `BackThen-iOS-Simulator.zip` | Unsigned iPhone app built for the simulator | Only the iOS Simulator on a Mac. Unzip it and drag `Back Then.app` onto a running simulator. It will not install on a real iPhone. |

A build for a real iPhone needs an Apple Developer account and its signing
certificate and profile. With those, a signed build can go out through TestFlight.
