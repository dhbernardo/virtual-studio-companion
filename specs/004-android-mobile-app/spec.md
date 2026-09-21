# Especificación: 004-android-mobile-app

## 1. Resumen Ejecutivo
Implementar la aplicación móvil para Android en el módulo `androidApp` aplicando **Clean Architecture** y **MVVM** con **Jetpack Compose**. La aplicación debe permitir el escaneo casi instantáneo del Código QR del Host mediante **Google ML Kit Barcode Scanning**, capturar video en alta resolución (1080p a 60/30 FPS) mediante **CameraX**, codificar por hardware con `MediaCodec` (H.264 / fallback MJPEG) transmitiendo frames binarios sobre WebSocket, responder al protocolo de latencia RTT mediante Ping/Pong, ofrecer un HUD de estudio profesional con mitigación térmica adaptativa y prevención de apagado de pantalla (`KeepScreenOn`), y ejecutar reactivamente comandos remotos tipados desde el Host de Windows.

---

## 2. Requisitos Funcionales (EARS)

### RF-001: Escáner Óptico de Código QR con ML Kit
- **Tipo:** Event-driven
- **Definición:** CUANDO el usuario apunte la cámara hacia el Código QR expuesto por el Host en Windows, el analizador de frames de ML Kit DEBE extraer la URI de conexión (`vcam://pair?...`), validar el esquema mediante `ParsePairingPayloadUseCase` e iniciar la conexión automática sin intervención manual.
- **Criterio de Aceptación:**
  - **DADO QUE** se detecta un QR válido en el encuadre
  - **CUANDO** ML Kit extrae la cadena
  - **ENTONCES** debe invocar `ParsePairingPayloadUseCase`, pasar la configuración al ViewModel y transitar a la pantalla de transmisión en menos de 300 ms tras la detección.

### RF-002: Pipeline de Captura y Codificación de Video por Hardware
- **Tipo:** Ubiquitous
- **Definición:** El sistema DEBE configurar una sesión de CameraX vinculada al ciclo de vida del Activity (`ProcessCameraProvider`), seleccionando la mejor resolución disponible (preferentemente 1080p a 60 FPS o 30 FPS según el sensor), canalizando los buffers hacia el codificador por hardware (`MediaCodec` / H.264 acelerado con fallback a MJPEG) y transmitiendo paquetes binarios sobre el WebSocket de video (`/ws/stream`) ejecutándose en un hilo dedicado fuera del hilo principal.
- **Criterio de Aceptación:**
  - **DADO QUE** se inicia la transmisión
  - **CUANDO** CameraX entrega buffers de imagen
  - **ENTONCES** deben codificarse y transmitirse en un despachador de alta prioridad en segundo plano (`Dispatchers.Default` o subproceso dedicado), garantizando que la UI de Compose mantenga 60 FPS estables sin tirones (*jank*) (Regla 4 AGENTS.md).

### RF-003: Interfaz Profesional "Studio Camera HUD" y Pantalla Activa
- **Tipo:** Ubiquitous
- **Definición:** La pantalla de transmisión DEBE superponer sobre el preview de la cámara los componentes compartidos del Design System (`StudioBadge`, `TelemetryPill`, `StudioIndicator` y `CameraControlBar`), manteniendo la pantalla encendida de forma continua mediante `FLAG_KEEP_SCREEN_ON` para prevenir suspensión o modo Doze durante el streaming.
- **Criterio de Aceptación:**
  - **DADO QUE** la cámara está transmitiendo
  - **CUANDO** el usuario interactúa con los controles táctiles del HUD (zoom, alternar linterna, cambio de lente)
  - **ENTONCES** la vista previa debe responder con fluidez y los controles deben respetar Touch Targets mínimos de 48dp.
  - **Y** el Activity debe configurar la bandera `FLAG_KEEP_SCREEN_ON` mientras la sesión esté activa, liberándola al cerrar la aplicación o retornar a `DISCONNECTED`.

### RF-004: Emisión de Telemetría, Latencia RTT y Mitigación Térmica Adaptativa
- **Tipo:** Event-driven
- **Definición:** CUANDO el stream esté activo, el sistema DEBE muestrear periódicamente (cada 500 ms) el nivel de batería y estado térmico con `BatteryManager` y `PowerManager` para emitir `TelemetryPacket`, responder inmediatamente con `Pong` ante paquetes `Ping` del Host para el cómputo de latencia RTT, y reducir la tasa de captura adaptativamente ante sobrecalentamiento.
- **Criterio de Aceptación:**
  - **DADO QUE** el canal WebSocket recibe un paquete `Ping(clientTimestamp)` del Host
  - **CUANDO** se procesa el mensaje de red
  - **ENTONCES** debe responder inmediatamente enviando `ProtocolMessage.Pong(clientTimestamp)` para permitir al Host medir la latencia RTT instantánea.
  - **DADO QUE** `PowerManager.getThermalStatus()` reporta estado `THERMAL_STATUS_SEVERE` o `THERMAL_STATUS_CRITICAL`
  - **CUANDO** se dispara la mitigación térmica
  - **ENTONCES** debe reducir dinámicamente la tasa de captura de 60 FPS a 30 FPS y notificar la alerta en el HUD (`StudioBadge(ALERT)`), preservando la estabilidad del terminal.

### RF-005: Ejecución Reactiva de Comandos Remotos y Cierre Ordenado
- **Tipo:** Event-driven
- **Definición:** CUANDO el canal de control reciba un comando válido proveniente del Host bajo la jerarquía sellada `ProtocolMessage.CommandPacket(CameraCommand)`, la app móvil DEBE validar las capacidades físicas del hardware y aplicar el cambio de forma inmediata, despachando `DISCONNECT_REQUEST` ante la salida voluntaria de la pantalla.
- **Criterio de Aceptación:**
  - **DADO QUE** se recibe un paquete de comando `{ "type": "COMMAND", "command": { "type": "TOGGLE_TORCH" } }`
  - **CUANDO** `cameraInfo.hasFlashUnit()` confirma la presencia de flash LED
  - **ENTONCES** debe alternar la linterna física mediante `cameraControl.enableTorch()` y reflejar el nuevo estado hacia el Host.
  - **DADO QUE** el usuario pulsa el botón de retroceso (*Back*) o salir en el HUD
  - **CUANDO** se destruye la vista de cámara
  - **ENTONCES** debe enviar un paquete `DISCONNECT_REQUEST` al Host antes de liberar la sesión de CameraX para permitir un cierre limpio de ambos extremos.
  - **DADO QUE** la app pasa a segundo plano (ej. llamada telefónica entrante)
  - **CUANDO** se suspende temporalmente el Activity
  - **ENTONCES** debe pausar el flujo de video y notificar al Host para transitar a `RECONNECTING` sin abortar bruscamente la conexión de red.

---

## 3. Requisitos No Funcionales (RNF)

- **RNF-001 (Aislamiento del Hilo Principal):** Todo el procesamiento de buffers de imagen, codificación `MediaCodec` y transmisión de sockets de red debe ejecutarse fuera del `Dispatchers.Main` mediante ejecutores dedicados, garantizando 60 FPS ininterrumpidos en Compose (AGENTS.md Regla 4).
- **RNF-002 (Gestión Térmica y de Batería con Codecs de Hardware):** El codificador debe emplear aceleración por hardware (`MediaCodec` con perfil H.264 Baseline/Main) para minimizar la carga de CPU y el consumo de batería en transmisiones continuas.
- **RNF-003 (Solicitud Mínima y Contingencia de Permisos):** La app debe solicitar exclusivamente el permiso `android.permission.CAMERA` mediante `rememberLauncherForActivityResult`. Si el usuario deniega el permiso de forma permanente (*Permanently Denied*), debe mostrar una pantalla de contingencia con textos explicativos provenientes de `Res.string.*` y un botón de navegación directa hacia `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` (Constitución Principio 8).
- **RNF-004 (Conformidad de Tema y Cero Hardcoded Strings):** La aplicación móvil debe encapsularse bajo `StudioTheme`, respetar el modo oscuro/claro del sistema y consumir el 100% de textos, etiquetas y descripciones accesibles a través de `Res.string.*` (Constitución Principios 5 y 8).
