# Fast Browser Plus

A lightweight Android WebView browser with a classic Windows-style interface.

Included:
- Back / Forward / Reload / Home
- URL and web search bar
- Cookies and DOM storage
- JavaScript
- Android DownloadManager for normal file downloads
- WebView screenshot (JPG)
- Save Web Archive (MHT)
- Windows-style toolbar toggle
- Settings screen
- GitHub Actions build
- Debug APK artifact

## Mgit replacement

Replace the files in the repository root with the contents of this project.
Do not create an extra `WindowsBrowserProject` folder.

Then commit and push normally. GitHub Actions will build:

`app/build/outputs/apk/debug/app-debug.apk`

The workflow uploads the APK as the artifact named `Fast-Browser-Plus-debug`.
