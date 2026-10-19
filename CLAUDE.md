# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proyecto

API REST de **ERP Agencia360** y **CRM CareCar** (+ backend de la app móvil postventa). Java 25, Spring Boot 3.5, Spring Modulith, JPA, Flyway, PostgreSQL, Redis, JWT. README.md es extenso y en español (stack, entorno local, datos de ejemplo, endpoints); el resto de la documentación y los mensajes de commit/PR van en español.

## Comandos

Maven viene incluido (`./mvnw`). Docker debe estar activo: las pruebas usan Testcontainers (PostgreSQL propio), no el `docker-compose.yml`.

```bash
docker compose up -d                                          # PostgreSQL + Redis locales
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

Antes de subir: `spotless:apply`, `checkstyle:check`, `test` (CI y Lint fallan si no pasan). Swagger UI en `/swagger-ui.html`, health en `/actuator/health`.

## Arquitectura

Monolito modular bajo `src/main/java/dev/latticesystems/erp_agente360/backend_agencia360/`. Cada área funcional es un módulo de Spring Modulith: `acceso`, `ventascrm`, `clientes`, `inventario` tienen código; `facturacion`, `servicio`, `refacciones`, `caja`, `contabilidad`, `carecarapp` están vacíos (solo `.gitkeep`). `shared/` es el kernel abierto (config, security, exception, domain base).

- Convención interna de cada módulo: `package-info.java` (`@ApplicationModule`), `domain/`, `application/`, `web/` (controllers + DTOs), `infrastructure/` (repositorios, adaptadores).
- Entre módulos: **eventos de dominio de Modulith**, no llamadas directas a servicios de otro módulo. `ModularityTests` ejecuta `ApplicationModules.verify()`; un ciclo o acceso a paquete interno de otro módulo rompe el build.
- Todos los endpoints bajo `/api/v1`, JWT `Authorization: Bearer`. Principal: `UsuarioAutenticado(id, usuario, roles)`. Un usuario del personal tiene un solo rol; el rol decide si entra a ERP o CRM. El enum `shared/security/Role.java` debe coincidir con la tabla `rol` (migraciones V2, V6).
- Esquema solo por Flyway (`src/main/resources/db/migration/V<n>__*.sql`); Hibernate en `validate`. Nunca editar una migración ya integrada a `main`: crear una nueva. Cada entidad JPA nueva necesita su migración.
- Reglas de dominio: dinero no se borra, se revierte con movimiento inverso; inventario por movimientos con bloqueo de fila (`SELECT ... FOR UPDATE`); unidades/refacciones se editan, no se eliminan; refacción nace con existencia 0.
- El contrato OpenAPI alimenta clientes React y Flutter generados: no romperlo sin versionar.

## Pruebas

Cada módulo con código tiene pruebas de dominio, de servicio (Mockito) y de integración HTTP (`*IntegrationTest`, heredan de `shared/AbstractIntegrationTest`, PostgreSQL real vía Testcontainers). Mayor rigor en pagos, facturación, inventario y contabilidad.

## Flujo git

Ver `docs/convenciones-commits.md`. Sin push directo a `main`; PR con merge commit. Ramas `<tipo>/<issue>-<descripcion>`; commits `<tipo>(<alcance>): <verbo + cambio>` (Conventional Commits, alcance = módulo).

## Reglas de seguridad y flujo de trabajo

- NUNCA modifiques archivos de código sin aprobación explícita.
- Si se detecta una vulnerabilidad, documéntala primero en `SECURITY_REPORT.md`. Ese archivo está en `.gitignore` porque el repositorio es público: los hallazgos de código abiertos se registran como borradores privados en GitHub Security Advisories (`gh api repos/lattice-systems/agencia-360-backend/security-advisories`), nunca como issues públicos.
- Los CVE de dependencias los bloquea `.github/workflows/trivy.yml` (HIGH/CRITICAL con parche). Se corrigen sobrescribiendo la propiedad del BOM de Spring Boot en `<properties>` del `pom.xml`; las excepciones van en `.trivyignore.yaml` con `statement` y `expired_at`.
- Sigue las pautas de OWASP Top 10 y Spring Security Best Practices.
- Al generar parches, prioriza consultas parametrizadas (PreparedStatements/JPA) y validaciones Jakarta.
