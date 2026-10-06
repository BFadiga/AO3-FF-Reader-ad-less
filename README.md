# AO3 Reader

A personal, ad-free Android reader for [Archive of Our Own](https://archiveofourown.org),
[FanFiction.net](https://www.fanfiction.net) and [Wattpad](https://www.wattpad.com). Unofficial; not affiliated
with any of these sites or the OTW.
Please consider donating to the OTW, which runs AO3.

## Features

- **Three sites, one library**: an AO3 / FF.net / Wattpad switch at the top right of Search and Categories. Each
  site keeps its own tags, fandoms, favorites and block list; the library holds works from all of them, grouped by
  series (fandom) or as one list.
- **Accounts**: sign in on each site's own login page inside the app. Follows (AO3 subscriptions, FanFiction.net
  story alerts) and likes (FanFiction.net favorites) are imported, and following or liking in the app is mirrored
  on the site.
- **Kudos and favorites**: leave AO3 kudos (no account needed) or favorite a FanFiction.net story from its page.
- **Author pages**: tap an author's name to see their works or block them.
- **FanFiction.net search**: pick a fandom from a searchable list, tap genres to include or exclude them, add
  characters, filter by rating, status, language and word count. Blocked genres and characters are excluded
  automatically. Covers are shown in listings and the library; reviews can be read in the app.
- **Wattpad**: search by text and tags (add pinned tags with one tap, leave tags out, hide mature or unfinished
  stories), browse genres, read parts and comments, vote, and sync your Wattpad library when signed in.
  Blocked Wattpad tags are left out of searches automatically.
- **App updates**: every push to `main` publishes a GitHub release; Settings > App updates downloads and installs
  it (the app can also ask when it opens).

- **Categories tab**: favorite tags grouped by section (fandoms & series, genres & tropes, point of view, characters & ships),
  browse fandoms by medium (Anime & Manga, TV, Video Games…), quick genre/POV/relationship-category chips,
  and a tag finder with AO3 autocomplete.
- **Search tab**: free-text search plus filters for title, author, included/excluded tags, rating, completion,
  crossovers, word count, language and sort order.
- **Library tab**: works you follow or downloaded, reading progress, "new chapters" badges, pull to refresh,
  filters (new, reading, not started, downloaded, complete) and sorting.
- **New-chapter notifications**: a background check (every 1–24 hours, optional Wi-Fi only) notifies you
  and can auto-download the new chapters.
- **Offline downloads**: whole works saved on the device; the reader falls back to them when offline.
- **Reader**: font size, line spacing, paragraph spacing, font family, alignment, margins,
  page color (app / black / dark / sepia / light), author notes on/off, keep screen on. The author's own bold,
  italics and other emphasis are always kept. Tap the top/bottom of
  the page to turn it, the middle to show or hide the bars. Progress is saved per work.
- **Blocking**: block any tag (including ratings and warnings) or author; matching works are hidden or collapsed.
- **Appearance**: dark theme by default, light/system options, pure-black mode, accent colors,
  wallpaper colors (Android 12+), compact or comfortable lists.
- Opens `archiveofourown.org/works/...`, `fanfiction.net/s/...` and `wattpad.com/story/...` links shared from a browser.

## How it talks to the sites

Neither site has a public API, so the app reads their regular web pages and parses them with jsoup
(`data/remote/Ao3Parser.kt`, `data/remote/ffn/FfnParser.kt`). Requests go one at a time with a pause between
them and back off when a site asks to slow down.

FanFiction.net sits behind Cloudflare's bot check. The app tries a normal request first and, when the check
answers instead, loads the page in an off-screen WebView (`data/remote/web/HiddenBrowser.kt`). If the check
wants a human, the app shows a Verify button that opens the page so it can be passed by hand.

Signing in happens in a WebView on the site's own login page; the app never sees the password. Cookies are
shared between the WebViews and the app's HTTP client (`WebCookieJar`), so the session carries over.

Works from both sites share one id space: AO3 work ids as-is, FanFiction.net story ids negated (`WorkIds`).

## Building

Requires JDK 17 and the Android SDK (API 35).

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # parser tests (AO3 and FanFiction.net)
```

Pushing to GitHub runs `.github/workflows/build.yml`, which builds debug and release APKs and attaches them to the
run as the `ao3-reader-apk` artifact. The release APK is signed with the debug key so it can be sideloaded directly.

## Project layout

```
app/src/main/java/com/ao3reader/
  data/model      plain models (works, chapters, filters)
  data/remote     AO3 HTTP client, URL builder, HTML parser; ffn/ for FanFiction.net; web/ for WebView helpers
  data/local      Room database (library, favorite tags, block list) and offline download files
  data/prefs      DataStore-backed settings
  data/repo       repositories the UI talks to
  work            background new-chapter checker and notifications
  ui/...          Compose screens (categories, search, library, settings, work, reader, tag, author, login)
```
