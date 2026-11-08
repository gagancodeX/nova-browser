# NOVA TV Browser

A lightweight, TV-first browser-style web app designed for the Xiaomi Mi TV 4A 32-inch and other Android TV devices.

## Web edition

The installable web app lives in [web/](web/). It is optimized for TV screens, remote controls, and desktop browsers.

Features:
- TV-friendly address/search bar and browser navigation
- D-pad support: Up, Down, Left, Right, OK/Select, and Back
- Search opens real search results as a normal top-level page
- Google, Bing, and DuckDuckGo search
- Up to three lightweight app tabs
- Bookmarks and browsing history stored locally
- Quick links for popular streaming and information sites
- HTML5 playback for direct MP4, WebM, and Ogg video URLs
- YouTube video embed support when the URL identifies a specific video
- Clear full-browser launch screen for sites that block iframes
- Full-screen video and viewer controls
- PWA manifest and offline app-shell caching

## Render deployment

Render Static Site is connected to this repository:
- Branch: `main`
- Build command: `echo "NOVA web edition is static"`
- Publish directory: `web`

## Important browser limits

This hosted web edition is a browser-style launcher, not a Chromium engine. It cannot display every website inside its own iframe. Search engines, YouTube's homepage, and several streaming services block embedded frames; NOVA now opens search results as normal pages and provides a full-browser launch screen for known restricted sites. DRM-protected streams and codecs depend on the TV's actual browser/WebView engine. For the full Chrome-like experience with site navigation in one window, use the native Android APK edition with Android WebView.

## Device notes

The Mi TV 4A 32-inch has limited memory. Keep tabs to three or fewer and close unused sites to reduce memory pressure.

## License

Personal project. Add a license before redistributing.
