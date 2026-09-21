# Desglose de Tareas: 001-protocol-and-domain-core

## Fase 1: Modelos de Dominio y Esquema del Código QR
- [x] **T01: Pruebas unitarias para generación, parseo y validación de URI QR (TDD)**  
  *Requisitos cubiertos:* `RF-001`, `RNF-001`  
  *Hecho cuando:* `shared/src/commonTest/.../PairingPayloadTest.kt` contenga casos para URIs válidas (`vcam://pair?...`), validación de TTL (120 s), rechazo de tokens expirados o reusados, parseo de capacidades (`fps`, `res`) y `fallbackHosts`, fallando antes de implementar.
- [x] **T02: Implementación de modelos `PairingConfig` y casos de uso de emparejamiento**  
  *Requisitos cubiertos:* `RF-001`, `RNF-001`, `RNF-003`  
  *Hecho cuando:* `GeneratePairingPayloadUseCase`, `ParsePairingPayloadUseCase` y `ValidateSessionTokenUseCase` pasen el 100% de las pruebas unitarias de `T01`.

## Fase 2: Máquina de Estados de Conexión (FSM)
- [ ] **T03: Pruebas unitarias para la máquina de estados con Turbine**  
  *Requisitos cubiertos:* `RF-002`, `RNF-001`  
  *Hecho cuando:* `shared/src/commonTest/.../ConnectionStateMachineTest.kt` valide las transiciones permitidas (`DISCONNECTED -> PAIRING -> CONNECTED -> STREAMING`), el manejo de timeout en `RECONNECTING` (10s hacia `DISCONNECTED`), la desconexión limpia (`DISCONNECT_REQUEST`) y la transición a `ERROR(ConnectionError)`.
- [ ] **T04: Implementación de `ConnectionStateMachine` y contratos de puertos**  
  *Requisitos cubiertos:* `RF-002`, `RNF-002`  
  *Hecho cuando:* La FSM exponga un `StateFlow<ConnectionState>` inmutable y satisfaga el 100% de las aserciones de `T03`.

## Fase 3: Protocolo de Telemetría, Latencia RTT y Comandos de Cámara
- [ ] **T05: Pruebas unitarias para serialización de `ProtocolMessage` y comandos remotos**  
  *Requisitos cubiertos:* `RF-003`, `RF-004`, `RNF-003`  
  *Hecho cuando:* Se verifique el encode/decode JSON exacto de la jerarquía sellada `ProtocolMessage` (Handshake, TelemetryPacket, Ping, Pong, Disconnect), cálculo de RTT y validación de límites de `CameraCommand.SetZoom` (1.0 a 5.0) con emisión de `CommandErrorCode`.
- [ ] **T06: Implementación del despachador de comandos y procesador de telemetría**  
  *Requisitos cubiertos:* `RF-003`, `RF-004`  
  *Hecho cuando:* `ProcessTelemetryUseCase` y `CameraCommandDispatcher` operen de forma inmutable, procesen pings RTT para latencia y aprueben las pruebas de `T05`.

## Fase 4: Integración del Core y Verificación de Rendimiento
- [ ] **T07: Benchmark de serialización JSON en memoria (< 2 ms) y pureza**  
  *Requisitos cubiertos:* `RNF-001`, `RNF-002`, `RNF-003`  
  *Hecho cuando:* Exista un test de rendimiento que ejecute 1.000 serializaciones consecutivas comprobando un promedio inferior a 2 ms por operación, verificando la ausencia total de imports de plataforma (`android.*`, `java.*`, `kotlinx.cinterop.*`).
