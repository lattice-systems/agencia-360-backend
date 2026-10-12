# TEC-1 · Modelo de datos (v1)

**Historia:** TEC-1 · Spike: modelo de datos y contrato de API
**Tarea:** 1 · Identificar las entidades a partir del backlog V2
**Responsable:** Ricardo (apoyo: Coper)
**Estado:** v1, revisado con Coper. Incluye las respuestas del PO del 9 de octubre de 2026.
**Fuentes:** Product Backlog V2 y decisiones de la tabla siguiente.

> Este borrador incluye **solo** los datos que piden los criterios de aceptación. Cada entidad indica la historia de la que sale. Lo que el backlog no define está en [Preguntas abiertas](#preguntas-abiertas) y no se debe resolver por suposición.

Diagrama: `agencia360-modelo-datos-v1.drawio` · Contrato: `agencia360-api-app-v1.yaml`

## Decisiones tomadas (9 de octubre)

| # | Decisión | Cambio en el modelo |
|---|---|---|
| 1 | Etapas del prospecto: primer contacto, visita, cotización, pago, facturación, entrega y **cerrado** (venta no concretada) | `etapa` es una lista fija. Al cerrar se guardan `motivo_cierre` y `ultima_etapa` |
| 2 | Un usuario tiene un solo rol | `usuario.rol_id` (muchos a uno) |
| 3 | Se considera cliente hasta la entrega | La venta se liga al **prospecto**; el cliente se crea en la entrega |
| 4 | El catálogo de la app es una lista aparte con los modelos actuales | Nueva entidad `modelo_catalogo` |
| 5 | Estatus del vehículo: Disponible, Apartada, Vendido, Entregado y No disponible | `vehiculo.estatus` es una lista fija |
| 6 | Factura simulada con folio interno (D2) | `factura` guarda folio interno y PDF; sin XML de timbrado |
| 7 | El pago se liga a órdenes y ventas de refacciones cuando entren esas historias | `pago` solo se liga a `venta` por ahora |
| 8 | El ticket es un documento que genera la orden de servicio | Sin entidad `ticket`; se genera desde `orden_servicio` |

Las decisiones 7 y 8 son propuesta de Ricardo; falta confirmarlas con el equipo.

---

## Entidades

### Acceso y seguridad

| Entidad | Atributos | Origen |
|---|---|---|
| **usuario** | id, usuario, contraseña_hash, nombre, activo, rol | ERP-0.1, CRM-0.1 · DoD: contraseñas con hash |
| **rol** | id, nombre | ERP-0.1: un rol por departamento, supervisor y administrador · CRM-0.1: vendedor, gerente de marketing y administrador |
| **bitacora** | id, usuario, acción, detalle, fecha y hora | DoD: bitácora de acciones sensibles · ERP-4.1: la cancelación queda en bitácora |

### CRM

| Entidad | Atributos | Origen |
|---|---|---|
| **prospecto** | id, nombre, teléfono, correo, etapa actual, vendedor, motivo de cierre, última etapa, fecha y hora de registro | CRM-1.0 (inicia en «Primer contacto») · CRM-0.1 (vendedor dueño) · CRM-1.2 y decisión 1 (etapa «Cerrado» con motivo y última etapa) |
| **historial_etapa** | id, prospecto, etapa, origen (CRM o ERP), fecha y hora | CRM-1.1: cada cambio registra fecha y hora; acepta cambios que vengan del ERP |
| **cliente** | id, prospecto de origen, nombre, teléfono, correo | D4 · decisión 3: se crea en la entrega · CRM-1.3 · ERP-3.0 |

### Inventario, ventas y caja

| Entidad | Atributos | Origen |
|---|---|---|
| **vehiculo** | id, VIN, modelo, versión, año, color, precio, estatus | ERP-2.0 · decisión 5 (Disponible, Apartada, Vendido, Entregado, No disponible) |
| **refaccion** | id, código, descripción, costo, precio, existencia, mínimo | ERP-2.0 |
| **movimiento_inventario** | id, tipo (entrada o salida), vehículo o refacción, cantidad, responsable, fecha | ERP-2.0 (recepción con fecha, cantidad y responsable) · ERP-2.1 (existencia nunca negativa) |
| **venta** | id, vehículo, prospecto, precio, estatus | ERP-1.0 · decisión 3 · ERP-1.1 («Listo para facturar») · el saldo se calcula: precio menos pagos |
| **pago** | id, venta, monto, fecha, cancelado, cancelado por, motivo de cancelación | ERP-4.1: cancelación con rol supervisor y motivo · decisión 7 |
| **factura** | id, venta, folio interno, PDF, fecha | ERP-1.3 · decisión 6 |

### Taller y agenda

| Entidad | Atributos | Origen |
|---|---|---|
| **vehiculo_cliente** | id, cliente, vehículo de inventario (si lo vendió la agencia), VIN, modelo, año, placas, kilometraje | ERP-3.0 · ADM-1: el modelo y el año se necesitan para mostrar el manual que corresponde |
| **orden_servicio** | id, vehículo del cliente, mano de obra, total, estatus, fecha de cierre | ERP-3.1: total = mano de obra + refacciones · decisión 8: genera el ticket |
| **orden_refaccion** | id, orden, refacción, cantidad, precio | ERP-3.1: asociar refacciones del inventario |
| **franja_horario** | id, día, hora de inicio, hora de fin, capacidad | ERP-3.3 |
| **cita** | id, cliente, vehículo del cliente, franja, fecha, estado | ERP-3.3 (confirmada, atendida, no asistió, cancelada) · APP-3.3 (sin doble reserva) |

### Administración de la app

| Entidad | Atributos | Origen |
|---|---|---|
| **cuenta_app** | id, cliente, usuario, contraseña_hash, debe cambiar contraseña, activa | CRM-1.3 (contraseña temporal, desactivar) · APP-2.3 (cambio obligatorio en el primer ingreso) |
| **token_recuperacion** | id, cuenta, token, expira, usado | APP-2.3: enlace de un solo uso que expira |
| **manual** | id, modelo, año, archivo PDF | ADM-1 (fuera del MVP, pero TEC-1 pide manuales en el contrato) |
| **modelo_catalogo** | id, modelo, activo | Decisión 4 · APP-4.1 (fuera del MVP) |

**Fuera de este borrador:** venta directa de refacciones (ERP-3.2), egresos para el balance (ERP-4.2) y parámetros de la calculadora (ADM-2). Están fuera del MVP o el backlog todavía no define sus datos.

---

## Relaciones

| De | A | Cardinalidad |
|---|---|---|
| rol | usuario | 1 a muchos |
| usuario (vendedor) | prospecto | 1 a muchos |
| prospecto | historial_etapa | 1 a muchos |
| prospecto | cliente | 1 a 0..1 |
| cliente | cuenta_app | 1 a 0..1 |
| cuenta_app | token_recuperacion | 1 a muchos |
| vehiculo / refaccion | movimiento_inventario | 1 a muchos |
| usuario (responsable) | movimiento_inventario | 1 a muchos |
| vehiculo | venta | 1 a muchos (solo una activa) |
| prospecto | venta | 1 a muchos |
| venta | pago | 1 a muchos |
| venta | factura | 1 a 0..1 |
| cliente | vehiculo_cliente | 1 a muchos |
| vehiculo | vehiculo_cliente | 0..1 a 0..1 |
| vehiculo_cliente | orden_servicio | 1 a muchos |
| orden_servicio | orden_refaccion | 1 a muchos |
| franja_horario | cita | 1 a muchos |
| usuario | bitacora | 1 a muchos |

---

## Preguntas abiertas

No bloquean el cierre de TEC-1; se resuelven cuando se tomen esas historias.

| # | Pregunta | Afecta | Quién decide |
|---|---|---|---|
| 1 | ¿Con cuántas horas de anticipación puede cancelar el cliente una cita? (APP-3.3) | cita, contrato | PO |
| 2 | ¿Qué datos muestra el catálogo además del modelo? (versiones, precio, imágenes) | modelo_catalogo, contrato | PO |
| 3 | ¿Puede haber clientes que no vinieron de un prospecto? (por ejemplo, alguien que solo trae su auto a servicio) | cliente | PO |
| 4 | ¿Qué estatus tiene una orden de servicio? | orden_servicio, contrato | PO |

---

## Siguientes pasos de TEC-1

- [x] 1. Identificar entidades (revisado con Coper)
- [x] 2. Diagrama en draw.io (v1)
- [x] 3. Contrato de API en OpenAPI (borrador v1)
- [ ] 4. Revisión y aceptación del contrato con Jael
- [ ] 5. Coper publica diagrama y contrato en el repositorio y la wiki
