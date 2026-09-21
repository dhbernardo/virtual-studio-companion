package com.vcompanion.shared.core.protocol

import kotlinx.serialization.json.Json

val CoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = false
}
