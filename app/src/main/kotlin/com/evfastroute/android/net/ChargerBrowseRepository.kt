package com.evfastroute.android.net

import com.evfastroute.core.Charger

internal typealias BrowseChargerFetcher = suspend (
    bounds: OcmBounds,
    maxResults: Int,
    minPowerKw: Int,
) -> ServiceResult<List<Charger>>

/**
 * Loads every station class for map browsing, then supplements it with a DC-only page so a dense
 * Level-2 area cannot consume OCM's result cap before fast chargers are returned. Either request
 * is independently useful; only two failures make the browse fail.
 */
internal class ChargerBrowseRepository(
    private val fetcher: BrowseChargerFetcher = { bounds, maxResults, minPowerKw ->
        OcmClient.chargers(
            minLat = bounds.minLat,
            minLon = bounds.minLon,
            maxLat = bounds.maxLat,
            maxLon = bounds.maxLon,
            maxResults = maxResults,
            minPowerKw = minPowerKw,
        )
    },
) {
    suspend fun chargers(
        bounds: OcmBounds,
        maxResults: Int = 300,
        minPowerKw: Int = 0,
    ): ServiceResult<List<Charger>> {
        val primary = fetcher(bounds, maxResults, minPowerKw)
        if (minPowerKw > 0) return primary

        val supplementalDc = fetcher(bounds, maxResults, 25)
        val successful = listOfNotNull(
            (primary as? ServiceResult.Success)?.value,
            (supplementalDc as? ServiceResult.Success)?.value,
        )
        if (successful.isNotEmpty()) {
            return ServiceResult.Success(mergeUniqueChargers(successful.flatten()))
        }
        return primary as? ServiceResult.Failure
            ?: supplementalDc as ServiceResult.Failure
    }
}

internal fun mergeUniqueChargers(chargers: List<Charger>): List<Charger> {
    val seen = mutableSetOf<String>()
    return chargers.filter { seen.add(it.id) }
}
