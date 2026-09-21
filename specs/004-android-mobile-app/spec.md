# Especificación: 004-android-mobile-app

## 1. Resumen Ejecutivo
Implementar la aplicación móvil para Android en el módulo `androidApp` aplicando **Clean Architecture** y **MVVM** con **Jetpack Compose**. La aplicación debe permitir el escaneo casi instantáneo del Código QR del Host mediante **Google ML Kit Barcode Scanning**, capturar video en alta resolución (1080p a 60/30 FPS) mediante **CameraX**, codificar por hardware con `MediaCodec` (H.264 / fallback MJPEG) transmitiendo frames binarios sobre WebSocket, responder al protocolo de latencia RTT mediante Ping/Pong, ofrecer un HUD de estudio profesional con mitigación térmica adaptativa y prevención de apagado de pantalla (`KeepScreenOn`), y ejecutar reactivamente comandos remotos tipados desde el Host de Windows.

---

## 2. Requisitos Funcionales (EARS)

### RF-001: Mecanismo de Emparejamiento (Escaneo Óptico QR y Contingencia Manual)
- **Tipo:** Event-driven & User-driven
- **Definición:** El sistema DEBE proveer un mecanismo integral de emparejamiento con el Host en Windows que soporte tanto detección óptica instantánea mediante **Google ML Kit** (`Barcode.FORMAT_QR_CODE`) sobre la URI de conexión (`vcam://pair?...`) con visor de recorte transparente, como contingencia manual mediante un diálogo modal Compose para el ingreso directo de IP, puerto y token, validando el esquema a través de `ParsePairingPayloadUseCase`, consumiendo de forma unívoca la configuración detectada (`consumeScannedConfig()`) para evitar bucles de navegación al regresar de la pantalla de transmisión, inicializando el estado de la pantalla de transmisión directamente en `Pairing(config)` y configurando los recolectores de mensajes antes del enlace de sockets para evitar la pérdida de paquetes de control.
- **Criterio de Aceptación:**
  - **DADO QUE** se detecta un Código QR válido en el encuadre
  - **CUANDO** ML Kit extrae la cadena
  - **ENTONCES** debe invocar `ParsePairingPayloadUseCase`, pasar la configuración al ViewModel, consumir el evento de configuración para que no quede retenido en el estado, y transitar a la pantalla de transmisión en menos de 300 ms tras la detección.
  - **DADO QUE** se abre `CameraScreen` con la configuración recibida
  - **CUANDO** se inicializa `CameraStreamViewModel`
  - **ENTONCES** su estado inicial debe nacer en `ConnectionState.Pairing(config)` y activar la escucha de mensajes antes de abrir la conexión WebSocket con el Host.
  - **DADO QUE** el usuario regresa de la pantalla de transmisión a la de escaneo tras desconectarse o finalizar
  - **CUANDO** se recompone `QrScannerScreen`
  - **ENTONCES** `scannedConfig` debe encontrarse nulo (`null`), manteniendo la cámara lista para un nuevo escaneo sin saltar automáticamente a la pantalla de transmisión.
  - **DADO QUE** el analizador de ML Kit reporta una falla interna o el modelo no está listo
  - **CUANDO** se invoca el listener de falla
  - **ENTONCES** debe propagar el estado de error al ViewModel para advertir al usuario sin colapsar el pipeline de análisis ni cerrar prematuramente el ejecutor.
  - **DADO QUE** el visor de escaneo se superpone a la vista previa de la cámara
  - **CUANDO** se renderiza la máscara de oscurecimiento (`ScannerOverlay`)
  - **ENTONCES** debe utilizar una estrategia de composición fuera de pantalla (`CompositingStrategy.Offscreen`) para que el recorte central preserve la transparencia completa sobre el `PreviewView` sin exponer el fondo negro de la ventana.
  - **DADO QUE** el usuario experimenta dificultades ópticas o de enfoque
  - **CUANDO** pulsa la acción de ingreso manual en la pantalla de escaneo
  - **ENTONCES** debe desplegar un diálogo modal con campos para IP, puerto y token de sesión, consumiendo el 100% de sus textos desde `Res.string.*`.
  - **DADO QUE** el usuario introduce parámetros válidos en el diálogo manual
  - **CUANDO** pulsa "Conectar"
  - **ENTONCES** debe validar los campos, construir un `PairingConfig` y transitar inmediatamente a la pantalla de transmisión.

### RF-002: Pipeline de Captura y Codificación de Video por Hardware
- **Tipo:** Ubiquitous
- **Definición:** El sistema DEBE configurar una sesión de CameraX vinculada al ciclo de vida del Activity (`ProcessCameraProvider`), seleccionando la mejor resolución disponible (preferentemente 1080p a 60 FPS o 30 FPS según el sensor), canalizando los buffers hacia el codificador por hardware (`MediaCodec` / H.264 acelerado con fallback a MJPEG) y transmitiendo paquetes binarios sobre el WebSocket de video (`/ws/stream`) ejecutándose en un hilo dedicado fuera del hilo principal.
- **Criterio de Aceptación:**
  - **DADO QUE** se inicia la transmisión
  - **CUANDO** CameraX entrega buffers de imagen
  - **ENTONCES** deben codificarse y transmitirse en un despachador de alta prioridad en segundo plano (`Dispatchers.Default` o subproceso dedicado), garantizando que la UI de Compose mantenga 60 FPS estables sin tirones (*jank*) (Regla 4 AGENTS.md).

### RF-003: Interfaz Profesional "Studio Camera HUD" y Pantalla Activa
- **Tipo:** Ubiquitous
- **Definición:** La pantalla de transmisión DEBE superponer sobre el preview de la cámara los componentes compartidos del Design System (`StudioBadge`, `TelemetryPill`, `StudioIndicator` y `CameraControlBar`), manteniendo la pantalla encendida de forma continua mediante `FLAG_KEEP_SCREEN_ON` para prevenir suspensión o modo Doze durante el streaming, y garantizando una renderización nítida y contrastada de los iconos y botones de control sin desenfoques indeseados sobre sus glifos.
- **Criterio de Aceptación:**
  - **DADO QUE** la cámara está transmitiendo
  - **CUANDO** el usuario interactúa con los controles táctiles del HUD (zoom, alternar linterna, cambio de lente)
  - **ENTONCES** la vista previa debe responder con fluidez, los controles deben renderizarse nítidos con fondo traslúcido estilizado y respetar Touch Targets mínimos de 48dp.
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
- **Definición:** CUANDO el canal de control reciba un comando válido proveniente del Host bajo la jerarquía sellada `ProtocolMessage.CommandPacket(CameraCommand)`, la app móvil DEBE validar las capacidades físicas del hardware y aplicar el cambio de forma inmediata, despachando `DISCONNECT_REQUEST` ante la salida voluntaria de la pantalla o la pausa/detención del ciclo de vida del Activity (`ON_STOP`/`ON_DESTROY`), y detectando el cierre remoto del socket del Host para regresar de inmediato a la pantalla de escaneo mediante un guardián de transición que evite cierres en el montaje inicial.
- **Criterio de Aceptación:**
  - **DADO QUE** se recibe un paquete de comando `{ "type": "COMMAND", "command": { "type": "TOGGLE_TORCH" } }`
  - **CUANDO** `cameraInfo.hasFlashUnit()` confirma la presencia de flash LED
  - **ENTONCES** debe alternar la linterna física mediante `cameraControl.enableTorch()` y reflejar el nuevo estado hacia el Host.
  - **DADO QUE** el usuario pulsa el botón "Finalizar Transmisión" en el HUD
  - **CUANDO** se acciona el botón
  - **ENTONCES** debe enviar un paquete `DISCONNECT_REQUEST` al Host, liberar la sesión de CameraX y navegar reactivamente de vuelta a la pantalla de escaneo QR.
  - **DADO QUE** el Host de Windows se cierra, desconecta o cae inesperadamente
  - **CUANDO** el canal WebSocket entrante de Ktor detecta la finalización de los frames
  - **ENTONCES** debe emitir `ProtocolMessage.DisconnectRequest("SERVER_CLOSED")`, provocando la transición del ViewModel a `Disconnected`, la liberación de CameraX y el retorno automático de la pantalla a la vista de escáner.
  - **DADO QUE** la pantalla `CameraScreen` se compone inicialmente
  - **CUANDO** se evalúa el observador reactivo de estado de conexión (`LaunchedEffect`)
  - **ENTONCES** debe protegerse mediante un guardián de sesión activa (`hasActiveSession`) de modo que solo transite a escaneo cuando una sesión previamente activa transite a `Disconnected`, impidiendo cualquier rebote prematuro.
  - **DADO QUE** la app pasa a segundo plano o se detiene (`ON_STOP` o `ON_DESTROY`)
  - **CUANDO** el ciclo de vida del Activity se suspende
  - **ENTONCES** debe enviar `DISCONNECT_REQUEST("APP_LIFECYCLE_STOP")` al Host, liberar CameraX y cerrar ordenadamente los sockets.

---

## 3. Requisitos No Funcionales (RNF)

- **RNF-001 (Aislamiento del Hilo Principal):** Todo el procesamiento de buffers de imagen, codificación `MediaCodec` y transmisión de sockets de red debe ejecutarse fuera del `Dispatchers.Main` mediante ejecutores dedicados, garantizando 60 FPS ininterrumpidos en Compose (AGENTS.md Regla 4).
- **RNF-002 (Gestión Térmica y de Batería con Codecs de Hardware):** El codificador debe emplear aceleración por hardware (`MediaCodec` con perfil H.264 Baseline/Main) para minimizar la carga de CPU y el consumo de batería en transmisiones continuas.
- **RNF-003 (Solicitud Mínima y Contingencia de Permisos):** La app debe solicitar exclusivamente el permiso `android.permission.CAMERA` mediante `rememberLauncherForActivityResult`. Si el usuario deniega el permiso de forma permanente (*Permanently Denied*), debe mostrar una pantalla de contingencia con textos explicativos provenientes de `Res.string.*` y un botón de navegación directa hacia `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` (Constitución Principio 8).
- **RNF-004 (Conformidad de Tema y Cero Hardcoded Strings):** La aplicación móvil debe encapsularse bajo `StudioTheme`, respetar el modo oscuro/claro del sistema y consumir el 100% de textos, etiquetas y descripciones accesibles a través de `Res.string.*` (Constitución Principios 5 y 8).
- **RNF-005 (Política de Tráfico Local y Resiliencia de Conexión):** La aplicación debe permitir tráfico en texto plano (`cleartextTrafficPermitted="true"`) mediante `network_security_config.xml` vinculado en `AndroidManifest.xml` exclusivamente para subredes privadas locales (HTTP y WebSockets `ws://`), y capturar de forma segura las excepciones de red en corrutinas (`KtorClientStreamAdapter`), detectando el fin del flujo entrante (`incoming.receiveCatching()`) para emitir desconexión tipada sin colapsar el proceso ante cortes del Host.
