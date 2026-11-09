# Sweet Match

A match-3 puzzle game (HTML/JS) with an Android APK wrapper.

| Folder | Contents |
|---|---|
| `web/` | The game: `index.html` + `assets/sprites/` (candy PNGs). Open `web/index.html` via any static server. |
| `models/` | Optimized 3D candy models (GLB), source for artwork. Not used at runtime. |
| `android/` | Android WebView project. Build: `cd android && ANDROID_HOME=<sdk> gradle assembleDebug` (needs JDK 17+, Android SDK 34). Bundles `../web`. |
| `dist/SweetMatch.apk` | Prebuilt debug APK (Android 7.0+). |

Gameplay: swap adjacent candies to match 3+, 25 moves, cascades and combos score more, best score saved locally.
