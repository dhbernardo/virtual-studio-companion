package com.vcompanion.shared.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionTokenMetadata(
    val token: String,
    val createdAtEpochMs: Long,
    val isUsed: Boolean = false,
    val ttlSeconds: Long = 120L
)

sealed interface TokenValidationResult {
    data object Valid : TokenValidationResult
    data class Invalid(val errorCode: ConnectionErrorCode) : TokenValidationResult

    val isValid: Boolean get() = this is Valid
}
