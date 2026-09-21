# Plan de Arquitectura: 004-android-mobile-app

## 1. Estructura de Paquetes en `androidApp`

```
androidApp/src/main/java/com/vcompanion/android/
├── adapters/
│   ├── camera/
│   │   ├── CameraXCaptureAdapter.kt   # Implementación del puerto de captura con MediaCodec H.264
│   │   └── QrCodeImageAnalyzer.kt     # Integración con Google ML Kit Barcode Analyzer
│   ├── telemetry/
│   │   └── AndroidTelemetryProvider.kt# Muestreo de batería, estado térmico y mitigación adaptativa
│   └── network/
│       └── KtorClientStreamAdapter.kt # WebSocket binario (/ws/stream), control y responder de Pong RTT
├── presentation/
│   ├── viewmodel/
│   │   ├── CameraStreamViewModel.kt   # MVVM puro con StateFlow y UseCases compartidos
│   │   └── QrScannerViewModel.kt      # Orquestador del escáner óptico
│   └── ui/
│       ├── CameraScreen.kt            # Vista de cámara con FLAG_KEEP_SCREEN_ON y Studio HUD
│       ├── QrScannerScreen.kt         # Visor de escaneo rápido con encuadre guiado
│       ├── PermissionScreen.kt        # Vista de contingencia con Res.string.* y acceso a Ajustes
│       └── components/
│           └── CameraPreviewView.kt   # Envoltorio Compose para PreviewView de CameraX
└── MainActivity.kt                    # Entry point con Compose Navigation y permiso exclusivo CAMERA
```

---

## 2. Diagrama de Flujo de Datos en la Aplicación Móvil

```mermaid
graph TD
    subgraph Sensors [Hardware y Sensores]
        SensorCamera[Sensor de Cámara] --> CamX[CameraX / MediaCodec H.264]
        SensorBattery[BatteryManager / Power] --> TelemetryProv[AndroidTelemetryProvider]
    end

    subgraph Adapters [Capa de Adaptadores]
        CamX -->|Frames Binarios /ws/stream| StreamAdapter[KtorClientStreamAdapter]
        TelemetryProv -->|DeviceMetrics| StreamAdapter
        MLKit[ML Kit QR Analyzer] --> QrScannerVM[QrScannerViewModel]
    end

    subgraph Architecture [Clean Arch & MVVM]
        QrScannerVM --> ParseQR[ParsePairingPayloadUseCase - Shared]
        ParseQR --> StreamVM[CameraStreamViewModel]
        StreamVM --> StartStream[StartStreamUseCase - Shared]
        StreamVM --> RemoteCmds[CameraCommandDispatcher - Shared]
    end

    subgraph UI [Jetpack Compose Presentation]
        StreamVM --> StateFlow[uiState: StateFlow<CameraUiState>]
        StateFlow --> CameraScreen[CameraScreen & Studio HUD]
        CameraScreen --> DesignSystem[Shared Design System: Theme, Badges, Pills, Controls]
    end

    StreamAdapter <==>|Ping / Pong RTT & Control| WindowsHost[Desktop Host .exe]
```

---

## 3. Estrategia de Pruebas Automatizadas
- Pruebas unitarias de `CameraStreamViewModel` con `Turbine` y `MockK` para verificar las transiciones de estado, respuesta a comandos remotos (`SetZoom`, `ToggleTorch`) y despacho de `DISCONNECT_REQUEST`.
- Pruebas unitarias de `KtorClientStreamAdapter` verificando la emisión inmediata de `Pong(clientTimestamp)` ante paquetes `Ping`.
- Pruebas unitarias del analizador óptico `QrCodeImageAnalyzerTest` con URIs simuladas, filtrado de formato `FORMAT_QR_CODE` y propagación de errores.
- Pruebas unitarias de `QrScannerViewModelTest` validando el flujo de escaneo, estados de error y emparejamiento manual.

---

## 4. Subsistema Unificado de Emparejamiento y Resiliencia de Red
- **Detección Óptica QR (ML Kit):** Inclusión de `com.google.mlkit.vision.DEPENDENCIES = barcode` en `AndroidManifest.xml` y restricción a `Barcode.FORMAT_QR_CODE` para un procesamiento de fotogramas ágil e inmediato (`RF-001`).
- **Contingencia de Emparejamiento Manual:** Diálogo modal Compose (`ManualPairingDialog`) que permite introducir `host`, `port` y `token` de forma manual con textos centralizados en `Res.string.*` (`RF-001`).
- **Recorte Transparente en Compose:** Uso de `Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)` en el `Canvas` de `ScannerOverlay` para evitar que `BlendMode.Clear` perfore la superficie hacia el fondo nativo negro (`RF-001`).
- **Aislamiento de Ciclo de Vida:** Control del ciclo de vida del ejecutor en `QrScannerScreen` evitando apagados prematuros por recomposición.
- **Tráfico Local Seguro y Resiliencia de Red:** Configuración de `network_security_config.xml` vinculada en `AndroidManifest.xml` permitiendo tráfico sin cifrar (`cleartextTrafficPermitted="true"`) exclusivamente para conexiones locales `ws://` / `http://` en subredes privadas, junto con captura resiliente de excepciones de red en corrutinas (`RNF-005`).

