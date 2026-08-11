package com.evfastroute.android.net

enum class ServiceFailureKind {
    CONFIGURATION,
    NETWORK,
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER,
    INVALID_RESPONSE,
}

data class ServiceFailure(
    val kind: ServiceFailureKind,
    val statusCode: Int? = null,
    val retryAfterSeconds: Long? = null,
)

sealed interface ServiceResult<out T> {
    data class Success<T>(val value: T) : ServiceResult<T>
    data class Failure(val error: ServiceFailure) : ServiceResult<Nothing>
}

fun ServiceFailure.userMessage(serviceName: String): String = when (kind) {
    ServiceFailureKind.CONFIGURATION ->
        "$serviceName is not configured for this build. Ask the tester who shared the app to update its service configuration."
    ServiceFailureKind.NETWORK ->
        "$serviceName couldn't be reached. Check your connection and try again."
    ServiceFailureKind.UNAUTHORIZED ->
        "$serviceName rejected this build's credentials. The app owner needs to update its protected configuration."
    ServiceFailureKind.RATE_LIMITED ->
        "$serviceName is busy right now. Wait a moment and try again."
    ServiceFailureKind.SERVER ->
        "$serviceName is temporarily unavailable. Try again shortly."
    ServiceFailureKind.INVALID_RESPONSE ->
        "$serviceName returned data the app could not safely verify. Try again."
}
