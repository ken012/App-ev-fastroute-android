# EV FastRoute third-party notices and data attribution

This file is also bundled in the Android app and available from Settings. Provider terms and
licenses can change; review them before every public release.

## Software included in the app

- AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines, kotlinx.serialization, OkHttp, and Okio are
  distributed under the Apache License 2.0. A complete copy is bundled as
  `app/src/main/assets/Apache-2.0.txt`.
- Photon geocoder software is Apache-2.0 licensed. EV FastRoute uses either the public Photon
  service or the compatible endpoint configured by the publisher; Photon is not embedded.
- MapLibre Native is distributed under the BSD 2-Clause License. Its complete notice follows:

  Copyright (c) 2021 MapLibre contributors

  Copyright (c) 2018-2021 MapTiler.com

  Copyright (c) 2014-2020 Mapbox

  Redistribution and use in source and binary forms, with or without modification, are permitted
  provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice, this list of
     conditions and the following disclaimer.
  2. Redistributions in binary form must reproduce the above copyright notice, this list of
     conditions and the following disclaimer in the documentation and/or other materials provided
     with the distribution.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
  IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
  OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

MapLibre source, dependency-level notices, and the canonical license are at
<https://github.com/maplibre/maplibre-native>.

## Map and place-search data

- Map style and tiles: OpenFreeMap — <https://openfreemap.org/>.
- Map and Photon place-search data: © OpenStreetMap contributors, available under the Open Database
  License — <https://www.openstreetmap.org/copyright>.
- Photon public demo service: <https://photon.komoot.io/>. It has no availability guarantee and is
  not suitable as an uncontracted high-volume production dependency.
- A device manufacturer may provide Android's platform geocoder. Its provider receives a search
  query only when that geocoder is available; Photon remains the fallback.

The map SDK's on-map attribution control remains enabled.

## Routing

Routes are produced by openrouteservice by HeiGIT using OpenStreetMap data:

> © openrouteservice.org by HeiGIT | Map data © OpenStreetMap contributors

API results are licensed under CC BY 4.0. See <https://openrouteservice.org/terms-of-service/>.

## Charging stations

Charging-station records are supplied by Open Charge Map and its listed data providers. The app
requests only records marked by OCM as open-data licensed (`opendata=true`) and displays the
specific provider and license returned with every planned charging stop.

> Charging-station data © Open Charge Map contributors and applicable listed data providers.

See <https://www.openchargemap.org/about/> for provider-specific attribution and licensing.

## Vehicle catalog

The bundled catalog is OpenEV Data v1.24.0, release date 2025-12-30. It is distributed under the
Community Data License Agreement — Permissive — Version 2.0 (`CDLA-Permissive-2.0`). The complete
agreement is bundled as `app/src/main/assets/CDLA-Permissive-2.0.txt`.

The catalog also contains a small Kia supplement sourced from Kia's publicly available 2024 US
specification pages. Each supplemental record retains its source URL in the catalog model.
