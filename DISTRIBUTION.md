# Getting the Android app onto testers' phones

Two paths — one works today with zero setup, the other adds email-invite distribution.

## 1. Download the APK from CI (no setup, works now)
Every push to `main` builds a debug APK and uploads it as a **GitHub Actions artifact**.

1. Open the repo → **Actions** → the latest **Build & Test** run.
2. Scroll to **Artifacts** → download **app-debug-apk**.
3. Unzip → send `app-debug.apk` to the tester (email / drive / cable).
4. On the phone: enable **Settings → Apps → Special access → Install unknown apps** for the source, then tap the APK.

Good for a handful of testers. No Google account or store review needed.

> ⚠️ **The public `app-debug-apk` artifact has NO API keys** (this is a public repo — keys must not
> be baked into a publicly downloadable file). Address search and the map work, but **route
> planning will not** ("Couldn't calculate a driving route"). For live routing, either build locally
> in Android Studio with your keys in `~/.gradle/gradle.properties`, or use the Firebase path below
> (which builds a keyed APK from secrets that only your invited testers receive).

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
| `ORS_API_KEY` *(recommended)* | your free OpenRouteService key — so the distributed build can actually plan routes |
| `OCM_API_KEY` *(recommended)* | your Open Charge Map key |

The `distribute` job **rebuilds a keyed APK from `ORS_API_KEY`/`OCM_API_KEY`** and sends *that* to
Firebase — so, unlike the public artifact, the version your testers receive can plan routes. The keys
are baked only into the Firebase build, never into the public artifact. If you omit the two key
secrets the Firebase build still ships but routing stays inactive.

That's it. The next push to `main` emails the build to the `testers` group. Until `FIREBASE_APP_ID`
and `FIREBASE_SERVICE_ACCOUNT` exist, the job runs and exits cleanly (CI stays green), so it never
blocks anything.

> Testers need "install unknown apps" enabled the first time — Firebase walks them through it.
>
> **Updating a build fails with "app not installed / signature mismatch"?** CI signs the debug APK
> with a keystore the GitHub runner generates fresh each run, so consecutive builds have different
> signatures and Android refuses to update in place. For a first tester round this is fine (they
> install once). For repeated updates without uninstalling, commit a **stable debug keystore** and
> point the debug build at it:
> ```
> keytool -genkeypair -v -keystore app/debug.keystore -storepass android \
>   -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
>   -dname "CN=Android Debug,O=Android,C=US"
> ```
> then in `app/build.gradle.kts` add a `signingConfigs { getByName("debug") { storeFile =
> file("debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword =
> "android" } }` and set `buildTypes.debug.signingConfig`. (`.gitignore` already whitelists
> `debug.keystore`; a debug keystore is not secret.) Switch to a real **release** signing config
> before any public launch.
