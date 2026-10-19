Autodroid keeps you logged in to the IIT Guwahati campus Wi-Fi portal.

## What's new in 2.3.1
- **Login now tile** now lights up while it logs in, shows the result (a message plus 10 seconds on the tile), then turns off again.
- A manual login (tile or in-app button) is tried **once and never retried**; automatic logins keep their retries.
- The two Quick Settings tiles now have **different icons**: an arrow into a door for Login now, a power symbol for Auto-login.

## All features
- Automatic login and re-login, triggered by Wi-Fi changes, a re-login timer, and a session check.
- Acts only on the Wi-Fi networks you add; add the connected network with one tap.
- Renews right away when Android reports no internet on a saved network.
- Failed logins retry, then send a notification and try again after a wait.
- Log out button, on/off switch, dark theme.
- Settings with defaults (Edit/Save, changes logged, reset to defaults): re-login every, session check every, attempts per round, first retry delay, wait after a failed round.
- Two Quick Settings tiles: **Login now** works even when auto-login is off, and **Auto-login** toggles the whole feature like the in-app switch. Add each from Diagnostics.
- A "Reset all settings to defaults" button that appears only when a setting has been changed.
- Diagnostics section: recent activity, permission checklist and the log viewer (with Share and Clear).
- About screen with the developer credit and a link to the GitHub repository.
- Credentials stored on the phone, encrypted with the Android Keystore.

## Install
Download the APK below and open it on your phone. It has the same app id and signing key as the earlier `com.autodroid` builds, so it installs over them. If you still have an older build with a different app id, uninstall it first.
