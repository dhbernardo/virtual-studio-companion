# Desglose de Tareas: 004-android-mobile-app

## Fase 1: Integración de ML Kit, Escaneo Óptico y Permisos
- [x] **T01: Pruebas unitarias para el analizador de código QR con ML Kit**  
  *Requisitos cubiertos:* `RF-001`, `RNF-001`  
  *Hecho cuando:* `androidApp/src/test/.../QrCodeImageAnalyzerTest.kt` valide que al recibir un Barcode con valor `vcam://pair?...` se extraigan los datos y se invoque el callback con la configuración correcta.
- [x] **T02: Implementación de `QrCodeImageAnalyzer`, `QrScannerScreen` y `PermissionScreen`**  
  *Requisitos cubiertos:* `RF-001`, `RNF-003`, `RNF-004` (Constitución Principio 8)  
  *Hecho cuando:* La pantalla de escaneo detecte el código QR en tiempo real, gestione exclusivamente el permiso `CAMERA`, provea y consuma el método `consumeScannedConfig()` para evitar saltos repetitivos de navegación, y exhiba la vista de contingencia con `Res.string.*` ante rechazos permanentes.

## Fase 2: Pipeline de Captura y Codificación de Video por Hardware
- [x] **T03: Adaptador de captura de video con `MediaCodec` H.264 (`CameraXCaptureAdapter`)**  
  *Requisitos cubiertos:* `RF-002`, `RNF-001`, `RNF-002` (AGENTS.md Regla 4)  
  *Hecho cuando:* Se configure CameraX a 1080p, se codifiquen frames H.264 acelerados por hardware en un hilo dedicado y se transmitan sobre `/ws/stream` sin penalizar el hilo de Compose.
- [x] **T04: Adaptador de telemetría y responder de Ping/Pong RTT (`AndroidTelemetryProvider` / `KtorClientStreamAdapter`)**  
  *Requisitos cubiertos:* `RF-004`, `RF-005`, `RNF-005`  
  *Hecho cuando:* El cliente de red responda inmediatamente enviando `ProtocolMessage.Pong` ante paquetes `Ping` del Host, emita periódicamente telemetría con batería y estado térmico, y capture el cierre del canal entrante de Ktor emitiendo `DisconnectRequest("SERVER_CLOSED")` ante caída o terminación del Host.

## Fase 3: ViewModels, HUD de Transmisión y Comandos Remotos
- [x] **T05: Pruebas unitarias para `CameraStreamViewModel` (TDD)**  
  *Requisitos cubiertos:* `RF-003`, `RF-004`, `RF-005`  
  *Hecho cuando:* Se validen con Turbine los estados de conexión (`CameraUiState`), la reducción automática a 30 FPS ante estado térmico `SEVERE`/`CRITICAL`, y la ejecución de `CameraCommand` tipados.
- [x] **T06: Implementación de `CameraStreamViewModel`**  
  *Requisitos cubiertos:* `RF-001`, `RF-003`, `RF-004`, `RF-005`  
  *Hecho cuando:* El ViewModel integre los casos de uso compartidos, soporte `initialConfig: PairingConfig?` inicializando su estado en `Pairing(config)` desde el fotograma inicial cuando esté disponible (preservando `Disconnected` por defecto cuando es nulo), controle la linterna con validación previa de hardware (`hasFlashUnit()`) y apruebe el 100% de las pruebas de `T05`.
- [x] **T07: Implementación de `CameraScreen` con HUD de Estudio y `FLAG_KEEP_SCREEN_ON`**  
  *Requisitos cubiertos:* `RF-001`, `RF-003`, `RF-005`, `RNF-004` (Constitución Principios 5 y 8)  
  *Hecho cuando:* La pantalla combine la vista previa con los componentes compartidos (`StudioBadge`, `TelemetryPill`, `StudioIndicator`, `CameraControlBar` con iconos nítidos), mantenga la pantalla activa durante el streaming, inicialice el ViewModel con `initialConfig = config`, active las escuchas de mensajes antes del enlace WebSocket, proteja la desconexión reactiva con un guardián de sesión activa (`hasActiveSession`) que prevenga rebotes en el montaje inicial, despache `DISCONNECT_REQUEST` al salir, y gestione eventos `ON_STOP`/`ON_DESTROY` para notificar al Host y liberar CameraX.

## Fase 4: Optimización de Resiliencia del Escáner QR y Contingencia Manual
- [x] **T08: Robustecimiento de ML Kit, Política Cleartext y Visor Transparente**  
  *Requisitos cubiertos:* `RF-001`, `RNF-004`, `RNF-005` (Constitución Principio 8)  
  *Hecho cuando:* Se declare `com.google.mlkit.vision.DEPENDENCIES = barcode` y `network_security_config.xml` con soporte Cleartext en `AndroidManifest.xml`, `KtorClientStreamAdapter` capture excepciones de red en corrutinas de forma resiliente, `ScannerOverlay` utilice `CompositingStrategy.Offscreen` para garantizar nitidez y transparencia en el visor de escaneo, se ofrezca un diálogo de ingreso manual de IP/Token con strings centralizados en `Res.string.*`, y las pruebas unitarias en `QrCodeImageAnalyzerTest`, `QrScannerViewModelTest` y `KtorClientStreamAdapterTest` pasen al 100%.


