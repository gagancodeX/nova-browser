# NOVA TV Browser

A TV-first browser experience for the Xiaomi Mi TV 4A 32-inch and other Android TV devices.

## Editions

- **Web edition** — a responsive, installable browser-style web app in [web/](web/). It is optimized for TV screens, remote controls, and desktop browsers.
- **Native Android edition** — the Android WebView APK project is maintained separately from the web edition.

## Web edition

The web app includes:
- TV-friendly address/search bar and browser navigation
- Google, Bing, and DuckDuckGo search
- Multi-tab workspace (up to 3 tabs)
- Bookmarks and browsing history stored locally
- Quick links for popular streaming and information sites
- HTML5 video playback for direct media URLs
- YouTube embed support
- Full-screen viewer and desktop/mobile link opening
- Keyboard and remote-friendly focus states
- PWA manifest and offline app-shell caching

### Run locally

Open `web/index.html` in a browser, or serve the `web/` directory with any static HTTP server.

### Render deployment

Create a **Static Site** connected to this repository:
- Branch: `main`
- Build command: `echo "NOVA web edition is static"`
- Publish directory: `web`

The in-page viewer uses an iframe. Some websites explicitly prevent embedding; use **Open full site** for those websites. The web edition is not a Chromium engine or an unrestricted network proxy. DRM-protected streaming services and codecs depend on the browser and device.

## Device notes

The original Mi TV 4A 32-inch has limited memory and an HD-class display. Keep tabs to three or fewer and close unused sites to reduce memory pressure.

## License

Personal project. Add a license before redistributing.
