<div align="center">

<a href="https://github.com/technicalnihilist-lgtm/nexa-browser/stargazers"><img src="https://img.shields.io/badge/stars-0-5A31F4?style=for-the-badge&logo=github&logoColor=white" alt="stars: 0"></a>&nbsp;<a href="https://github.com/technicalnihilist-lgtm/nexa-browser/network/members"><img src="https://img.shields.io/badge/forks-0-3FB950?style=for-the-badge" alt="forks: 0"></a>

<h3>A complete, working Android web browser with a Safari-inspired liquid-glass interface</h3>

<img src="https://img.shields.io/badge/Kotlin-5865F2?style=flat-square" alt="Kotlin"> <img src="https://img.shields.io/badge/Android-3B3F4A?style=flat-square" alt="Android"> <img src="https://img.shields.io/badge/Browser-3FB950?style=flat-square" alt="Browser"> <img src="https://img.shields.io/badge/Jetpack%20Compose-EC4899?style=flat-square" alt="Jetpack Compose">

<a href="https://github.com/technicalnihilist-lgtm/nexa-browser/releases/latest"><img src="https://img.shields.io/badge/DOWNLOAD-APK-6D5AE6?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"></a>

<b>Support the work</b>

<img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-COMING%20SOON-4B5563?style=for-the-badge&logo=buymeacoffee&logoColor=white" alt="Buy me a coffee — coming soon"> <img src="https://img.shields.io/badge/UPI-COMING%20SOON-4B5563?style=for-the-badge&logo=googlepay&logoColor=white" alt="UPI — coming soon"> <img src="https://img.shields.io/badge/PayPal-COMING%20SOON-4B5563?style=for-the-badge&logo=paypal&logoColor=white" alt="PayPal — coming soon">

<sub>Donations aren't live yet — Paytm, UPI, PayPal and Buy&nbsp;Me&nbsp;a&nbsp;Coffee all show <b>coming soon</b>. Until then, a ⭐ star is the best support.</sub>

<a href="https://github.com/technicalnihilist-lgtm/nexa-browser/releases/latest"><img src="https://img.shields.io/badge/VERSION-v1.3.59-6D5AE6?style=for-the-badge" alt="VERSION: v1.3.59"></a> <img src="https://img.shields.io/badge/DOWNLOADS-141-3FB950?style=for-the-badge" alt="DOWNLOADS: 141"> <a href="https://github.com/technicalnihilist-lgtm/nexa-browser/releases"><img src="https://img.shields.io/badge/RELEASES-GITHUB-EC4899?style=for-the-badge&logo=github&logoColor=white" alt="RELEASES: GITHUB"></a>

<h4>Get it from</h4>

<a href="https://github.com/technicalnihilist-lgtm/nexa-browser/releases"><img src="https://img.shields.io/badge/GitHub-Releases-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Releases"></a> <a href="https://github.com/technicalnihilist-lgtm/nexa-browser/archive/refs/heads/main.zip"><img src="https://img.shields.io/badge/Source-ZIP-0EA5E9?style=for-the-badge&logo=github&logoColor=white" alt="Source ZIP"></a>

</div>

---

---

<p align="center"><img src="icon/nexa-icon-192.png" width="104" alt="Nexa Browser icon" /></p>

## What is implemented

### Browsing
* Live WebView rendering of real sites, with links, forms, cookies, sessions,
  redirects, JavaScript dialogs, HTTP errors and fullscreen HTML video.
* Address bar that tells a URL from a search query:
  `https://example.com` opens the site, `example.com` opens
  `https://example.com`, `localhost:8080` and `192.168.1.10` open over HTTP, and
  `how does android work` runs a search.
* Back, forward, reload/stop, home, new tab, tab counter, menu, and a real
  loading-progress indicator driven by `onProgressChanged`.
* Page title, favicon, and an accurate secure-connection indicator (lock for
  HTTPS, warning for HTTP, private glyph in private tabs).
* Error and offline states rendered as a glass overlay instead of a blank page.
* Find on page, share, copy link, and a per-tab "request desktop site" toggle.
* A **liquid-glass dock** at the bottom, built on the iOS 26 model. A soft
  capsule glides between slots on a spring and stretches in proportion to the
  distance still to travel; a **glass lens then rides on top of the row**, so the
  active icon is seen *through* it — enlarged, brightened, with a chromatic
  fringe on the rim. Each control has an icon and a label.
* The start-page clock is drawn as **translucent glass numerals** (a light rim
  plus a filled gradient) whenever a wallpaper is set, the way the lock screen
  does it.
* Keyboard-aware address input (the bar lifts with the IME).

### Search engines
Google (default), Bing, DuckDuckGo, Brave Search, plus a custom URL template
(use `%s` for the query). Configured in Settings and applied immediately.

### Tabs
Create, close, duplicate, switch, reorder-by-creation, and close all with a
confirmation. Each tab owns its own WebView and therefore its own navigation
history and scroll position. Recently closed tabs can be reopened. Tabs are
persisted as URLs and restored on the next launch.

Android kills a WebView's renderer whenever it needs the memory back, and until
now an app that did not answer that callback was killed along with it — one
heavy page could take the whole browser down. The renderer death is now handled:
the dead WebView is thrown away, the tab keeps its id, title, URL and place in
the strip, and the page is reloaded. A page that kills its own renderer on every
attempt stops being reloaded after two tries and shows the normal "couldn't be
loaded" overlay instead, so a recovery can never become a crash loop.

### Private browsing
A visually distinct dark mode with its own tab set. Private visits are never
written to history, bookmarks or the reading list. When the last private tab
closes, the session's DOM storage, WebView cache and (optionally) cookies are
cleared. **The limitations are documented honestly in
`PrivateBrowsingManager`** — see "Known limitations" below.

### Start page
Clock and date, user-managed favourites (add, rename, reorder, delete),
frequently visited sites computed from *real* stored history, and recent visits.
No fabricated or seeded sample data anywhere. Six background presets.

### Bookmarks, History, Reading List
* Bookmarks: add/remove, edit title and URL, folders, shown on the start page.
* History: grouped by day, searchable. Rows are removed by **swiping right** —
  there is no delete button on the row. Bulk clearing by range (last hour /
  today / last week / all) lives in the toolbar. Private sessions are excluded.
* Reading list: save for later, open, remove, and a genuine offline copy via
  `WebView.saveWebArchive` — the "available offline" badge only appears once the
  archive file actually exists on disk.

### Downloads
Built on Android's `DownloadManager`. Shows file name, size, live progress and
status; supports cancel, retry, open and remove; de-duplicates file names;
handles interruption and failure with real reason codes from the system. Progress
is read from the DownloadManager cursor — nothing is faked.

### Extensions

Every extension changes real behaviour; none of them are decorative switches.

| Extension | What it actually does |
|---|---|
| **Ad & Tracker Blocker** | Answers requests to ~150 known advertising, analytics and attribution hosts with an empty response inside `shouldInterceptRequest`. Host and all its subdomains are matched, so the page still loads normally. The list ships in `assets/blocklist.txt` and is deliberately conservative. |
| **Force HTTPS** | Rewrites `http://` navigations to `https://` before the WebView loads them. `localhost`, `127.x`, `::1`, `10.0.2.2` and `.local` are left alone so local testing still works. |
| **Dark Mode for Websites** | Injects a stylesheet that inverts the page while re-inverting images, video, canvas and background images, so photos keep their true colours. |
| **Data Saver** | Sets `loadsImagesAutomatically = false`, so pages load without images. |
| **Clean Links** | Strips `utm_*`, `fbclid`, `gclid`, `msclkid`, `mc_cid` and similar tracking parameters before a URL is loaded. |
| **Custom CSS** | Injects your own stylesheet into every page. |
| **Theme Studio** | Changes the app's accent colour (six presets) and corner roundness (square / rounded / extra rounded). This is the "theme / app UI" extension. |

Page-level extensions are delivered as a single `<style id="nexa-extension-style">`
element injected on `onPageFinished` and re-injected whenever a setting changes,
so toggling one takes effect on the open page immediately. No JavaScript bridge
is exposed to pages — the injection is one-way, from the app into the document.

### Settings
Every setting on the screen changes real behaviour:

| Setting | Effect |
|---|---|
| Search engine / custom URL | Changes the address-bar query template |
| Homepage, New-tab behaviour | Changes where a new tab lands |
| Theme (automatic/light/dark) | Rebuilds the Material colour scheme |
| Text size | Applies `WebSettings.textZoom` |
| Desktop site by default | Applies a desktop user agent |
| JavaScript | Applies `WebSettings.javaScriptEnabled` |
| Pop-ups | Applies `javaScriptCanOpenWindowsAutomatically` + `setSupportMultipleWindows` |
| Cookies | Applies `CookieManager.setAcceptCookie` and third-party cookies |
| Save history | Gates history writes |
| Do Not Track | Injects a `navigator.doNotTrack` shim into each page |
| Restore tabs on start | Gates session restore |
| Start-page clock / background | Changes the start page |
| Address Bar Style | Liquid Glass (default), solid light (black on white), or solid themed |
| Download over Wi-Fi only | Sets `setAllowedOverMetered(false)` |
| Download notifications | Switches the DownloadManager notification visibility |
| Clear history / cache / cookies / website data | Calls the matching platform API |
| Website permissions | Lists remembered camera/mic/location decisions and revokes them |

### Security
* `onReceivedSslError` **always** calls `cancel()`. The app never calls
  `proceed()`, so a MITM certificate cannot be accepted. There is no
  "continue anyway" path.
* A Network Security Configuration blocks cleartext by default, trusts only
  system CAs, and permits HTTP only for `localhost`, `127.0.0.1` and `10.0.2.2`.
* `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` are off,
  and a remote page is blocked from navigating the WebView onto `file://`.
* `javascript:` URLs in links are refused. No JavaScript interface is ever
  registered, so no Java object is exposed to page script.
* Mixed content is never allowed (`MIXED_CONTENT_NEVER_ALLOW`).
* Only a small allow-list of schemes (`mailto`, `tel`, `sms`, `geo`, `market`,
  `intent`, `whatsapp`, `upi`) is handed to external apps.
* Camera, microphone and location are denied unless the user grants them through
  an in-app prompt, and decisions can be remembered or revoked.
* No analytics, no telemetry, no background network requests, and no plaintext
  password storage.

---

## Screens

These images are **UI renders** produced from the same design tokens, sizes and
strings as the app, drawn on the build machine. No emulator can run in the build
sandbox (the host has no KVM), so they are not photographs of a device. Install
the APK to see the real thing.

| | |
|---|---|
| ![Start page](screenshots/01-start-page.png) | ![Browsing](screenshots/02-browsing-liquid-glass.png) |
| **Start page** — clock, favourites, frequently visited from real history | **Browsing** — Liquid Glass bars, page inset below the chrome |
| ![Tab switcher](screenshots/03-tab-switcher.png) | ![Private](screenshots/04-private-browsing.png) |
| **Tab switcher** — card per tab, live title and load state | **Private browsing** — distinct dark palette and honest notice |
| ![History](screenshots/05-history-swipe-to-delete.png) | ![Settings](screenshots/06-settings.png) |
| **History** — swipe a row right to delete it | **Settings** — every option changes real behaviour |
| ![Extensions](screenshots/07-extensions.png) | |
| **Extensions** — ad blocker, HTTPS, dark pages, data saver, Theme Studio | |

The renders include the opaque chrome strip and the inset page, matching the app.

---

## Project layout

```
app/src/main/java/com/nexabrowser/app/
├── MainActivity.kt                     Activity; implements BrowserActivityHost
├── NexaApplication.kt                  WebView warm-up, debug setup
├── browser/                            The engine
│   ├── BrowserTab.kt                   TabInfo + BrowserTab (owns one WebView)
│   ├── BrowserTabManager.kt            Tab lifecycle, active tab, session, sink
│   ├── BrowserWebViewFactory.kt        WebView setup + WebViewClient/ChromeClient
│   ├── BrowserNavigationController.kt  back/forward/reload/stop/load
│   ├── BrowserActivityHost.kt          Platform callbacks the engine needs
│   ├── DelegatingActivityHost.kt       Leak-free indirection to the Activity
│   ├── PrivateBrowsingManager.kt       Private session lifecycle + data clearing
│   └── PageError.kt                    Load-failure model
├── data/
│   ├── local/                          Room database, entities, DAOs
│   ├── prefs/SettingsDataStore.kt      DataStore-backed BrowserSettings
│   └── repository/                     BrowserRepository, DownloadRepository
├── domain/model/                       Bookmark, HistoryEntry, DownloadItem, …
├── ui/
│   ├── BrowserScreen.kt                WebView surface, address bar, toolbar
│   ├── BrowserViewModel.kt             All UI state + orchestration
│   ├── NexaApp.kt                      Root composable, overlays, back handling
│   ├── components/                     AddressBar, BottomToolbar, menu, glass bits
│   ├── screens/                        Start page, tabs, bookmarks, history,
│   │                                   downloads, reading list, settings
│   └── theme/                          Colours, type, glass surface, NexaIcons
└── util/                               UrlUtils (pure, unit-tested), FaviconStore
```

State flows one way: `WebView` events → `BrowserTabManager` (a `TabEventSink`) →
`TabInfo` `StateFlow` → `BrowserViewModel` → Compose. The engine never holds an
Activity reference; `DelegatingActivityHost` is the only bridge, and it is
cleared in `onDestroy`.

---

## Build and install

Requirements: JDK 17, Android SDK Platform 35, Build-Tools 35.0.0.

The Gradle wrapper is committed, so no local Gradle install is needed. If the
script loses its executable bit when the repository is unpacked, run
`chmod +x gradlew` first (or `sh gradlew ...`).

```bash
## Tests

**Unit tests (run, passing — 278 tests, 0 failures):**

```
./gradlew :app:testDebugUnitTest
com.nexabrowser.app.browser.AdBlockPolicyTest           4 tests, 0 failures
com.nexabrowser.app.browser.BlocklistIndexTest          9 tests, 0 failures
com.nexabrowser.app.browser.BlocklistMatchTest          8 tests, 0 failures
com.nexabrowser.app.browser.FaviconDedupeTest          11 tests, 0 failures
com.nexabrowser.app.browser.FullscreenSessionTest       5 tests, 0 failures
com.nexabrowser.app.browser.HostDecisionCacheTest       7 tests, 0 failures
com.nexabrowser.app.browser.HttpsUpgradeTest            8 tests, 0 failures
com.nexabrowser.app.browser.LoadOutcomeTest             6 tests, 0 failures
com.nexabrowser.app.browser.MemoryTrimTest              6 tests, 0 failures
com.nexabrowser.app.browser.PageErrorTest               2 tests, 0 failures
com.nexabrowser.app.browser.PageInjectionsTest         17 tests, 0 failures
com.nexabrowser.app.browser.ProgressGateTest            8 tests, 0 failures
com.nexabrowser.app.browser.ReloadTargetTest            5 tests, 0 failures
com.nexabrowser.app.browser.RendererRecoveryTest        6 tests, 0 failures
com.nexabrowser.app.browser.SessionWriteTest            9 tests, 0 failures
com.nexabrowser.app.browser.TabChromeStateTest          5 tests, 0 failures
com.nexabrowser.app.browser.TabLookupTest               7 tests, 0 failures
com.nexabrowser.app.browser.TabRestorePlanTest          5 tests, 0 failures
com.nexabrowser.app.browser.TabSelectionTest            5 tests, 0 failures
com.nexabrowser.app.browser.WebViewSettingsDiffTest     9 tests, 0 failures
com.nexabrowser.app.data.repository.DownloadActiveTest  5 tests, 0 failures
com.nexabrowser.app.data.repository.DownloadProgressRowTest  6 tests, 0 failures
com.nexabrowser.app.data.repository.HistoryDedupeTest  10 tests, 0 failures
com.nexabrowser.app.data.repository.TopSitesMergeTest   7 tests, 0 failures
com.nexabrowser.app.domain.SearchEngineTest             6 tests, 0 failures
com.nexabrowser.app.ui.ScrollAccumulatorTest            6 tests, 0 failures
com.nexabrowser.app.util.ClockTickTest                  5 tests, 0 failures
com.nexabrowser.app.util.DateLabelsTest                 7 tests, 0 failures
com.nexabrowser.app.util.FaviconNamingTest             10 tests, 0 failures
com.nexabrowser.app.util.FaviconPathCacheTest          10 tests, 0 failures
com.nexabrowser.app.util.FaviconPruningTest             6 tests, 0 failures
com.nexabrowser.app.util.FaviconSizingTest             14 tests, 0 failures
com.nexabrowser.app.util.HistoryGroupingTest            7 tests, 0 failures
com.nexabrowser.app.util.UrlUtilsTest                  37 tests, 0 failures
```

They cover the address-bar resolver (URL vs search, host:port, IP, localhost,
percent-encoding, custom templates), secure-scheme detection, URL display and
origin extraction, and search-engine/theme/new-tab parsing. One of them caught a
real bug during development: `localhost:8080` was initially misread as a URL
scheme, and the resolver was fixed accordingly.

**Instrumented tests (written, not run here):**
`app/src/androidTest/.../BrowserPersistenceTest.kt` exercises Room-backed
bookmark toggling, history recording and de-duplication, permission memory, and
session-tab round-tripping. These need a device or emulator:

```
./gradlew :app:connectedDebugAndroidTest
```

**Not performed in this environment:** no emulator or physical device was
available, so the app was never launched on Android. Everything below that
depends on runtime behaviour is verified by compilation and code review only —
see "Known limitations".

---

## Known limitations

### WebView compatibility (not Safari)
Nexa renders with the *system* WebView, whose engine version depends on the
device. Sites that need a newer Chromium, or features the installed WebView lacks
(WebRTC codecs, some PWA/Web App Manifest behaviours, certain CSS features, some
DRM/EME video), may render differently from Safari, Chrome or Firefox. WebView
also cannot match a standalone browser's process isolation.

### Private browsing is not isolated storage
Android WebView has **one** app-wide cookie store and HTTP cache. There is no
per-tab incognito profile. Therefore:
* Clearing the private session also clears data that regular tabs may have set.
* Cookies set inside a private tab remain visible to regular tabs until the
  session ends.
True isolation would require running private tabs in a separate process with its
own WebView data directory.

Private mode also does not hide activity from websites, the network operator, an
employer, or anyone who can observe the device or its traffic. The app does not
claim otherwise.

### Tab previews are not screenshots
Tab cards show the live title, favicon and origin, not a rasterised thumbnail.
Drawing a detached WebView is unreliable across OEM WebView builds and costs a
lot of memory per tab.

### Chrome and the page
The bars sit on an **opaque chrome strip** and the page is **inset** by the
chrome height, so a site's own header can never run underneath the address bar.
That was the cause of the "two search bars" look: before, a page like a Google
results page rendered its own search field directly behind the floating bar.

The app draws exactly one search/address bar. A website's own search field is
page content and is not removable — it now sits cleanly below the chrome instead
of behind it.

### Glass and backdrop blur
Settings offers three bar styles: **Liquid Glass** (the default), Solid light
(black on white) and Solid themed.

The Liquid Glass pane is a five-layer composite — a three-stop body gradient, a
diagonal specular sheen, a rim-light border, a lit top lip and a shaded bottom
edge — over a soft elevation shadow. Every glass surface in the app (bars, tab
cards, shortcut tiles, the find bar, the error card, the tab-switcher controls)
uses it, so the visual language stays consistent.

Compose exposes no public backdrop-blur modifier, so the pane itself is layered
translucency rather than a real refraction. Where a true backdrop blur *is*
possible it is used: the page genuinely blurs behind the browser menu via a
`RenderEffect` (API 31+).

### Other
* The start page's background is chosen from six presets; an arbitrary image is
  not supported.
* `saveWebArchive` is best-effort: some sites produce a partial archive.
* Session restore stores URLs and titles only. WebView state itself cannot be
  serialised, so restored tabs reload their pages.
* Do Not Track is delivered to page scripts (`navigator.doNotTrack`); WebView
  exposes no API to add the HTTP `DNT` header.
* Download destinations use the public Downloads collection via DownloadManager;
  a custom folder picker is not implemented.

---

## Glass Browser (second app in this repository)

> **Glass Browser now has its own repository:
> [technicalnihilist-lgtm/glass-browser](https://github.com/technicalnihilist-lgtm/glass-browser).**
> Development continues there — this folder is a snapshot of v1.1 and is no
> longer updated. It is kept here only so existing links keep working.

`glass-browser/` holds **Glass Browser** — a separate, much smaller Android
browser: a single-file HTML chrome over a native WebView content surface,
written in Java with no Gradle, shipped as an 85 KB signed APK. It shares the
liquid-glass look and nothing else: different package
(`com.rajok.glassbrowser`), different architecture, its own history.

Highlights: real WebView rendering (no iframe, so sites that forbid embedding
still load), pop-up to new tab, downloads, file pickers, per-tab back/forward
that collapses `http → https → www` redirects, and a screenshot button that
captures the whole window with `PixelCopy` into `Pictures/Glass Browser`.

See [`glass-browser/README.md`](glass-browser/README.md) for details, the build
script and screenshots.

---

## Licence

MIT.

---

---

## 🌍 Languages

<sub>Donations are <b>coming soon</b> in every language below — nothing to pay today, nothing to click.</sub>

<div align="center">

<sub><b>हिन्दी · বাংলা · मराठी · తెలుగు · தமிழ் · ગુજરાતી · ಕನ್ನಡ · മലയാളം · ਪੰਜਾਬੀ · ଓଡ଼ିଆ · অসমীয়া · اردو</b></sub>

</div>

<details>
<summary><b>🌍 Open the same message in 53 languages</b></summary>

**🇮🇳 Indian languages**

- **हिन्दी** (Hindi) — ☕ जल्द ही आ रहा है। · ⭐ अगर आपको यह ऐप और हमारा काम पसंद है, तो कृपया रिपॉजिटरी को स्टार दें और हमें फॉलो करें।
- **বাংলা** (Bengali) — ☕ খুব শিগগিরই আসছে। · ⭐ যদি আপনার এই অ্যাপ এবং আমাদের কাজ ভালো লাগে, তবে অনুগ্রহ করে রিপোজিটরিটিকে একটি স্টার দিন এবং আমাদের ফলো করুন।
- **मराठी** (Marathi) — ☕ लवकरच येत आहे. · ⭐ जर तुम्हाला हे ॲप आणि आमचं काम आवडलं असेल, तर कृपया रिपॉझिटरीला स्टार द्या आणि आम्हाला फॉलो करा.
- **తెలుగు** (Telugu) — ☕ త్వరలో రాబోతోంది. · ⭐ మీకు ఈ యాప్, మా పని నచ్చితే, దయచేసి రిపోజిటరీకి ఒక స్టార్ ఇచ్చి మమ్మల్ని ఫాలో అవ్వండి.
- **தமிழ்** (Tamil) — ☕ விரைவில் வருகிறது. · ⭐ உங்களுக்கு இந்த ஆப்பும் எங்க வேலையும் பிடிச்சிருந்தா, தயவுசெஞ்சு இந்த ரெபாசிட்டரிக்கு ஒரு ஸ்டார்…
- **ગુજરાતી** (Gujarati) — ☕ ટૂંક સમયમાં આવી રહ્યું છે. · ⭐ જો તમને આ એપ અને અમારું કામ ગમતું હોય, તો પ્લીઝ રિપોઝિટરીને સ્ટાર આપો અને અમને ફોલો કરો.
- **ಕನ್ನಡ** (Kannada) — ☕ ಶೀಘ್ರದಲ್ಲೇ ಬರುತ್ತಿದೆ. · ⭐ ನಿಮಗೆ ಈ ಆ್ಯಪ್ ಮತ್ತು ನಮ್ಮ ಕೆಲಸ ಇಷ್ಟವಾದರೆ, ದಯವಿಟ್ಟು ರೆಪೊಸಿಟರಿಗೆ ಸ್ಟಾರ್ ಕೊಡಿ ಮತ್ತು ನಮ್ಮನ್ನು ಫಾಲೋ ಮಾಡಿ.
- **മലയാളം** (Malayalam) — ☕ ഉടൻ വരുന്നു. · ⭐ നിങ്ങൾക്ക് ഈ ആപ്പും ഞങ്ങളുടെ പ്രവർത്തനവും ഇഷ്ടപ്പെട്ടെങ്കിൽ, ദയവായി റെപ്പോസിറ്ററിക്ക് ഒരു സ്റ്റാർ…
- **ਪੰਜਾਬੀ** (Punjabi) — ☕ ਜਲਦੀ ਆ ਰਿਹਾ ਹੈ। · ⭐ ਜੇ ਤੁਹਾਨੂੰ ਇਹ ਐਪ ਅਤੇ ਸਾਡਾ ਕੰਮ ਪਸੰਦ ਹੈ, ਤਾਂ ਕਿਰਪਾ ਕਰਕੇ ਰਿਪੋਜ਼ਟਰੀ ਨੂੰ ਸਟਾਰ ਦਿਓ ਅਤੇ ਸਾਨੂੰ ਫਾਲੋ ਕਰੋ।
- **ଓଡ଼ିଆ** (Odia) — ☕ ଖୁବ୍ ଶୀଘ୍ର ଆସୁଛି। · ⭐ ଯଦି ଆପଣଙ୍କୁ ଏହି ଆପ୍ ଏବଂ ଆମର କାମ ଭଲ ଲାଗୁଛି, ତେବେ ଦୟାକରି ରିପୋଜିଟୋରୀକୁ ଗୋଟିଏ ଷ୍ଟାର୍ ଦିଅନ୍ତୁ ଏବଂ ଆମକୁ ଫଲୋ କରନ୍ତୁ।
- **অসমীয়া** (Assamese) — ☕ অতি সোনকালেই আহি আছে। · ⭐ যদি আপুনি এই এপটো আৰু আমাৰ কাম ভাল পায়, তেন্তে অনুগ্ৰহ কৰি ৰিপজিটৰীটোক এটা ষ্টাৰ দিয়ক আৰু আমাক ফ'ল' কৰক।
- **اردو** (Urdu) — ☕ جلد آرہا ہے۔ · ⭐ اگر آپ کو یہ ایپ اور ہمارا کام پسند آئے تو براہ کرم ریپازٹری کو سٹار دیں اور ہمیں فالو کریں۔
- **संस्कृतम्** (Sanskrit) — ☕ शीघ्रमेव आगच्छति।
- **मैथिली** (Maithili) — ☕ जल्दिये आबि रहल अछि।
- **कोंकणी** (Konkani) — ☕ रोखडेंच येता.
- **کٲشُر** (Kashmiri) — ☕ جَلدی یِوان چھُ۔
- **سنڌي** (Sindhi) — ☕ जल्द अची रह्यो आहे।
- **डोगरी** (Dogri) — ☕ जल्दी आ करदा ऐ।
- **ꯃꯤꯇꯩ ꯂꯣꯟ** (Manipuri) — ☕ ꯑꯊꯨꯕꯥ ꯃꯇꯝꯗꯥ ꯂꯥꯛꯀꯅꯤ꯫
- **बर’** (Bodo) — ☕ थाबैनो फैगोन।
- **ᱥᱟᱱᱛᱟᱲᱤ** (Santali) — ☕ ᱞᱚᱜᱚᱱ ᱦᱤᱡᱩᱜ ᱠᱟᱱᱟ᱾
- **नेपाली** (Nepali) — ☕ छिट्टै आउँदैछ। · ⭐ यदि तपाईंलाई यो एप र हाम्रो काम मन पर्‍यो भने, कृपया रिपोजिटरीलाई स्टार दिनुहोस् र हामीलाई फलो गर्नुहोस्।

**🌍 World languages**

- **العربية** (Arabic) — ☕ قريبًا. · ⭐ إذا أعجبك هذا التطبيق وعملنا، فيرجى منح المستودع نجمة ومتابعتنا.
- **简体中文** (Chinese (Simplified)) — ☕ 即将推出。
- **繁體中文** (Chinese (Traditional)) — ☕ 即將推出。
- **Čeština** (Czech) — ☕ Již brzy.
- **Dansk** (Danish) — ☕ Kommer snart.
- **Nederlands** (Dutch) — ☕ Binnenkort beschikbaar. · ⭐ Vind je deze app en ons werk leuk?
- **Filipino** (Filipino) — ☕ Malapit na.
- **Suomi** (Finnish) — ☕ Tulossa pian.
- **Français** (French) — ☕ Bientôt disponible. · ⭐ Si cette application et notre travail vous plaisent, mettez une étoile au dépôt et suivez-nous.
- **Deutsch** (German) — ☕ Bald verfügbar. · ⭐ Wenn dir diese App und unsere Arbeit gefallen, gib dem Repository einen Stern und folge uns.
- **Ελληνικά** (Greek) — ☕ Έρχεται σύντομα.
- **עברית** (Hebrew) — ☕ בקרוב.
- **Magyar** (Hungarian) — ☕ Hamarosan.
- **Bahasa Indonesia** (Indonesian) — ☕ Segera hadir. · ⭐ Jika kamu suka aplikasi ini dan karya kami, beri bintang pada repositori ini dan ikuti kami.
- **Italiano** (Italian) — ☕ In arrivo.
- **日本語** (Japanese) — ☕ 近日公開。 · ⭐ このアプリと私たちの仕事が気に入ったら、リポジトリにスターを付けてフォローしてください。
- **한국어** (Korean) — ☕ 곧 공개됩니다. · ⭐ 이 앱과 저희 작업이 마음에 드신다면 저장소에 스타를 눌러 주시고 팔로우해 주세요.
- **Bahasa Melayu** (Malay) — ☕ Akan datang.
- **Norsk** (Norwegian) — ☕ Kommer snart.
- **فارسی** (Persian) — ☕ به‌زودی.
- **Polski** (Polish) — ☕ Wkrótce. · ⭐ Jeśli podoba Ci się ta aplikacja i nasza praca, daj repozytorium gwiazdkę i obserwuj nas.
- **Português** (Portuguese) — ☕ Em breve.
- **Română** (Romanian) — ☕ În curând.
- **Русский** (Russian) — ☕ Скоро. · ⭐ Если вам нравится это приложение и наша работа, поставьте звёздочку репозиторию и подпишитесь на нас.
- **Español** (Spanish) — ☕ Muy pronto. · ⭐ Si te gusta esta app y nuestro trabajo, dale una estrella al repositorio y síguenos.
- **Kiswahili** (Swahili) — ☕ Inakuja hivi karibuni.
- **Svenska** (Swedish) — ☕ Kommer snart.
- **ไทย** (Thai) — ☕ เร็ว ๆ นี้
- **Türkçe** (Turkish) — ☕ Çok yakında.
- **Українська** (Ukrainian) — ☕ Незабаром.
- **Tiếng Việt** (Vietnamese) — ☕ Sắp ra mắt. · ⭐ Nếu bạn thích ứng dụng này và công việc của chúng tôi, hãy tặng kho lưu trữ một ngôi sao và theo…

</details>

