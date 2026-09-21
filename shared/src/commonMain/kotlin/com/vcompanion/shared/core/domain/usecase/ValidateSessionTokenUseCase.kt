package com.vcompanion.shared.core.domain.usecase

import com.vcompanion.shared.core.domain.model.ConnectionErrorCode
import com.vcompanion.shared.core.domain.model.SessionTokenMetadata
import com.vcompanion.shared.core.domain.model.TokenValidationResult

/**
 * Valida un token de sesión presentado contra sus metadatos y TTL (120 s por defecto).
 * Cumple con RF-001 y RNF-001 (sin dependencias de plataforma).
 */
class ValidateSessionTokenUseCase {

    operator fun invoke(
        presentedToken: String,
        metadata: SessionTokenMetadata?,
        currentEpochMs: Long
    ): TokenValidationResult {
        if (presentedToken.isBlank() || metadata == null || metadata.token != presentedToken) {
            return TokenValidationResult.Invalid(ConnectionErrorCode.INVALID_TOKEN_FORMAT)
        }

        if (metadata.isUsed) {
            return TokenValidationResult.Invalid(ConnectionErrorCode.TOKEN_ALREADY_USED)
        }

        val elapsedMs = currentEpochMs - metadata.createdAtEpochMs
        val ttlMs = metadata.ttlSeconds * 1000L

        if (elapsedMs < 0 || elapsedMs > ttlMs) {
            return TokenValidationResult.Invalid(ConnectionErrorCode.TOKEN_EXPIRED)
        }

        return TokenValidationResult.Valid
    }
}
