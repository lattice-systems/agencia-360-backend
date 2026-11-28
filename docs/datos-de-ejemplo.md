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
| `3N1AB8CV5PY123456` | Sentra | Advance | 2026 | Blanco | 489,900.00 | Vendido (factura de Valeria Díaz) |
| `3N1CN8AE4RL123457` | Versa | Sense | 2026 | Plata | 329,900.00 | Disponible |
| `3N1CP5CV6RL123458` | Kicks | Exclusive | 2026 | Azul | 529,900.00 | Vendido (factura de Roberto Salas, ya entregada) |
| `3N6AD33A2RK123459` | Frontier | LE | 2025 | Gris | 689,900.00 | No disponible |
| `3N1CK3CD0RL123460` | March | Advance | 2025 | Rojo | 279,900.00 | Disponible |
| `5N1DR3MN4RC123461` | Pathfinder | Exclusive | 2026 | Negro | 1,049,900.00 | Disponible |
| `JN1BJ1CW6RW123462` | X-Trail | Advance | 2026 | Blanco | 699,900.00 | Disponible |
| `1N6ED1EJ8RN123463` | Frontier | PRO-4X | 2026 | Azul | 899,900.00 | No disponible |

Qué se puede probar:
- En Vehículos, los indicadores (8 registradas, 4 disponibles, 2 no disponibles y 2 vendidas), la búsqueda por VIN, modelo o versión, y el filtro por estatus.
- La edición de una unidad, por ejemplo pasar la Frontier LE de No disponible a Disponible.
- Que el Administrador ve todas las unidades, pero no puede dar de alta ni editar.

**Unidades vendidas:** una unidad pasa a Vendido solo cuando Facturación emite su factura (ERP-1.3). El script le factura la Sentra a Valeria Díaz y la Kicks a Roberto Salas; la venta de Roberto queda Entregada porque su vendedor ya lo finalizó.

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

Los registran los vendedores desde el CRM; cada cambio de etapa queda en el historial. El script avanza cada prospecto etapa por etapa, con el usuario al que le corresponde cada una (decisión del PO del 12 de octubre): el vendedor hasta Pago, donde captura el precio total y los datos fiscales del cliente (RFC de ejemplo `EJEM850101AB1`, ERP-1.3); Caja registra y envía un pago de ejemplo y Contabilidad lo valida, con lo que pasa a Facturación (ERP-1.1); Facturación emite la factura con una unidad Disponible y el prospecto pasa solo a Entrega (ERP-1.2); otra vez el vendedor lo finaliza.

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
| Carlos Núñez | 477 555 0109 | | Elena Ríos | Facturación | Tiene una **solicitud de cierre pendiente** para el Administrador, así que no se le puede facturar hasta que la rechace |
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

La registra el Gerente de marketing desde el CRM con sus dos vehículos y le abre la cuenta de la app; la contraseña temporal llega a Mailpit (`http://localhost:8025`). Servicio corrige placas y kilometraje del March y registra sus servicios; el Gerente sube los manuales de propietario (ver más abajo).

| Vehículo | VIN | Placas y kilometraje | Servicios | Manual |
|---|---|---|---|---|
| Nissan March 2026 Advance | `3N1CK3CD7RL900001` | GTO-123-A, 10,250 km | Servicio de 5,000 km (hace 150 días) y de 10,000 km (hace 10 días) | Sí, manual completo de 12 páginas con la foto del coche |
| Nissan Sentra 2025 Sense | `3N1AB8CV9RY900002` | Sin capturar | Cambio de balatas (hace 30 días) | Sí |

Qué se puede probar:
- Con `servicio1`, buscar `900001` en Expediente, abrir los detalles de cada servicio y corregir placas y kilometraje.
- Con `admin`, que el expediente se puede consultar pero no corregir.
- En la app, con `laura.mendez@example.com`: el primer ingreso con cambio de contraseña, los 2 vehículos y el manual del March y el del Sentra, que se abren con su URL temporal.
- En la app, con `laura.mendez@example.com`: el historial de servicios del vehículo que tenga servicios en el expediente y su ticket en PDF; la disponibilidad de la agenda (lunes a sábado de 9:00 a 17:00, capacidad de 3 por franja, sembradas en `franja_horario`), agendar una cita y cancelarla (hasta 24 h antes).
- Catálogo de la app: 22 modelos Nissan 2026, todos distintos, con versión, precio desde (MXN) y descripción, sin imagen (las fotos van en la app): March, Versa, Kicks, Sentra, X-Trail, Frontier PRO-4X, Leaf e+, Ariya Evolve+, Z Performance, GT-R Premium, Altima Platinum, Pathfinder Platinum, Murano Platinum, Armada Platinum Reserve, Titan Pro-4X, Urvan Panel, Maxima SR, Juke Exclusive, Qashqai Exclusive, Patrol Platinum, Tiida Advance, Note e-Power (`GET /api/v1/app/catalogo`).
- Citas de Laura (por la API de la app, con `LAURA_CONTRASENA`, por defecto `CareCar2026!`): una CONFIRMADA del March a los 3+ días y una CANCELADA del Sentra (`GET /api/v1/app/citas`). Si Laura ya tiene citas no agrega más; si no inicia sesión, omite este paso.

## Valuación formal

El script pide con la cuenta de Laura una valuación del March a partir de una estimación (V2-M26.01). Si la base no tiene filas en `parametro_toma_cuenta` para ese auto, la estimación responde 422 y el paso se omite: esos parámetros los entrega el PO y todavía no se cargan por la API. Si Laura ya tiene una valuación no agrega otra.

Qué se puede probar:
- En la app: las fotos guiadas y la inspección desde la valuación.
- Con `vendedor1`, `vendedor2` o `admin`, desde Swagger UI: `GET /api/v1/valuaciones` y, ya con la cita de hoy o anterior, `POST /api/v1/valuaciones/{id}/oferta`. Ventas solo ve las valuaciones de sus prospectos.

## Lo que no incluye todavía

- **Laura Méndez**: en local su primer ingreso, con la contraseña temporal de Mailpit, sigue siendo parte de la demostración. Sus citas, su valuación y su historia completa (V2-T05.12, abajo) se cargan solo si ya entra con `LAURA_CONTRASENA`: define su contraseña con `scripts/completar-datos-de-ejemplo-en-base.sh` o pásala si ya la cambió.
- **Asistencia en el camino y traslados** (M11 y M17): los pide el cliente desde la app con su ubicación y el personal los avanza en vivo, así que se demuestran en el momento. El script deja una dirección guardada para pedir la recolección.
- **Asistente** (M24): necesita la llave de IA por variable de entorno.
- **Derechos ARCO y cancelación de cuenta** (M22): se piden desde la app al demostrarlos.
- **Parámetros de toma a cuenta**: sin API todavía (ver «Valuación formal»).

## Clientes de la app con vehículos

El script también da de alta 5 clientes más de la app CareCar (además de Laura), cada uno con 3 a 5 vehículos Nissan, 1 a 3 servicios por vehículo (fechas pasadas, mano de obra y refacciones) y cuenta de app abierta. Su contraseña de prueba es `CareCar2026!` (`CLIENTES_CONTRASENA`): el script la define con el flujo de recuperación, leyendo el código de 6 dígitos de Mailpit, así que no piden cambiarla. Si se corre otra vez, no duplica nada.

| Correo | Contraseña | Vehículos (modelo versión año, placas, km) |
|---|---|---|
| carlos.ramirez@example.com | `CareCar2026!` | Sentra Advance 2024 GTO-482-B 18,400; Versa Sense 2022 GTO-715-C 42,150; Kicks Exclusive 2025 GTO-309-D 6,200; March Sense 2019 GTO-864-A 78,900 |
| ana.torres@example.com | `CareCar2026!` | X-Trail Exclusive 2023 MXP-27-83 29,700; Kicks Advance 2021 GTO-157-F 51,300; Versa Advance 2026 GTO-921-G 2,100 |
| luis.hernandez@example.com | `CareCar2026!` | Frontier PRO-4X 2024 GTO-603-H 23,800; Frontier LE 2020 GTO-248-J 96,500; Sentra Exclusive 2023 GTO-376-K 35,200; March Advance 2018 GTO-590-L 112,400; X-Trail Sense 2025 GTO-834-M 8,900 |
| sofia.morales@example.com | `CareCar2026!` | Kicks Sense 2022 NMR-48-19 47,800; Sentra Sense 2021 GTO-662-N 61,200; Versa Exclusive 2024 GTO-127-P 15,300; X-Trail Advance 2019 GTO-945-R 88,700 |
| diego.castillo@example.com | `CareCar2026!` | Frontier PRO-4X 2026 GTO-501-S 3,400; March Exclusive 2023 GTO-218-T 27,600; Sentra Advance 2020 GTO-776-V 74,100 |

## Manuales de propietario

`scripts/generar-manuales.py` genera un PDF de 12 páginas por cada modelo y año (portada con la foto del coche, índice, ficha técnica de referencia, identificación con VIN y placas, tablero, mantenimiento, fluidos y llantas, seguridad, conducción eficiente, solución de problemas, garantía y contacto). Arma un HTML y lo imprime con Google Chrome headless (`/usr/bin/google-chrome`, variable `CHROME`), usa las fotos de `carecar-mobile/assets/autos/`, buscando ese repo clonado junto a este (se cambia con la variable `FOTOS_AUTOS` o con `--fotos`) y deja los PDF en `scripts/manuales-generados/` (ignorada por git) junto con `indice.txt`.

Cubre la unión de los vehículos de `cargar-datos-de-ejemplo.sh` y los 22 modelos del catálogo en 2026 (42 manuales). `cargar-datos-de-ejemplo.sh` los genera si faltan y los sube como Gerente de marketing; la API reemplaza el manual de la misma marca, modelo y año, así que se puede repetir. Busca Python 3 como `python3`, `python` o `py -3` (en Windows, `python3` puede ser solo el atajo a la Microsoft Store) y, en Git Bash, Chrome donde lo instala Google si no se pasa `CHROME`. Si no hay Python 3 o Chrome, dice que omitió los manuales y la carga sigue con lo demás. Los datos técnicos son valores de referencia, y el contacto (+52 000 000 0000, contacto@agencia.example.com) es de ejemplo. Para regenerar: `python3 scripts/generar-manuales.py --forzar`.

## Módulos de la app v2 (V2-T05.10)

Lo carga el final del script: el personal por sus rutas (`servicio1` en el taller, garantías y campañas; `inventario1` en la tienda) y cada cliente por la API de la app con `CLIENTES_CONTRASENA`, como desde el celular. Así pasa por las mismas reglas: estados del taller, idempotencia del pago, timbrado simulado, puntos y existencias. Si un cliente ya no entra con esa contraseña, sus datos se omiten y el script lo dice. Al repetirlo no duplica nada.

| Módulo | Qué deja | Con quién se prueba |
|---|---|---|
| Garantías y campañas (M10) | Campaña `RC-26-101` «Bolsa de aire del conductor» (ALTA) para March 2023 a 2026 y `RC-26-102` «Sensor de presión de llantas» (MEDIA) para Kicks 2021 y 2022. Garantía de fábrica y batería de la Frontier GTO-501-S de Diego (la batería vence en 35 días: aviso de garantía por vencer) y tren motriz del Kicks GTO-309-D de Carlos | Diego (March GTO-218-T y Frontier), Laura (su March 2026 recibe la campaña), Ana y Sofía (sus Kicks), Carlos |
| Tienda (M14) | Publicadas REF-005 plumas limpiaparabrisas (accesorio, universal), REF-006 aceite 5W-30 (cuidado, universal), REF-001 filtro de aceite y REF-007 batería (March, Versa, Kicks y Sentra 2018 a 2026) y REF-008 amortiguador (X-Trail 2019 a 2026) | Cualquier cliente; Sofía tiene un apartado para mostrador (plumas y 4 litros de aceite) que vence a las 48 horas |
| Taller en vivo (M04) | Kicks NMR-48-19 de Sofía en el taller: diagnóstico, inspección con frenos traseros en rojo, llantas en amarillo y dos puntos en verde, esperando aprobación de un trabajo adicional («Cambio de balatas traseras», $1,540). March GTO-218-T de Diego listo para entrega con afinación mayor ($3,386) | Sofía aprueba o rechaza el adicional con su firma; Diego ve que su auto está listo |
| Pagos y facturas (M08) | Carlos pagó la orden de su Sentra GTO-482-B ($3,754) con la Visa de prueba 4242, guardó la tarjeta y tenía datos fiscales: la factura simulada se timbra sola (serie A, PDF y XML). Ana pagó su Kicks GTO-157-F ($1,540) con Google Pay simulado y sin factura; sus datos fiscales son de persona moral con «SA DE CV» en la razón social. La orden de Diego queda por pagar | Carlos descarga el PDF y el XML; Ana toca «Facturar» y ve el rechazo del timbrado (`TIMBRADO_RECHAZADO`, razón social); Diego paga desde la app con cualquier tarjeta de prueba o con la billetera |
| Lealtad (M12) | Carlos ganó 375 puntos por su servicio, canjeó «Lavado exprés» (300 puntos, con su código) y refirió a Mario Pérez. Ana ganó 154 puntos | Carlos: saldo, movimientos, canje y referido |
| Bitácora (M18) | Versa GTO-127-P de Sofía con 4 gastos: 2 cargas de gasolina (15,600 y 16,050 km), casetas y lavado | Sofía: rendimiento y gasto del mes |
| Recolección (M17) | Dirección «Casa» de Sofía (Blvd. Campestre 1520, León) | Sofía pide la recolección de un auto |
| Estreno (M16) | Prueba de manejo de la X-Trail para dentro de 3 días en la mañana; llega al CRM como prospecto con origen de la app | Sofía la ve en sus solicitudes; el Gerente de marketing, en el CRM |
| Documentos (M09) | Frontier GTO-501-S de Diego: póliza de «Seguros Ejemplo» (+52 800 000 1234) que vence en 30 días (por vencer), tarjeta de circulación vigente, verificación vencida hace 35 días y factura de compra de la agencia sin fecha. March GTO-218-T: tarjeta de circulación vigente. Los siembra la migración V61 si los vehículos ya existen; en una base nueva, `docker compose exec -T postgres psql -U agencia360 -d agencia360 < scripts/documentos-de-ejemplo.sql` después de cargar los datos | Diego ve los cuatro estados; con la póliza, «Asistencia» ofrece llamar a la aseguradora (V2-M11.02) |

Datos fiscales de prueba (RFC con dígito verificador válido, no son de personas reales):

| Cliente | RFC | Razón social | Régimen y uso |
|---|---|---|---|
| Carlos Ramírez | `RACC880704H33` | CARLOS RAMIREZ | 612 y G03 |
| Ana Torres | `TAS150310QW3` | TORRES Y ASOCIADOS SA DE CV | 601 y G03 |

Notas:
- La factura de Carlos y sus puntos se generan en segundo plano al confirmarse el pago; el script espera hasta 10 segundos a que lleguen los puntos para el canje. Si no llegaron, lo dice y el canje se hace al volver a correrlo.
- Las órdenes de Carlos y Ana quedan entregadas: pasan a su historial con su ticket.
- Para empezar de cero: `./scripts/limpiar-datos.sh`.

## Sembrado completo (V2-T05.12)

Lo agregan `scripts/completar-datos-de-ejemplo-en-base.sh` (con sus SQL) y el final de `scripts/cargar-datos-de-ejemplo.sh`. Sirve igual en local y en una API real; el procedimiento para Azure está en la sección 5.6 del README.

| Qué | Cómo se carga | Qué deja |
| --- | --- | --- |
| Contraseña de los 6 clientes | `psql`: hash BCrypt de fuerza 10 en `cuenta_app` | `CareCar2026!` sin pedir cambio de contraseña. Necesario donde el correo no llega a Mailpit |
| Parámetros de la toma a cuenta (`scripts/parametros-toma-cuenta-de-ejemplo.sql`) | `psql` (no hay API) | 352 filas: los 22 modelos Nissan del catálogo × 2012–2027. El valor base es el precio de catálogo de 2026 retrocedido 4 % por año; la depreciación y el factor de kilometraje cambian con el tipo de auto. Con ellos funcionan la calculadora, «Mis estimaciones» y la valuación formal |
| Documentos (`scripts/documentos-de-ejemplo.sql`) | `psql` | Los de Diego (arriba) y, para cada vehículo de los 6 clientes con placas: tarjeta de circulación vigente; póliza vigente, por vencer o vencida según el id (o ninguna); verificación vigente, por vencer o vencida; tenencia en los de id par; factura de la agencia en los de 2023 en adelante. Los cuatro estados se ven en todos los clientes |
| Citas pasadas (`scripts/citas-pasadas-de-ejemplo.sql`) | `psql` | 43 citas con fechas relativas a hoy, sin domingos: una atendida por vehículo (hace 160 días), otra en los de id par (hace 45), una que no se presentó en los de id múltiplo de 5 (hace 80) y una cancelada en los de id múltiplo de 3 (hace 25). La API no agenda en el pasado ni marca una cita como atendida |
| Cita próxima de cada cliente | API de la app | Carlos (Versa, mantenimiento), Ana (X-Trail, revisión), Luis (Frontier PRO-4X, mantenimiento), Sofía (Sentra, revisión) y Diego (Sentra 2020, reparación), en los próximos 1 a 9 días. «Mis citas» y la tarjeta de próxima cita del inicio ya tienen qué mostrar |
| Encuestas de servicio (M15) | API de la app | Ana respondió la de su orden entregada (NPS 9, comentario) y la de Carlos queda pendiente para que la app se la ofrezca |
| Valuación de Laura | API de la app | Con los parámetros anteriores deja de omitirse: Laura solicita la valuación de su March |
| Historia completa de Laura | API de la app | Cita próxima del March; orden 48 del Sentra entregada y pagada con la Visa de prueba (sin factura), que le deja puntos y una encuesta pendiente; y orden del March en el taller en vivo (en reparación, con las llantas en amarillo). Así la cuenta más antigua también muestra Inicio con taller y cita, pagos, lealtad y encuestas |

Para cargarlo en una API real:

1. `SIN_MAILPIT=1 ./scripts/cargar-datos-de-ejemplo.sh`
2. `CONFIRMAR=si ./scripts/completar-datos-de-ejemplo-en-base.sh`
3. `SIN_MANUALES=1 SIN_MAILPIT=1 ./scripts/cargar-datos-de-ejemplo.sh`

Con `SIN_MAILPIT=1` la contraseña de prueba de un cliente nuevo no se define en el paso 1: sus pasos como cliente se omiten y el script lo dice. En el paso 2 ya existe y en el 3 se cargan.

Cuentas de la app para probar (contraseña `CareCar2026!`): `laura.mendez@example.com` (March 2026 y Sentra 2025: orden en el taller, orden pagada, cita próxima, puntos, encuesta pendiente y valuación), `carlos.ramirez@example.com` (orden pagada con factura y canje de puntos, encuesta pendiente), `ana.torres@example.com` (pago con billetera y datos fiscales que el timbrado rechaza), `luis.hernandez@example.com` (5 vehículos), `sofia.morales@example.com` (auto en el taller con un adicional por aprobar, apartado en la tienda, gastos y dirección) y `diego.castillo@example.com` (orden lista para entrega, por pagar, y garantías).
