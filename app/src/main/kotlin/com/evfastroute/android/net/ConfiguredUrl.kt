package com.evfastroute.android.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Builds a fixed provider endpoint without ever throwing on a malformed remote configuration.
 * Production gateway roots must be HTTPS; invalid values fail closed as CONFIGURATION errors. */
internal fun configuredHttpsUrl(baseUrl: String, endpointPath: String): HttpUrl? {
    val base = baseUrl.trim().trimEnd('/')
    val path = endpointPath.trim().trimStart('/')
    if (base.isEmpty() || path.isEmpty()) return null
    return "$base/$path".toHttpUrlOrNull()?.takeIf { it.isHttps }
}
