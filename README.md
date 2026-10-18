# BoomTube (Android APK)

WebView wrapper for https://213.158.167.154:4042/boom2

## Build the APK (pick one)

### A) GitHub Actions (no installs needed)
1. Create a new private GitHub repo and push this whole folder to it.
2. Repo > Actions > "Build APK" > Run workflow (it also runs on every push).
3. When it finishes, download the artifact **BoomTube-apk** -> BoomTube.apk
4. Copy to the phone and install (allow "Install unknown apps").

### B) Android Studio
File > Open this folder > wait for Gradle sync > Build > Build APK(s).
APK: app/build/outputs/apk/debug/app-debug.apk

## Features
- Name: BoomTube, icon: WE logo
- Accepts the server self-signed certificate (for 213.158.167.154 only)
- Session kept between launches (cookies re-installed for 30 days + localStorage)
- Username/password captured after a successful login, stored encrypted
  (Android Keystore), auto-filled and auto-submitted once per app launch
- GPS check-in works (location permission prompt, passed to the page)
- Excel/file downloads are saved to the Downloads folder
- To forget saved login: Android Settings > Apps > BoomTube > Storage > Clear data

## Change the URL
Edit HOST and START_URL at the top of app/src/main/java/com/we/boomtube/MainActivity.java
