# Autodroid

A small Android app that keeps you logged in to the IIT Guwahati campus Wi-Fi captive portal.

The portal ends your session after about 20 minutes even though the phone stays on Wi-Fi. Browser
extensions already handle this on a desktop. Autodroid does the same on Android, without a browser
and without a constantly running service.

## How it logs in

It follows the same flow as the IITG Chrome extensions, over HTTPS to `agnigarh.iitg.ac.in:1442`:

1. `GET /login?` and read the hidden `magic` and `4Tredir` fields from the page.
2. `POST /` with `username`, `password`, `magic` and `4Tredir`.
3. `GET /keepalive?` to confirm. HTTP 200 means you are logged in.

Requests go over a raw TLS socket bound to the Wi-Fi network, with the server name set explicitly.
The portal closes connections from Android's default HTTP client, so a plain `HttpURLConnection`
does not work.

## When it runs

There is no polling loop. A check starts when:

- Android reports a change on the Wi-Fi (connected, disconnected, or "no internet").
- The re-login timer fires (default 15 minutes, editable in the app).
- A background job fires (about every 15 minutes, the Android minimum).
- The phone reboots or the app is updated.
- You tap **Login now**, save settings, add a Wi-Fi to the list, or switch the app on.

Each check acts only on Wi-Fi networks in your list. If the portal says you are logged out, it logs in.
A failed login is retried 3 times, then you get a notification and it tries again 2 minutes later.
A wrong password is not retried; you get a notification instead.

## Features

- On/off switch for the whole app.
- Wi-Fi list: add the network you are connected to, or remove one.
- Re-login interval, read-only until you press **Edit**; changes are written to the log.
- **Log out** button (calls `/logout?` and pauses auto-login until you tap **Login now**).
- Log viewer with Share and Clear, plus a "Recent activity" panel on the main screen.
- Failure notifications.
- Dark theme.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state, Wi-Fi state | Talk to the portal and see the connection |
| Location (and "all the time") | Android only shows the Wi-Fi name to apps with location access |
| Notifications | Tell you when a login fails |
| Battery unrestricted | Stops Android from delaying checks while the phone is idle |
| Receive boot completed | Re-register the Wi-Fi listener after a reboot |

If the Wi-Fi name can't be read, the app still tries the portal. It gives up quietly when the
portal can't be reached, so other networks are not affected.

## Privacy

- Your username and password are stored on the phone only, encrypted with the Android Keystore.
- They are sent only to the portal host.
- The portal's certificate is accepted without validation, **only** for connections to
  `agnigarh.iitg.ac.in`. Everything else uses normal checks.
- The log never contains your password.

## Build

There is no local Android Studio setup. GitHub Actions builds a debug APK on every push
(`.github/workflows/build.yml`: JDK 17, Gradle 8.9).

1. Push to `master`.
2. Open the repo's **Actions** tab, then the latest run.
3. Download the `iitg-autologin-debug` artifact and unzip it to get `app-debug.apk`.
4. Copy the APK to your phone and install it. Android will ask you to allow installs from that app.

If a build fails, open the failed step and look for lines starting with `e:`.

## Troubleshooting

- Open **Log** in the app. Each step of a login attempt is logged with its result.
- On phones that kill background apps (Xiaomi, Oppo, Vivo, OnePlus and others), turn on Autostart and set
  battery to "No restrictions" for Autodroid. Avoid "Force stop".
- A debug APK may be signed with a different key from one build to the next. If Android refuses to
  update, uninstall the old version and install the new one.

## Project layout

```
app/src/main/java/com/iitg/autologin/
  MainActivity.kt      settings screen
  PortalClient.kt      login, keepalive and logout over TLS
  LoginWorker.kt       the decision logic for each check
  Scheduler.kt         Wi-Fi listener and scheduled work
  NetworkReceiver.kt   receives Wi-Fi change events
  BootReceiver.kt      re-registers after reboot
  CredentialStore.kt   encrypted settings
  LogStore.kt          file logger
  Notifier.kt          failure notification
  WifiUtil.kt          Wi-Fi name and network helpers
```

## Credits

The portal flow was worked out from these open-source IITG extensions:
[imsahil37/iitg-wifi-autologin](https://github.com/imsahil37/iitg-wifi-autologin) and
[Samuel-Aktar-Laskar/IITG-Auto-Login-Extension](https://github.com/Samuel-Aktar-Laskar/IITG-Auto-Login-Extension).

Not affiliated with IIT Guwahati.
