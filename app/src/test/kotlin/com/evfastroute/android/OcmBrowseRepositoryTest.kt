package com.evfastroute.android

import com.evfastroute.android.net.ChargerBrowseRepository
import com.evfastroute.android.net.OcmBounds
import com.evfastroute.android.net.ServiceFailure
import com.evfastroute.android.net.ServiceFailureKind
import com.evfastroute.android.net.ServiceResult
import com.evfastroute.android.net.ocmCacheKey
import com.evfastroute.android.net.ocmPoiUrl
import com.evfastroute.core.Charger
import com.evfastroute.core.ConnectorType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcmBrowseRepositoryTest {
    private val bounds = OcmBounds(45.0, -75.0, 46.0, -74.0)

    @Test
    fun browseRequestOmitsMinimumPowerWhileRouteRequestIncludesIt() {
        val browse = ocmPoiUrl("https://api.openchargemap.io/v3", bounds, 300, 0)!!
        val route = ocmPoiUrl("https://api.openchargemap.io/v3", bounds, 200, 25)!!

        assertNull(browse.queryParameter("minpowerkw"))
        assertEquals("25", route.queryParameter("minpowerkw"))
        assertEquals("(46.0,-75.0),(45.0,-74.0)", browse.queryParameter("boundingbox"))
        assertTrue(ocmCacheKey(bounds, 300, 0) != ocmCacheKey(bounds, 300, 25))
    }

    @Test
    fun dualFetchMergesByStableIdAndKeepsPartialSuccess() = runBlocking {
        val requestedPower = mutableListOf<Int>()
        val level2 = charger("level2", ConnectorType.J1772, 7)
        val dc = charger("dc", ConnectorType.CCS, 50)
        val repository = ChargerBrowseRepository { _, _, minPower ->
            requestedPower += minPower
            ServiceResult.Success(if (minPower == 0) listOf(level2, dc) else listOf(dc))
        }

        val merged = repository.chargers(bounds) as ServiceResult.Success
        assertEquals(listOf(0, 25), requestedPower)
        assertEquals(listOf("level2", "dc"), merged.value.map { it.id })

        val partial = ChargerBrowseRepository { _, _, minPower ->
            if (minPower == 0) ServiceResult.Failure(ServiceFailure(ServiceFailureKind.NETWORK))
            else ServiceResult.Success(listOf(dc))
        }.chargers(bounds) as ServiceResult.Success
        assertEquals(listOf(dc), partial.value)
    }

    @Test
    fun dualFetchFailsOnlyWhenBothRequestsFail() = runBlocking {
        val result = ChargerBrowseRepository { _, _, _ ->
            ServiceResult.Failure(ServiceFailure(ServiceFailureKind.SERVER))
        }.chargers(bounds)

        assertTrue(result is ServiceResult.Failure)
    }

    @Test
    fun viewportRefreshUsesPanAndZoomThresholds() {
        val original = OcmBounds(45.0, -75.0, 46.0, -74.0)
        assertTrue(!browseBoundsMovedMeaningfully(OcmBounds(45.20, -75.0, 46.20, -74.0), original))
        assertTrue(browseBoundsMovedMeaningfully(OcmBounds(45.26, -75.0, 46.26, -74.0), original))
        assertTrue(browseBoundsMovedMeaningfully(OcmBounds(44.84, -75.0, 46.16, -74.0), original))
    }

    private fun charger(id: String, connector: ConnectorType, power: Int) = Charger(
        id = id,
        name = id,
        network = "Test",
        latitude = 45.5,
        longitude = -74.5,
        connectorTypes = listOf(connector),
        maxKw = power,
        numberOfStalls = 1,
    )
}
