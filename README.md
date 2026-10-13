# backend-agencia360

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Spring%20Modulith-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Modulith" />
  <img src="https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven" />
</p>
<p align="center">
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis" />
  <img src="https://img.shields.io/badge/Flyway-CC0200?style=for-the-badge&logo=flyway&logoColor=white" alt="Flyway" />
  <img src="https://img.shields.io/badge/Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white" alt="Hibernate" />
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" alt="Spring Security" />
  <img src="https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white" alt="JWT" />
  <img src="https://img.shields.io/badge/OpenAPI%20%2F%20Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black" alt="OpenAPI / Swagger" />
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker" />
  <img src="https://img.shields.io/badge/Microsoft%20Azure-0078D4?style=for-the-badge&logo=microsoftazure&logoColor=white" alt="Microsoft Azure" />
  <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white" alt="GitHub Actions" />
  <img src="https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase Cloud Messaging" />
</p>
<p align="center">
  <img src="https://img.shields.io/badge/JUnit%205-25A162?style=for-the-badge&logo=junit5&logoColor=white" alt="JUnit 5" />
  <img src="https://img.shields.io/badge/Testcontainers-2496ED?style=for-the-badge&logo=testcontainers&logoColor=white" alt="Testcontainers" />
</p>

API REST del **ERP Agencia360** y del backend de **CareCar** (CRM de prospectos y app postventa). Este documento resume el proyecto y el stack para quien llega al repositorio por primera vez; la fuente de verdad es `Agencia360_Stack_y_Arquitectura_v2.pdf` (versión 2) — este README es un extracto orientado a arrancar el backend, no lo reemplaza.

## 1. Qué resuelve el proyecto

La agencia automotriz no tiene sus sistemas centralizados. El proyecto los centraliza en dos productos que comparten este mismo backend:

- **ERP Agencia360**: Facturación/Administración, Contabilidad, Servicio, Refacciones, Caja, Inventario y Ventas.
- **CRM CareCar**: sistema web (orientado a móviles) para que los vendedores den seguimiento a prospectos, más una **app móvil nativa** (Android e iOS, Flutter) de servicio postventa para clientes que ya compraron una unidad.

### Alcance del MVP

| Producto | Alcance del MVP |
|---|---|
| ERP Agencia360 | Inventario básico · Caja y Ventas (cobros y liquidación) · Facturación y Administración (validar pagos y liberar entregas) |
| CRM CareCar (web) | Prospectos por etapas, del primer contacto a la entrega · categorización de perdidos para marketing |
| App CareCar | Autenticación cerrada · Mis Vehículos con historial de servicios y tickets · Agendar citas de servicio |

> ⚠️ El MVP del ERP **no incluye** la épica de Taller/Servicio, pero la app sí necesita historial de servicios y agenda de citas. Falta definir qué parte de Servicio entra al MVP para que esas pantallas tengan datos reales (observación del documento, sección "Observación sobre el MVP").

## 2. Stack tecnológico de este backend

| Capa | Tecnología |
|---|---|
| Lenguaje / runtime | Java 25 |
| Framework | Spring Boot 3.5.16 |
| Build / empaquetado | Maven · `jar` |
| Modularidad y eventos internos | Spring Modulith 1.4.13 (monolito modular, eventos de dominio) |
| Acceso a datos | Spring Data JPA (Hibernate) |
| Migraciones de BD | Flyway |
| Base de datos | PostgreSQL |
| Caché y colas | Redis (Spring Data Redis) |
| Seguridad | Spring Security + JWT (`io.jsonwebtoken`/jjwt), roles vía `ROLE_*`; el principal de cada petición es `UsuarioAutenticado(id, usuario, roles)` |
| Contrato de API | springdoc-openapi (Swagger UI en `/swagger-ui.html`) — de aquí se generan los clientes TypeScript (React) y Dart (Flutter) con openapi-generator |
| Notificaciones push | Firebase Cloud Messaging (consumido desde el módulo que corresponda, pendiente de definir) |
| Nube objetivo | Microsoft Azure (Container Apps, Database for PostgreSQL, Managed Redis, Blob Storage, Key Vault — ver sección 5 del PDF; servicios aún no confirmados) |
| Repositorio y CI/CD | GitHub + GitHub Actions: build/pruebas, lint (Spotless + Checkstyle), CodeQL + Dependency Review y publicación a GitHub Packages (ver `docs/workflows-ci-cd.md`) |
| Pruebas | JUnit 5, Mockito, Testcontainers, Spring Modulith tests |

Todas las versiones de dependencias del `pom.xml` se verificaron contra Maven Central al generar este scaffold (2026-09-27); antes de una entrega revisa si hay parches más nuevos de Spring Boot 3.5.x.

**Nota sobre versiones respecto al PDF**: el documento fija "Java 21 + Spring Boot 3" como decisión de equipo; este repositorio usa **Java 25** por instrucción explícita del equipo, sobre la misma línea de **Spring Boot 3** (3.5.16, la última compatible con Java 25 al momento de crear este scaffold).

## 3. Arquitectura: monolito modular

Un solo backend con una única API REST atiende a las tres apps cliente (ERP web, CRM web, App CareCar). Se eligió sobre microservicios porque hay procesos acoplados transaccionalmente (p. ej. un pago total habilita la facturación, y la factura timbrada mueve la etapa del prospecto en el CRM); con microservicios eso exigiría transacciones distribuidas.

```
ERP web (React)   CRM web (React)   App CareCar (Flutter)
        \               |                 /
         \              |                /
          API REST · Spring Boot 3 + Spring Modulith
          (monolito modular, eventos de dominio internos)
        /        |         |         |        \
  PostgreSQL   Redis   Archivos   PAC CFDI   Firebase FCM
```

La comunicación **entre módulos** es por eventos de dominio de Spring Modulith (no llamadas directas a servicios de otro módulo). Ejemplos tomados del backlog:

- Pago total de una unidad → la venta pasa a "Listo para facturar".
- Factura timbrada → se notifica a Ventas y el prospecto pasa a "Facturado/Listo para entrega".
- Venta de refacciones o servicio de taller → se descuentan existencias en Inventario.
- Se genera un ticket de servicio → queda visible en el historial de la app CareCar.
- Cliente agenda cita en la app → se registra en el ERP de Servicio.
- Cobro en Caja → se vincula a una orden de servicio, ticket de refacciones o venta de auto.

### División en módulos (propuesta inicial)

El PDF marca la división de módulos como **pendiente de definir** (sección 9); como referencia da las áreas funcionales del backlog. Este scaffold ya usa esas áreas como límites de módulo de Spring Modulith (`ApplicationModules.of(...).verify()` pasa sin ciclos), pero **es un punto de partida, no una decisión cerrada**:

```
src/main/java/dev/latticesystems/erp_agente360/backend_agencia360/
├── shared/          # kernel compartido (abierto a todos los módulos)
│   ├── config/      # OpenAPI, CORS, JPA auditing
│   ├── security/    # Spring Security, JWT, roles
│   ├── exception/   # manejo global de errores
│   └── domain/      # clases base (auditoría)
├── acceso/          # Usuarios, roles y login del personal — ERP-0.1 · CRM-0.1
├── ventascrm/       # Ventas y CRM de prospectos       — ERP HU 1.1 · CareCar HU 1.1, 1.2
├── facturacion/     # Facturación y Administración      — ERP HU 1.2
├── inventario/      # Vehículos, refacciones y recepciones — ERP HU 2.0 · 2.1
├── servicio/        # Taller y Servicio (órdenes, tickets) — ERP HU 3.1
├── refacciones/     # Ventas directas de refacciones     — ERP HU 3.2
├── caja/            # Caja (pagos, cobros, cancelaciones) — ERP HU 4.1
├── contabilidad/    # Balance de ingresos y egresos       — ERP HU 4.2
└── carecarapp/      # API para la app móvil de clientes  — CareCar HU 2.1, 2.2, 3.1, 3.2, 3.3, 4.1
```

Cada módulo de negocio sigue la misma convención interna (capas, no sub-módulos de Modulith):

```
<modulo>/
├── package-info.java   # límite del módulo (@ApplicationModule) + qué épicas/HU cubre
├── domain/              # entidades, value objects, eventos de dominio
├── application/         # casos de uso / servicios de aplicación
├── web/                 # controladores REST y DTOs
└── infrastructure/      # repositorios Spring Data, adaptadores externos (p. ej. cliente del PAC)
```

Ya tienen código `acceso`, `ventascrm` e `inventario`; los demás módulos siguen vacíos (solo `.gitkeep`) hasta que lleguen sus historias. El modelo de datos v1 está en `docs/TEC-1_modelo-datos_v1.md`. `ModularityTests` (en `src/test/java/.../ModularityTests.java`) corre `ApplicationModules.verify()`: cualquier ciclo entre módulos o acceso a un paquete interno de otro módulo rompe el build.

### Endpoints implementados

Todos van bajo `/api/v1` y, salvo el login, piden el token en `Authorization: Bearer <token>`. El detalle de cada uno (cuerpo, respuestas y errores) está en Swagger UI.

| Módulo | Endpoints | Quién |
|---|---|---|
| `acceso` | `POST /auth/login` | Todos (`aplicacion`: `ERP` o `CRM`) |
| `acceso` | `GET /roles`, `GET /usuarios`, `POST /usuarios`, `PUT /usuarios/{id}/rol` | Administrador (ERP) y Gerente de marketing (CRM) |
| `ventascrm` | `POST /prospectos`, `GET /prospectos?etapa=`, `PATCH /prospectos/{id}/etapa` | Ventas (sus prospectos); el Gerente de marketing consulta todos |
| `ventascrm` | `PUT /prospectos/{id}/vendedor` | Gerente de marketing |
| `inventario` | `POST /vehiculos`, `PUT /vehiculos/{id}` | Inventario |
| `inventario` | `GET /vehiculos?estatus=`, `GET /vehiculos/{id}` | Inventario y Administrador (todas); Ventas (solo Disponibles) |
| `inventario` | `POST /refacciones`, `PUT /refacciones/{id}`, `POST /refacciones/{id}/recepciones` | Inventario |
| `inventario` | `GET /refacciones?bajoMinimo=`, `GET /refacciones/{id}`, `GET /refacciones/{id}/recepciones` | Inventario, Refacciones y Administrador |

Reglas del inventario (decisiones del PO del 13 de octubre):
- Las unidades y refacciones se editan, pero no se eliminan.
- Una refacción entra con existencia 0, y la existencia solo cambia con movimientos.
- La recepción guarda fecha de llegada y de registro, proveedor, notas y responsable.
- La recepción y la edición de una refacción bloquean su fila (`SELECT ... FOR UPDATE`) para que no se pisen.

## 4. Lineamientos técnicos ya acordados (impactan el diseño del código)

- **Facturación por PAC externo**: el timbrado CFDI se hace consumiendo la API de un proveedor autorizado; no se timbra internamente. La emisión se bloquea si el saldo pendiente es mayor a cero.
- **Dinero: no se borra, se revierte**: una cancelación de pago se registra como movimiento inverso, nunca como borrado, para conservar trazabilidad. Requiere autorización.
- **Inventario por movimientos**: cada entrada/salida es un registro; se usa bloqueo de fila para no vender la misma pieza dos veces.
- **Citas sin empalmes**: restricción a nivel de base de datos para que dos clientes no reserven el mismo horario.
- **Acceso por roles**: cada usuario del personal tiene un solo rol, y el rol define si entra al ERP, al CRM o a ambos (tabla `rol`, migración V2). Roles: Administrador, Gerente de marketing, Ventas, Facturación, Inventario, Servicio, Refacciones, Caja y Contabilidad. El enum `shared/security/Role.java` debe coincidir con esa tabla.
- **Una sola API con OpenAPI**: de ella se generan los clientes React y Flutter — evitar romper el contrato sin versionarlo.
- **App CareCar sin registro público**: el acceso es con usuario/contraseña emitidos por la agencia tras validar la compra.

## 5. Entorno local (PostgreSQL y Redis)

Para correr la API en tu máquina necesitas una base de datos PostgreSQL y un Redis. No hace falta instalarlos: el `docker-compose.yml` de la raíz los levanta en contenedores con la configuración que la API espera por defecto.

> Las **pruebas** no usan este entorno: Testcontainers levanta su propio PostgreSQL temporal (ver sección 6). Puedes correr `./mvnw verify` sin haber hecho `docker compose up`, solo necesitas Docker activo.

### 5.1 Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 25 | Compilar y correr la API (Maven viene incluido con `./mvnw`, no hay que instalarlo) |
| Docker con Docker Compose | Docker Engine o Docker Desktop reciente (Compose v2: comando `docker compose`, sin guion) | Levantar PostgreSQL y Redis, y correr Testcontainers en las pruebas |

Con **Podman** también funciona (`podman compose up -d`), siempre que tenga activo el socket compatible con Docker para Testcontainers.

### 5.2 Servicios del `docker-compose.yml`

| Servicio | Imagen | Puerto local | Datos de conexión | ¿Los datos se conservan? |
|---|---|---|---|---|
| `postgres` | `postgres:16-alpine` | `5432` | BD `agencia360`, usuario `agencia360`, contraseña `agencia360` | Sí, en el volumen `postgres-data` (sobreviven a `docker compose down`) |
| `redis` | `redis:7-alpine` | `6379` | Sin contraseña | No hay que confiar en ellos: es caché y se puede vaciar en cualquier momento |

Estas credenciales son **solo para desarrollo local** y coinciden con los valores por defecto de `src/main/resources/application.properties`, así que no hay que configurar nada para que la API se conecte.

### 5.3 Paso a paso

```bash
# 1. Levantar PostgreSQL y Redis en segundo plano
docker compose up -d

# 2. Revisar que los dos contenedores estén "Up"
docker compose ps

# 3. Levantar la API con el perfil dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Al arrancar, **Flyway crea las tablas** aplicando las migraciones de `src/main/resources/db/migration/`. En el log debe aparecer:

```text
Successfully applied 4 migrations to schema "public", now at version v4
Started BackendAgencia360Application in ... seconds
```

Hibernate está en modo `validate`: **nunca crea ni modifica tablas por su cuenta**. Cualquier cambio al esquema va en una nueva migración `V<n>__descripcion.sql`.

Para comprobar que la API se conectó a PostgreSQL y a Redis:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

| URL | Qué es |
|---|---|
| `http://localhost:8080/actuator/health` | Estado de la API, la base de datos y Redis (`UP` / `DOWN`) |
| `http://localhost:8080/swagger-ui.html` | Swagger UI para probar los endpoints |
| `http://localhost:8080/v3/api-docs` | Contrato OpenAPI en JSON |

**Perfil `dev`**: muestra el SQL que ejecuta Hibernate (`spring.jpa.show-sql=true`) y sube el nivel de log del proyecto a `DEBUG`. Ver `src/main/resources/application-dev.properties`.

### 5.4 Conectarse a la base de datos y a Redis

Desde la terminal, sin instalar clientes (se usan los que vienen dentro de los contenedores):

```bash
# Consola de PostgreSQL
docker compose exec postgres psql -U agencia360 -d agencia360
#   \dt   lista las tablas      \q   sale

# Consola de Redis
docker compose exec redis redis-cli
#   PING  responde PONG       KEYS *   lista las llaves (solo en local)
```

Desde un cliente gráfico (IntelliJ Database, DBeaver, pgAdmin): host `localhost`, puerto `5432`, base `agencia360`, usuario `agencia360`, contraseña `agencia360`. URL JDBC: `jdbc:postgresql://localhost:5432/agencia360`.

### 5.5 Variables de entorno

La API lee su configuración de variables de entorno. Si no defines ninguna, usa los valores por defecto, que apuntan al `docker-compose.yml`:

| Variable | Valor por defecto | Para qué |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/agencia360` | URL JDBC de PostgreSQL |
| `DB_USERNAME` | `agencia360` | Usuario de la base de datos |
| `DB_PASSWORD` | `agencia360` | Contraseña de la base de datos |
| `REDIS_HOST` | `localhost` | Host de Redis |
| `REDIS_PORT` | `6379` | Puerto de Redis |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origen del frontend que puede llamar a la API |
| `JWT_SECRET` | Valor de solo-desarrollo | Llave para firmar los JWT |
| `JWT_EXPIRATION_MINUTES` | `60` | Duración del token |
| `ADMIN_INICIAL_USUARIO` / `ADMIN_INICIAL_CONTRASENA` | Vacío | Si tienen valor, al arrancar se crea el primer Administrador (ERP) cuando todavía no existe |
| `GERENTE_INICIAL_USUARIO` / `GERENTE_INICIAL_CONTRASENA` | Vacío | Igual, para el primer Gerente de marketing (administrador del CRM) |

**`JWT_SECRET` trae un valor de solo-desarrollo por defecto: hay que sobrescribirlo en cualquier ambiente real** (en Azure, vía Key Vault según la sección 5 del PDF).

Ejemplo, para apuntar a otro PostgreSQL sin tocar código:

```bash
DB_URL=jdbc:postgresql://localhost:5433/agencia360 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 5.6 Usuarios de ejemplo

La base empieza sin usuarios. Para probar el ERP y el CRM en local, crea uno por rol:

```bash
# 1. Levanta la API con el Administrador (ERP) y el Gerente de marketing (CRM) iniciales
ADMIN_INICIAL_USUARIO=admin ADMIN_INICIAL_CONTRASENA=Admin12345 \
GERENTE_INICIAL_USUARIO=gerente GERENTE_INICIAL_CONTRASENA=Gerente12345 \
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 2. En otra terminal, crea los demás usuarios (se puede correr varias veces)
./scripts/usuarios-de-ejemplo.sh
```

> **Solo para desarrollo local.** Estas contraseñas son públicas: nunca las uses en un ambiente real.

| Usuario | Contraseña | Rol | Entra a | Para probar |
|---|---|---|---|---|
| `admin` | `Admin12345` | Administrador | ERP | Usuarios del ERP; consulta de vehículos y refacciones |
| `gerente` | `Gerente12345` | Gerente de marketing | CRM | Usuarios del CRM; todos los prospectos y su reasignación |
| `inventario1` | `Inventario123` | Inventario | ERP | Alta, edición y consulta de vehículos y refacciones; recepciones |
| `refacciones1` | `Refacciones123` | Refacciones | ERP | Consulta del stock y aviso de refacciones por resurtir |
| `facturacion1` | `Facturacion123` | Facturación | ERP | Menú vacío por ahora (sus historias llegan después) |
| `servicio1` | `Servicio123` | Servicio | ERP | Menú vacío por ahora |
| `caja1` | `Caja12345` | Caja | ERP | Menú vacío por ahora |
| `contabilidad1` | `Contabilidad123` | Contabilidad | ERP y CRM | Menú vacío por ahora |
| `vendedor1`, `vendedor2` | `Vendedor123` | Ventas | CRM | Registro y seguimiento de sus prospectos; reasignación entre los dos |

Sin el script también se pueden crear desde la pantalla **Usuarios** del ERP (sesión de `admin`) o del CRM (sesión de `gerente`). Para borrar todo y empezar de cero: `docker compose down -v`.

### 5.7 Comandos útiles

| Comando | Qué hace |
|---|---|
| `docker compose ps` | Muestra si los contenedores están corriendo |
| `docker compose logs -f postgres` | Muestra el log de PostgreSQL en vivo (igual con `redis`) |
| `docker compose stop` | Detiene los contenedores sin borrarlos |
| `docker compose down` | Detiene y borra los contenedores; **los datos de PostgreSQL se conservan** |
| `docker compose down -v` | Borra también el volumen: **base de datos vacía** la próxima vez que levantes (Flyway vuelve a crear las tablas) |

### 5.8 Problemas comunes

| Síntoma | Causa | Solución |
|---|---|---|
| La API no arranca y el log dice `Connection to localhost:5432 refused` | PostgreSQL no está corriendo | `docker compose up -d` y vuelve a levantar la API |
| `docker compose up` falla con `port is already allocated` o `address already in use` | Ya tienes otro PostgreSQL o Redis usando el puerto `5432` o `6379` | Detén el servicio local (por ejemplo `sudo systemctl stop postgresql`) o cambia el puerto de la izquierda en `docker-compose.yml` (`"5433:5432"`) y usa `DB_URL` con ese puerto |
| La API arranca pero `/actuator/health` responde `{"status":"DOWN"}` (HTTP 503) | Redis no está corriendo: la API no lo necesita para arrancar, pero el health check lo revisa | `docker compose up -d redis` |
| La API no arranca por un error de Flyway (`Validate failed`, `checksum mismatch`) | Se editó una migración que ya se había aplicado en tu base local | No edites migraciones ya integradas a `main`: crea una nueva. Si solo fue en tu máquina, reinicia la base con `docker compose down -v && docker compose up -d` |
| La API no arranca con `Schema-validation: missing table` | Una entidad JPA no tiene su migración | Agrega la migración `V<n>__...sql` que crea esa tabla |
| El login funciona, pero el ERP dice "No se pudo conectar con el servidor" | El navegador bloquea por CORS las peticiones con token: el frontend corre en un origen que no está en `CORS_ALLOWED_ORIGINS` (por ejemplo `5174`, si el CRM ya ocupa el `5173`) | Agrega el origen: `CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174` |
| `./scripts/usuarios-de-ejemplo.sh` dice "No se pudo iniciar sesión" | La API se levantó sin las variables `ADMIN_INICIAL_*` y `GERENTE_INICIAL_*`, o con otras contraseñas | Levántala como en la sección 5.6, o pásale las tuyas: `ADMIN_CONTRASENA=... GERENTE_CONTRASENA=... ./scripts/usuarios-de-ejemplo.sh` |

## 6. Estrategia de pruebas (backend)

| Capa | Herramientas | Qué prueba |
|---|---|---|
| Unitarias | JUnit 5 + Mockito | Servicios y reglas de negocio aisladas |
| Integración | Testcontainers | Acceso a datos y transacciones contra PostgreSQL/Redis reales en Docker (ver `shared/AbstractIntegrationTest` en tests) |
| Módulos | Spring Modulith tests | Cada módulo aislado y la publicación de eventos (ver `ModularityTests`) |

El mayor esfuerzo de pruebas debe ir en pagos, facturación, inventario y contabilidad: ahí un error significa dinero mal registrado.

Lo que hay hoy:
- Cada módulo con código (`acceso`, `ventascrm` e `inventario`) tiene pruebas de dominio, de servicio con Mockito y de integración por HTTP contra PostgreSQL real (`*IntegrationTest`). Las de integración cubren seguridad por rol y restricciones de la base.
- `RecepcionesConcurrentesIntegrationTest` lanza recepciones y ediciones simultáneas de una refacción para comprobar el bloqueo de fila. Sin el bloqueo, la existencia termina mal (5 de 20 recepciones, o 0 si hay ediciones).
- `CorsIntegrationTest` comprueba que el preflight de CORS de un origen permitido se responde sin pedir sesión.

Para ver la cobertura sin tocar el `pom.xml`:

```bash
./mvnw org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent verify org.jacoco:jacoco-maven-plugin:0.8.13:report
# Reporte en target/site/jacoco/index.html
```

## 7. Documentación y diagramas

- `docs/TEC-1_modelo-datos_v1.md` y `docs/diagramas/agencia360-modelo-datos-v1.drawio`: modelo de datos v1.
- `docs/api/`: contrato OpenAPI de la app CareCar (YAML y PDF).
- `scripts/usuarios-de-ejemplo.sh`: crea los usuarios de ejemplo de la sección 5.6.
- `docs/convenciones-commits.md`: formato de commits (Conventional Commits), scopes por módulo y nombres de rama.
- `docs/workflows-ci-cd.md`: qué hace cada GitHub Action (CI, lint, seguridad, publicación) y cómo lintear el código en local antes de subirlo.
- La documentación de referencia del equipo vive en **Azure DevOps Wiki** (fuera de este repo).
- La documentación de la API se genera sola con springdoc-openapi (Swagger UI).

## 8. Pendiente por definir (heredado del documento de arquitectura)

Estas decisiones **no** están tomadas todavía; no asumas nada más allá de lo que dice esta lista:

- División técnica final de los módulos del backend y sus responsabilidades.
- Modelo de datos: la v1 cubre el Sprint 1 (`docs/TEC-1_modelo-datos_v1.md`); faltan las preguntas abiertas que ahí se listan.
- Servicios de Azure a confirmar (región, ambientes).
- Herramienta de infraestructura como código (Bicep o Terraform).
- Proveedor del PAC para timbrado CFDI.
- Estrategia de ramas más allá de lo básico (ver `docs/convenciones-commits.md`): política de protección de `main`, revisores mínimos por PR.
- Convenciones de la API (más allá de "una sola API documentada con OpenAPI").
- Qué parte del módulo de Servicio entra al MVP.
- Si el backlog se gestiona en Azure Boards o GitHub.

## 9. Fuente

`Agencia360_Stack_y_Arquitectura_v2.pdf` — Stack tecnológico y arquitectura, Agencia360 (ERP) y CareCar (CRM y app postventa), versión 2.
