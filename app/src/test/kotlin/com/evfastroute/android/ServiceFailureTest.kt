package com.evfastroute.android

import com.evfastroute.android.net.ServiceFailure
import com.evfastroute.android.net.ServiceFailureKind
import com.evfastroute.android.net.userMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceFailureTest {
    @Test
    fun configurationMessageNeverLeaksCredentialDetails() {
        val message = ServiceFailure(ServiceFailureKind.CONFIGURATION).userMessage("Routing")
        assertTrue(message.contains("not configured"))
        assertFalse(message.contains("ORS_API_KEY"))
    }

    @Test
    fun transientFailuresGiveRetryableLanguage() {
        assertTrue(ServiceFailure(ServiceFailureKind.RATE_LIMITED).userMessage("Routing").contains("try again"))
        assertTrue(ServiceFailure(ServiceFailureKind.SERVER).userMessage("Routing").contains("Try again"))
    }
}
