package com.evfastroute.android

import com.evfastroute.android.net.configuredHttpsUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfiguredUrlTest {
    @Test
    fun preservesBasePathAndAppendsProviderEndpoint() {
        assertEquals(
            "https://gateway.example.com/ocm/v3/poi",
            configuredHttpsUrl(" https://gateway.example.com/ocm/v3/ ", "/poi")?.toString(),
        )
    }

    @Test
    fun rejectsMissingMalformedAndCleartextGatewayRoots() {
        assertNull(configuredHttpsUrl("", "poi"))
        assertNull(configuredHttpsUrl("not a URL", "poi"))
        assertNull(configuredHttpsUrl("http://gateway.example.com/v3", "poi"))
        assertNull(configuredHttpsUrl("https://gateway.example.com/v3", ""))
    }
}
