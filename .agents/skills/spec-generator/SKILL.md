# Skill: spec-generator

## Propósito
Generar una especificación formal (`spec.md`) basada en la metodología Spec-Driven Development (SDD), utilizando la sintaxis de requisitos EARS (Easy Approach to Requirements Syntax) y criterios de aceptación claros.

## Cuándo usarla
- Al iniciar una nueva funcionalidad o módulo (`specs/NNN-nombre-feature/`).
- Cuando se requiera transformar una idea informal en requisitos testeables.

## Reglas de Generación
1. **Identificador único:** Todo requisito funcional debe seguir el formato `RF-XXX` (ej. `RF-001`).
2. **Sintaxis EARS:** Todo requisito funcional debe redactarse usando exclusivamente una de las 5 estructuras EARS:
   - **Ubiquitous:** "El sistema DEBE [acción]."
   - **Event-driven:** "CUANDO [evento], el sistema DEBE [acción]."
   - **State-driven:** "MIENTRAS [estado], el sistema DEBE [acción]."
   - **Unwanted event:** "SI [error/excepción], ENTONCES el sistema DEBE [acción]."
   - **Optional:** "DONDE [opción activa], el sistema DEBE [acción]."
3. **Criterios de Aceptación (Gherkin):** Cada requisito debe incluir al menos un escenario en formato:
   - `DADO QUE [contexto]`
   - `CUANDO [acción/evento]`
   - `ENTONCES [resultado esperado]`
4. **Requisitos No Funcionales (RNF):** Categorizados en Rendimiento, Seguridad, Compatibilidad y Mantenibilidad.
5. **Sin código de implementación:** El documento especifica *qué* hace el sistema, no *cómo* está programado en detalle.
