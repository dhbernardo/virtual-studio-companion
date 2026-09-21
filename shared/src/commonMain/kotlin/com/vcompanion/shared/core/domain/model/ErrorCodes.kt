package com.vcompanion.shared.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionErrorCode {
    TOKEN_EXPIRED,
    TOKEN_ALREADY_USED,
    INVALID_TOKEN_FORMAT,
    INVALID_URI_SCHEME,
    MALFORMED_URI,
    CONNECTION_TIMEOUT,
    HANDSHAKE_REJECTED,
    NETWORK_LOST,
    UNKNOWN
}

@Serializable
enum class CommandErrorCode {
    VALUE_OUT_OF_RANGE,
    UNSUPPORTED_HARDWARE,
    INVALID_STATE,
    UNKNOWN_COMMAND
}
