# Plan del backend para CareCar v2

El plan maestro (propósito, objetivos, hitos, módulos, reparto y tablero) vive en **`carecar-mobile/plan/`**: la app es la prioridad y el backend la sirve. Este directorio es el **espejo técnico** del trabajo de backend.

| Documento | Para qué |
| --- | --- |
| [`historias-backend.md`](historias-backend.md) | Las 111 historias **B** agrupadas por módulo de Modulith, con su diseño técnico |
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
| `carecarapp` | Con código (acceso, vehículos, manuales, servicios, citas, catálogo, asistente, avisos en vivo) | Puerta de entrada `/api/v1/app`; orquesta lecturas para la app; el asistente (M24) vive aquí mientras no crezca |
| `clientes` | Con código | Cliente y vehículos del cliente (ficha) |
| `servicio` | Con código (`orden_servicio`, `orden_refaccion`, `orden_mano_de_obra`; publica `OrdenServicioCambiada`) | Orden de servicio, etapas, inspección, adicionales, mantenimiento, garantía, traslados (lo que V2-T05.01 llamaba `postventa`) |
| `refacciones`, `inventario` | Inventario con código | Tienda y apartados (M14) |
| `caja`, `facturacion` | Facturación con código | Pagos en app y CFDI (M08; lo que V2-T05.01 llamaba `pagosapp`) |
| `ventascrm` | Con código | Prospectos desde la app (M16, M26, referidos M12) |
| `notificaciones` | **Nuevo** | Push FCM, bandeja y preferencias (M06) |
| `mensajeria` | **Nuevo** | Chat cliente–asesor (M07) |
| `documentos` | **Nuevo** | Guantera digital (M09) |
| `lealtad` | **Nuevo** | Puntos y canjes (M12) |
| `promociones` | **Nuevo** | Ofertas segmentadas (M13) |
| `encuestas` | **Nuevo** | NPS (M15) |
| `asistencia` | **Nuevo** | SOS (M11) |
| `asistente` | Dentro de `carecarapp` | IA sobre manuales (M24); se separa si crece |
| `archivos` | **Nuevo, con código** (V2-T05.01, V2-T05.09) | Archivos de evidencias, documentos y fotos con URL temporal firmada (`AlmacenArchivos`) |

Los módulos nuevos se crean solo cuando su primera historia se implementa (no se crean carpetas vacías por adelantado). V2-T05.01 fija este catálogo y crea `archivos`, el primero que hace falta; `notificaciones` y `lealtad` nacen con V2-M06.01 y V2-M12.01. `ModularityTests` verifica el catálogo.

### Tiempo real (V2-T05.08)

- **Órdenes, por Server-Sent Events:** `GET /api/v1/app/eventos` con el token de la app. Cuando `servicio` publica `OrdenServicioCambiada`, `carecarapp` manda el evento `orden` (`ordenId`, `vehiculoId`, `estatus`) solo a las conexiones de ese cliente, después del commit. La app vuelve a consultar la orden.
- **Chat, por WebSocket STOMP:** `/ws`. El `CONNECT` lleva `Authorization: Bearer <token>` (sin token o con uno inválido se rechaza); solo se puede suscribir a `/user/queue/...` y mandar a `/app/...`. El destino de usuario es `UsuarioAutenticado.getName()` (el correo del cliente o el usuario del personal). Lo usa `mensajeria` (M07) con `SimpMessagingTemplate.convertAndSendToUser`.
- Las conexiones viven en la memoria de la réplica (ver `docs/despliegue-azure-neon.md`).

## Cómo se mantiene

- **Una historia B se toma sin diseño previo.** Quien la toma escribe el diseño (tablas, migración, entidades, eventos publicados y escuchados, endpoints con sus errores) en su **primer commit** bajo su sección de `historias-backend.md` (`docs(plan): diseñar V2-xxx`), y el endpoint en el contrato OpenAPI v2 antes de programarlo. Al cerrar, el PR deja esa sección con el diseño final.
- **La app no espera al backend.** Con el contrato escrito, la app avanza contra `carecar-mobile/mock_api`; en el tablero esas dependencias llevan la marca `(simulable)`. Las estrategias completas están en [`carecar-mobile/plan/trabajo-en-paralelo.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/trabajo-en-paralelo.md).
- El estado se mueve en el tablero de `carecar-mobile`, no aquí.
- Si se agrega o cambia una historia **B**, se cambia primero en `carecar-mobile/plan/modulos/` y luego se refleja aquí.
