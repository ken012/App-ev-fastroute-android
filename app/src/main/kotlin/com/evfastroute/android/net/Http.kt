package com.evfastroute.android.net

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Shared OkHttp client for the service clients. */
internal val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        },
    )
}

/** Cancellation-aware HTTP with one bounded retry for transient failures. */
internal suspend fun fetchText(request: Request, attempts: Int = 2): ServiceResult<String> {
    var lastFailure = ServiceFailure(ServiceFailureKind.NETWORK)
    repeat(attempts.coerceAtLeast(1)) { attempt ->
        try {
            httpClient.newCall(request).awaitResponse().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    return if (body.isNullOrBlank()) {
                        ServiceResult.Failure(ServiceFailure(ServiceFailureKind.INVALID_RESPONSE, response.code))
                    } else {
                        ServiceResult.Success(body)
                    }
                }

                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                val kind = when (response.code) {
                    401, 403 -> ServiceFailureKind.UNAUTHORIZED
                    429 -> ServiceFailureKind.RATE_LIMITED
                    in 500..599 -> ServiceFailureKind.SERVER
                    else -> ServiceFailureKind.INVALID_RESPONSE
                }
                lastFailure = ServiceFailure(kind, response.code, retryAfter)
                val transient = kind == ServiceFailureKind.RATE_LIMITED || kind == ServiceFailureKind.SERVER
                if (!transient || attempt == attempts - 1) return ServiceResult.Failure(lastFailure)
                delay(((retryAfter ?: 1L).coerceIn(1L, 5L)) * 1_000L)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            lastFailure = ServiceFailure(ServiceFailureKind.NETWORK)
            if (attempt == attempts - 1) return ServiceResult.Failure(lastFailure)
            delay(500L * (attempt + 1))
        } catch (_: RuntimeException) {
            return ServiceResult.Failure(ServiceFailure(ServiceFailureKind.INVALID_RESPONSE))
        }
    }
    return ServiceResult.Failure(lastFailure)
}
