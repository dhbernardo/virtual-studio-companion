# Especificación: 003-desktop-windows-host

## 1. Resumen Ejecutivo
Implementar la aplicación de escritorio nativa para Windows en el módulo `desktopApp` utilizando **Compose Multiplatform Desktop** y **Ktor Server** bajo Clean Architecture y Puertos y Adaptadores. El Host opera como el servidor local de ingesta y señalización, dibuja en pantalla el Código QR dinámico con temporizador TTL para el emparejamiento instantáneo con Android, mide la latencia RTT mediante Ping/Pong, se conecta automáticamente con **OBS Studio** mediante **OBS WebSocket API v5** creando una fuente *"Browser Source"* en la escena activa, y se empaqueta como un ejecutable `.exe` / `.msi` autocontenido mediante `jpackage` (**sin requerir Java preinstalado en el equipo del usuario**).

---

## 2. Requisitos Funcionales (EARS)

### RF-001: Servidor Ktor Embebido, Fallback de Puertos, Ingesta de Video y Gateway de Red
- **Tipo:** Ubiquitous
- **Definición:** El sistema DEBE levantar un servidor Ktor local embebido implementando el puerto `IStreamGateway`, intentando enlazar en el puerto por defecto `:8080` y buscando secuencialmente el siguiente puerto libre en el rango `8080-8090` ante colisiones de enlace (`BindException`), filtrando adaptadores virtuales de Windows (WSL, Hyper-V, VPN) para propagar la IP de la interfaz física activa a la URI de emparejamiento. Asimismo, el gateway DEBE monitorizar el ciclo de vida de los sockets WebSocket, emitir un evento de desconexión tipado (`DisconnectRequest("CLIENT_CLOSED")`) cuando el cliente cierre la conexión o finalice el socket de control, recibir paquetes binarios (`Frame.Binary`) en `/ws/stream` exponiéndolos a través de `incomingVideoFrames: Flow<ByteArray>`, y servir la transmisión de video continua mediante el endpoint HTTP multipart `/stream/mjpeg` (`multipart/x-mixed-replace; boundary=--frame`) consumido por `/stream/preview`.
- **Criterio de Aceptación:**
  - **DADO QUE** el Host arranca en Windows
  - **CUANDO** se inicializa el ciclo de vida del servidor
  - **ENTONCES** debe abrir un puerto libre dentro del rango `8080-8090` y exponer los endpoints `/ws/control`, `/ws/stream`, `/stream/preview` y `/stream/mjpeg`.
  - **DADO QUE** el cliente móvil transmite cuadros JPEG binarios en `/ws/stream`
  - **CUANDO** Ktor recibe una trama `Frame.Binary`
  - **ENTONCES** debe emitir los bytes a `incomingVideoFrames` y transmitirlos de forma reactiva y sin bloqueos al flujo multipart de `/stream/mjpeg`.
  - **DADO QUE** el puerto 8080 está ocupado por otra aplicación
  - **CUANDO** Ktor captura la colisión
  - **ENTONCES** debe enlazar automáticamente en el siguiente puerto disponible (ej. `:8081`) y notificar el puerto efectivo al generador del Código QR sin abortar la aplicación.
  - **DADO QUE** el cliente Android cierra o pierde su sesión de control WebSocket
  - **CUANDO** el bloque de sesión de Ktor finaliza o se captura un cierre de canal
  - **ENTONCES** debe emitir `ProtocolMessage.DisconnectRequest("CLIENT_CLOSED")` para que el Host retorne automáticamente a la pantalla de emparejamiento QR.

### RF-002: Renderizado Visual del Código QR y Temporizador de Token
- **Tipo:** Event-driven
- **Definición:** CUANDO el Host no cuente con una sesión móvil activa, el sistema DEBE renderizar en la interfaz Compose Desktop un Código QR con la URI estandarizada `vcam://pair?...` generada por `GeneratePairingPayloadUseCase`, generado en estricta conformidad con el estándar internacional **ISO/IEC 18004** mediante una biblioteca permisiva (**ZXing Core**, Apache 2.0), mostrando un temporizador visual de cuenta regresiva del TTL del token (120 segundos) y un botón de refresco manual.
- **Criterio de Aceptación:**
  - **DADO QUE** el servidor está a la espera de conexión
  - **CUANDO** se muestra la vista de emparejamiento
  - **ENTONCES** debe exhibir el QR renderizado conforme a ISO/IEC 18004 garantizando decodificación óptica inmediata en lectores móviles, la IP física, el puerto activo y el token de sesión alfanumérico legible para permitir la entrada manual, junto a una barra/indicador con los 120 segundos de validez restante.
  - **DADO QUE** transcurren los 120 segundos sin que el móvil complete el handshake
  - **CUANDO** expira el temporizador
  - **ENTONCES** debe regenerar automáticamente un nuevo token efímero y actualizar el QR sin intervención del usuario.
  - **DADO QUE** el cliente Android completa el handshake
  - **CUANDO** el estado transita a `CONNECTED`
  - **ENTONCES** el QR debe ocultarse y dar paso al dashboard de telemetría y controles de cámara.

### RF-003: Integración Automática con OBS Studio (OBS WebSocket API v5)
- **Tipo:** Event-driven
- **Definición:** CUANDO el Host detecte que OBS Studio está en ejecución (o al pulsar "Conectar OBS" tanto en la vista de emparejamiento como en el dashboard de transmisión activa), el sistema DEBE conectarse al puerto `4455` implementando el puerto `IObsConnector`, autenticarse mediante desafío criptográfico SHA256 (si OBS tiene contraseña activada) y crear o actualizar de forma idempotente una fuente `"Browser Source"` (`inputKind = "browser_source"`) denominada `"Virtual Studio Camera"` en la escena activa.
- **Criterio de Aceptación:**
  - **DADO QUE** OBS Studio v5 requiere contraseña
  - **CUANDO** se recibe la solicitud de autenticación (`GetAuthRequired` / Hello auth)
  - **ENTONCES** debe resolver el desafío SHA256 utilizando la contraseña suministrada en la configuración de la UI y persistida localmente.
  - **DADO QUE** la autenticación concluye con éxito
  - **CUANDO** se inicia la transmisión o se pulsa conectar en cualquier momento con sesión móvil activa (`Connected` o `Streaming`)
  - **ENTONCES** debe verificar si `"Virtual Studio Camera"` existe previamente: si no existe, invocar `CreateInput`; si ya existe, invocar `SetInputSettings`, configurando la URL local (`http://localhost:<port>/stream/preview`) a 1080p y 60 FPS de forma automática e inmediata.
  - **DADO QUE** la sesión móvil ya está emparejada pero OBS no estaba abierto o se desconectó
  - **CUANDO** el usuario visualiza el dashboard de transmisión y pulsa "Conectar OBS"
  - **ENTONCES** la UI debe conectarse a OBS y disparar de inmediato `setupBrowserSource` sin tener que reiniciar ni desemparejar el móvil.

### RF-004: Dashboard de Telemetría, Monitor Nativo Skia, Sincronización Bidireccional y Desconexión
- **Tipo:** Event-driven
- **Definición:** CUANDO la transmisión esté activa, la interfaz de escritorio DEBE renderizar el dashboard utilizando los componentes compartidos `StudioCounterCard` (para FPS, bitrate y latencia RTT), `StudioBadge` (para estado de transmisión), `StudioIndicator` (para estado del enlace con OBS) y un **Monitor de Retorno de Video Nativo** renderizado mediante Skia a partir de `incomingVideoFrames`. Asimismo, el sistema DEBE mantener sincronización bidireccional inmediata de controles (`ProtocolMessage.CommandPacket`) y telemetría periódica (`ProtocolMessage.TelemetryPacket`) con el móvil, emitiendo paquetes `Ping` periódicos para medición de RTT y permitiendo la desconexión explícita retornando a la pantalla de emparejamiento QR con un nuevo token sin abortar la aplicación Host.
- **Criterio de Aceptación:**
  - **DADO QUE** el stream está activo
  - **CUANDO** se reciben tramas en `incomingVideoFrames`
  - **ENTONCES** el Host debe decodificar los cuadros en segundo plano mediante `org.jetbrains.skia.Image` y mostrarlos en el Monitor de Retorno en `StreamingDashboardView` con relación 16:9 y aceleración por GPU sin usar WebViews externos.
  - **DADO QUE** el móvil transmite métricas en `ProtocolMessage.TelemetryPacket`
  - **CUANDO** Ktor recibe el paquete de telemetría
  - **ENTONCES** debe actualizar reactivamente los valores reales de FPS, Bitrate (Kbps) y nivel de batería en las tarjetas `StudioCounterCard`.
  - **DADO QUE** el usuario acciona un control de cámara (zoom o linterna) en el móvil
  - **CUANDO** el móvil despacha un `ProtocolMessage.CommandPacket`
  - **ENTONCES** el Host debe recibir el comando y actualizar inmediatamente su estado visual (`torchEnabled`, `currentZoom`) sin rebotar el paquete de vuelta hacia el socket.
  - **DADO QUE** el streamer acciona un control en la UI de Windows (ej. alternar linterna o zoom)
  - **CUANDO** se despacha la orden desde el Host
  - **ENTONCES** debe actualizar su estado local y enviar `ProtocolMessage.CommandPacket` hacia el móvil.
  - **DADO QUE** el streamer opera el canal de control
  - **CUANDO** Ktor opera la sesión
  - **ENTONCES** debe emitir un paquete `Ping(clientTimestamp)` cada 1000 ms y computar la latencia RTT al recibir el `Pong(clientTimestamp)`, actualizando la tarjeta `StudioCounterCard` correspondiente.
  - **DADO QUE** el usuario pulsa "Desconectar" en el dashboard de transmisión activa
  - **CUANDO** se acciona el botón
  - **ENTONCES** debe despachar `DISCONNECT_REQUEST` al móvil, transitar el estado a `Disconnected`, generar un nuevo token efímero, reactivar el temporizador de cuenta regresiva y exhibir la vista de emparejamiento QR sin cerrar la aplicación de escritorio.
  - **DADO QUE** el usuario cierra la ventana de Windows durante una transmisión activa (`onCloseRequest`)
  - **CUANDO** se procesa el evento de salida
  - **ENTONCES** debe despachar inmediatamente un paquete `DISCONNECT_REQUEST` al móvil, desconectar de OBS y apagar el servidor Ktor limpiamente antes de salir del proceso.

### RF-005: Distribución Autocontenida sin Java (`jpackage`)
- **Tipo:** Ubiquitous
- **Definición:** La tarea de distribución de Gradle DEBE invocar a `jpackage` para generar un instalador `.msi` o ejecutable `.exe` para Windows x64 que contenga su propio runtime mínimo de OpenJDK Temurin generado con `jlink`.
- **Criterio de Aceptación:**
  - **DADO QUE** una máquina Windows limpia no tiene instalado Java ni variables de entorno `JAVA_HOME`
  - **CUANDO** el usuario ejecuta el instalador o ejecutable generado
  - **ENTONCES** la aplicación debe arrancar y operar normalmente sin dependencias externas.

---

## 3. Requisitos No Funcionales (RNF)

- **RNF-001 (Independencia de Runtime):** El artefacto de Windows no debe solicitar dependencias externas ni ejecución de comandos de instalación de Java en el sistema cliente (Constitución Principio 2).
- **RNF-002 (Consumo Eficiente de CPU):** El Host no debe superar el 4% de uso de CPU durante el enrutamiento de paquetes de red y preview en equipos quad-core modernos.
- **RNF-003 (Reconexión Resiliente con OBS):** Ante un reinicio o cierre imprevisto de OBS Studio, el adaptador `ObsWebSocketAdapter` debe intentar la reconexión con retroceso exponencial (*exponential backoff*) a los 2, 4 y 8 segundos sin bloquear el hilo principal de Compose Desktop.
- **RNF-004 (Conformidad de Tema y Recursos Centralizados):** La interfaz Compose Desktop debe operar bajo `StudioTheme`, responder reactivamente a `isSystemInDarkTheme()` y consumir el 100% de cadenas mediante `Res.string.*` e iconos mediante `Res.drawable.*` (Constitución Principios 5 y 8).
