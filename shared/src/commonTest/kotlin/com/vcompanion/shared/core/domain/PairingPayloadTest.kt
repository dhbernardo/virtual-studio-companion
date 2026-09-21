package com.vcompanion.shared.core.domain

import com.vcompanion.shared.core.domain.model.ConnectionErrorCode
import com.vcompanion.shared.core.domain.model.PairingConfig
import com.vcompanion.shared.core.domain.model.PairingPayloadException
import com.vcompanion.shared.core.domain.model.SessionTokenMetadata
import com.vcompanion.shared.core.domain.model.TokenValidationResult
import com.vcompanion.shared.core.domain.usecase.GeneratePairingPayloadUseCase
import com.vcompanion.shared.core.domain.usecase.ParsePairingPayloadUseCase
import com.vcompanion.shared.core.domain.usecase.ValidateSessionTokenUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T01: Pruebas unitarias para generación, parseo y validación de URI QR (TDD).
 * Valida RF-001 y RNF-001.
 */
class PairingPayloadTest {

    private val generateUseCase = GeneratePairingPayloadUseCase()
    private val parseUseCase = ParsePairingPayloadUseCase()
    private val validateTokenUseCase = ValidateSessionTokenUseCase()

    @Test
    fun shouldGenerateStandardPairingUri() {
        val uri = generateUseCase(
            host = "192.168.1.50",
            port = 8080,
            token = "sec-9f8a1b2c3d4e",
            version = 1,
            fps = 60,
            res = "1080p"
        )

        assertEquals(
            "vcam://pair?host=192.168.1.50&port=8080&token=sec-9f8a1b2c3d4e&v=1&fps=60&res=1080p",
            uri
        )
    }

    @Test
    fun shouldGeneratePairingUriWithFallbackHosts() {
        val config = PairingConfig(
            host = "192.168.1.50",
            port = 8080,
            sessionToken = "sec-9f8a1b2c3d4e",
            version = 1,
            recommendedFps = 60,
            recommendedRes = "1080p",
            fallbackHosts = listOf("192.168.1.51", "10.0.0.5")
        )

        val uri = generateUseCase(config)

        assertTrue(uri.startsWith("vcam://pair?"))
        assertTrue(uri.contains("fallbackHosts=192.168.1.51,10.0.0.5"))
    }

    @Test
    fun shouldParseValidPairingUri() {
        val rawUri = "vcam://pair?host=192.168.1.50&port=8080&token=sec-9f8a1b2c3d4e&v=1&fps=60&res=1080p"
        val config = parseUseCase(rawUri)

        assertEquals("192.168.1.50", config.host)
        assertEquals(8080, config.port)
        assertEquals("sec-9f8a1b2c3d4e", config.sessionToken)
        assertEquals(1, config.version)
        assertEquals(60, config.recommendedFps)
        assertEquals("1080p", config.recommendedRes)
        assertTrue(config.fallbackHosts.isEmpty())
    }

    @Test
    fun shouldParsePairingUriWithFallbackHosts() {
        val rawUri = "vcam://pair?host=192.168.1.50&port=8080&token=sec-9f8a1b2c3d4e&v=1&fps=60&res=1080p&fallbackHosts=192.168.1.51,10.0.0.5"
        val config = parseUseCase(rawUri)

        assertEquals(2, config.fallbackHosts.size)
        assertEquals("192.168.1.51", config.fallbackHosts[0])
        assertEquals("10.0.0.5", config.fallbackHosts[1])
    }

    @Test
    fun shouldParsePairingUriWithDefaultValuesWhenOptionalOmitted() {
        val rawUri = "vcam://pair?host=192.168.1.50&port=8080&token=sec-9f8a1b2c3d4e"
        val config = parseUseCase(rawUri)

        assertEquals("192.168.1.50", config.host)
        assertEquals(8080, config.port)
        assertEquals("sec-9f8a1b2c3d4e", config.sessionToken)
        assertEquals(1, config.version)
        assertEquals(60, config.recommendedFps)
        assertEquals("1080p", config.recommendedRes)
        assertTrue(config.fallbackHosts.isEmpty())
    }

    @Test
    fun shouldFailWhenSchemeIsInvalid() {
        val invalidSchemeUri = "http://pair?host=192.168.1.50&port=8080&token=sec-9f8a1b2c3d4e"
        val exception = assertFailsWith<PairingPayloadException> {
            parseUseCase(invalidSchemeUri)
        }
        assertEquals(ConnectionErrorCode.INVALID_URI_SCHEME, exception.errorCode)
    }

    @Test
    fun shouldFailWhenRequiredParametersAreMissing() {
        val missingTokenUri = "vcam://pair?host=192.168.1.50&port=8080"
        val exception = assertFailsWith<PairingPayloadException> {
            parseUseCase(missingTokenUri)
        }
        assertEquals(ConnectionErrorCode.MALFORMED_URI, exception.errorCode)
    }

    @Test
    fun shouldValidateSessionTokenSuccessfullyWithin120sTtl() {
        val createdAt = 1_700_000_000_000L
        val now = createdAt + 60_000L // 60 seconds elapsed (< 120s)
        val metadata = SessionTokenMetadata(
            token = "sec-9f8a1b2c3d4e",
            createdAtEpochMs = createdAt,
            isUsed = false,
            ttlSeconds = 120L
        )

        val result = validateTokenUseCase(
            presentedToken = "sec-9f8a1b2c3d4e",
            metadata = metadata,
            currentEpochMs = now
        )

        assertTrue(result.isValid)
        assertIs<TokenValidationResult.Valid>(result)
    }

    @Test
    fun shouldRejectExpiredTokenAfter120sTtl() {
        val createdAt = 1_700_000_000_000L
        val now = createdAt + 120_001L // 120.001 seconds elapsed (> 120s)
        val metadata = SessionTokenMetadata(
            token = "sec-9f8a1b2c3d4e",
            createdAtEpochMs = createdAt,
            isUsed = false,
            ttlSeconds = 120L
        )

        val result = validateTokenUseCase(
            presentedToken = "sec-9f8a1b2c3d4e",
            metadata = metadata,
            currentEpochMs = now
        )

        assertFalse(result.isValid)
        assertIs<TokenValidationResult.Invalid>(result)
        assertEquals(ConnectionErrorCode.TOKEN_EXPIRED, (result as TokenValidationResult.Invalid).errorCode)
    }

    @Test
    fun shouldRejectTokenAlreadyUsed() {
        val createdAt = 1_700_000_000_000L
        val now = createdAt + 10_000L
        val metadata = SessionTokenMetadata(
            token = "sec-9f8a1b2c3d4e",
            createdAtEpochMs = createdAt,
            isUsed = true, // Already used!
            ttlSeconds = 120L
        )

        val result = validateTokenUseCase(
            presentedToken = "sec-9f8a1b2c3d4e",
            metadata = metadata,
            currentEpochMs = now
        )

        assertFalse(result.isValid)
        assertIs<TokenValidationResult.Invalid>(result)
        assertEquals(ConnectionErrorCode.TOKEN_ALREADY_USED, (result as TokenValidationResult.Invalid).errorCode)
    }

    @Test
    fun shouldRejectMissingOrMismatchedToken() {
        val createdAt = 1_700_000_000_000L
        val now = createdAt + 10_000L
        val metadata = SessionTokenMetadata(
            token = "sec-9f8a1b2c3d4e",
            createdAtEpochMs = createdAt,
            isUsed = false,
            ttlSeconds = 120L
        )

        // Metadata null
        val nullResult = validateTokenUseCase("sec-9f8a1b2c3d4e", null, now)
        assertFalse(nullResult.isValid)
        assertEquals(ConnectionErrorCode.INVALID_TOKEN_FORMAT, (nullResult as TokenValidationResult.Invalid).errorCode)

        // Token mismatch
        val mismatchResult = validateTokenUseCase("wrong-token", metadata, now)
        assertFalse(mismatchResult.isValid)
        assertEquals(ConnectionErrorCode.INVALID_TOKEN_FORMAT, (mismatchResult as TokenValidationResult.Invalid).errorCode)

        // Empty token
        val emptyResult = validateTokenUseCase("", metadata, now)
        assertFalse(emptyResult.isValid)
        assertEquals(ConnectionErrorCode.INVALID_TOKEN_FORMAT, (emptyResult as TokenValidationResult.Invalid).errorCode)
    }
}
