# NOVA TV Browser

A lightweight, TV-first browser-style web app designed for the Xiaomi Mi TV 4A 32-inch and other Android TV devices.

## Web edition

The installable web app lives in [web/](web/). It is optimized for TV screens, remote controls, and desktop browsers.

Features:
- TV-friendly address/search bar and browser navigation
- D-pad support: Up, Down, Left, Right, OK/Select, and Back
- Search opens real results as a normal top-level browser page
- Google, Bing, and DuckDuckGo search
- Up to three lightweight app tabs
- Bookmarks and browsing history stored locally
- HTML5 playback for direct MP4, WebM, and Ogg video URLs
- YouTube direct-site launch for its homepage (the YouTube homepage blocks iframe embedding)
- YouTube embed support for individual video URLs where the player is available
- Full-browser launch screen for sites that block iframes
- PWA manifest and offline app-shell caching

## Render deployment

Render Static Site is connected to this repository:
- Branch: `main`
- Build command: `echo "NOVA web edition is static"`
- Publish directory: `web`

## Important browser limits

The hosted web edition is a browser-style shell, not a Chromium engine. Websites that block frames must be opened as top-level pages. Selecting **Open full website** navigates the current browser tab to the actual site. DRM playback, codecs, remote control compatibility, and sign-in behavior depend on the browser engine provided by the TV.

## Device notes

The Mi TV 4A 32-inch has limited memory. Keep tabs to three or fewer and close unused sites to reduce memory pressure.

## License

Personal project. Add a license before redistributing.
