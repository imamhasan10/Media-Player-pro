# MediaPlay Pro (Android)

Kotlin + Jetpack Compose + Media3. Music player, video player, library, playlists,
equalizer, sleep timer, audio trimmer, and a download manager (direct links only).

## APK বানানোর নিয়ম (GitHub)
1. GitHub এ নতুন repo বানাও, এই ফোল্ডারের সব ফাইল push করো (`.github` ফোল্ডারসহ).
2. Repo → **Actions** ট্যাব → **Build APK** workflow নিজে চলবে (বা **Run workflow** চাপো).
3. ৫-১০ মিনিট পর run খুলে নিচে **Artifacts → MediaPlayPro-APK** ডাউনলোড করো.
4. Zip খুলে `MediaPlayPro-release.apk` ফোনে install করো ("Install unknown apps" চালু করে).

Release হিসেবে প্রকাশ: `git tag v1.0.0 && git push origin v1.0.0` — APK Releases পেজে চলে যাবে.

## Local build
`gradle assembleDebug` (Gradle 8.7, JDK 17, Android SDK 34)

## Notes
- Release APK debug key দিয়ে sign করা. Play Store এর জন্য নিজের keystore বসাও.
- Downloads: Android DownloadManager ব্যবহার করে — direct http(s) media link. Pause/resume নেই (cancel/retry আছে).
- Trimmer: lossless, শুধু AAC/M4A.
