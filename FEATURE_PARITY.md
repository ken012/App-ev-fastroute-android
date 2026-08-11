# EV FastRoute cross-platform feature contract

EV FastRoute is one product with native iOS and Android clients. A product feature is not complete
until its user-facing behavior is implemented and verified in both repositories:

- iOS: `ken012/App-ev-fastroute`
- Android: `ken012/App-ev-fastroute-android`

Platform-native APIs and presentation may differ, but inputs, safety rules, stored settings,
route/energy behavior, major labels, empty/error states, and expected outcomes must match.

## Required workflow for every feature

1. Write the shared behavior and acceptance cases before implementation.
2. Audit both clients and identify data-model, persistence, service, UI, privacy, and test changes.
3. Implement both clients in the same delivery cycle. Never silently leave one client behind.
4. Port deterministic logic tests to both platforms. Add UI/smoke coverage for visible entry points.
5. Update privacy, store-submission, support, and parity documentation when data use or behaviour changes.
6. Pass the iOS code-verification/TestFlight gates and Android verification/signed-release gates.
7. Exercise both signed builds on physical devices before calling the feature release-ready.

If a platform cannot support equivalent behavior, stop and obtain explicit product approval. Record
the exception, user impact, fallback, and planned resolution in both repositories. A technical
constraint is not permission to omit a feature silently.

## Pull-request checklist

- [ ] Shared behavior and safety invariants are documented.
- [ ] iOS implementation and tests are linked.
- [ ] Android implementation and tests are linked.
- [ ] Persistence migrations are safe on both platforms.
- [ ] Loading, empty, offline, permission-denied, and provider-error states are covered.
- [ ] Privacy/store disclosures were reviewed.
- [ ] Both CI verification workflows are green.
- [ ] Both signed builds were smoke-tested on physical devices.
