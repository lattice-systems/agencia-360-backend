# Plan del backend para CareCar v2

El plan maestro (propósito, objetivos, hitos, módulos, reparto y tablero) vive en **`carecar-mobile/plan/`**: la app es la prioridad y el backend la sirve. Este directorio es el **espejo técnico** del trabajo de backend.

| Documento | Para qué |
| --- | --- |
| [`historias-backend.md`](historias-backend.md) | Las 110 historias **B** agrupadas por módulo de Modulith, con su diseño técnico |
| [Plan maestro](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/README.md) | Propósito, hitos H0–H7, catálogo de módulos |
| [Tablero](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/tablero.md) | Estado de todas las historias (única fuente) |
| [Reparto](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/equipo-y-reparto.md) | Quién es dueño de qué |
| [Ficha técnica](https://github.com/lattice-systems/carecar-mobile/blob/main/docs/ficha-tecnica/README.md) | Tablas y campos obligatorios |

## Objetivos del backend (sin fechas)

| ID | Objetivo | Se cumple cuando |
| --- | --- | --- |
| OB1 | Tablas idénticas a la ficha técnica | `V2-T05.03`–`V2-T05.07` integradas y la matriz de cumplimiento en ✅ para tablas |
| OB2 | Contrato v2 sin romper v1 | `agencia360-api-app-v2.yaml` publicado; la app v1 instalada sigue funcionando |
| OB3 | Cada módulo nuevo de la app con su módulo de Modulith | `ModularityTests` verde con los módulos nuevos y eventos documentados |
| OB4 | Tiempo real para la app | SSE (órdenes) y WebSocket (chat) detrás de JWT (`V2-T05.08`) |
| OB5 | Datos de ejemplo para todo | `scripts/cargar-datos-de-ejemplo.sh` cubre cada módulo nuevo (`V2-T05.10`) |
| OB6 | Seguridad comprobada | ARCO, auditoría de acciones sensibles, Trivy y CodeQL verdes, sin hallazgos abiertos |

## Módulos de Modulith

| Módulo | Estado hoy | Uso en la v2 |
| --- | --- | --- |
| `carecarapp` | Con código (acceso, vehículos, manuales, servicios, citas, catálogo) | Puerta de entrada `/api/v1/app`; orquesta lecturas para la app |
| `clientes` | Con código | Cliente y vehículos del cliente (ficha) |
| `servicio` | Vacío | Orden de servicio, etapas, inspección, adicionales, mantenimiento, garantía, traslados |
| `refacciones`, `inventario` | Inventario con código | Tienda y apartados (M14) |
| `caja`, `facturacion` | Facturación con código | Pagos en app y CFDI (M08) |
| `ventascrm` | Con código | Prospectos desde la app (M16, M26, referidos M12) |
| `notificaciones` | **Nuevo** | Push FCM, bandeja y preferencias (M06) |
| `mensajeria` | **Nuevo** | Chat cliente–asesor (M07) |
| `documentos` | **Nuevo** | Guantera digital (M09) |
| `lealtad` | **Nuevo** | Puntos y canjes (M12) |
| `promociones` | **Nuevo** | Ofertas segmentadas (M13) |
| `encuestas` | **Nuevo** | NPS (M15) |
| `asistencia` | **Nuevo** | SOS (M11) |
| `asistente` | **Nuevo** | IA sobre manuales (M24) |

Los módulos nuevos se crean solo cuando su primera historia se implementa (no se crean carpetas vacías por adelantado).

## Cómo se mantiene

- Cada PR de una historia **B** actualiza su sección en `historias-backend.md` con el diseño final (migraciones, eventos, endpoints) y el contrato OpenAPI.
- El estado se mueve en el tablero de `carecar-mobile`, no aquí.
- Si se agrega o cambia una historia **B**, se cambia primero en `carecar-mobile/plan/modulos/` y luego se refleja aquí.
