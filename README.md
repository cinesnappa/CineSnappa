<div align="center">

  <img src="composeApp/src/commonMain/composeResources/drawable/app_logo_wordmark.png" alt="CineSnappa" width="300" />

  <p>
    A media hub for Android and iOS, with Stremio addon support and the latest
    <a href="https://www.youtube.com/@CineSnappa">Cine Snappa Official</a> Shorts on the home screen.
  </p>

</div>

## About

CineSnappa is a fork of [Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile) by tapframe
and the Nuvio contributors. It is released under the same licence, GNU GPL v3.0 (see `LICENSE`).

### What's different from Nuvio

- Own name, icon and logo (CineSnappa), with its own app ID (`com.cinesnappa.app`), so it installs alongside Nuvio.
- Own link scheme (`cinesnappa://`), so its links don't open Nuvio.
- **CineSnappa Shorts row** on the home screen: the latest uploads from @CineSnappa, a Subscribe button and a "See all" card.
  The code is in `composeApp/src/commonMain/kotlin/com/nuvio/app/features/cinesnappa/`.
- The in-app updater checks this fork's own GitHub releases (set `gitHubOwner` in `features/updater/AppUpdater.kt`).

## Legal

Like Nuvio, CineSnappa is only a client. It shows metadata and plays media from extensions and sources
that the user installs. It is meant for content the user owns or is otherwise allowed to access.
It does not host, store or distribute any media.

## Building

See **CINESNAPPA_BUILD_GUIDE.md** for step-by-step Windows instructions.

```bash
./gradlew :androidApp:assembleFullDebug     # Android APK
./scripts/run-mobile.sh android             # build, install and launch on a connected phone
```

## Credits

Built on Nuvio Mobile: https://github.com/NuvioMedia/NuvioMobile. All credit for the core app goes to its authors.
