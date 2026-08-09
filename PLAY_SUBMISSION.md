# Google Play submission checklist

Code can automate build correctness; the publisher must complete the account, policy, legal, and
device-validation items below. Treat unchecked release-blocking items as blockers, not paperwork to
defer until after launch.

## Identity and signing

- [ ] Confirm `com.evfastroute.android` is the permanent package ID before the first Play upload.
- [ ] Create the Play Console app and enable Play App Signing.
- [ ] Generate/upload the permanent upload keystore as described in `DISTRIBUTION.md`; keep two
      independent offline backups and record the passwords in a password manager.
- [ ] Use a strictly increasing `VERSION_CODE` for every uploaded AAB.

## Store listing assets

- [ ] Final app name, short description, full description, category, and support contact.
- [ ] Production 512×512 PNG store icon. The in-app adaptive icon is present, but Play requires a
      separate high-resolution listing asset.
- [ ] At least two representative phone screenshots from a physical device, including planner,
      route map/options, and the charging itinerary. Do not use mocked results as live claims.
- [ ] 1024×500 feature graphic; tablet screenshots only if tablet layouts have been device-tested.
- [ ] Declare English as the launch locale. The current UI is English-only.

## Privacy, data safety, and content

- [ ] Host `PRIVACY.md` at the exact public `PRIVACY_POLICY_URL` used in the release build and keep
      the support tracker monitored.
- [ ] Complete Google Play **Data safety** from observed release behavior: place queries, selected
      route/stop coordinates, optional foreground device location, and ordinary network metadata
      leave the device to provide app functionality. No account, ads, analytics, or data sale are
      present in this version.
- [ ] Declare foreground approximate/precise location as optional and explain it powers “current
      location” and arrival prompts; do not declare background location.
- [ ] Complete content rating, target audience, ads (“No”), app access, government, financial,
      health, and other Play policy questionnaires accurately.
- [ ] Review `THIRD_PARTY_NOTICES.md`, OCM provider licenses, OpenFreeMap terms, Photon usage policy,
      and openrouteservice terms immediately before release.

## Production services

- [ ] Rotate any provider key that has been pasted into chat, logs, tickets, or another uncontrolled
      location.
- [ ] Replace direct mobile provider keys with controlled proxy/gateway credentials before broad
      public distribution. Add rate limits, per-install abuse controls/app attestation, monitoring,
      budget/quota alerts, and a provider-outage dashboard.
- [ ] Obtain capacity/SLA appropriate for the audience. Public Photon and free ORS/OCM tiers are not
      a million-user backend; self-host or contract managed capacity.
- [ ] Verify the hosted privacy/support URLs and every provider attribution link from the release APK.

## Required release validation

- [ ] Green `Android production gate` or Codemagic verification on the release commit.
- [ ] Green signed Codemagic release build, signature verification, R8 build, lint, and 16 KB native
      library validation.
- [ ] API 35/36 emulator smoke test and physical tests on at least: API 26–28, API 31–33 with
      approximate-only location, and API 35/36 with gesture navigation/dark mode.
- [ ] Drive a safe real-world route with no charging, one charging stop, multiple charging stops,
      user waypoints, Fewest stops, unavailable network, provider rate limit, denied location, and
      external Google/Waze/default-map handoff.
- [ ] Verify station coordinates/connector power against operator apps; verify the car's edited
      battery/consumption values survive relaunch; verify saved-trip/session expiry behavior.
- [ ] Internal testing first, then closed testing. Review crash/ANR, battery, network, and tester
      feedback before any production rollout.

## Launch control

- [ ] Start production with a staged rollout and written rollback criteria.
- [ ] Keep the previous known-good AAB/version and service configuration available.
- [ ] Publish a support/incident process for unsafe routes, stale chargers, privacy requests, and
      provider outages.
