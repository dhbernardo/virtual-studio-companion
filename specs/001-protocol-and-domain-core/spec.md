# Especificación: 001-protocol-and-domain-core

## 1. Resumen Ejecutivo
Implementar el núcleo de dominio y el protocolo de comunicación compartido en `shared/commonMain` bajo Clean Architecture. Esta capa define la máquina de estados finitos (FSM) del ciclo de vida de conexión, el esquema y parseo de la URI del Código QR de emparejamiento con token criptográfico efímero, el protocolo de telemetría en tiempo real con cálculo de latencia por viaje redondo (RTT Ping/Pong), y los comandos tipados de control remoto de cámara con códigos de error estandarizados sin textos en crudo.

---

## 2. Requisitos Funcionales (EARS)

### RF-001: Generación y parseo del payload del Código QR
- **Tipo:** Event-driven
- **Definición:** CUANDO el Host de escritorio inicie la sesión de espera, el sistema DEBE generar una URI estandarizada con esquema `vcam://` conteniendo la dirección IP física activa del Host, el puerto del gateway, un token de sesión criptográfico efímero de un solo uso (128 bits, Base64URL, TTL de 120 segundos), las capacidades iniciales recomendadas (`fps`, `res`) y opcionalmente direcciones IP secundarias de respaldo (`fallbackHosts`).
- **Criterio de Aceptación:**
  - **DADO QUE** el Host se inicializa en `192.168.1.50:8080` con el token `sec-9f8a1b2c3d4e`
  - **CUANDO** se invoca `GeneratePairingPayloadUseCase`
  - **ENTONCES** debe generar la URI estandarizada `vcam://pair?host=192.168.1.50&port=8080&token=sec-9f8a1b2c3d4e&v=1&fps=60&res=1080p`.
  - **DADO QUE** el cliente Android escanea dicha URI antes de transcurrir los 120 segundos de TTL
  - **CUANDO** se procesa mediante `ParsePairingPayloadUseCase`
  - **ENTONCES** debe deserializar un objeto `PairingConfig` inmutable conteniendo los parámetros exactos y capacidades sugeridas.
  - **DADO QUE** el token presentado supera los 120 segundos de antigüedad o ya fue utilizado en un handshake previo
  - **CUANDO** se invoca `ValidateSessionTokenUseCase`
  - **ENTONCES** debe rechazar la conexión emitiendo `ConnectionErrorCode.TOKEN_EXPIRED` o `ConnectionErrorCode.TOKEN_ALREADY_USED`.

### RF-002: Máquina de estados finitos del ciclo de vida de conexión
- **Tipo:** State-driven
- **Definición:** MIENTRAS el sistema esté en ejecución, la conexión DEBE transitar exclusivamente por los estados definidos: `DISCONNECTED`, `DISCOVERING`, `PAIRING`, `CONNECTED`, `STREAMING`, `RECONNECTING` y `ERROR(val reason: ConnectionError)`.
- **Criterio de Aceptación:**
  - **DADO QUE** el estado actual es `DISCONNECTED`
  - **CUANDO** se recibe un payload QR válido
  - **ENTONCES** debe transitar a `PAIRING` y posteriormente a `CONNECTED` al validar exitosamente el handshake y token de sesión.
  - **DADO QUE** el handshake falla por token inválido, expirado o timeout de red
  - **CUANDO** se detecta la anomalía
  - **ENTONCES** debe transitar a `ERROR(reason)` sin colapsar el proceso.
  - **DADO QUE** se produce una pérdida de paquetes de heartbeat durante `STREAMING`
  - **CUANDO** transcurren más de 3000 ms sin recibir paquetes
  - **ENTONCES** debe transitar a `RECONNECTING` notificando a la UI sin liberar prematuramente los recursos de la cámara.
  - **DADO QUE** la conexión no se recupera durante `RECONNECTING`
  - **CUANDO** transcurren más de 10 segundos continuos sin restablecer el canal
  - **ENTONCES** debe abortar el intento y transitar a `DISCONNECTED`, liberando recursos de red y captura.
  - **DADO QUE** el usuario solicita el cierre de sesión voluntario en cualquiera de los extremos
  - **CUANDO** se despacha un paquete `DISCONNECT_REQUEST`
  - **ENTONCES** debe transitar inmediatamente a `DISCONNECTED` sin esperar timeouts de inactividad.

### RF-003: Paquete unificado de telemetría y medición de latencia RTT
- **Tipo:** Event-driven
- **Definición:** CUANDO la cámara móvil esté transmitiendo, el sistema DEBE generar periódicamente (cada 500 ms) un paquete serializable de telemetría (`TelemetryPacket`), incorporando la latencia de red calculada mediante el mecanismo de Heartbeat Ping/Pong de viaje redondo (RTT) medido por el Host para prevenir discrepancias por desincronización horaria (Clock Drift).
- **Criterio de Aceptación:**
  - **DADO QUE** la cámara emite frames a 60 FPS con un bitrate de 8500 kbps y nivel de batería del 85%
  - **CUANDO** se serializa el paquete de telemetría
  - **ENTONCES** el payload JSON debe cumplir con el siguiente esquema tipado:
    ```json
    {
      "type": "TELEMETRY",
      "timestamp": 1726850000000,
      "metrics": {
        "fps": 60.0,
        "bitrateKbps": 8500,
        "latencyMs": 42,
        "batteryLevel": 85,
        "isCharging": false,
        "thermalState": "NOMINAL"
      }
    }
    ```
  - **Y** el valor de `latencyMs` debe calcularse en el Host a partir de la diferencia entre el envío de `Ping(clientTimestamp)` y la recepción del correspondiente `Pong(clientTimestamp)`.

### RF-004: Despacho y validación de comandos de control remoto
- **Tipo:** Event-driven
- **Definición:** CUANDO el Host de escritorio envíe un comando de ajuste hacia la cámara (`CameraCommand`), el sistema DEBE validar que la FSM se encuentre en estado `CONNECTED` o `STREAMING`, comprobar los límites físicos y de rango del comando, y despacharlo hacia el adaptador correspondiente mediante la jerarquía sellada `ProtocolMessage`.
- **Criterio de Aceptación:**
  - **DADO QUE** la conexión está en `STREAMING` y el Host envía `{ "type": "COMMAND", "command": { "type": "SET_ZOOM", "zoomRatio": 2.5 } }`
  - **CUANDO** el valor de zoom se sitúa entre el rango permitido (1.0 a 5.0)
  - **ENTONCES** debe emitir el evento `CameraCommand.SetZoom(2.5f)`.
  - **DADO QUE** el Host envía un valor fuera de rango (ej. `-1.0` o `15.0`)
  - **CUANDO** se procesa la validación
  - **ENTONCES** debe rechazar el comando emitiendo `CommandErrorCode.VALUE_OUT_OF_RANGE` sin alterar el estado de la cámara ni emitir textos en crudo (Constitución Principio 8).
  - **DADO QUE** el Host envía un comando de linterna (`ToggleTorch`) y el dispositivo no cuenta con flash disponible
  - **CUANDO** se evalúa la capacidad del sensor
  - **ENTONCES** debe rechazar la orden con `CommandErrorCode.UNSUPPORTED_HARDWARE`.

---

## 3. Requisitos No Funcionales (RNF)

- **RNF-001 (Pureza Absoluta de Plataforma):** Todo el código de este módulo debe ser 100% Kotlin común puro dentro de `shared/src/commonMain`, quedando estrictamente vetado el uso o importación de `android.*`, `java.*`, `java.io.*`, `java.net.*` y `kotlinx.cinterop.*` (Constitución Principio 1).
- **RNF-002 (Baja Asignación de Memoria y Cero Garbage Collection Spikes):** Los modelos de telemetría y comandos deben estructurarse como clases de datos inmutables (`@Serializable data class` / `@JvmInline value class`) para garantizar cero fuga de memoria y minimizar pausas del Garbage Collector durante transmisiones a 60 FPS.
- **RNF-003 (Rendimiento de Serialización):** La codificación y decodificación de mensajes JSON (`ProtocolMessage`) debe promediar menos de 2 milisegundos por operación en pruebas de rendimiento sobre CPU móvil estándar.
