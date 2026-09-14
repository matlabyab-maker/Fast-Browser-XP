Fast Browser XP — Clean Build Project

This ZIP is prepared from the user's MGit project and cleaned for GitHub Actions.

Main fixes:
- One MainActivity only: com.fastbrowser.xp.MainActivity
- Android namespace corrected to com.fastbrowser.xp
- Launcher icon exists in mipmap/ic_launcher.png and density folders.
- Old duplicate MainActivity/Settings/VPN source removed.
- Unused Settings/VPN manifest entries removed.
- GitHub Actions builds assembleDebug with Gradle 8.11.1.
- .git is intentionally NOT included; keep the existing MGit repository metadata.

After replacing the project contents in the existing WBB folder:
MGit -> Commit -> Push -> GitHub Actions -> Build.
