# Mind Space: what the public record says about starting captures from outside ColorOS

Ticket: heykay-47/SnapKeyMap#39 (parent #37, blocks #42). Researched 2026-10-28.

## Short answer

The public record has the package name and the user-facing behaviour. It has **no** documented intent, action, deep link, exported component or broadcast for adding a capture to Mind Space, and no report of anyone triggering one from Tasker, MacroDroid or a key remapper. Everything below the package name needs the device (`dumpsys package`, manifest extraction).

## Established (primary or first-party sources)

| Fact | Source |
| --- | --- |
| Mind Space package is `com.oplus.aimemory` (APK title "Breeno Memory", label "Mind Space"), versions 16.0.311 to 16.11.4, min SDK 35, target SDK 36. | APKMirror: https://www.apkmirror.com/apk/oneplus-ltd/breeno-memory/breeno-memory-16-0-317-release/breeno-memory-16-0-317-android-apk-download/ ; ColorOS System Apps channel: https://t.me/s/oneoxygenos?before=4119 ; Bayton database: https://sysapps.bayton.org/packages/com.oplus.aimemory |
| The same package is on OnePlus (CPH2653, Android 16) and on Oppo; the channel marks some builds "INDIA yes, CHINA no". | Bayton (above); https://t.me/s/oneoxygenos?before=3327 |
| Snap Key (Settings > Snap Key) can be set to "Save to Mind Space". Short press saves the page; press and hold records speech, and the system saves the recording automatically when you finish. | ColorOS 16.0 User Manual: https://m.media-amazon.com/images/I/A1jX9eLPpYL.pdf |
| Alternate triggers for the same capture: 3-finger swipe up, and a "Save to Mind Space" action on the screenshot thumbnail. Supports video, multi-image posts and screenshots; works in landscape. | ColorOS 16.0 User Manual (above) |
| OnePlus uses the same app: Plus Key press saves the screen, long press records an audio memo of up to 60 seconds. | https://www.oneplus.com/global/oneplus-ai ; https://community.oneplus.com/thread/1890181804061622275 |
| Captures are analysed by AI after saving (categories, tags, calendar events, expense detection). Screenshots and receipts saved to Mind Space are processed. | ColorOS 16 manual; https://community.oppo.com/thread/2125846309754634241 ; https://www.androidauthority.com/oneplus-ai-plus-mind-3561402/ |
| ColorOS 17 adds Long-doc Capture and saving YouTube/TikTok links. | https://www.oppo.com/en/newsroom/stories/oppo-color-os-17-ai/ |
| Gemini can read Mind Space content (app-side integration, found in Google app strings). That is Gemini reading, not a way to write. | https://www.androidauthority.com/gemini-oneplus-mind-space-apk-teardown-3586032/ |

## Share targets (ACTION_SEND)

- No official statement that Mind Space accepts shares.
- A OnePlus 15 / OxygenOS 16 user thread (posted Feb 27) asks for a "share with MindSpace" entry in the share menu and for File Dock drag-and-drop, which implies that at the time it had neither: https://communityus.oneplus.com/thread/2069882450879709192
- Not verified: whether later builds (16.1 to 16.11) added a share target. If one exists, a shared screenshot becoming a normal, AI-processed entry is also unverified. The manual says Save to Mind Space on a screenshot thumbnail produces a normal entry, but that is the system path, not a share.

## Third-party remappers and automation

- SelenStudio/plus-key-remapper (OnePlus 15, package `com.pluskeymap.app`) detects the key through logcat and offers "launch app, send broadcast, custom intent". Its XDA thread asks users to share "clever Intent strings to trigger cool system actions"; none appear for Mind Space. https://xdaforums.com/t/plus-key-remapper.4785945/ , https://github.com/SelenStudio/plus-key-remapper
- No Tasker, MacroDroid or Shizuku report for Mind Space was found in web search. Searches for `com.oplus.aimemory` returned only package databases and APK mirrors.
- Reddit was **not** checked (curl/exa blocked; browser panel not used). Treat the absence of Reddit reports as unknown.
- How OnePlus's own Plus Key / Plus Mind sends captures is undocumented publicly; the path is system-internal (key handler or SystemUI gesture to Mind Space), not a documented intent.

## Implications for #42 (Find X9 device capture)

1. Target package: `com.oplus.aimemory`. Its exported components are not published; capture them on the device:
   - `adb shell dumpsys package com.oplus.aimemory` (activities, services, receivers with intent filters; look for `android.intent.action.SEND*` filters and exported flags).
   - Pull the APK (`pm path`) and read AndroidManifest.xml for custom actions and exported providers.
2. Observe the real trigger while pressing the Snap Key: logcat around `aimemory`, `AIMemory`, SystemUI and the Snap Key handler, and `dumpsys activity` / `dumpsys window` before and after, to see which component or service the system starts.
3. Expect that the capture itself (screen grab, record-until-release) may be done by the system caller, not by `com.oplus.aimemory`; the app may only receive the result through a private or permission-protected interface. Components protected by signature/system permissions would not be reachable by SnapKey Map, matching the earlier hidden-API precedent for Circle to Search.
4. Fallbacks to evaluate on device if no exported entry exists: share a screenshot to Mind Space if a share filter turns up; the 3-finger swipe-up gesture (injection feasibility unknown); the screenshot-thumbnail action.
