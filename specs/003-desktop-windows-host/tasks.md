# Desglose de Tareas: 003-desktop-windows-host

## Fase 1: Gateway de Servidor Local Ktor
- [x] **T01: Pruebas unitarias de Ktor con fallback de puertos y Ping RTT (`testApplication`)**  
  *Requisitos cubiertos:* `RF-001`, `RF-004`, `RNF-002`  
  *Hecho cuando:* `desktopApp/src/desktopTest/.../KtorServerGatewayTest.kt` verifique el enlace secuencial en el rango 8080-8090 ante colisiones, la emisión periódica de `Ping` cada 1000 ms y la deserialización de `ProtocolMessage.CommandPacket`.
- [x] **T02: Implementación de `KtorServerGateway` implementando `IStreamGateway`**  
  *Requisitos cubiertos:* `RF-001`, `RNF-002`  
  *Hecho cuando:* El servidor Ktor gestione conexiones concurrentes, filtre adaptadores virtuales de Windows (WSL/VPN) y pase el 100% de los tests de `T01`.

## Fase 2: Conector de OBS Studio (OBS WebSocket API v5)
- [x] **T03: Pruebas unitarias con mocks para OBS v5 con autenticación SHA256**  
  *Requisitos cubiertos:* `RF-003`, `RNF-003`  
  *Hecho cuando:* Se verifique el desafío criptográfico SHA256 y el envío de `CreateInput` / `SetInputSettings` para `browser_source` sin colisión de nombres.
- [x] **T04: Implementación de `ObsWebSocketAdapter` implementando `IObsConnector`**  
  *Requisitos cubiertos:* `RF-003`, `RNF-003`  
  *Hecho cuando:* El adaptador resuelva el handshake autenticado en `ws://localhost:4455`, soporte reconexión con backoff exponencial y apruebe las pruebas de `T03`.

## Fase 3: Interfaz Gráfica Compose Desktop y ViewModel
- [x] **T05: Implementación de `DesktopHostViewModel` y pruebas con Turbine**  
  *Requisitos cubiertos:* `RF-002`, `RF-004`  
  *Hecho cuando:* El ViewModel exponga el cálculo de RTT, el temporizador de TTL del token (120 s) y despache `CameraCommand` tipados hacia el gateway.
- [ ] **T06: Vistas Compose Desktop (`MainWindow`, `QrPairingView`, `StreamingDashboardView`)**  
  *Requisitos cubiertos:* `RF-002`, `RF-004`, `RNF-004` (Constitución Principios 5 y 8)  
  *Hecho cuando:* La ventana renderice el QR con cuenta regresiva, el dashboard consuma `StudioCounterCard`, `StudioBadge` y `StudioIndicator`, y el cierre de ventana (`onCloseRequest`) despache `DISCONNECT_REQUEST` ordenadamente.

## Fase 4: Empaquetado y Distribución Autocontenida
- [ ] **T07: Configuración y verificación de empaquetado `jpackage` a `.exe` / `.msi`**  
  *Requisitos cubiertos:* `RF-005`, `RNF-001`  
  *Hecho cuando:* La ejecución de `./gradlew :desktopApp:packageMsi` o `packageExe` genere el instalador ejecutable autocontenido en `desktopApp/build/compose/binaries/main/` sin requerir Java en el sistema.
