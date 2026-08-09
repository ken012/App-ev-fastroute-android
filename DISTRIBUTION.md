# Getting the Android app onto testers' phones

Two paths — one works today with zero setup, the other adds email-invite distribution.

## 1. Download the APK from CI (no setup, works now)
Every push to `main` builds a debug APK and uploads it as a **GitHub Actions artifact**.

1. Open the repo → **Actions** → the latest **Build & Test** run.
2. Scroll to **Artifacts** → download **app-debug-apk**.
3. Unzip → send `app-debug.apk` to the tester (email / drive / cable).
4. On the phone: enable **Settings → Apps → Special access → Install unknown apps** for the source, then tap the APK.

Good for a handful of testers. No Google account or store review needed.

## 2. Firebase App Distribution (email invites, nicer for a group)
The CI has a `distribute` job that **stays dormant until you add the secrets below**, then
auto-sends each `main` build to your testers by email. Nothing to change in code.

### One-time Firebase setup
1. Create a (free) project at <https://console.firebase.google.com>.
2. Add an **Android app** with package name `com.evfastroute.android`. Copy its **App ID**
   (looks like `1:1234567890:android:abcdef…`).
3. In **App Distribution** → **Testers & Groups**, create a group named **`testers`** and add tester emails.
4. Create a **service account** with the *Firebase App Distribution Admin* role
   (Project settings → Service accounts → Generate new private key) and download the JSON.

### Add two GitHub repo secrets
Repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `FIREBASE_APP_ID` | the Android App ID from step 2 |
| `FIREBASE_SERVICE_ACCOUNT` | the **entire contents** of the service-account JSON |

That's it. The next push to `main` emails the build to the `testers` group. Until both secrets
exist, the job runs and exits cleanly (CI stays green), so it never blocks anything.

> Testers still need "install unknown apps" enabled the first time — Firebase walks them through it.
> This distributes the **debug** APK; switch to a signed release build before a public launch.
