# Android build, signing, and tester distribution

The repository supports three distinct outputs. Do not confuse them:

1. **Keyless verification build** — safe for public CI artifacts; search/map work, protected routing
   and charging APIs intentionally do not.
2. **Protected internal-test build** — contains provider credentials or a restricted proxy token;
   share only with invited testers.
3. **Signed Play build** — uses the permanent Android upload key and a production service proxy.

Mobile `BuildConfig` values can be extracted from an APK/AAB. CI secret masking prevents a key from
appearing in logs; it does not make the key secret after installation. Direct ORS/OCM keys are
acceptable only for a small private beta. A broad public release needs an EV FastRoute-controlled
proxy or API gateway with rate limiting, quotas, monitoring, abuse controls, and provider
credentials held server-side. `ORS_BASE_URL`, `OCM_BASE_URL`, and `PHOTON_BASE_URL` are configurable
so API-compatible proxy endpoints can be introduced without another client rewrite.

## Local/CI configuration

Values can be supplied as Gradle properties or environment variables:

| Name | Purpose |
|---|---|
| `ORS_API_KEY` | openrouteservice credential, or the restricted token expected by your proxy |
| `OCM_API_KEY` | Open Charge Map credential, or the restricted token expected by your proxy |
| `ORS_BASE_URL` | Defaults to `https://api.heigit.org/openrouteservice` |
| `OCM_BASE_URL` | Defaults to `https://api.openchargemap.io/v3` |
| `PHOTON_BASE_URL` | Defaults to the public Photon demo; replace/self-host for production volume |
| `PRIVACY_POLICY_URL` | Public hosted policy shown in Settings |
| `SUPPORT_URL` | Monitored support page shown in Settings |
| `VERSION_CODE` | Positive Play version code; every uploaded build must increase |
| `VERSION_NAME` | User-facing version, for example `0.2.12` |

For a local private build, place credentials in `~/.gradle/gradle.properties`, never in this repo:

```properties
ORS_API_KEY=...
OCM_API_KEY=...
```

## GitHub Actions verification

`Android production gate` runs on pull requests, `main`, `codex/**`, and manual dispatch. It runs:

- all `:core` and app JVM unit tests;
- Android lint;
- debug APK and minified release AAB builds;
- a 16 KB zip check plus ELF LOAD-alignment checks for every packaged 64-bit native library;
- an API 35 emulator launch/Compose smoke test.

The `android-keyless-builds` artifact is deliberately uncredentialed and the release AAB is
deliberately unsigned. It proves buildability; it is not a Play upload.

The optional Firebase job still runs only from `main` and only when `FIREBASE_APP_ID` and
`FIREBASE_SERVICE_ACCOUNT` exist. Add `ORS_API_KEY` and `OCM_API_KEY` as protected GitHub secrets for
a functional private tester APK. A debug APK is not the right long-term update channel because
runner-generated debug signatures are not stable.

## Codemagic

`codemagic.yaml` defines two workflows:

- **Android verification**: keyless tests, lint, APK/AAB, and 16 KB validation.
- **Signed Android internal release**: protected tests/lint plus signed release APK/AAB.

One-time setup for the signed workflow:

1. Generate a permanent upload keystore, store two offline backups, and never commit it.
2. Codemagic Team settings → **Code signing identities** → **Android keystores** → upload it with
   reference name exactly `evfastroute-android-upload`.
3. Create a secret variable group named exactly `android-production`.
4. Add `ORS_API_KEY` and `OCM_API_KEY` to that group for the private beta. Before public release,
   replace the direct provider configuration with production proxy URLs/tokens.
5. Start **Signed Android internal release** manually. Download `app-release.apk` for direct testers
   or `app-release.aab` for Play Console.

The Gradle signing block accepts Codemagic's `CM_KEYSTORE_*` variables and generic
`ANDROID_KEYSTORE_*` variables. The build fails closed if only part of a signing identity is set.

## Google Play internal testing

1. Decide the permanent package name before the first upload. This project currently uses
   `com.evfastroute.android`; Play package IDs cannot be changed for an existing app.
2. Create the app in Play Console and enable **Play App Signing**.
3. Run the signed Codemagic workflow and upload its AAB to **Testing → Internal testing**.
4. Complete the required policy/listing declarations in `PLAY_SUBMISSION.md`.
5. Add tester emails or a Google Group, publish the internal-test release, and open the opt-in link
   on each test device.
6. After the first manual upload succeeds, optionally add a narrowly permissioned Google Play
   service account to Codemagic for automatic internal-track publishing.

Keep the original upload keystore indefinitely. If it is lost, recovery is possible only through
Google's upload-key reset process; if Play App Signing was not enabled, loss can be unrecoverable.

## Release commands

Keyless verification:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease
```

Signed release (environment variables omitted here intentionally):

```bash
./gradlew :app:lintRelease :app:assembleRelease :app:bundleRelease
```

Never upload an AAB until `apksigner`/`jarsigner`, the 16 KB script, and a real-device smoke test
have all passed for the same commit.
