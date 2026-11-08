# NOVA TV Browser

A lightweight, TV-first browser-style web app designed for the Xiaomi Mi TV 4A 32-inch and other Android TV devices.

## Web edition

The installable web app lives in [web/](web/). It is a static HTML/CSS/JavaScript project, optimized for TV screens, remote controls, and desktop browsers.

Features:
- TV-friendly address/search bar and browser navigation
- Google, Bing, and DuckDuckGo search
- Up to three lightweight tabs
- Bookmarks and browsing history stored locally
- Quick links for popular streaming and information sites
- HTML5 playback for direct MP4, WebM, and Ogg video URLs
- YouTube embed support
- Full-screen viewer and open-full-site fallback
- Keyboard and remote-friendly focus states
- PWA manifest and offline app-shell caching

### Run locally

Open `web/index.html` in a browser, or serve the `web/` directory with any static HTTP server.

### Render deployment

Create a **Static Site** connected to this repository:
- Branch: `main`
- Build command: `echo "NOVA web edition is static"`
- Publish directory: `web`

### Browser limitations

The in-page viewer uses an iframe. Some websites explicitly prevent embedding; use **Open full site** for those websites. This web edition is not a Chromium engine or an unrestricted network proxy. DRM-protected streaming services, codecs, and playback quality depend on the browser and device. Browser extensions and system-level ad blocking are not available to this web app.

## Device notes

The Mi TV 4A 32-inch has limited memory. Keep tabs to three or fewer and close unused sites to reduce memory pressure.

## License

Personal project. Add a license before redistributing.
