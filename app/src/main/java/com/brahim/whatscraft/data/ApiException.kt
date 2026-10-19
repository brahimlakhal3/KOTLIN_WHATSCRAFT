package com.brahim.whatscraft.data

/**
 * Anything the UI needs to show the user, mapped from the relay's error codes.
 */
class ApiException(
    val code: String,
    override val message: String,
    val status: Int,
) : Exception(message) {

    val isUnauthorized: Boolean get() = status == 401

    companion object {
        fun network(cause: Throwable): ApiException = ApiException(
            code = "network_unreachable",
            message = "Can't reach the server. Check your connection.",
            status = 0,
        ).also { it.initCause(cause) }

        fun offline(): ApiException = ApiException(
            code = "network_unreachable",
            message = "You are offline.",
            status = 0,
        )
    }
}
