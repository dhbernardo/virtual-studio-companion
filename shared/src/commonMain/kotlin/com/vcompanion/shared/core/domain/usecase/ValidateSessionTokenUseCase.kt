package com.vcompanion.shared.core.domain.usecase

import com.vcompanion.shared.core.domain.model.SessionTokenMetadata
import com.vcompanion.shared.core.domain.model.TokenValidationResult

class ValidateSessionTokenUseCase {
    operator fun invoke(
        presentedToken: String,
        metadata: SessionTokenMetadata?,
        currentEpochMs: Long
    ): TokenValidationResult {
        TODO("Not implemented")
    }
}
