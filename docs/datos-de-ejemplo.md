# Datos de ejemplo (TEC-3)

Datos para probar a mano el ERP y el CRM en el ambiente de desarrollo. Cubren todo lo que existe al cierre del Sprint 1: usuarios por rol, vehículos, refacciones con sus recepciones y prospectos en todas las etapas.

Los carga `scripts/cargar-datos-de-ejemplo.sh` **a través de la API**, igual que lo haría una persona desde las pantallas. Así respetan las mismas reglas de negocio, quedan sus movimientos e historial, y el script no depende del esquema de la base. Cómo cargarlos y limpiarlos está en la sección 5.6 del README.

> **Solo para desarrollo local.** Las contraseñas de este documento son públicas.

Si cambias un dato, cámbialo en el script y en este documento.

## Usuarios

Uno por rol. El Administrador y el Gerente de marketing se crean al arrancar la API (variables `ADMIN_INICIAL_*` y `GERENTE_INICIAL_*`); el script crea los demás.

| Usuario | Contraseña | Nombre | Rol | Entra a |
|---|---|---|---|---|
| `admin` | `Admin12345` | Administrador inicial | Administrador | ERP |
| `gerente` | `Gerente12345` | Gerente de marketing inicial | Gerente de marketing | CRM |
| `inventario1` | `Inventario123` | Andrea Ruiz | Inventario | ERP |
| `refacciones1` | `Refacciones123` | Daniela León | Refacciones | ERP |
| `facturacion1` | `Facturacion123` | Sofía Méndez | Facturación | ERP |
| `servicio1` | `Servicio123` | Jorge Castillo | Servicio | ERP |
| `caja1` | `Caja12345` | Carla Cajera | Caja | ERP |
| `contabilidad1` | `Contabilidad123` | Marco Ortiz | Contabilidad | ERP |
| `vendedor1` | `Vendedor123` | Sergio Luna | Ventas | CRM |
| `vendedor2` | `Vendedor123` | Elena Ríos | Ventas | CRM |

## Vehículos

Los da de alta `inventario1`. Cada alta deja su movimiento de entrada.

| VIN | Modelo | Versión | Año | Color | Precio (MXN) | Estatus |
|---|---|---|---|---|---|---|
| `3N1AB8CV5PY123456` | Sentra | Advance | 2026 | Blanco | 489,900.00 | Disponible |
| `3N1CN8AE4RL123457` | Versa | Sense | 2026 | Plata | 329,900.00 | Disponible |
| `3N1CP5CV6RL123458` | Kicks | Exclusive | 2026 | Azul | 529,900.00 | Disponible |
| `3N6AD33A2RK123459` | Frontier | LE | 2025 | Gris | 689,900.00 | No disponible |
| `3N1CK3CD0RL123460` | March | Advance | 2025 | Rojo | 279,900.00 | Disponible |
| `5N1DR3MN4RC123461` | Pathfinder | Exclusive | 2026 | Negro | 1,049,900.00 | Disponible |
| `JN1BJ1CW6RW123462` | X-Trail | Advance | 2026 | Blanco | 699,900.00 | Disponible |
| `1N6ED1EJ8RN123463` | Frontier | PRO-4X | 2026 | Azul | 899,900.00 | No disponible |

Qué se puede probar:
- En Vehículos, los indicadores (8 registradas, 6 disponibles, 2 no disponibles), la búsqueda por VIN, modelo o versión, y el filtro por estatus.
- La edición de una unidad, por ejemplo pasar la Frontier LE de No disponible a Disponible.
- Que el Administrador ve todas las unidades, pero no puede dar de alta ni editar.

**No incluye unidades Vendidas ni Entregadas:** una unidad pasa a Vendido solo cuando Facturación emite su factura (ERP-1.3). Como los datos fiscales del cliente llegan con la V17 (Coper), el script todavía no emite facturas; cuando exista la captura, se agregan aquí.

## Refacciones

Las da de alta `inventario1`; todas entran con existencia 0. La existencia sale de sus recepciones.

| Código | Descripción | Costo | Precio | Mínimo | Recepciones | Existencia | Estado |
|---|---|---|---|---|---|---|---|
| `REF-001` | Filtro de aceite | 85.00 | 149.00 | 5 | 12 de Refacciones del Bajío | 12 | Disponible |
| `REF-002` | Balatas delanteras | 620.00 | 890.00 | 5 | 3 de Refacciones del Bajío: **llegaron hace 2 días** y se registraron hoy, con la nota "Una caja golpeada" | 3 | Bajo mínimo |
| `REF-003` | Bujía de iridio | 170.00 | 259.00 | 8 | 10 de Distribuidora León y 8 de Refacciones del Bajío | 18 | Disponible |
| `REF-004` | Filtro de aire | 120.00 | 199.00 | 4 | Ninguna | 0 | Sin existencia |
| `REF-005` | Limpiaparabrisas | 150.00 | 249.00 | 4 | 9 de Distribuidora León | 9 | Disponible |
| `REF-006` | Aceite sintético 5W-30 (litro) | 160.00 | 239.00 | 20 | 20 de Lubricantes del Centro | 20 | Bajo mínimo (está justo en el mínimo) |
| `REF-007` | Batería 12 V | 1,850.00 | 2,590.00 | 2 | 6 de Distribuidora León | 6 | Disponible |
| `REF-008` | Amortiguador delantero | 980.00 | 1,450.00 | 4 | 2 de Refacciones del Bajío | 2 | Bajo mínimo |

Qué se puede probar:
- Con `refacciones1` o `admin`, el aviso "4 refacciones llegaron a su existencia mínima": incluye las que están justo en el mínimo y las que no tienen existencia.
- Los tres estados de existencia, el filtro "Bajo mínimo" y el botón "Ver por resurtir" del aviso.
- El historial de recepciones con sus dos fechas:
  - `REF-002` muestra la marca "Registrada después".
  - `REF-003` tiene dos entregas de proveedores distintos.
- Con `inventario1`, registrar una recepción de `REF-004` y ver cómo pasa de "Sin existencia" a su nuevo estado.

## Prospectos

Los registran los vendedores desde el CRM; cada cambio de etapa queda en el historial. El script avanza cada prospecto etapa por etapa, con el usuario al que le corresponde cada una (decisión del PO del 12 de octubre): el vendedor hasta Pago; Caja registra y envía un pago de ejemplo y Contabilidad lo valida, con lo que pasa a Facturación (ERP-1.1); Facturación lo pasa a Entrega y otra vez el vendedor lo finaliza.

| Prospecto | Teléfono | Correo | Vendedor | Etapa | Notas |
|---|---|---|---|---|---|
| Ana López | 477 555 0101 | ana.lopez@example.com | Sergio Luna | Primer contacto | |
| Diego Ramírez | 477 555 0102 | | Sergio Luna | Visita | |
| Fernanda Soto | | fernanda.soto@example.com | Sergio Luna | Cotización | Solo correo |
| Luis Herrera | 477 555 0104 | luis.h@example.com | Sergio Luna | Pago | |
| Valeria Díaz | 477 555 0105 | valeria.d@example.com | Sergio Luna | Entrega | Contabilidad validó el pago y Facturación emitió la factura |
| Jorge Medina | 477 555 0106 | | Sergio Luna | Cerrado | Desde Cotización; motivo: "Se fue por otra marca" |
| Ricardo Fuentes | 477 555 0107 | | Elena Ríos | Visita | Lo registró Sergio Luna y el Gerente lo **reasignó** a Elena Ríos |
| Mariana Torres | 477 555 0108 | mariana.t@example.com | Elena Ríos | Primer contacto | |
| Carlos Núñez | 477 555 0109 | | Elena Ríos | Facturación | Tiene una **solicitud de cierre pendiente** para el Administrador |
| Paola Vega | | paola.vega@example.com | Elena Ríos | Visita | Solo correo |
| Ana Lucía López | 477 555 0101 | | Elena Ríos | Primer contacto | **Mismo teléfono** que Ana López: al registrarla, el CRM avisa del contacto repetido |
| Óscar Ramos | 477 555 0111 | | Elena Ríos | Cerrado | Desde Primer contacto; motivo: "Rechazo de financiamiento" |
| Roberto Salas | 477 555 0112 | roberto.s@example.com | Elena Ríos | Finalizado | Recorrió todo el proceso hasta la entrega |

Qué se puede probar:
- Con `vendedor1` y `vendedor2`, que cada uno ve **solo sus prospectos** (6 y 7).
- Con `gerente`, que se ven los 13 y que se pueden reasignar.
- El filtro por etapa: hay al menos un prospecto en cada una de las 8.
- Con `admin`, la solicitud de cierre pendiente de Carlos Núñez (`GET /api/v1/solicitudes-cierre`).
- Los cerrados guardan su motivo y la última etapa a la que llegaron.

## Clienta de la app CareCar y su expediente

La registra el Gerente de marketing desde el CRM con sus dos vehículos y le abre la cuenta de la app; la contraseña temporal llega a Mailpit (`http://localhost:8025`). Servicio corrige placas y kilometraje del March y registra sus servicios; el Gerente sube el manual del March 2026.

| Vehículo | VIN | Placas y kilometraje | Servicios | Manual |
|---|---|---|---|---|
| Nissan March 2026 Advance | `3N1CK3CD7RL900001` | GTO-123-A, 10,250 km | Servicio de 5,000 km (hace 150 días) y de 10,000 km (hace 10 días) | Sí, uno de ejemplo de una página (o el real con `MANUAL_MARCH_PDF`) |
| Nissan Sentra 2025 Sense | `3N1AB8CV9RY900002` | Sin capturar | Cambio de balatas (hace 30 días) | No |

Qué se puede probar:
- Con `servicio1`, buscar `900001` en Expediente, abrir los detalles de cada servicio y corregir placas y kilometraje.
- Con `admin`, que el expediente se puede consultar pero no corregir.
- En la app, con `laura.mendez@example.com`: el primer ingreso con cambio de contraseña, los 2 vehículos y el manual del March, que se abre con su URL temporal. El Sentra regresa una lista de manuales vacía.

## Lo que no incluye todavía

Ventas, pagos, facturas, clientes, órdenes de servicio y citas: llegan en S2 a S4. El criterio de TEC-3 los pide, pero se agregan al script cuando existan sus endpoints.
