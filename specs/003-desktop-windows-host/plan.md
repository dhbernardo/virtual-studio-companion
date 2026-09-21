# Plan de Arquitectura: 003-desktop-windows-host

## 1. Arquitectura Hexagonal en `desktopApp`

```
desktopApp/src/desktopMain/kotlin/com/vcompanion/desktop/
├── adapters/
│   ├── inbound/
│   │   └── KtorServerGateway.kt       # Implementa IStreamGateway (Ktor CIO, Ping RTT, fallback 8080-8090)
│   └── outbound/
│       ├── ObsWebSocketAdapter.kt     # Implementa IObsConnector (Ktor WS Client, OBS WS v5, SHA256 auth)
│       └── DesktopNetworkDetector.kt  # Detección de IP en interfaces físicas activas (excluye WSL/VPN)
├── presentation/
│   ├── viewmodel/
│   │   └── DesktopHostViewModel.kt    # MVVM con StateFlow (RTT latency, token TTL, OBS status)
│   └── ui/
│       ├── MainWindow.kt              # Ventana principal Compose Desktop con onCloseRequest
│       ├── QrCanvasRenderer.kt        # Generación de matriz QR vía ZXing Core y renderizado en Compose Canvas
│       ├── QrPairingView.kt           # Vista de espera, QR renderizado, IP:puerto, token legible y cuenta regresiva TTL (120s)
│       └── StreamingDashboardView.kt  # StudioCounterCard (FPS/bitrate/RTT), StudioBadge y StudioIndicator
└── Main.kt                            # Composition Root: inyección e inicio
```

---

## 2. Diagrama de Secuencia: Emparejamiento, OBS v5 y Medición RTT

```mermaid
sequenceDiagram
    autonumber
    actor Streamer as Streamer (Windows)
    participant Host as Desktop Host (Compose + Ktor)
    participant Phone as Android App
    participant OBS as OBS Studio (:4455)

    Streamer->>Host: Inicia Host .exe
    Host->>Host: Enlaza Ktor (8080-8090) & Genera QR con TTL (120s)
    Host->>OBS: Handshake WebSocket v5 (Desafío SHA256 Auth)
    OBS-->>Host: Autenticación Exitosa (Identified)
    Phone->>Host: Escanea QR y envía HANDSHAKE_INIT(token)
    Host->>Phone: HANDSHAKE_ACK (Sesión validada)
    Host->>OBS: CreateInput / SetInputSettings ("browser_source")
    Phone->>Host: Transmite frames & telemetría
    loop Cada 1000 ms
        Host->>Phone: PING(timestamp)
        Phone-->>Host: PONG(timestamp)
        Host->>Host: Calcula RTT = now - timestamp
    end
    Host->>Streamer: Renderiza StudioCounterCard (FPS, Bitrate, RTT Latency)
    alt Streamer conecta o desconecta OBS de forma independiente
        Streamer->>Host: Click "Conectar OBS" (connectObs) / "Desconectar OBS" (disconnectObs)
        Host->>OBS: Handshake WS v5 (replay=1) / Cierre limpio de socket
        Host->>Host: Actualiza obsConnectionState (CONNECTED/DISCONNECTED) preservando stream móvil
    else Streamer desconecta sesión desde Dashboard
        Streamer->>Host: Click "Desconectar" (disconnectSession)
        Host->>Phone: DISCONNECT_REQUEST("HOST_DISCONNECT")
        Host->>Host: Transita a Disconnected, regenera token y reinicia ticker QR
    else Phone cierra app o finaliza stream
        Phone->>Host: DISCONNECT_REQUEST o cierre de WebSocket
        Host->>Host: Ktor detecta cierre y emite DisconnectRequest("CLIENT_CLOSED")
        Host->>Host: Transita a Disconnected, regenera token y reinicia ticker QR
    else Streamer cierra ventana Windows (onCloseRequest)
        Streamer->>Host: Cierra ventana (onClose)
        Host->>Phone: DISCONNECT_REQUEST
        Host->>OBS: Desconecta WebSocket
        Host->>Host: Apaga Ktor limpiamente y finaliza proceso
    end
```

---

## 3. Configuración de `jpackage` en `desktopApp/build.gradle.kts`

```kotlin
compose.desktop {
    application {
        mainClass = "com.vcompanion.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "VirtualStudioCompanion"
            packageVersion = "1.0.0"
            vendor = "Virtual Studio"
            description = "Virtual Camera and Studio Companion for OBS"
            windows {
                menuGroup = "Virtual Studio"
                upgradeUuid = "6b4d32a0-8f1b-4f40-8b1b-3b7c2598f821"
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }
        }
    }
}
```

---

## 4. Estrategia de Pruebas Automatizadas
- Pruebas de Ktor Server con `testApplication` verificando enlace dinámico ante puertos ocupados y despacho de `Ping`/`Pong`.
- Mocks para OBS WebSocket v5 verificando el protocolo de autenticación SHA256 y la solicitud `CreateInput` con `browser_source`.
- Pruebas con Turbine sobre `DesktopHostViewModel` validando transiciones de estado, TTL y `onCloseRequest`.
- Pruebas unitarias de codificación y decodificación round-trip de QR en `QrCanvasRendererTest` con ZXing.

---

## 5. Arquitectura de Generación QR (ZXing Core & Compose Canvas)
- **Biblioteca:** `com.google.zxing:core` (Apache 2.0), empaquetada en el micro-runtime por `jpackage`.
- **Generación:** `QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)` genera una `BitMatrix` pura en memoria respetando rigurosamente ISO/IEC 18004.
- **Renderizado:** Composable `@Composable QrCodeCanvas` mapea la `BitMatrix` de dimensiones dinámicas sobre un `Canvas` vectorial de Compose con colores del tema activo (`StudioTheme.colors`).

