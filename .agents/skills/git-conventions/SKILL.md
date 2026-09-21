---
name: Git Conventions
description: Convenciones para creación de ramas, commits (Conventional Commits) y flujo de trabajo Git. Cubre nomenclatura de branches, formato de mensajes de commit, tipos permitidos, scopes por módulo y ejemplos prácticos.
---

# Git Conventions

Convenciones de Git para mantener un historial limpio, legible y automatizable. Basado en [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

---

## 1. Conventional Commits — Formato

Todo mensaje de commit **debe** seguir esta estructura:

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Reglas

| Regla | Detalle |
|-------|---------|
| `type` | **Obligatorio.** Tipo del cambio (ver sección 2) |
| `scope` | **Opcional pero recomendado.** Módulo afectado (ver sección 3) |
| `description` | **Obligatorio.** Descripción breve en **imperativo** y **minúsculas** |
| `body` | Opcional. Explicación detallada del porqué |
| `footer` | Opcional. Breaking changes o referencias a tickets |
| Idioma | **Inglés** para commits |
| Longitud | Máximo **72 caracteres** en la primera línea |

---

## 2. Tipos de Commit

| Tipo | Cuándo usar | ¿Aparece en changelog? |
|------|-------------|----------------------|
| `feat` | Nueva funcionalidad | ✅ Sí |
| `fix` | Corrección de bug | ✅ Sí |
| `docs` | Solo documentación | ❌ No |
| `style` | Formato (espacios, comas, sin cambio de lógica) | ❌ No |
| `refactor` | Reestructuración sin cambiar funcionalidad ni fix | ❌ No |
| `perf` | Mejora de rendimiento | ✅ Sí |
| `test` | Agregar o corregir tests | ❌ No |
| `build` | Cambios en build o dependencias (npm, docker) | ❌ No |
| `ci` | Cambios en CI/CD (pipelines, GitHub Actions) | ❌ No |
| `chore` | Tareas de mantenimiento sin impacto en código | ❌ No |
| `revert` | Revertir un commit anterior | ✅ Sí |

### Breaking Changes

Si un commit introduce un cambio que **rompe compatibilidad**, agregar:
- `!` después del tipo/scope: `feat(auth)!: remove legacy token format`
- O en el footer: `BREAKING CHANGE: description`

---

## 3. Scopes por Módulo

Usar el nombre del módulo como scope para identificar rápidamente qué parte del sistema se modifica.

| Scope | Módulo | Tablas relacionadas |
|-------|--------|-------------------|
| `saas` | Gestión SaaS | `saas_plans`, `saas_plan_features`, `company_subscriptions`, `saas_invoices` |
| `org` | Organización / Tenants | `companies`, `branches`, `users`, `branch_users` |
| `clients` | Clientes/Usuarios finales | `clients` |
| `resources` | Catálogo de espacios | `resources`, `schedules`, `special_availability`, `price_rules` |
| `products` | Productos e inventario | `products`, `product_branch_stock` |
| `reservations` | Reservas y operaciones | `reservations`, `reservation_groups`, `reservation_addons`, `waitlists`, `cancellation_policies` |
| `billing` | Facturación SUNAT | `invoices`, `invoice_lines` |
| `payments` | Pagos y caja | `payments` |
| `auth` | Autenticación y seguridad | JWT, guards, login |
| `db` | Base de datos / Migraciones | Migraciones, seeds |
| `core` | Infraestructura global | Interceptors, filters, middlewares |
| `config` | Configuración | Variables de entorno, módulos config |
| `docker` | Docker y DevOps | Dockerfile, docker-compose |

### Ejemplos de Commits

```bash
# Feature nueva
feat(reservations): add availability check before booking

# Bug fix
fix(billing): correct IGV calculation for exonerated items

# Migración de BD
feat(db): create reservations and related tables

# Refactor
refactor(auth): extract token validation to dedicated service

# Documentación
docs(resources): add swagger decorators to controller

# Dependencias
build: upgrade nestjs to v10.3

# Breaking change
feat(org)!: change company_id from bigint to uuid

# Con body y footer
feat(reservations): implement recurring booking flow

Create N reservations linked to a reservation_group.
Validate schedule availability for each occurrence.

Refs: #PBI-1234
```

---

## 4. Nomenclatura de Ramas

### Formato

```
<type>/<ticket-id>-<short-description>
```

| Parte | Regla | Ejemplo |
|-------|-------|---------|
| `type` | Mismo tipo que en commits | `feat`, `fix`, `refactor`, `hotfix` |
| `ticket-id` | ID del PBI/Bug/Task (si aplica) | `PBI-1234`, `BUG-567` |
| `short-description` | kebab-case, máximo 4 palabras | `add-booking-flow` |

### Tipos de Rama

| Tipo | Propósito | Ejemplo |
|------|-----------|---------|
| `feat/` | Nueva funcionalidad | `feat/PBI-1234-reservation-crud` |
| `fix/` | Corrección de bug | `fix/BUG-567-igv-calculation` |
| `hotfix/` | Fix urgente en producción | `hotfix/BUG-890-login-crash` |
| `refactor/` | Reestructuración | `refactor/extract-pricing-service` |
| `chore/` | Mantenimiento | `chore/upgrade-nestjs-v10` |
| `docs/` | Solo documentación | `docs/swagger-endpoints` |
| `test/` | Solo tests | `test/reservation-use-cases` |

### Ramas Protegidas

| Rama | Propósito | ¿Se hace push directo? |
|------|-----------|----------------------|
| `main` | Producción | ❌ Solo via PR |
| `qas` | QA | ❌ Solo via PR |
| `develop` | Desarrollo integrado | ❌ Solo via PR |
| `release/*` | Preparación de release | ❌ Solo via PR |

---

## 5. Flujo de Trabajo

```
1. Crear rama desde develop:
   git checkout develop
   git pull origin develop
   git checkout -b feat/PBI-1234-reservation-crud

2. Hacer commits con conventional commits:
   git add .
   git commit -m "feat(reservations): add create reservation use case"
   git commit -m "feat(reservations): add availability validation"
   git commit -m "test(reservations): add unit tests for booking flow"

3. Push y crear PR hacia develop:
   git push origin feat/PBI-1234-reservation-crud

4. Después del merge, eliminar la rama:
   git branch -d feat/PBI-1234-reservation-crud
```

---

## 6. Checklist para Commits

- [ ] El tipo es correcto (`feat`, `fix`, `refactor`, etc.)
- [ ] El scope corresponde al módulo afectado
- [ ] La descripción es en imperativo e inglés ("add", no "added" ni "adding")
- [ ] La primera línea no supera 72 caracteres
- [ ] Si hay breaking change, está marcado con `!` o `BREAKING CHANGE:`
- [ ] Un commit = un cambio lógico (no mezclar features con fixes)

---
