# EV FastRoute — iOS ⇄ Android parity

Goal: **both apps at the same standard.** iOS (SwiftUI/MapKit, repo `ken012/App-ev-fastroute`)
is the canonical spec. Android is native Kotlin/Compose. The *intelligence* (routing, charging,
objectives) is ported **test-for-test** so behavior is provably identical; the UI is
platform-native but must match feature-for-feature.

## Free stack (Android)
| Concern | iOS | Android | Notes |
|---|---|---|---|
| Map render | MapKit | **MapLibre GL** | free, no key |
| Tiles/style | (MapKit) | **OpenFreeMap** | free, no key |
| Routing | MKDirections | **OpenRouteService** | free key (~2k/day); paid/self-host at scale |
| Address search | MKLocalSearch | **Photon** (OSM) | free, no key |
| Chargers | Open Charge Map | Open Charge Map | same API + key |
| Storage | UserDefaults | DataStore | |

## Ported logic (pure Kotlin `:core`, JVM-testable on free CI)
| iOS source | Android | Tests ported | Status |
|---|---|---|---|
| `ChargePlan.swift` (ChargePlanner) | `ChargePlanner.kt` | `ChargePlannerTest` | ✅ done |
| haversine (NavigationTracker) | `Geometry.kt` | `GeometryTest` | ✅ done |
| `RouteOptimizationService` energyPlan | `EnergyModel.kt` | `EnergyModelTest` | ✅ done |
| `RouteObjective.swift` | `RouteObjective.kt` | `RouteObjectiveTest` | ✅ done |
| Charger projection / corridor | `Corridor.kt` | `CorridorTest` | ✅ done |
| Planner models (Charger/Vehicle/ConnectorType/ProjectedCharger) | `Models.kt` | `ChargerScoringTest` | ✅ done |
| Charger scoring (speedScore/networkMatches/sequenceKey) | `ChargerScoring.kt` | `ChargerScoringTest` | ✅ done |
| Beam-search sequence selection (objective-aware) | `ChargerSequenceSelector.kt` | `ChargerSequenceSelectorTest` | ✅ done |
| Build route from sequence+leg data (SOC walk → RouteOption) | `RoutePlanner.buildRoute` | `RoutePlannerTest` | ✅ done |
| Optimize: best-per-objective + dedup-merge + comparator | `RoutePlanner.optimize` | `RoutePlannerTest` | ✅ done |
| Live planning glue (direct→project→beam-search→build→optimize) | `:app` TripPlanner | (net-bound) | ✅ |
| `RangeEstimator.swift` | `RangeEstimator.kt` | `RangeEstimatorTest` | ✅ done |
| `PlaceSearch.swift` (ranker) | `PlaceRanker.kt` | `PlaceRankerTest` | ✅ done |

## App/services (Android `:app`, added after core CI is green)
| Feature (iOS) | Android | Status |
|---|---|---|
| OCM response parsing → Charger (+ connector/reliability) | `OpenChargeMap.kt` (:core) | `OpenChargeMapTest` ✅ |
| OCM HTTP fetch | `:app` OpenChargeMapClient | ⬜ |
| ORS directions parsing → RouteLeg | `OpenRouteService.kt` (:core) | `OpenRouteServiceTest` ✅ |
| ORS HTTP fetch | `:app` net/OrsClient | ✅ |
| Photon geocoding parsing → PlaceCandidate | `Photon.kt` (:core) | `PhotonTest` ✅ |
| Photon HTTP fetch | `:app` net/PhotonClient | ✅ |
| Map + charger pins + route line | MapLibre Compose | ⬜ |
| Planner (start/dest search + battery/buffer + Find Route) | Compose PlannerApp | ✅ v1 |
| Route options list (title/ETA/stops/cost/itinerary) | Compose RouteCard | ✅ v1 |
| Arrival timeline | Compose | ⬜ |
| Live traffic overlay | (limited on free stack) | ⬜ |
| Sequential nav handoff (Google/Waze/geo) | Android intents | ⬜ |
| Settings (units, region, preferred nav, etc.) | Compose | ⬜ |

## Verification
- `:core` builds + tests on free GitHub Actions (Linux) — proves logic parity.
- `:app` builds an APK on CI; distributed to testers via **Firebase App Distribution** (free).
- Keep this checklist current: a feature isn't "done" until it exists on **both** platforms.
