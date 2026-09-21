# Desglose de Tareas: 002-shared-design-system

## Fase 1: Tokens de Diseño, Modo Claro/Oscuro y Recursos Centralizados
- [x] **T01: Definición de tokens de color Dark & Light (WCAG AA), tipografía y espaciados**  
  *Requisitos cubiertos:* `RF-001`, `RNF-003`  
  *Hecho cuando:* Se implementen `StudioDarkColors`, `StudioLightColors` (con acentos de alto contraste), `StudioTypography` y `StudioSpacing` en `shared/.../designsystem/theme/` verificando matemáticamente ratio WCAG AA > 4.5:1 en ambos modos.
- [x] **T02: Implementación de `StudioTheme` con persistencia mediante `ThemePreferencesRepository`**  
  *Requisitos cubiertos:* `RF-001`, `RF-005`, `RNF-001`  
  *Hecho cuando:* El composable `StudioTheme` evalúe `isSystemInDarkTheme()`, exponga los tokens correctos, permita alternar entre `SYSTEM`, `DARK` y `LIGHT` y persista la preferencia en el repositorio reactivo.
- [x] **T02b: Creación del catálogo de recursos centralizados en `composeResources`**  
  *Requisitos cubiertos:* `RF-006` (Constitución Principio 8)  
  *Hecho cuando:* Se organicen `values/strings.xml`, `drawable/` (iconos vectoriales de control) y `font/`, garantizando cero strings, dimensiones fijas o rutas de dibujo en crudo en los composables.

## Fase 2: Componentes Atómicos y Base Constitucional
- [x] **T03: Pruebas unitarias de contraste WCAG AA, formateo y mapeo de estados**  
  *Requisitos cubiertos:* `RF-001`, `RF-002`, `RF-003`, `RNF-003`  
  *Hecho cuando:* `shared/src/commonTest/.../DesignSystemSanityTest.kt` compruebe los ratios de contraste de las paletas, la función de mapeo `ConnectionState.toStreamStatus()` y el formateo de placeholders (`--- ms`).
- [x] **T04: Implementación de `StudioBadge` con pulso optimizado en `graphicsLayer`**  
  *Requisitos cubiertos:* `RF-002`, `RNF-002`  
  *Hecho cuando:* El composable renderice los estados `OFFLINE`, `STANDBY`, `LIVE` y `ALERT` con animación pulsante aislada en capa gráfica a 60 FPS.
- [x] **T05: Implementación de `TelemetryPill` con tokens semánticos reactivos**  
  *Requisitos cubiertos:* `RF-003`, `RNF-002`  
  *Hecho cuando:* `TelemetryPill` consuma `StudioTheme.colors.*` dinámicamente y muestre placeholders ante métricas nulas o pendientes.
- [ ] **T06: Implementación de `StudioIndicator` y `StudioCounterCard`**  
  *Requisitos cubiertos:* `RF-007` (Constitución Principio 5)  
  *Hecho cuando:* `StudioIndicator` (punto luminoso pulsante) y `StudioCounterCard` (tarjeta de métricas con valor grande para dashboards) estén implementados y listos para su consumo en móvil y escritorio.

## Fase 3: Componentes de Control de Estudio y Envoltorios
- [ ] **T07: Implementación de `CameraControlBar` con degradación elegante de Glassmorphism**  
  *Requisitos cubiertos:* `RF-004`, `RNF-001`  
  *Hecho cuando:* La barra aplique `Modifier.blur` en Android 12+ / Desktop y fondo translúcido sólido al 85% en Android 8.0-11, respetando Touch Targets de 48dp y estados deshabilitados.
- [ ] **T08: Implementación de `QrCard`**  
  *Requisitos cubiertos:* `RF-001`, `RNF-001`  
  *Hecho cuando:* `QrCard` renderice un contenedor estilizado con bordes sutiles y elevación para albergar el código QR generado.
