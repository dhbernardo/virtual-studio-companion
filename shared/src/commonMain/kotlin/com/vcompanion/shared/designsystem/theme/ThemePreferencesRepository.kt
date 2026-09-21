package com.vcompanion.shared.designsystem.theme

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puerto de repositorio para persistencia y observación reactiva de la preferencia de tema.
 * Cumple con RF-005 y Clean Architecture.
 */
interface ThemePreferencesRepository {
    val themePreference: Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)
    fun getThemePreferenceSync(): ThemePreference
}

/**
 * Implementación reactiva en memoria con fallback seguro para testing y runtime desacoplado.
 */
class InMemoryThemePreferencesRepository(
    initialPreference: ThemePreference = ThemePreference.SYSTEM
) : ThemePreferencesRepository {

    private val _themePreference = MutableStateFlow(initialPreference)
    override val themePreference: Flow<ThemePreference> = _themePreference.asStateFlow()

    override suspend fun setThemePreference(preference: ThemePreference) {
        _themePreference.value = preference
    }

    override fun getThemePreferenceSync(): ThemePreference = _themePreference.value
}
