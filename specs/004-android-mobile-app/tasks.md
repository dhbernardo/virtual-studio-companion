# Desglose de Tareas: 004-android-mobile-app

## Fase 1: Integración de ML Kit, Escaneo Óptico y Permisos
- [x] **T01: Pruebas unitarias para el analizador de código QR con ML Kit**  
  *Requisitos cubiertos:* `RF-001`, `RNF-001`  
  *Hecho cuando:* `androidApp/src/test/.../QrCodeImageAnalyzerTest.kt` valide que al recibir un Barcode con valor `vcam://pair?...` se extraigan los datos y se invoque el callback con la configuración correcta.
- [x] **T02: Implementación de `QrCodeImageAnalyzer`, `QrScannerScreen` y `PermissionScreen`**  
  *Requisitos cubiertos:* `RF-001`, `RNF-003`, `RNF-004` (Constitución Principio 8)  
  *Hecho cuando:* La pantalla de escaneo detecte el código QR en tiempo real, gestione exclusivamente el permiso `CAMERA` y exhiba la vista de contingencia con `Res.string.*` ante rechazos permanentes.

## Fase 2: Pipeline de Captura y Codificación de Video por Hardware
- [x] **T03: Adaptador de captura de video con `MediaCodec` H.264 (`CameraXCaptureAdapter`)**  
  *Requisitos cubiertos:* `RF-002`, `RNF-001`, `RNF-002` (AGENTS.md Regla 4)  
  *Hecho cuando:* Se configure CameraX a 1080p, se codifiquen frames H.264 acelerados por hardware en un hilo dedicado y se transmitan sobre `/ws/stream` sin penalizar el hilo de Compose.
- [x] **T04: Adaptador de telemetría y responder de Ping/Pong RTT (`AndroidTelemetryProvider` / `KtorClientStreamAdapter`)**  
  *Requisitos cubiertos:* `RF-004`  
  *Hecho cuando:* El cliente de red responda inmediatamente enviando `ProtocolMessage.Pong` ante paquetes `Ping` del Host y emita periódicamente telemetría con batería y estado térmico.

## Fase 3: ViewModels, HUD de Transmisión y Comandos Remotos
- [x] **T05: Pruebas unitarias para `CameraStreamViewModel` (TDD)**  
  *Requisitos cubiertos:* `RF-003`, `RF-004`, `RF-005`  
  *Hecho cuando:* Se validen con Turbine los estados de conexión (`CameraUiState`), la reducción automática a 30 FPS ante estado térmico `SEVERE`/`CRITICAL`, y la ejecución de `CameraCommand` tipados.
- [x] **T06: Implementación de `CameraStreamViewModel`**  
  *Requisitos cubiertos:* `RF-003`, `RF-004`, `RF-005`  
  *Hecho cuando:* El ViewModel integre los casos de uso compartidos, controle la linterna con validación previa de hardware (`hasFlashUnit()`) y apruebe el 100% de las pruebas de `T05`.
- [x] **T07: Implementación de `CameraScreen` con HUD de Estudio y `FLAG_KEEP_SCREEN_ON`**  
  *Requisitos cubiertos:* `RF-003`, `RF-005`, `RNF-004` (Constitución Principios 5 y 8)  
  *Hecho cuando:* La pantalla combine la vista previa con los componentes compartidos (`StudioBadge`, `TelemetryPill`, `StudioIndicator`, `CameraControlBar`), mantenga la pantalla activa durante el streaming y despache `DISCONNECT_REQUEST` al salir.

## Fase 4: Optimización de Resiliencia del Escáner QR y Contingencia Manual
- [x] **T08: Robustecimiento de ML Kit, Política Cleartext y Visor Transparente**  
  *Requisitos cubiertos:* `RF-001`, `RF-006`, `RNF-001`, `RNF-004` (Constitución Principio 8)  
  *Hecho cuando:* Se declare `com.google.mlkit.vision.DEPENDENCIES = barcode` y `network_security_config.xml` con soporte Cleartext en `AndroidManifest.xml`, `KtorClientStreamAdapter` capture excepciones de red en corrutinas de forma resiliente, `ScannerOverlay` utilice `CompositingStrategy.Offscreen` para garantizar nitidez y transparencia en el visor de escaneo, se ofrezca un diálogo de ingreso manual de IP/Token con strings centralizados en `Res.string.*`, y las pruebas unitarias en `QrCodeImageAnalyzerTest`, `QrScannerViewModelTest` y `KtorClientStreamAdapterTest` pasen al 100%.


