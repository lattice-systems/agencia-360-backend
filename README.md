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
| Seguridad | Spring Security + JWT (`io.jsonwebtoken`/jjwt), roles vía `ROLE_*` |
| Contrato de API | springdoc-openapi (Swagger UI en `/swagger-ui.html`) — de aquí se generan los clientes TypeScript (React) y Dart (Flutter) con openapi-generator |
| Notificaciones push | Firebase Cloud Messaging (consumido desde el módulo que corresponda, pendiente de definir) |
| Nube objetivo | Microsoft Azure (Container Apps, Database for PostgreSQL, Managed Redis, Blob Storage, Key Vault — ver sección 5 del PDF; servicios aún no confirmados) |
| Repositorio y CI/CD | GitHub + GitHub Actions (workflow aún no agregado en este repo) |
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
├── ventascrm/       # Ventas y CRM de prospectos       — ERP HU 1.1 · CareCar HU 1.1, 1.2
├── facturacion/     # Facturación y Administración      — ERP HU 1.2
├── inventario/      # Inventario de vehículos/refacciones — ERP HU 2.1
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

Por ahora las carpetas de capas están vacías (solo `.gitkeep`): el modelo de datos y las entidades **no están definidos todavía** (sección 9 del PDF), así que no se generó ningún dominio inventado. `ModularityTests` (en `src/test/java/.../ModularityTests.java`) ya corre `ApplicationModules.verify()` para que, en cuanto empiece a llegar código, cualquier ciclo entre módulos o acceso a un paquete interno de otro módulo rompa el build.

## 4. Lineamientos técnicos ya acordados (impactan el diseño del código)

- **Facturación por PAC externo**: el timbrado CFDI se hace consumiendo la API de un proveedor autorizado; no se timbra internamente. La emisión se bloquea si el saldo pendiente es mayor a cero.
- **Dinero: no se borra, se revierte**: una cancelación de pago se registra como movimiento inverso, nunca como borrado, para conservar trazabilidad. Requiere autorización.
- **Inventario por movimientos**: cada entrada/salida es un registro; se usa bloqueo de fila para no vender la misma pieza dos veces.
- **Citas sin empalmes**: restricción a nivel de base de datos para que dos clientes no reserven el mismo horario.
- **Acceso por roles**: para personal de la agencia y para clientes de la app (ver `shared/security/Role.java`, propuesta inicial derivada de los departamentos del documento).
- **Una sola API con OpenAPI**: de ella se generan los clientes React y Flutter — evitar romper el contrato sin versionarlo.
- **App CareCar sin registro público**: el acceso es con usuario/contraseña emitidos por la agencia tras validar la compra.

## 5. Cómo correr el proyecto en local

Requisitos: JDK 25, Docker (para Postgres/Redis y para Testcontainers en las pruebas de integración).

```bash
# Levantar Postgres y Redis locales
docker compose up -d

# Compilar
./mvnw clean compile

# Correr pruebas (unitarias + Spring Modulith + Testcontainers)
./mvnw test

# Levantar la API (perfil dev)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La API queda en `http://localhost:8080`; Swagger UI en `http://localhost:8080/swagger-ui.html`.

Variables de entorno relevantes (ver `src/main/resources/application.properties`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`. **`JWT_SECRET` trae un valor de solo-desarrollo por defecto: hay que sobrescribirlo en cualquier ambiente real** (en Azure, vía Key Vault según la sección 5 del PDF).

## 6. Estrategia de pruebas (backend)

| Capa | Herramientas | Qué prueba |
|---|---|---|
| Unitarias | JUnit 5 + Mockito | Servicios y reglas de negocio aisladas |
| Integración | Testcontainers | Acceso a datos y transacciones contra PostgreSQL/Redis reales en Docker (ver `shared/AbstractIntegrationTest` en tests) |
| Módulos | Spring Modulith tests | Cada módulo aislado y la publicación de eventos (ver `ModularityTests`) |

El mayor esfuerzo de pruebas debe ir en pagos, facturación, inventario y contabilidad: ahí un error significa dinero mal registrado.

## 7. Documentación y diagramas

- `docs/diagramas/`: diagramas locales en draw.io del proyecto (carpeta creada, vacía).
- `docs/convenciones-commits.md`: formato de commits (Conventional Commits), scopes por módulo y nombres de rama.
- La documentación de referencia del equipo vive en **Azure DevOps Wiki** (fuera de este repo).
- La documentación de la API se genera sola con springdoc-openapi (Swagger UI).

## 8. Pendiente por definir (heredado del documento de arquitectura)

Estas decisiones **no** están tomadas todavía; no asumas nada más allá de lo que dice esta lista:

- División técnica final de los módulos del backend y sus responsabilidades.
- Modelo de datos: entidades, relaciones y convenciones de la base de datos.
- Servicios de Azure a confirmar (región, ambientes).
- Herramienta de infraestructura como código (Bicep o Terraform).
- Proveedor del PAC para timbrado CFDI.
- Estrategia de ramas más allá de lo básico (ver `docs/convenciones-commits.md`): política de protección de `main`, revisores mínimos por PR.
- Convenciones de la API (más allá de "una sola API documentada con OpenAPI").
- Qué parte del módulo de Servicio entra al MVP.
- Si el backlog se gestiona en Azure Boards o GitHub.

## 9. Fuente

`Agencia360_Stack_y_Arquitectura_v2.pdf` — Stack tecnológico y arquitectura, Agencia360 (ERP) y CareCar (CRM y app postventa), versión 2.
