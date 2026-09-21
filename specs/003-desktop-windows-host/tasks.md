# Desglose de Tareas: 003-desktop-windows-host

## Fase 1: Gateway de Servidor Local Ktor
- [x] **T01: Pruebas unitarias de Ktor con fallback de puertos, Ping RTT y streaming MJPEG (`testApplication`)**  
  *Requisitos cubiertos:* `RF-001`, `RF-004`, `RNF-002`  
  *Hecho cuando:* `desktopApp/src/desktopTest/.../KtorServerGatewayTest.kt` verifique el enlace secuencial en el rango 8080-8090 ante colisiones, la emisión periódica de `Ping` cada 1000 ms, la deserialización de `ProtocolMessage.CommandPacket`, la propagación de tramas binarias (`Frame.Binary`) hacia `incomingVideoFrames` y la respuesta multipart en `/stream/mjpeg`.
- [x] **T02: Implementación de `KtorServerGateway` implementando `IStreamGateway`**  
  *Requisitos cubiertos:* `RF-001`, `RNF-002`  
  *Hecho cuando:* El servidor Ktor gestione conexiones concurrentes, filtre adaptadores virtuales de Windows (WSL/VPN), detecte el cierre de la sesión de control WebSocket emitiendo `DisconnectRequest("CLIENT_CLOSED")` para restaurar el estado de emparejamiento, sirva el endpoint `/stream/mjpeg` (`multipart/x-mixed-replace`) y `/stream/preview` para OBS Studio, y pase el 100% de los tests de `T01`.

## Fase 2: Conector de OBS Studio (OBS WebSocket API v5)
- [x] **T03: Pruebas unitarias con mocks para OBS v5 con autenticación SHA256**  
  *Requisitos cubiertos:* `RF-003`, `RNF-003`  
  *Hecho cuando:* Se verifique el desafío criptográfico SHA256, el envío de `CreateInput` / `SetInputSettings` para `browser_source` sin colisión de nombres, la desconexión limpia de OBS sin bucles de reintento (`disconnect()`) y la resiliencia del transporte ante mensajes de bienvenida tempranos (`replay = 1`).
- [x] **T04: Implementación de `ObsWebSocketAdapter` implementando `IObsConnector`**  
  *Requisitos cubiertos:* `RF-003`, `RNF-003`  
  *Hecho cuando:* El adaptador resuelva el handshake autenticado en `ws://localhost:4455`, implemente `KtorObsTransport` con buffer de repetición (`replay = 1`) previniendo pérdida del frame `op: 0`, soporte desconexión explícita (`disconnect()`) cancelando reconexiones sin disparar bucles espurios en `finally`, configure de forma idempotente la fuente Browser Source `Virtual Studio Camera` (`setupBrowserSource`) al conectarse con sesión móvil activa, y apruebe las pruebas de `T03`.

## Fase 3: Interfaz Gráfica Compose Desktop y ViewModel
- [x] **T05: Implementación de `DesktopHostViewModel` y pruebas con Turbine**  
  *Requisitos cubiertos:* `RF-002`, `RF-004`  
  *Hecho cuando:* El ViewModel exponga el cálculo de RTT, el temporizador de TTL del token (120 s), despache `CameraCommand` tipados hacia el gateway, procese `CommandPacket` entrante sincronizando `torchEnabled` y `currentZoom` desde el móvil sin re-emisión en bucle, consuma `TelemetryPacket` actualizando FPS y bitrate vivos, decodifique `incomingVideoFrames` nativamente con Skia (`Image.makeFromEncoded`) exponiendo `videoFrame: StateFlow<ImageBitmap?>`, provea `disconnectObs()` para desvincular OBS de forma independiente, provea `disconnectSession()` para retorno ordenado a QR con nuevo token sin apagar la app, y maneje `onClose()` exclusivamente para el cierre del proceso.
- [x] **T06: Vistas Compose Desktop (`MainWindow`, `QrPairingView`, `StreamingDashboardView`)**  
  *Requisitos cubiertos:* `RF-002`, `RF-003`, `RF-004`, `RNF-004` (Constitución Principios 5 y 8)  
  *Hecho cuando:* La ventana renderice el QR con cuenta regresiva, exhiba la dirección IP:puerto y el token de sesión alfanumérico para entrada manual, el dashboard integre el **Monitor de Retorno de Video Nativo** con Skia en 16:9, consuma `StudioCounterCard`, `StudioBadge` y `StudioIndicator` reflejando métricas vivas y controles sincronizados, ofrezca contextualmente el botón "Conectar OBS" o "Desconectar OBS" según `obsConnectionState` en la vista QR y el dashboard, el botón "Desconectar" invoque `disconnectSession()` retornando a QR, y el cierre de ventana (`onCloseRequest`) despache `DISCONNECT_REQUEST` ordenadamente.

## Fase 4: Empaquetado y Distribución Autocontenida
- [x] **T07: Configuración y verificación de empaquetado `jpackage` a `.exe` / `.msi`**  
  *Requisitos cubiertos:* `RF-005`, `RNF-001`  
  *Hecho cuando:* La ejecución de `./gradlew :desktopApp:packageMsi` o `packageExe` genere el instalador ejecutable autocontenido en `desktopApp/build/compose/binaries/main/` sin requerir Java en el sistema.

## Fase 5: Conformidad Estándar QR y Resiliencia Óptica
- [x] **T08: Generación estandarizada de Código QR con ZXing Core (ISO/IEC 18004)**  
  *Requisitos cubiertos:* `RF-002`, `RNF-004` (Constitución Principios 3 y 4)  
  *Hecho cuando:* Se integre `com.google.zxing:core` (Apache 2.0) en `desktopApp`, `QrCanvasRendererTest` valide la generación y decodificación round-trip de payloads `vcam://pair?...` sin pérdida de datos, y el código QR renderizado en `QrCodeCanvas` sea decodificable por cualquier lector estándar.


