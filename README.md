# NOVA TV Browser

<p align="center"><img src="assets/nova-brand.svg" alt="NOVA Browser — Browse Beyond" width="760"></p>

<p align="center"><a href="assets/nova-brand.svg">View / download the SVG brand logo</a> · <a href="https://github.com/gagancodeX/nova-browser/releases/latest/download/NOVA-TV-Browser.apk">Download latest Android TV APK</a></p>

NOVA has two editions:

- **Native Android TV APK** — the recommended version for your Xiaomi Mi TV 4A 32-inch. Websites load as normal top-level pages inside Android WebView, rather than inside cross-site iframes.
- **Web preview** — the browser-style interface hosted on Render at https://nova-tv-browser.onrender.com. It is limited by iframe restrictions and is not the full browser app.

## Native APK features

- Search Google and open website URLs in the same browser window
- YouTube and other websites load directly in the native WebView
- TV remote mouse pointer: D-pad moves the visible arrow, OK/Enter clicks
- Back button returns through page history
- Address bar with Android TV on-screen keyboard
- Three tabs (low-memory cap for 1 GB TVs)
- Back, forward, reload and home
- Bookmarks and browsing history
- Downloads via Android DownloadManager
- HTML5 video and fullscreen custom video playback
- Desktop-site user-agent mode
- Popup/new-window tab handling
- Leanback launcher support and TV banner
- No ad blocking by default, so it is less likely to break pages

## Compatibility

- Minimum Android version: Android 6.0 (API 23)
- Target Android version: API 28 for older TV firmware compatibility
- Requires Android System WebView on the TV
- Built for landscape TV screens

The 2018 Mi TV 4A 32-inch has limited memory. NOVA intentionally limits the browser to three tabs.

## Download the APK

<https://github.com/gagancodeX/nova-browser/releases/latest/download/NOVA-TV-Browser.apk>

**[Download the latest NOVA TV Browser APK](https://github.com/gagancodeX/nova-browser/releases/latest/download/NOVA-TV-Browser.apk)**

[View all releases and checksums](https://github.com/gagancodeX/nova-browser/releases)

Every successful Android build now publishes a GitHub Release with stable asset names:
- `NOVA-TV-Browser.apk` — latest installable APK
- `NOVA-TV-Browser.apk.sha256` — checksum for verifying the APK

The version-specific GitHub Actions artifact is also uploaded for 30 days. [Open build workflow](https://github.com/gagancodeX/nova-browser/actions/workflows/build-native-apk.yml).

## Install on Xiaomi TV

1. Copy `NOVA-TV-Browser.apk` to a USB drive.
2. Connect the USB drive to the TV.
3. Open the TV File Manager and select the APK.
4. Allow the File Manager to install unknown apps if Android asks.
5. Install and open **NOVA TV Browser** from Apps.

## Known limitations

This app uses the Android System WebView installed on the TV. It is not a Chromium fork and cannot guarantee every desktop website will work. Modern sites may require a newer WebView than the TV firmware provides. DRM-protected services such as some subscription streaming playback depend on device DRM support and may not play in WebView. Playback resolution and codecs depend on the TV hardware and firmware.

The Render-hosted web preview is not the native browser and should not be used as the final TV browser experience.

## Repository layout

- `android/` — native Android TV application
- `web/` — hosted web preview
- `.github/workflows/build-native-apk.yml` — APK build workflow


## TV pointer controls (v1.2.0)

While a website is open:
- Press remote arrows to move the on-screen pointer.
- Press OK / Select / Enter to click a link, button, video control or input.
- Use Back to return through website history.
- The pointer is positioned over the webpage, not the browser toolbar.


## Native browser extras (1.3.0)

- TV mode and desktop-site mode toggle
- Optional built-in ad request filter (off by default) for common ad-network hostnames
- Per-site ad allowlist
- Search provider cycle: Google, Bing, DuckDuckGo
- New-window links open in a browser tab (up to 3 tabs)
- Find in page, text zoom, share/copy current URL
- Reload without cache
- Focus address bar and pointer on/off controls in the remote menu

The ad blocker is a lightweight hostname filter, not a full extension engine or a regularly updated EasyList/uBlock filter engine. Some websites can break when blocking is enabled; turn it off or allow ads for that site if needed.

HTML5 playback depends on the video format, codec, DRM and Android System WebView version installed on the TV. The browser cannot guarantee playback on every streaming site.


### 1.4.0 notes

- TV and Desktop site modes use the installed WebView's Chromium version in their user-agent strings.
- New-window website links are handled by real WebView tabs.
- Ad allowlisting now exempts third-party ad requests while visiting the allowed site.
- Downloads include the site's current session cookies.


## 1.4.1 Android 6 compatibility notes

- NOVA now reports the actual Android release and installed WebView/Chromium version in **Menu → Browser engine info**.
- The TV-mode user agent is based on the installed WebView's real user agent; NOVA no longer falsely claims Android 9 or invents a Chrome version.
- Open WebView's Play Store listing from the diagnostics screen to check for the last compatible update.

Android 6 is outside current Chromium WebView support. Google documents that Android 5/6 devices can only receive their final supported WebView release and cannot install newer WebView updates after that point. If the TV firmware offers the Android TV 9 OTA, install it from Settings → System update / MIUI TV version before judging website compatibility. YouTube may still reject a legacy WebView even after NOVA is updated.


## 1.4.2 diagnostics for TVs without Play Store

The **Menu → Browser engine info** screen now also lists CPU ABIs and links to the Android 6-compatible Google WebView release page. It does not install WebView automatically. Android only accepts a package update if its package name, signing certificate and device firmware provider configuration are compatible.

For Android 6, the last Google WebView branch listed for API 23 is 105.0.5195.136. This is legacy and may still be rejected by current websites such as YouTube.


## 1.4.3 home dashboard

The native Android TV home screen has been redesigned with:
- Left TV navigation rail
- Search field with Google/Bing/DuckDuckGo cycling
- TV-friendly quick launch carousel
- Current open tabs with direct resume and close-all action
- Bookmarks, history, downloads and browser tools shortcuts
- Recent browsing history list
- Quick status chips for TV/Desktop mode, ad blocker and pointer


## 1.4.4 Cursor and settings update

### Cursor settings
- Cursor movement speed: 5–40 dp per key press, with repeat acceleration
- Cursor size: 24–60 dp
- Cursor shapes: Arrow, Crosshair, Ring
- Cursor colours: White, Cyan, Yellow, Lime, Magenta, Orange
- Optional dark outline for contrast
- Cursor enable/disable and reset-to-centre action
- Click pulse feedback

### Browser settings
- Dedicated Browser settings dialog
- Ad blocker switch and site allowlist
- TV/Desktop site mode switch
- Google/Bing/DuckDuckGo search provider
- Cursor settings shortcut
- Browser engine diagnostics
- Clear browsing data action


## Brand assets

- Main scalable vector logo: [assets/nova-brand.svg](assets/nova-brand.svg)
- Web edition logo asset: [web/assets/nova-brand.svg](web/assets/nova-brand.svg)

The SVG logo is a scalable vector recreation based on the NOVA artwork. It is used for repository branding and can be reused in presentations, websites and promotional material.


## 1.4.5 startup diagnostics

This build wraps app initialization with a crash catcher. If startup fails, NOVA displays the exception and offers a **Copy error details** action instead of returning silently to the launcher. Send that diagnostic text or a clear photo to identify the exact device-specific cause.


## 1.4.6 startup fix

Fixed the duplicate `content.addView(shell)` call in the native home screen. The home shell is now attached to the content container exactly once, preventing the `IllegalStateException: The specified child already has a parent` crash on launch.
