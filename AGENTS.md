# AGENTS.md — cómo se trabaja en `agencia-360-backend`

Archivo canónico para **cualquier** agente de código (Claude Code, Codex, Antigravity/Gemini, Cursor…) y para las personas. `CLAUDE.md` y `GEMINI.md` solo apuntan aquí.

Orden de verdad si algo choca:

1. **Ficha técnica CareCar v2.0** — vive en `carecar-mobile/docs/ficha-tecnica/` (`.docx` + transcripción). Cada funcionalidad lista las tablas que participan; todas existen sí o sí.
2. **Este archivo**.
3. **Plan maestro v2** — `carecar-mobile/plan/` (fuente única de módulos e historias). El espejo backend está en [`plan/`](plan/README.md).
4. `README.md`, `docs/` y el contrato OpenAPI en `docs/api/`.

Idioma: **español** en docs, commits, PR, nombres de dominio y comentarios.

## Prioridades del equipo

**App CareCar (móvil) > este backend > CRM y ERP web (congelados).** Aquí se trabaja sobre todo el módulo `carecarapp` y los módulos nuevos que la app v2 necesita. Lo que solo sirve al ERP o al CRM web no se toca salvo que una historia de la app lo requiera.

## Reglas no negociables

| # | Regla |
| --- | --- |
| R1 | **La ficha se cumple.** Toda tabla que la ficha técnica v2.0 nombra existe con ese nombre (58 funcionalidades, ver su transcripción y `carecar-mobile/plan/cumplimiento-ficha.md`). Se pueden agregar campos; nunca quitar ni renombrar los de la ficha. Primeras brechas: `V2-T05.03`–`V2-T05.07`. |
| R2 | **Documentación siempre, sin excepción.** Todo PR actualiza las docs afectadas (README, `docs/`, contrato OpenAPI, `plan/`) y el estado de la historia en `carecar-mobile/plan/tablero.md` (PR aparte en ese repo si hace falta). |
| R3 | **Sin IA como autor o co-autor.** Prohibido `Co-Authored-By:` de Claude/Codex/Gemini/Copilot/Antigravity y firmas "Generated with". El autor es la persona dueña de la tarea. Lo vigilan `.githooks/commit-msg` y el workflow `Autoría`. |
| R4 | **Commits atómicos con intención** — `<tipo>(<modulo>): <verbo + cambio>`; el cuerpo explica el porqué ([`docs/convenciones-commits.md`](docs/convenciones-commits.md)). |
| R5 | **Nada directo a `main`.** Rama `<tipo>/<id-historia>-<descripcion>` → PR → 1 aprobación humana + checks verdes → merge commit. |
| R6 | **Todos contribuyen** (las 6 personas, sin importar rol Scrum). Reparto en `carecar-mobile/plan/equipo-y-reparto.md`. |
| R7 | **Sin fechas en el plan**: objetivos, hitos, criterios de salida. |
| R8 | **Seguridad primero** (sección final). |

Primera vez en el repo: `git config core.hooksPath .githooks`.

## Roles de agente

Iguales en todas las herramientas; en Claude Code son subagentes en [`.claude/agents/`](.claude/agents/).

| Rol | Modelo sugerido | Hace |
| --- | --- | --- |
| **Sincronizador** | Haiku | Baja cambios y **descarta todo lo local** en los repos (`fetch` → `reset --hard origin/main` → `clean -fd`). |
| **Planificador** | Opus | Diseña tablas, migraciones, eventos y endpoints para las historias **B** del plan; escribe ADR. No implementa. |
| **Orquestador** | Sonnet | Reparte historias **B** a implementadores en paralelo (worktrees), vigila WIP, commits, docs y PR. No implementa. |
| **Implementador** | Sonnet | Una historia, TDD, commits atómicos, docs, PR. |

## Comandos

Maven viene incluido (`./mvnw`). Docker debe estar activo: las pruebas usan Testcontainers (PostgreSQL propio), no el `docker-compose.yml`.

```bash
docker compose up -d                                          # PostgreSQL, Redis y Mailpit locales
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev         # API en :8080 (perfil dev: SQL + logs DEBUG)
./mvnw clean compile                                          # solo compilar
./mvnw test                                                   # pruebas
./mvnw verify                                                 # compila + todas las pruebas (lo que corre CI)
./mvnw test -Dtest=NombreTest                                 # una clase
./mvnw test -Dtest=NombreTest#metodo                          # un método
./mvnw spotless:apply                                         # corrige formato (Eclipse formatter)
./mvnw checkstyle:check                                       # reglas de estilo (config/checkstyle/checkstyle.xml, líneas ≤140)
./scripts/cargar-datos-de-ejemplo.sh                          # datos de ejemplo vía API (requiere API corriendo con ADMIN_INICIAL_*/GERENTE_INICIAL_*)
./scripts/limpiar-datos.sh                                    # borra datos de ejemplo
```

Antes de subir: `spotless:apply`, `checkstyle:check`, `verify` (CI y Lint fallan si no pasan). Swagger UI en `/swagger-ui.html`, health en `/actuator/health`.

## Arquitectura

Monolito modular bajo `src/main/java/dev/latticesystems/erp_agente360/backend_agencia360/`. Cada área funcional es un módulo de Spring Modulith: `acceso`, `ventascrm`, `clientes`, `inventario`, `carecarapp`, `facturacion`, `servicio`, `refacciones`, `caja`, `contabilidad`. `shared/` es el kernel abierto (config, security, exception, domain base). Módulos nuevos para la app v2: ver [`plan/README.md`](plan/README.md).

- Convención interna de cada módulo: `package-info.java` (`@ApplicationModule`), `domain/`, `application/`, `web/` (controllers + DTOs), `infrastructure/` (repositorios, adaptadores).
- Entre módulos: **eventos de dominio de Modulith**, no llamadas directas a servicios de otro módulo. `ModularityTests` ejecuta `ApplicationModules.verify()`; un ciclo o acceso a paquete interno de otro módulo rompe el build.
- Endpoints bajo `/api/v1` (los de la app bajo `/api/v1/app`), JWT `Authorization: Bearer`. Principal: `UsuarioAutenticado(id, usuario, roles)`. Un usuario del personal tiene un solo rol; el rol decide si entra a ERP o CRM. El enum `shared/security/Role.java` debe coincidir con la tabla `rol` (migraciones V2, V6).
- Esquema solo por Flyway (`src/main/resources/db/migration/V<n>__*.sql`); Hibernate en `validate`. Nunca editar una migración ya integrada a `main`: crear una nueva. Cada entidad JPA nueva necesita su migración.
- Reglas de dominio: dinero no se borra, se revierte con movimiento inverso; inventario por movimientos con bloqueo de fila (`SELECT ... FOR UPDATE`); unidades/refacciones se editan, no se eliminan; refacción nace con existencia 0.
- El contrato OpenAPI alimenta los clientes de la app y los frontends: no romperlo sin versionar. La app v2 usa `docs/api/agencia360-api-app-v2.yaml` (compatible con v1); al cambiarlo, copia en `carecar-mobile/api/`.

## Pruebas

Cada módulo con código tiene pruebas de dominio, de servicio (Mockito) y de integración HTTP (`*IntegrationTest`, heredan de `shared/AbstractIntegrationTest`, PostgreSQL real vía Testcontainers). Mayor rigor en pagos, facturación, inventario y contabilidad. Cada criterio de aceptación de una historia **B** tiene su prueba.

## Flujo de una historia B

1. Toma la historia del tablero (`carecar-mobile/plan/tablero.md`) y su detalle en `carecar-mobile/plan/modulos/`.
2. Rama desde `main` actualizado: `feat/v2-t05-07-franja-horario`.
3. TDD: migración + entidad + servicio + controlador + pruebas, un commit por intención.
4. Contrato OpenAPI, README/`docs/` y `plan/` actualizados en el mismo PR.
5. `./mvnw spotless:apply checkstyle:check verify` en verde.
6. PR con la plantilla; actualiza el tablero en `carecar-mobile`.

## Seguridad

- Si se detecta una vulnerabilidad, documéntala primero en `SECURITY_REPORT.md`. Ese archivo está en `.gitignore` porque el repositorio es público: los hallazgos de código abiertos se registran como borradores privados en GitHub Security Advisories (`gh api repos/lattice-systems/agencia-360-backend/security-advisories`), nunca como issues públicos.
- Los CVE de dependencias los bloquea `.github/workflows/trivy.yml` (HIGH/CRITICAL con parche). Se corrigen sobrescribiendo la propiedad del BOM de Spring Boot en `<properties>` del `pom.xml`; las excepciones van en `.trivyignore.yaml` con `statement` y `expired_at`.
- OWASP Top 10 y Spring Security Best Practices; consultas parametrizadas (JPA/PreparedStatements) y validaciones Jakarta.
- Sin secretos en el repo; llaves de terceros (pagos, IA, FCM) solo por variables de entorno.
