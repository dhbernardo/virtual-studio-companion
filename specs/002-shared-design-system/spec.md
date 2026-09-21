# Especificación: 002-shared-design-system

## 1. Resumen Ejecutivo
Diseñar e implementar el Sistema de Diseño (Design System) unificado para el ecosistema **Virtual Studio Companion** utilizando **Compose Multiplatform** en `shared/commonMain`. Este subsistema provee paletas simétricas de tokens visuales ("Studio Broadcast Dark" y "Studio Light"), escalas tipográficas y espaciados estandarizados, y componentes visuales reutilizables de grado broadcast (`StudioBadge`, `TelemetryPill`, `CameraControlBar`, `StudioIndicator` y `StudioCounterCard`) que garantizan paridad estética, accesibilidad WCAG AA y cero recursos en crudo tanto en Android como en Windows Desktop.

---

## 2. Requisitos Funcionales (EARS)

### RF-001: Tokens de Diseño Centralizados (Modo Oscuro, Modo Claro, Tipografía y Espaciados)
- **Tipo:** Ubiquitous
- **Definición:** El sistema DEBE exponer un tema composable unificado (`StudioTheme`) compuesto por contratos de tokens inmutables: **StudioDarkColors**, **StudioLightColors**, **StudioTypography** y **StudioSpacing**, garantizando contraste WCAG AA (>4.5:1) en ambas paletas.
- **Criterio de Aceptación:**
  - **DADO QUE** se utiliza `StudioTheme` en modo oscuro
  - **ENTONCES** debe proveer:
    - `Background`: `#0E1117`, `Surface`: `#161B22`, `SurfaceElevated`: `#21262D`, `TextPrimary`: `#F0F6FC`, `TextSecondary`: `#8B949E`, `Border`: `#30363D`.
    - Acentos: `LiveAccent`: `#EF4444`, `StandbyAccent`: `#10B981`, `WarningAccent`: `#F59E0B`.
  - **DADO QUE** se utiliza `StudioTheme` en modo claro
  - **ENTONCES** debe proveer acentos adaptados de alto contraste:
    - `Background`: `#F6F8FA`, `Surface`: `#FFFFFF`, `SurfaceElevated`: `#EAEFF5`, `TextPrimary`: `#24292F`, `TextSecondary`: `#57606A`, `Border`: `#D0D7DE`.
    - Acentos: `LiveAccent`: `#CF222E`, `StandbyAccent`: `#1A7F37`, `WarningAccent`: `#9A6700`.
  - **Y** `StudioTypography` debe definir escalas tipográficas (`DisplayLarge: 32sp`, `HeadlineMedium: 24sp`, `TitleMedium: 16sp`, `BodyMedium: 14sp`, `LabelSmall: 11sp`, `MonoNumeric: 13sp`) utilizando fuentes Inter y JetBrains Mono.
  - **Y** `StudioSpacing` debe proveer dimensiones inmutables (`ExtraSmall: 2dp`, `Small: 4dp`, `Medium: 8dp`, `Large: 12dp`, `ExtraLarge: 16dp`, `Section: 24dp`, `ScreenEdge: 32dp`).

### RF-002: Componente `StudioBadge` y Mapeo con el Dominio
- **Tipo:** State-driven
- **Definición:** MIENTRAS el estado de la conexión o stream cambie, el componente `StudioBadge` DEBE renderizar una etiqueta visual con animación de pulso sutil reflejando el estado mediante la función canónica de mapeo `ConnectionState.toStreamStatus()`.
- **Criterio de Aceptación:**
  - **DADO QUE** la conexión transita por la FSM de dominio
  - **CUANDO** se evalúa `toStreamStatus()`
  - **ENTONCES** debe mapear: `DISCONNECTED -> OFFLINE`, `PAIRING / CONNECTED -> STANDBY`, `STREAMING -> LIVE` y `RECONNECTING / ERROR -> ALERT`.
  - **DADO QUE** el estado mapeado es `LIVE` o `REC`
  - **CUANDO** se renderiza `StudioBadge`
  - **ENTONCES** debe exhibir un indicador luminoso pulsante con ciclo de 1.0s optimizado mediante `Modifier.graphicsLayer { alpha = ... }` para evitar recomposiciones del árbol de Compose a 60 FPS.

### RF-003: Componente `TelemetryPill` (Latencia y FPS)
- **Tipo:** Event-driven
- **Definición:** CUANDO se reciban métricas de rendimiento, el componente `TelemetryPill` DEBE formatear los valores de FPS y latencia en milisegundos aplicando colores semánticos reactivos basados en los tokens del tema actual.
- **Criterio de Aceptación:**
  - **DADO QUE** la latencia es $\le 50\text{ ms}$
  - **ENTONCES** el texto de latencia debe colorearse con `StudioTheme.colors.standbyAccent`.
  - **DADO QUE** la latencia está entre $51\text{ ms}$ y $120\text{ ms}$
  - **ENTONCES** debe colorearse con `StudioTheme.colors.warningAccent`.
  - **DADO QUE** la latencia es $> 120\text{ ms}$
  - **ENTONCES** debe colorearse con `StudioTheme.colors.liveAccent`.
  - **DADO QUE** las métricas aún no se reciben o son nulas
  - **ENTONCES** debe mostrar el placeholder `--- ms / -- FPS` en color `StudioTheme.colors.textSecondary`.

### RF-004: Barra de Controles de Estudio (`CameraControlBar`) y Degradación Elegante
- **Tipo:** Ubiquitous
- **Definición:** El sistema DEBE proveer un componente flotante estilo *glassmorphism* con degradación elegante de desenfoque según las capacidades de la plataforma, botones táctiles con Touch Target mínimo de 48dp y soporte para estados deshabilitados.
- **Criterio de Aceptación:**
  - **DADO QUE** se ejecuta en Windows Desktop o Android 12+ (API 31+)
  - **CUANDO** se renderiza el fondo de la barra flotante
  - **ENTONCES** debe aplicar desenfoque de hardware en tiempo real (`Modifier.blur(16.dp)`) con translucidez superficial.
  - **DADO QUE** se ejecuta en Android con nivel de API entre 26 y 30
  - **CUANDO** se renderiza la barra
  - **ENTONCES** debe aplicar automáticamente un fondo translúcido sólido al 85% de opacidad (`surface.copy(alpha = 0.85f)`) con borde sutil sin aplicar desenfoque por hardware para eliminar lag de GPU.
  - **DADO QUE** una acción de cámara no está disponible (ej. flash en cámara frontal)
  - **ENTONCES** el botón correspondiente debe mostrarse deshabilitado (`enabled = false`) con opacidad al 38% sin disparar callbacks.

### RF-005: Detección Automática de Tema y Persistencia de Preferencia
- **Tipo:** State-driven
- **Definición:** El sistema DEBE iniciar por defecto evaluando el tema del sistema operativo mediante `isSystemInDarkTheme()`, permitir la conmutación entre `SYSTEM`, `DARK` y `LIGHT`, y persistir la preferencia a través del puerto `ThemePreferencesRepository`.
- **Criterio de Aceptación:**
  - **DADO QUE** el usuario selecciona `DARK` o `LIGHT`
  - **CUANDO** se actualiza la preferencia
  - **ENTONCES** `StudioTheme` debe actualizar inmediatamente la UI sin reiniciar la aplicación y almacenar la selección en el repositorio persistente.
  - **DADO QUE** la app se reinicia
  - **CUANDO** se lee `ThemePreferencesRepository.getThemePreference()`
  - **ENTONCES** debe restaurar el último modo seleccionado por el usuario.

### RF-006: Prohibición de Hardcoded Strings, Dimensiones e Iconos en Crudo
- **Tipo:** Ubiquitous
- **Definición:** El sistema DEBE consumir todas las cadenas de texto a través de `Res.string.*`, todos los iconos vectoriales a través de `Res.drawable.*` y todas las dimensiones a través de `StudioSpacing` o tokens del tema (Constitución Principio 8).
- **Criterio de Aceptación:**
  - **DADO QUE** se analiza el código composable de `shared/src/commonMain/kotlin`
  - **CUANDO** se ejecuta la validación estática
  - **ENTONCES** no debe existir ningún string literal entre comillas (todos deben usar `stringResource(Res.string.<key>)`), ningún valor de dimensión fijo (`.dp` / `.sp` sin pasar por `StudioSpacing` o `StudioTypography`), ni rutas de dibujo en crudo.

### RF-007: Componentes Base Constitucionales (`StudioIndicator` y `StudioCounterCard`)
- **Tipo:** Ubiquitous
- **Definición:** El sistema DEBE proveer los componentes compartidos `StudioIndicator` y `StudioCounterCard` para dashboards de métricas y presentación de estado en Windows y Android (Constitución Principio 5).
- **Criterio de Aceptación:**
  - **DADO QUE** se renderiza `StudioIndicator(color, isPulsing)`
  - **ENTONCES** debe dibujar un punto circular con animación de respiración sutil para acompañar etiquetas de estado.
  - **DADO QUE** se renderiza `StudioCounterCard(label, value, unit, accentColor)`
  - **ENTONCES** debe exhibir una tarjeta de superficie elevada con el valor numérico destacado en `StudioTypography.displayLarge` o `headlineMedium`, su etiqueta descriptiva y el borde acentuado.

---

## 3. Requisitos No Funcionales (RNF)

- **RNF-001 (Paridad Visual y Pureza de Plataforma):** El 100% de los componentes deben compilar y renderizar idénticamente en Android y Windows Desktop sin importar paquetes dependientes de plataforma (`android.*`, `java.*`, `java.awt.*`, `kotlinx.cinterop.*`) en `shared/src/commonMain` (Constitución Principio 1).
- **RNF-002 (Estabilidad de Recomposición y Animaciones de 60 FPS):** Todos los modelos de datos expuestos a los composables deben anotarse con `@Immutable` o `@Stable`. Las animaciones continuas deben encapsularse en capas de renderizado gráfico (`graphicsLayer`) para garantizar que la vista previa de cámara sostenga 60 FPS estables sin tirones (*jank*).
- **RNF-003 (Accesibilidad y Contraste WCAG AA):** Todas las combinaciones de color de texto sobre superficie y acentos semánticos deben mantener un ratio de contraste matemático mínimo de 4.5:1 bajo los estándares WCAG AA en ambos modos de visualización.
