# CineSnappa app: build guide (Windows)

This is how to turn the source code into an app on your Android phone or TV box.
The first time takes 30–60 minutes, mostly downloads. After that a rebuild takes a few minutes.

---

## 1. What you need

- A Windows PC with **at least 16 GB of RAM** and about **25 GB of free disk space**.
  With 8 GB of RAM, do step 3b too.
- **Android Studio** (free): https://developer.android.com/studio
  It comes with the Java version the build needs, so you don't install Java separately.
- An Android phone and a USB cable, or you can just copy the APK file to the phone.

## 2. Unzip the project

Unzip `CineSnappaApp.zip` into a short path with **no spaces**, for example:

```
C:\dev\CineSnappaApp
```

Long paths or spaces in the path cause odd build errors on Windows.

## 3. Open it in Android Studio

1. Start Android Studio and choose **Open**, then pick `C:\dev\CineSnappaApp`.
2. Click **Trust Project**.
3. Wait for **Gradle sync** to finish (progress bar at the bottom right). It downloads a lot the first time.
4. If it asks to install a missing **Android SDK Platform** (API 37) or **Build Tools**, click the blue *Install* link.
   You can also install them under **Tools → SDK Manager**.

### 3b. Only if your PC has 8 GB of RAM

Open `gradle.properties` in the project root and change these two lines:

```
kotlin.daemon.jvmargs=-Xmx3072M
org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8 -XX:MaxMetaspaceSize=1024m
```

## 4. Build the APK

**Easy way:** open the **Build Variants** panel on the left edge and set `androidApp` to **fullDebug**.
Then choose **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

**Command way:** open the **Terminal** tab at the bottom of Android Studio and run:

```
gradlew.bat :androidApp:assembleFullDebug
```

When it finishes, the APK is at:

```
androidApp\build\outputs\apk\full\debug\androidApp-full-debug.apk
```

## 5. Install it

**Phone, via USB:** turn on *Developer options → USB debugging* on the phone, plug it in, then press the green ▶ **Run** button in Android Studio.

**Phone, no cable:** send the APK to your phone (Google Drive, Telegram, WhatsApp to yourself),
tap it, and allow "Install unknown apps" when asked.

**Android TV / Fire TV:** upload the APK to Google Drive or similar, then install it on the TV with the *Downloader* app.
It works, but this is the phone layout, so it's not designed for a TV remote.

It shows up as **CineSnappa** with the red C icon. It can sit next to the real Nuvio without clashing.

## 6. First run

1. Open **Settings → Addons** and add the Stremio addon manifest URLs you use.
   Only use addons for content you have the right to watch.
2. Optional: add your own **TMDB API key** in settings for richer artwork and metadata.
   You can get one free at https://www.themoviedb.org/settings/api.
3. The **CineSnappa Shorts** row shows up on the home screen automatically.

## 7. Making changes

| What | Where |
|---|---|
| Channel shown on the home screen | `composeApp/src/commonMain/kotlin/com/nuvio/app/features/cinesnappa/CineSnappaFeed.kt` → `CineSnappaBrand` |
| Look of the Shorts row (colours, card size) | `.../features/cinesnappa/CineSnappaSection.kt` |
| App name | `composeApp/src/androidMain/res/values/strings.xml` → `app_name` |
| Version number | `iosApp/Configuration/Version.xcconfig` (used for Android too) |
| Update checker | `.../features/updater/AppUpdater.kt` → `gitHubOwner`, `gitHubRepo` |

## 8. Sharing the app with other people (release build)

- Debug APKs are fine for you and your friends. For a proper release you need your own signing key.
  Use **Build → Generate Signed App Bundle / APK → Create new…**, then put its details in
  `local.properties` (`NUVIO_RELEASE_STORE_FILE`, `NUVIO_RELEASE_STORE_PASSWORD`, `NUVIO_RELEASE_KEY_ALIAS`,
  `NUVIO_RELEASE_KEY_PASSWORD`). Back up the key file. If you lose it, you can't update the app.
- **GPL-3.0 rule:** because this is based on Nuvio, if you give the app to anyone you must also make
  the source code available, for example as a public GitHub repo. Keep the `LICENSE` file and the credit to Nuvio.
- Don't call it "Nuvio" or use their logo.

## 9. iPhone and desktop

- **iPhone:** building needs a **Mac with Xcode** and an Apple developer account. Open `iosApp/iosApp.xcodeproj` in Xcode.
- **Desktop and a TV-optimised version** are separate Nuvio projects
  (`NuvioDesktop`, which is still in alpha, and `NuvioTV`). They can be forked and rebranded the same way later.

## Troubleshooting

- **"SDK location not found":** open *Tools → SDK Manager* once, or create `local.properties` with
  `sdk.dir=C\:\\Users\\YOU\\AppData\\Local\\Android\\Sdk`.
- **Out of memory / "Gradle daemon disappeared":** do step 3b and close Chrome while building.
- **Shorts row doesn't appear:** it only shows once the YouTube feed loads. Check your internet connection, then close and reopen the app.
