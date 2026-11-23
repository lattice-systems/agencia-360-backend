# Historias de backend (B) de la app CareCar v2

Espejo de las historias marcadas **B** en el plan maestro de `carecar-mobile`. **El estado vive solo en** [`carecar-mobile/plan/tablero.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/tablero.md) para no tener dos verdades; aquí está el diseño técnico.

Al tomar una historia, el planificador o el implementador agrega bajo su sección: migración(es), entidades, eventos publicados/escuchados y endpoints con sus errores.

Total: **111 historias B**.

## Alineación con la ficha técnica v2.0 (prioridad máxima)

Antes que cualquier módulo nuevo: `V2-T05.03`–`V2-T05.07` dejan las tablas con los nombres y campos de la ficha. Ver la matriz en [`carecar-mobile/plan/cumplimiento-ficha.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/cumplimiento-ficha.md).

| Ficha | Hoy | Historia |
| --- | --- | --- |
| `cuenta_app.usuario` | No existe (se usa `cliente.correo`) | V2-T05.03 |
| `token_recuperacion` | `codigo_recuperacion` (código de 6 dígitos) | V2-T05.04 |
| `manual.archivo_pdf` | `manual.archivo` | V2-T05.05 |
| `orden_servicio`, `orden_refaccion` | `servicio`, `renglon_servicio` | V2-T05.06 |
| `franja_horario` | No existe (`HorarioTaller` en código) | V2-T05.07 |

## Historias por módulo

### M01 · Acceso y perfil — H2 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M01-acceso-y-perfil.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M01-acceso-y-perfil.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M01.04 | Como cliente quiero recuperar mi contraseña con un enlace de un solo uso enviado a mi correo para volver a entrar | Enlace abre la app (deep link) en Nueva contraseña · expira y sirve una sola vez (`usado`) · enlace vencido/usado → pantalla con opción de pedir otro · respuesta igual exista o no el correo | Jael |
| V2-M01.07 | Como cliente quiero editar mi teléfono y correo para que la agencia me contacte | Cambio se guarda tras confirmar un código enviado al nuevo dato · al cambiar correo cambia también `cuenta_app.usuario` · validación de formato | Jael |
| V2-M01.09 | Como cliente quiero ver y cerrar mis sesiones abiertas en otros celulares para sentirme seguro | Lista con dispositivo y último uso · cerrar una o todas revoca sus tokens | Jael |

### M02 · Mis vehículos y postventa (garaje, manuales, historial, tickets, citas) — H2 — módulo `carecarapp + servicio`

Detalle: [`carecar-mobile/plan/modulos/M02-vehiculos-y-postventa.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M02-vehiculos-y-postventa.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M02.04 | Como cliente quiero la ficha técnica de mi vehículo para conocer sus especificaciones | Datos desde la API por modelo/año · secciones plegables · respaldo local mientras el backend no los tenga | Juan Pablo |
| V2-M02.05 | Como cliente quiero el historial de servicios de mi auto para saber qué se le ha hecho | `GET /vehiculos/{id}/servicios` suma el filtro `anio` a la paginación · cada orden trae fecha de cierre, estatus y total | Ricardo |
| V2-M02.08 | Como cliente quiero agendar una cita eligiendo vehículo, fecha, franja y motivo | Confirmación con resumen · 409 `FRANJA_SIN_CUPO` → ofrece franjas cercanas · 409 `CITA_DUPLICADA` (hoy sin código) → muestra la existente · `cita.motivo` | Juan Pablo |
| V2-M02.11 | Como cliente quiero un comprobante con QR de mi cita para recepción rápida | QR con id firmado en el PDF que ya entrega `GET /citas/{id}/ticket` · agregable a Wallet (M25) | Juan Pablo |
| V2-M02.13 | Como cliente quiero reprogramar una cita sin cancelarla | Elige nueva franja; la anterior se libera en la misma operación | Juan Pablo |

#### Diseño técnico — V2-M02.04, M02.08, M02.11 y M02.13 (parte B de Juan Pablo)

Estado: en curso en la rama `feat/v2-juan-pablo-citas-b`. Los nombres de campos y rutas son los que usa la app en `carecar-mobile`; si la app pide otros, se alinean aquí y en el contrato antes de integrar.

**V2-M02.08 · Motivo y cita duplicada**

- Migración `V24__agregar_motivo_a_la_cita.sql`: `cita.motivo varchar(15)` nulo, con `check` en `MANTENIMIENTO, REPARACION, REVISION, GARANTIA, OTRO`. No renombra nada de la ficha; las citas anteriores quedan con `motivo` nulo.
- `POST /api/v1/app/citas` acepta `motivo` opcional. En el JSON viaja en minúsculas (`mantenimiento`, `reparacion`, `revision`, `garantia`, `otro`; se acepta sin importar mayúsculas) y se guarda en mayúsculas. Un valor fuera de la lista responde 400 `DATOS_INVALIDOS`.
- `GET /api/v1/app/citas` y las respuestas de agendar, cancelar y reprogramar devuelven `motivo` (nulo si no se dio).
- 409 con `codigo` `CITA_DUPLICADA` (formato `Problema`) cuando el mismo vehículo ya tiene una cita confirmada ese **día** (cubre también la misma franja, que hoy responde 409 sin código). `FRANJA_SIN_CUPO` no cambia. Se revisa primero la duplicada y después el cupo.

**V2-M02.13 · Reprogramar sin cancelar**

- `POST /api/v1/app/citas/{citaId}/reprogramar` con `{ "franjaId": 5, "fecha": "2026-12-03" }`. `franjaId` es obligatorio; `fecha` es opcional y, si falta, se queda el día de la cita (la franja es el número dentro del día, así que cambiar de día pide la fecha; a alinear con la app).
- Responde 200 con la `Cita` actualizada. Sin migración: se actualizan `fecha`, `franja_id`, `hora_inicio` y `hora_fin` de la misma fila, así que liberar la franja anterior y ocupar la nueva es una sola transacción (el cupo se cuenta por citas confirmadas).
- Reglas: la cita debe ser del cliente (404 si no), estar `CONFIRMADA` y no haber empezado; si no, 409 `CITA_NO_REPROGRAMABLE`. La nueva franja pasa las mismas validaciones que agendar (400 si el taller no abre, la franja no existe, ya empezó o pasa del límite de anticipación; 400 si es la misma franja y fecha). 409 `FRANJA_SIN_CUPO` si no hay lugar y 409 `CITA_DUPLICADA` si el vehículo ya tiene otra cita ese día. Bloquea la franja nueva con el mismo candado que agendar.

**V2-M02.11 · QR firmado en el comprobante**

- `GET /api/v1/app/citas/{citaId}/ticket` sigue igual y agrega al PDF un QR con el texto `CARECAR-CITA:<id>.<firma>`; `firma` es HMAC-SHA256 en base64url de `cita:<id>` con la llave `app.citas.qr-secreto`.
- La llave sale de la variable de entorno `CITAS_QR_SECRETO` (al menos 32 caracteres; en el repo solo hay un valor de solo-desarrollo, igual que `MANUALES_SECRETO`, y el arranque falla si es más corta). Hay que sobrescribirla en cualquier ambiente real.
- `GET /api/v1/app/citas/{citaId}/comprobante` (alineado con la app) responde JSON `{ citaId, codigo, fecha, horaInicio, horaFin, vehiculoNombre }` donde `codigo` es exactamente el texto del QR del PDF. Solo del dueño (404 si no); una cita cancelada responde 409 `CITA_CANCELADA` (formato `Problema`). `vehiculoNombre` puede faltar.
- Clase `FirmaCitas` (`firmar` y `esValida`, con comparación en tiempo constante) para que recepción y el pase de Wallet (V2-M25.03) verifiquen el mismo QR. El QR se dibuja con `com.google.zxing:core` (OpenPDF no trae generador de QR).

**V2-M02.04 · Ficha técnica del vehículo**

- Migración `V25__crear_ficha_tecnica_modelo.sql`: tabla `ficha_tecnica_modelo` (`id`, `marca`, `modelo`, `anio`, `seccion`, `nombre`, `valor`, `orden`, auditoría), una fila por especificación, índice por `lower(marca), lower(modelo), anio`. Es tabla nueva: no toca la ficha técnica del equipo.
- `GET /api/v1/app/vehiculos/{vehiculoId}/ficha-tecnica` responde `{ vehiculoId, marca, modelo, anio, version, secciones: [{ nombre, especificaciones: [{ nombre, valor }] }] }`, secciones y especificaciones en su `orden`. Se busca por marca (si el vehículo la tiene), modelo y año; sin datos para ese modelo responde 200 con `secciones: []` (la app usa su respaldo local). 404 si el vehículo no existe o no es del cliente.
- La carga de datos de la ficha (captura por el equipo de producto) queda fuera de esta historia: se agrega después como historia de ERP o script.
- Eventos: ninguno publicado ni escuchado en estas cuatro historias.

### M03 · Catálogo y calculadora de toma a cuenta — H2 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M03-catalogo-y-calculadora.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M03-catalogo-y-calculadora.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M03.02 | Como cliente quiero el detalle de un modelo con galería y versiones | Galería deslizable con zoom · versiones con precio · ficha resumida | Cristian |
| V2-M03.04 | Como cliente quiero calcular cuánto me darían por mi auto a cuenta de uno nuevo | Elijo uno de mis vehículos (datos precargados) o capturo modelo/año · ajusto km y estado · veo rango mínimo–máximo · **leyenda "estimación no vinculante" siempre visible** | Cristian |
| V2-M03.06 | Como agencia quiero que la estimación salga de parámetros que puedo ajustar | La fórmula vive en el backend (`parametro_toma_cuenta`), no en la app · pruebas de la fórmula en backend | Cristian |
| V2-M03.07 | Como cliente quiero guardar y volver a ver mis estimaciones | Lista con fecha y modelo · se puede pedir valuación formal (M26) | Cristian |

#### Diseño de V2-M03.02: detalle de un modelo

**Endpoint.** `GET /api/v1/app/catalogo/{modeloId}`, rol `CLIENTE_CARECAR`. Responde un `DetalleModeloCatalogo` (contrato 1.3.0, `docs/api/agencia360-api-app-v1.yaml`). Cada fila de `modelo_catalogo` es una versión de un modelo (marca, modelo, año y versión son únicos), así que el detalle se pide por el id de una fila y las versiones hermanas son las filas activas con la misma marca, modelo y año.

| Código | Cuándo |
| --- | --- |
| 200 | El modelo existe y está activo. |
| 401 | Sin token o vencido. |
| 403 | Cuenta desactivada (`CUENTA_DESACTIVADA`). |
| 404 | El id no existe o el modelo está retirado. Un modelo retirado se trata igual que uno inexistente para no filtrar el catálogo viejo. |

**Respuesta.** Los campos de `ModeloCatalogo` más tres listas:

- `imagenes`: la galería, ordenada por `orden`, con `url` https, `color` y `descripcion` opcionales. Puede venir vacía, y entonces la app muestra `imagenUrl` como única foto.
- `versiones`: las filas activas de la misma marca, modelo y año, de la más barata a la más cara (precio nulo al final). Siempre incluye la versión pedida. `nombre` es la `version` de la fila o el `modelo` si no tiene.
- `especificaciones`: la ficha resumida, de 4 a 8 pares `etiqueta` y `valor` ordenados por `orden`. Puede venir vacía. No sustituye a `ficha_tecnica_modelo` de V2-M02.04, que es la ficha completa por modelo y año con secciones.

**Tablas nuevas** (migración `V24__crear_galeria_y_especificaciones_del_catalogo.sql`, el siguiente número libre):

| Tabla | Campos | Notas |
| --- | --- | --- |
| `modelo_catalogo_imagen` | id, modelo_catalogo_id (FK), url (https, 500), color (40, nulo), descripcion (120, nulo), orden (entero, no negativo), created_at, updated_at | Es la tabla `modelo_catalogo_imagen` de la ficha. Índice único en (modelo_catalogo_id, orden). |
| `modelo_catalogo_especificacion` | id, modelo_catalogo_id (FK), etiqueta (60), valor (120), orden (entero, no negativo), created_at, updated_at | Índice único en (modelo_catalogo_id, orden). |

Las dos se borran en cascada con su modelo, aunque un modelo se retira (`activo = false`) y no se borra.

**Capas en `carecarapp`.** `domain`: entidades `ImagenModeloCatalogo` y `EspecificacionModeloCatalogo`, con la misma validación de URL https y largos que `ModeloCatalogo`. `infrastructure`: sus repositorios y una consulta `findByActivoTrueAndMarcaIgnoreCaseAndModeloIgnoreCaseAndAnio` para las versiones. `application`: `CatalogoService.detalle(id)` y el record `DetalleModeloCatalogoApp`, que lanza `RecursoNoEncontradoException` (404). `web`: `CatalogoController.detalle`.

**Alta de galería y ficha.** Queda fuera de esta historia: el ERP o el CRM web están congelados. Mientras tanto se capturan por SQL o por una historia propia del ERP, y la app ya tolera las listas vacías.

**Eventos.** No publica ni escucha ninguno.

**Pruebas pedidas.** 200 con galería, versiones y ficha, 200 con listas vacías, 404 para id inexistente y para modelo retirado, versiones sin las retiradas y ordenadas por precio, 401 y 403.

**Mientras no se implemente.** La app trabaja contra el contrato y `mock_api` de `carecar-mobile` (dependencia simulable).

#### Diseño de V2-M03.04, V2-M03.06 y V2-M03.07: estimación de toma a cuenta

Las tres historias comparten tablas y endpoint, así que se diseñan juntas. V2-M03.05 (la diferencia a pagar) no necesita backend: la app resta el rango estimado del `precioDestino` que ya viene en la respuesta.

**Endpoints.** Rol `CLIENTE_CARECAR`, contrato 1.4.0 (`docs/api/agencia360-api-app-v1.yaml`).

| Método | Ruta | Historia | Uso |
| --- | --- | --- | --- |
| POST | `/api/v1/app/toma-a-cuenta/estimaciones` | M03.04 y M03.06 | Calcula el rango con los parámetros de la agencia y lo guarda. |
| GET | `/api/v1/app/toma-a-cuenta/estimaciones` | M03.07 | Lista las estimaciones del cliente, la más reciente primero, con `pagina` y `tamano`. |

Calcular y guardar es una sola operación: toda estimación queda en el historial del cliente, porque es un prospecto suave para la agencia y porque así la app no tiene un paso de "guardar" que se pueda olvidar. La respuesta del POST es la misma `EstimacionTomaCuenta` que devuelve el GET.

**Petición.** `km` y `estado` (`EXCELENTE`, `BUENO`, `REGULAR` o `MALO`) son obligatorios. El auto se indica con `vehiculoId` (de ahí salen marca, modelo y año) o con `marca`, `modelo` y `anio`, nunca con las dos formas ni con ninguna. `modeloCatalogoId` es opcional y dice contra qué modelo nuevo se estima.

| Código | Cuándo |
| --- | --- |
| 201 | Estimación calculada y guardada. |
| 400 | Faltan datos, `km` fuera de 0 a 999999, año menor a 1900 o mayor al actual, estado desconocido, o se mandó el auto de las dos formas o de ninguna (`DATOS_INVALIDOS`). |
| 401 | Sin token o vencido. |
| 403 | Cuenta desactivada (`CUENTA_DESACTIVADA`). |
| 404 | El `vehiculoId` no existe o es de otro cliente, o el `modeloCatalogoId` no existe o está retirado. |
| 422 | No hay parámetros activos para esa marca, modelo y año (`SIN_PARAMETROS_DE_VALUACION`). |

Un 422 no es un fallo de la app ni del cliente: la agencia aún no valuó ese auto. La app lo explica y ofrece pedir una valuación formal (V2-M26.01). No se inventa un valor por omisión.

**Tablas nuevas** (migración `V25__crear_tablas_de_toma_a_cuenta.sql`, el siguiente número libre después de V24 de V2-M03.02):

| Tabla | Campos | Notas |
| --- | --- | --- |
| `parametro_toma_cuenta` | id, marca (40), modelo (80), anio, valor_base (numeric 12,2), depreciacion_anual (numeric 5,4), factor_km (numeric 5,4), factor_estado (numeric 5,4), activo, created_at, updated_at | Es la tabla `parametro_toma_cuenta` de la ficha, con `marca` agregada porque el modelo solo no identifica el auto. Índice único en (lower(marca), lower(modelo), anio). Checks: `valor_base >= 0`, `depreciacion_anual` entre 0 y 0.5, `factor_km` entre 0 y 0.2, `factor_estado` entre 0 y 0.3. |
| `estimacion_toma_cuenta` | id, cliente_id (FK), vehiculo_cliente_id (FK, nulo), modelo_catalogo_id (FK, nulo), marca, modelo, anio, km, estado, minimo (numeric 12,2), maximo (numeric 12,2), parametro_toma_cuenta_id (FK), supuestos (texto JSON), modelo_destino (120, nulo), precio_destino (numeric 12,2, nulo), created_at, updated_at | Es la tabla `estimacion_toma_cuenta` de la ficha, con campos agregados. `created_at` es la `creada_en` de la ficha. Guarda marca, modelo y año aunque haya vehículo, para que borrar o editar el vehículo no cambie la estimación. `supuestos`, `modelo_destino` y `precio_destino` congelan lo que se usó ese día. Índice en (cliente_id, created_at desc). Checks: `km >= 0`, `minimo <= maximo`, `estado` en los cuatro valores. |

Un parámetro que la agencia deja de usar se desactiva (`activo = false`), no se borra, porque las estimaciones guardadas lo referencian.

**La fórmula** (V2-M03.06). Vive en `CalculadoraTomaCuenta`, una clase de `domain` sin dependencias de Spring ni de la base, para probarla con números exactos. Con el parámetro activo de la marca, modelo y año del auto:

1. `edad = max(0, anioActual - anio)`.
2. `base = valor_base * (1 - depreciacion_anual) ^ edad`.
3. `kmEsperado = edad * kmAnuales`, con `kmAnuales` en 15,000 por omisión (`carecarapp.toma-cuenta.km-anuales`).
4. `ajusteKm = 1 - factor_km * (km - kmEsperado) / 10000`, limitado a entre 0.5 y 1.2. Menos kilómetros de los esperados lo sube y más lo baja.
5. `ajusteEstado = 1 - factor_estado * pasos`, con pasos `EXCELENTE = -1`, `BUENO = 0`, `REGULAR = 1` y `MALO = 2`.
6. `estimado = base * ajusteKm * ajusteEstado`.
7. `minimo = estimado * (1 - margen)` y `maximo = estimado * (1 + margen)`, con `margen` en 0.08 por omisión (`carecarapp.toma-cuenta.margen`). Ambos se redondean a la centena más cercana y nunca bajan de 0.

`valor_base` es el valor de referencia de ese modelo y año tal como sale de agencia, no un precio de lista. Las dos constantes (`km-anuales` y `margen`) son propiedades de la aplicación y no columnas, porque hoy no hay una pantalla donde la agencia las edite. Si se necesitan por modelo, se agregan columnas con otra migración sin romper el contrato, porque la respuesta ya expone `supuestos`.

**Alta y mantenimiento de parámetros.** Queda fuera de esta historia: el ERP y el CRM web están congelados. Mientras tanto se capturan por SQL y la migración no trae filas: los valores reales los entrega el PO, y sin ellos la app responde con el caso 422.

**Capas en `carecarapp`.** `domain`: entidades `ParametroTomaCuenta` y `EstimacionTomaCuenta`, el enum `EstadoVehiculo` y `CalculadoraTomaCuenta`. `infrastructure`: sus repositorios, con `findByActivoTrueAndMarcaIgnoreCaseAndModeloIgnoreCaseAndAnio` y la consulta paginada por cliente. `application`: `TomaCuentaService.estimar(clienteId, solicitud)` y `TomaCuentaService.listar(clienteId, pagina)`, que lanzan `RecursoNoEncontradoException` (404) y la nueva `SinParametrosDeValuacionException` (422). `web`: `TomaCuentaController`. El cliente sale del token, nunca del cuerpo.

**Seguridad.** Una estimación ajena no se puede leer: el listado filtra siempre por el cliente del token y un `vehiculoId` de otro cliente responde 404. Los datos del auto son del cliente, así que no van a los registros de la aplicación.

**Eventos.** No publica ni escucha ninguno. Cuando exista V2-M26.01, la valuación formal leerá la estimación por su id.

**Pruebas pedidas.** De la fórmula, con casos de números exactos: auto nuevo, auto con los kilómetros esperados, con el doble, con cero, cada estado, el límite de 0.5 y de 1.2 del ajuste de kilómetros, el redondeo a 100 y el rango que no baja de 0. Del servicio y del controlador: 201 con vehículo, 201 con auto capturado, 201 con y sin modelo destino, 400 con las dos formas del auto y con ninguna, 400 con km o año fuera de rango, 404 con vehículo ajeno y con modelo retirado, 422 sin parámetros o con el parámetro desactivado, 401 y 403, y que el listado salga ordenado, paginado y solo del cliente. Una estimación guardada no cambia aunque después cambie el parámetro.

**Mientras no se implemente.** La app trabaja contra el contrato y `mock_api` de `carecar-mobile` (dependencia simulable). El mock aplica la misma fórmula con un puñado de parámetros de ejemplo.

### M04 · Taller en vivo: estado de la orden y aprobación de trabajos — H3 — módulo `servicio`

Detalle: [`carecar-mobile/plan/modulos/M04-taller-en-vivo.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M04-taller-en-vivo.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M04.01 | Como cliente quiero ver en Inicio que mi auto está en el taller | Tarjeta aparece si hay orden abierta · desaparece al entregarse | Ricardo |
| V2-M04.02 | Como cliente quiero la línea de tiempo de mi orden con la hora de cada etapa | Etapas en orden con hora · etapa actual animada · ETA visible | Ricardo |
| V2-M04.03 | Como cliente quiero que la línea de tiempo se actualice sola | Cambio de etapa aparece sin recargar (SSE) · si la app está cerrada llega push | Ricardo |
| V2-M04.04 | Como cliente quiero ver la inspección multipunto con fotos | Semáforo por punto · fotos ampliables · video reproducible | Ricardo |
| V2-M04.05 | Como cliente quiero aprobar o rechazar trabajos adicionales desde la app | Cotización por concepto · aprobar/rechazar uno por uno · firma en pantalla · total se recalcula · auditoría en backend | Ricardo |
| V2-M04.06 | Como cliente quiero saber cuándo mi auto está listo | Push + tarjeta "Listo" con total y horario de entrega | Ricardo |
| V2-M04.07 | Como cliente quiero que la orden cerrada pase a mi historial con su ticket | Al cerrar, aparece en historial (M02) con ticket | Ricardo |

### M05 · Mantenimiento inteligente — H3 — módulo `servicio`

Detalle: [`carecar-mobile/plan/modulos/M05-mantenimiento-inteligente.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M05-mantenimiento-inteligente.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M05.01 | Como cliente quiero ver el plan de mantenimiento de mi modelo | Lista por km/meses con hechos (cruzados con historial) y pendientes | Juan Pablo |
| V2-M05.02 | Como cliente quiero saber cuándo toca mi próximo servicio | Estimación por km promedio diario (lecturas) y por tiempo; gana lo que llegue primero | Juan Pablo |
| V2-M05.03 | Como cliente quiero registrar mi kilometraje actual | Validación: no menor a la última lectura oficial · foto opcional · el km oficial (taller) no se sobrescribe | Juan Pablo |
| V2-M05.05 | Como cliente quiero un recordatorio cuando se acerque mi servicio | Push a 1 000 km o 2 semanas antes (configurable) | Juan Pablo |
| V2-M05.06 | Como cliente quiero ver qué incluye cada servicio y su precio estimado | Conceptos y precio con leyenda de estimado | Juan Pablo |

### M06 · Notificaciones y bandeja — H3 — módulo `notificaciones (nuevo)`

Detalle: [`carecar-mobile/plan/modulos/M06-notificaciones.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M06-notificaciones.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M06.02 | Como cliente quiero recibir push de la agencia en Android e iOS | Token FCM registrado tras login y borrado al cerrar sesión · payload sin datos personales sensibles | Jael |
| V2-M06.04 | Como cliente quiero una bandeja con todos mis avisos | Paginada · no leídos con contador en el ícono · marcar todas como leídas | Jael |
| V2-M06.05 | Como cliente quiero elegir qué avisos recibir y un horario silencioso | Por categoría · respetado por el backend al enviar | Jael |
| V2-M06.07 | Como equipo queremos un servicio único de envío en el backend | Módulo `notificaciones` en Modulith escucha eventos de dominio (cita, orden, documento…) | Jael |

### M07 · Mensajería con el asesor — H4 — módulo `mensajeria (nuevo)`

Detalle: [`carecar-mobile/plan/modulos/M07-mensajeria-asesor.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M07-mensajeria-asesor.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M07.01 | Como cliente quiero escribirle a mi asesor sobre mi orden | Conversación ligada a la orden · aparece en el detalle de la orden | Jael |
| V2-M07.02 | Como cliente quiero recibir respuestas en tiempo real | WebSocket con reconexión · push si la app está cerrada | Jael |
| V2-M07.03 | Como cliente quiero mandar fotos y audios | Compresión antes de subir · límite de tamaño · progreso | Jael |
| V2-M07.04 | Como cliente quiero saber si mi mensaje fue leído | Doble palomita al leer | Jael |

### M08 · Pagos y facturas — H5 — módulo `caja + facturacion`

Detalle: [`carecar-mobile/plan/modulos/M08-pagos-y-facturas.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M08-pagos-y-facturas.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M08.02 | Como cliente quiero pagar mi orden con tarjeta desde la app | 3-D Secure · idempotencia · estado pendiente/pagado/fallido · el ticket marca "pagado" | Ricardo |
| V2-M08.03 | Como cliente quiero pagar con Apple Pay o Google Pay | Botón nativo según plataforma | Ricardo |
| V2-M08.04 | Como cliente quiero guardar mis tarjetas | Tokenizadas · borrar · predeterminada | Ricardo |
| V2-M08.05 | Como cliente quiero capturar mis datos fiscales | Validación de RFC y CP · varios perfiles | Ricardo |
| V2-M08.06 | Como cliente quiero descargar mi factura PDF y XML | Disponible al timbrar · compartir/guardar | Ricardo |
| V2-M08.07 | Como cliente quiero ver todos mis pagos | Lista paginada con estado y referencia | Ricardo |

### M09 · Documentos y vencimientos — H3 — módulo `documentos (nuevo)`

Detalle: [`carecar-mobile/plan/modulos/M09-documentos-y-vencimientos.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M09-documentos-y-vencimientos.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M09.01 | Como cliente quiero ver los documentos de mi auto con su estado | Tipos fijos + "otro" · estado calculado por fecha | Haziel |
| V2-M09.02 | Como cliente quiero fotografiar un documento y guardarlo | Recorte de bordes · compresión · máx. tamaño · progreso | Haziel |
| V2-M09.03 | Como cliente quiero ver los documentos que me dio la agencia | Factura de compra y póliza de garantía cargadas por la agencia aparecen solas | Haziel |
| V2-M09.04 | Como cliente quiero alertas antes de que venza un documento | Push 30, 7 y 1 día antes · configurable | Haziel |

### M10 · Garantía y campañas de seguridad — H3 — módulo `servicio`

Detalle: [`carecar-mobile/plan/modulos/M10-garantia-y-campanas.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M10-garantia-y-campanas.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M10.01 | Como cliente quiero ver qué cubre mi garantía y hasta cuándo | Por componente con fecha y km límite contra km actual | Ricardo |
| V2-M10.02 | Como cliente quiero saber si mi auto tiene una campaña de seguridad | Cruce por modelo/año/rango de VIN · badge en Garaje | Ricardo |
| V2-M10.03 | Como cliente quiero agendar la atención de una campaña | Agendar con motivo "Campaña <código>" · estado pasa a agendada | Ricardo |
| V2-M10.04 | Como cliente quiero un aviso cuando salga una campaña para mi auto | Push con gravedad · no silenciable si es crítica | Ricardo |
| V2-M10.05 | Como cliente quiero saber que mi garantía está por vencer | Aviso 60 y 15 días antes / 1 000 km antes | Ricardo |

### M11 · Asistencia en camino (SOS) — H6 — módulo `asistencia (nuevo)`

Detalle: [`carecar-mobile/plan/modulos/M11-asistencia-en-camino.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M11-asistencia-en-camino.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M11.01 | Como cliente varado quiero pedir ayuda compartiendo mi ubicación para que la agencia me atienda rápido | `POST /asistencia` crea `solicitud_asistencia` (cliente, vehículo propio, tipo, lat/lng opcionales, comentario) → 201 con estado `recibida` · 404 si el vehículo no es del cliente · `GET /asistencia?activa=true` devuelve la abierta | Juan Pablo |
| V2-M11.04 | Como cliente quiero ver el estado de mi solicitud para saber cuándo llega la ayuda | `GET /asistencia/{id}` solo del dueño · estados `recibida` → `en_camino` → `atendida`/`cancelada` · endpoint interno para el personal mientras el ERP siga congelado · cada cambio publica un evento para el push (M06) | Juan Pablo |

### M12 · Lealtad y recompensas — H4 — módulo `lealtad (nuevo)`

Detalle: [`carecar-mobile/plan/modulos/M12-lealtad.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M12-lealtad.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M12.01 | Como cliente quiero ver mis puntos y nivel | Saldo por suma de movimientos · nivel por reglas del backend | Ricardo |
| V2-M12.02 | Como cliente quiero ganar puntos al pagar un servicio | Evento de orden cerrada/pagada suma puntos | Ricardo |
| V2-M12.03 | Como cliente quiero canjear recompensas | Canje descuenta puntos y genera código QR · sin saldo negativo (bloqueo de fila) | Ricardo |
| V2-M12.04 | Como cliente quiero referir a un amigo | Enlace con código · el referido entra como prospecto en el backend · recompensa al cerrar venta | Ricardo |
| V2-M12.05 | Como cliente quiero ver mis movimientos de puntos | Paginado, con motivo | Ricardo |

### M13 · Promociones y novedades — H4 — módulo `promociones (nuevo)`

Detalle: [`carecar-mobile/plan/modulos/M13-promociones.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M13-promociones.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M13.01 | Como cliente quiero ver promociones que apliquen a mi auto | Filtradas por segmento en backend · vigentes | Haziel |
| V2-M13.02 | Como cliente quiero usar una promoción al agendar | Cupón se adjunta a la cita | Haziel |
| V2-M13.03 | Como cliente quiero ver novedades de la agencia | Lista con imagen y detalle | Haziel |
| V2-M13.04 | Como agencia queremos saber qué promociones funcionan | Vistas y usos registrados (M23) | Haziel |

### M14 · Tienda de refacciones y accesorios — H5 — módulo `refacciones + inventario`

Detalle: [`carecar-mobile/plan/modulos/M14-tienda.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M14-tienda.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M14.01 | Como cliente quiero ver accesorios compatibles con mi auto | Filtro por vehículo seleccionado · existencia visible | Cristian |
| V2-M14.02 | Como cliente quiero apartar productos | Apartado reserva existencia por tiempo limitado · se libera al vencer | Cristian |
| V2-M14.03 | Como cliente quiero que me instalen lo comprado en mi cita | Pedido ligado a la cita; el taller lo ve en la orden | Cristian |
| V2-M14.04 | Como cliente quiero pagar mi pedido en la app | Reusa M08 | Cristian |
| V2-M14.05 | Como cliente quiero seguir mis pedidos | Estados con push | Cristian |

### M15 · Encuestas y NPS — H4 — módulo `encuestas (nuevo)`

Detalle: [`carecar-mobile/plan/modulos/M15-encuestas-nps.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M15-encuestas-nps.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M15.01 | Como cliente quiero calificar mi servicio al recogerlo | Se ofrece al cerrar orden (push + tarjeta) · una vez por orden · se puede posponer | Haziel |
| V2-M15.02 | Como cliente insatisfecho quiero que me contacten | NPS ≤ 6 → opción de contacto que crea tarea para la agencia | Haziel |
| V2-M15.03 | Como cliente quiero ver la respuesta de la agencia | Llega a la bandeja (M06) | Haziel |
| V2-M15.04 | Como agencia queremos el NPS por periodo y asesor | Endpoint de agregados para analítica (M23) | Haziel |

### M16 · Estreno: prueba de manejo y cotización de auto nuevo — H5 — módulo `ventascrm`

Detalle: [`carecar-mobile/plan/modulos/M16-estreno.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M16-estreno.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M16.01 | Como cliente quiero pedir una prueba de manejo | Crea prospecto en backend vía evento de dominio · confirmación | Daniel |
| V2-M16.02 | Como cliente quiero cotizar un auto nuevo con mensualidad estimada | Cálculo en backend · leyenda no vinculante | Daniel |
| V2-M16.03 | Como cliente quiero adjuntar mi estimación de toma a cuenta | Se liga a la solicitud y al prospecto | Daniel |
| V2-M16.04 | Como cliente quiero ver el estado de mis solicitudes | Estado y vendedor asignado | Daniel |

### M17 · Recolección y entrega a domicilio — H5 — módulo `servicio`

Detalle: [`carecar-mobile/plan/modulos/M17-recoleccion-y-entrega.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M17-recoleccion-y-entrega.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M17.01 | Como cliente quiero guardar mis direcciones para pedir recolección sin escribirlas cada vez | Tabla `direccion_cliente` · `GET/POST /direcciones` y `PUT/DELETE /direcciones/{id}` solo del dueño · 422 si queda fuera de la zona de cobertura (configurable) | Juan Pablo |
| V2-M17.02 | Como cliente quiero pedir que recojan mi auto al agendar para no tener que llevarlo | Tabla `traslado` · `GET /recoleccion/cotizacion?direccionId=&fecha=` da costo y franja · `POST /citas` acepta `recoleccion.direccionId` y crea los traslados de recoger y entregar en la misma transacción | Juan Pablo |
| V2-M17.03 | Como cliente quiero seguir al chofer para saber cuándo llega | `GET /traslados/{id}` con estado y ubicación del chofer solo en `en_camino`/`de_regreso` · endpoint interno para el chofer mientras el ERP siga congelado · evento por cambio de estado para el push | Juan Pablo |
| V2-M17.04 | Como cliente quiero ver el checklist con fotos del estado de mi auto para comprobar cómo lo recogieron y entregaron | Tabla `checklist_traslado` · `GET /traslados/{id}/checklist` con km, gasolina, fotos y firma con URL firmadas de vida corta (V2-T05.09) · carga por endpoint interno | Juan Pablo |

### M18 · Bitácora del conductor — H6 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M18-bitacora-conductor.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M18-bitacora-conductor.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M18.01 | Como cliente quiero registrar una carga de gasolina para llevar el control de lo que gasto | Tabla `gasto_vehiculo` · `POST /vehiculos/{id}/gastos` tipo gasolina con litros, monto, km y fecha · 422 si el km es menor a la última `lectura_km` · crea una `lectura_km` de fuente cliente · `GET` paginado por mes | Juan Pablo |
| V2-M18.04 | Como cliente quiero registrar otros gastos para conocer el costo total de mi auto | Mismo `POST /vehiculos/{id}/gastos` con tipo estacionamiento, casetas, lavado u otro (descripción obligatoria en otro) · `PUT/DELETE /gastos/{id}` solo del dueño | Juan Pablo |

### M19 · Cuentas familiares y conductores autorizados — H6 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M19-cuentas-familiares.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M19-cuentas-familiares.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M19.01 | Como titular quiero invitar a alguien a ver mi vehículo | Invitación por correo con enlace de un solo uso | Jael |
| V2-M19.02 | Como invitado quiero aceptar y ver el vehículo compartido | Garaje muestra "compartido por <titular>" · permisos respetados en backend | Jael |
| V2-M19.03 | Como titular quiero limitar lo que puede hacer el invitado | Permisos ver/agendar/avisos/documentos | Jael |
| V2-M19.04 | Como titular quiero revocar un acceso | Efecto inmediato; el invitado deja de ver el vehículo | Jael |

### M21 · Sin conexión y sincronización — H2 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M21-sin-conexion.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M21-sin-conexion.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M21.03 | Como cliente sin red quiero que mis acciones se envíen al volver la conexión | Cola de operaciones idempotentes (clave de idempotencia) · reintentos con espera exponencial · estado visible | Jael |
| V2-M21.05 | Como equipo queremos resolver conflictos de forma predecible | Servidor gana en datos del taller; cliente gana en preferencias; documentado | Jael |

### M22 · Seguridad y privacidad (ARCO) — H2 — módulo `carecarapp + acceso`

Detalle: [`carecar-mobile/plan/modulos/M22-seguridad-y-privacidad.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M22-seguridad-y-privacidad.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M22.01 | Como cliente quiero leer el aviso de privacidad real | Texto versionado del backend · aceptación registrada en primer ingreso y al cambiar de versión | Daniel |
| V2-M22.02 | Como cliente quiero descargar mis datos (acceso ARCO) | Solicitud genera archivo con todos mis datos · aviso al estar listo | Daniel |
| V2-M22.03 | Como cliente quiero pedir que borren mi cuenta (cancelación ARCO) | Flujo con confirmación · se desactiva `cuenta_app` y se anonimizan datos no obligatorios por ley; lo fiscal se conserva | Daniel |

### M23 · Analítica y observabilidad — H2 — módulo `shared`

Detalle: [`carecar-mobile/plan/modulos/M23-analitica-y-observabilidad.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M23-analitica-y-observabilidad.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M23.06 | Como equipo queremos un tablero de objetivos | Panel con indicadores O1–O10 enlazado en `plan/proposito.md` | Haziel |

### M24 · Asistente CareCar (IA) — H6 — módulo `asistente (nuevo)`

Detalle: [`carecar-mobile/plan/modulos/M24-asistente-carecar.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M24-asistente-carecar.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M24.02 | Como equipo queremos indexar los manuales por página para que el asistente busque solo en el manual correcto y pueda citar la página | Al registrar o cambiar `manual.archivo_pdf` se extrae el texto de cada página a `fragmento_manual` (`manual_id`, `pagina`, `texto`, `embedding` según el ADR de V2-M24.01) · reindexar no duplica · PDF escaneado sin texto queda marcado "sin texto" sin romper · comando de administración para indexar los existentes · prueba con un PDF de 3 páginas | Daniel |
| V2-M24.03 | Como cliente quiero preguntar algo de mi auto y recibir una respuesta citada para resolver dudas sin buscar en el manual | `POST /asistente/preguntas` usa solo fragmentos del manual del vehículo (modelo y año) · cada respuesta trae al menos una fuente `{manual_id, pagina}` · si no está en el manual lo dice sin inventar · errores `ASISTENTE_SIN_MANUAL`, `ASISTENTE_LIMITE` (límite diario por cliente) y `ASISTENTE_NO_DISPONIBLE` · guarda en `consulta_asistente` · prompt sin datos personales (prueba que lo verifica) · llave del proveedor solo en secreto de Azure | Daniel |
| V2-M24.05 | Como cliente quiero fotografiar una luz del tablero y saber qué significa para decidir si puedo seguir manejando | `POST /asistente/testigos` recibe la imagen sin EXIF y no la conserva tras responder · devuelve testigo, gravedad (roja, amarilla, informativa), qué hacer y página del manual si existe · si no la reconoce lo dice · se registra en `consulta_asistente` | Daniel |
| V2-M24.07 | Como equipo queremos medir la calidad de respuestas para saber si el asistente ayuda y mejorarlo | `POST /asistente/consultas/{id}/calificacion` guarda `util` · conjunto de evaluación de al menos 20 preguntas por modelo con página esperada · script que reporta el % de citas correctas contra el umbral del ADR | Daniel |
| V2-M24.09 | Como cliente quiero ver la respuesta mientras se escribe para no esperar en blanco | `POST /asistente/preguntas` responde en streaming (SSE) con las fuentes al final · una conexión cortada deja registro de respuesta parcial | Daniel |

#### Diseño de V2-M24.02 y V2-M24.03 (ADR 0007) — implementado

Primera versión dentro del módulo `carecarapp` (subpaquetes `application`, `infrastructure`, `web`), porque usa sus manuales y vehículos. Se separa a un módulo `asistente` si crece (V2-T05.01).

**Migración** `V27__crear_tablas_del_asistente.sql`:

- `create extension if not exists vector` (pgvector; Neon lo trae y Testcontainers usa la imagen `pgvector/pgvector:pg16`).
- `fragmento_manual`: `id`, `manual_id` (FK a `manual`, en cascada), `pagina`, `texto`, `embedding vector(768)` nulo hasta calcularse y `created_at`.
  - Único (`manual_id`, `pagina`).
  - Índice GIN de texto completo en español.
  - Índice HNSW coseno sobre `embedding`.
- `consulta_asistente`: `id`, `cliente_id`, `vehiculo_cliente_id`, `pregunta` (500), `respuesta`, `fuentes` (jsonb `[{manualId, pagina}]`), `util` (nulo), `encontrado`, `proveedor` y `created_at`.
  - Índice por cliente y fecha.

**Indexado (V2-M24.02):**
- `IndexadorManuales` lee el PDF del `AlmacenManuales` con PDFBox y guarda el texto de cada página. Reindexar no duplica (upsert por página) y borra las páginas que ya no existen. Un PDF sin texto queda sin fragmentos y se registra en el log.
- Se indexa al subir un manual (después del commit), al preguntar si el manual aún no tiene fragmentos, y cada 30 minutos los que falten.
- Los vectores se calculan aparte, como máximo 500 por día, con pausa entre llamadas por la cuota gratis.

**Pregunta (V2-M24.03):** `POST /api/v1/app/asistente/preguntas`, solo para `CLIENTE_CARECAR`.
- Cuerpo `{vehiculoId, pregunta}` (1 a 500 caracteres).
- Respuesta `{consultaId, respuesta, encontrado, fuentes: [{manualId, pagina, titulo}]}`.

Pasos:

1. El vehículo debe ser del cliente (404). Se busca el manual de su marca, modelo y año (`409 ASISTENTE_SIN_MANUAL`).
2. Límites: 10 por cliente al día (`429 ASISTENTE_LIMITE` con `Retry-After` hasta medianoche) y 450 en total al día (`503 ASISTENTE_NO_DISPONIBLE`).
3. Se borran de la pregunta correo, teléfono, VIN y placas.
4. Se eligen las 5 páginas más parecidas:
   - por vector, si se pudo calcular el de la pregunta y el manual ya tiene vectores;
   - si no, por texto completo.
5. Sin páginas, se responde "no lo encontré" sin llamar al modelo.
6. Con páginas, `ProveedorIA.responder` pide JSON `{respuesta, encontrado, paginas}`. Solo se aceptan páginas que se le dieron.
7. Se guarda en `consulta_asistente`.

**Proveedor:**
- `ProveedorIA` tiene dos implementaciones:
  - `GeminiProveedorIA`: REST con `RestClient`, encabezado `x-goog-api-key`, espera de 20 s y un reintento.
  - `ProveedorIAFalso`: determinista, sin red, para pruebas.
- `app.asistente.proveedor=auto` usa Gemini si hay `GEMINI_API_KEY`; si no, el falso.
- Los modelos se fijan con `ASISTENTE_MODELO_TEXTO` y `ASISTENTE_MODELO_EMBEDDING`.
- Una cuota agotada, un error o una espera vencida dan `503 ASISTENTE_NO_DISPONIBLE`.

### M25 · Widgets, Wallet y accesos rápidos — H6 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M25-widgets-y-wallet.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M25-widgets-y-wallet.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M25.03 | Como cliente quiero guardar mi cita en Wallet para presentarla en recepción | `GET /citas/{id}/pase?plataforma=apple\|google` firma el pase con el QR del comprobante (V2-M02.11) · certificados fuera del repo (R8) · al cancelar la cita el pase queda vencido | Juan Pablo |
| V2-M25.05 | Como cliente de iPhone quiero ver el estado del taller en la pantalla bloqueada para seguir mi servicio | Push de ActivityKit (APNs) en cada cambio de etapa de la orden abierta y al cerrarla · llaves de APNs fuera del repo (R8) | Juan Pablo |


#### Diseño de V2-M25.03: pase de Wallet de la cita

- `GET /api/v1/app/citas/{citaId}/pase?plataforma=apple|google`, solo `CLIENTE_CARECAR`. `plataforma` es obligatoria; otro valor es 400 `DATOS_INVALIDOS`.
- **Apple**: responde `application/vnd.apple.pkpass` con un `.pkpass` (zip con `pass.json`, `manifest.json` de SHA-1, `signature` y un `icon.png` generado en código, sin binarios en el repo). `signature` es PKCS#7 detached (SHA-256) firmado con el certificado de Pass Type ID y la cadena del WWDR (BouncyCastle `bcpkix`). `barcodes[0]` es QR con `message` igual a `FirmaCitas.contenidoQr(id)` (`CARECAR-CITA:<id>.<firma>`).
- **Google**: responde JSON `{ "url": "https://pay.google.com/gp/v/save/<jwt>" }`. El JWT (`typ` `savetowallet`, `aud` `google`, `iss` el correo de la cuenta de servicio) se firma RS256 con la llave de la cuenta de servicio (jjwt, ya en el proyecto) y lleva clase y objeto genérico con el mismo código en `barcode` (`QR_CODE`).
- **Errores**: 404 si la cita no existe o es de otro cliente; 409 `CITA_CANCELADA` si está cancelada (no se emite pase nuevo); 503 con `WALLET_NO_CONFIGURADO` (código nuevo en `CodigosError` y en el enum `Problema.codigo`, aditivo) si faltan las credenciales de la plataforma pedida. El arranque nunca falla por esto: la configuración es opcional y solo el endpoint responde 503.
- **Credenciales, todas por variable de entorno (R8)**: `WALLET_APPLE_PASS_TYPE_ID`, `WALLET_APPLE_TEAM_ID`, `WALLET_APPLE_CERT_P12_BASE64`, `WALLET_APPLE_CERT_PASSWORD`, `WALLET_APPLE_WWDR_PEM_BASE64`, `WALLET_GOOGLE_ISSUER_ID`, `WALLET_GOOGLE_SERVICE_ACCOUNT_JSON_BASE64`. Apple se considera configurado con las cinco primeras; Google con las dos últimas. Las pruebas generan sus propias llaves y certificados; nunca se commitean llaves reales.
- **Pase vencido al cancelar, sin servicio de actualización**: el pase emitido no se puede revocar desde el servidor sin un web service de Apple (`webServiceURL` + registro de dispositivos y APNs) ni una llamada OAuth a la API de Google Wallet, y ninguno entra en esta historia. Lo que sí se hace: (1) el pase lleva `expirationDate` / `validTimeInterval.end` igual al fin de la cita, así que vence solo al pasar la hora; (2) el QR sigue siendo el de V2-M02.11, que recepción valida contra el estado actual de la cita, por lo que un pase de una cita cancelada no sirve aunque siga en el teléfono; (3) el endpoint no emite pases de citas canceladas. Marcar `voided` (Apple) o `EXPIRED` (Google) en pases ya emitidos queda como historia nueva `Borrador` (servicio de actualización de pases).
- Sin migración: el pase se arma al vuelo con la cita y el vehículo.
- Historia nueva (`Borrador`): **V2-M25.06 · Servicio de actualización de pases** para marcar `voided` (Apple, con `webServiceURL`, registro de dispositivos y APNs) y `EXPIRED` (Google, PATCH con OAuth) los pases ya emitidos al cancelar o reprogramar la cita.

### M26 · Valuación y toma a cuenta avanzada — H5 — módulo `ventascrm`

Detalle: [`carecar-mobile/plan/modulos/M26-valuacion.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M26-valuacion.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M26.01 | Como cliente quiero pedir una valuación formal desde mi estimación | Crea solicitud ligada a `estimacion_toma_cuenta` | Cristian |
| V2-M26.02 | Como cliente quiero subir fotos guiadas de mi auto | 8 tomas guiadas (frente, laterales, interior, tablero con km…) con silueta | Cristian |
| V2-M26.03 | Como cliente quiero agendar la inspección física | Reusa agenda con motivo "Valuación" | Cristian |
| V2-M26.04 | Como cliente quiero recibir la oferta y aceptarla o rechazarla | Oferta con vigencia · se liga al prospecto de M16 | Cristian |
| V2-M26.05 | Como cliente quiero ver cómo cambia el valor de mi auto con el tiempo | Gráfica de valor estimado por mes | Cristian |

### M27 · Centro de ayuda y sucursales — H4 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M27-ayuda-y-sucursales.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M27-ayuda-y-sucursales.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M27.01 | Como cliente quiero buscar en preguntas frecuentes | Búsqueda local · contenido desde backend con caché | Haziel |
| V2-M27.02 | Como cliente quiero ver sucursales en un mapa con horarios | "Abierto ahora" calculado · cómo llegar abre la app de mapas | Haziel |
| V2-M27.04 | Como cliente quiero reportar un problema de la app | Formulario con versión, sistema y equipo (sin datos personales extra) | Haziel |

### T05 · Contrato API v2 y backend de la app — H1 — módulo `varios`

Detalle: [`carecar-mobile/plan/modulos/T05-contrato-api-v2.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/T05-contrato-api-v2.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-T05.01 | Como equipo queremos módulos Modulith nuevos para la v2 | Paquetes `notificaciones`, `postventa` (taller en vivo, mantenimiento, garantía), `lealtad`, `pagosapp`, `asistente` según se necesiten · `ModularityTests` verde | Ricardo |
| V2-T05.02 | Como equipo queremos el contrato `agencia360-api-app-v2.yaml` | Nace como copia de v1 + endpoints nuevos · v1 sigue respondiendo igual · copia en `carecar-mobile/api/` | Ricardo |
| V2-T05.03 | Como PO quiero `cuenta_app.usuario` como en la ficha | Migración nueva agrega `usuario` (= correo, único) y lo llena · login usa `usuario` · al cambiar correo se actualiza | Ricardo |
| V2-T05.04 | Como PO quiero `token_recuperacion` como en la ficha | Migración crea `token_recuperacion` (id, cuenta_app_id, token_hash, expira, usado) · recuperación por enlace de un solo uso · `codigo_recuperacion` se retira tras migrar | Ricardo |
| V2-T05.05 | Como PO quiero `manual.archivo_pdf` como en la ficha | Migración renombra `archivo` → `archivo_pdf` · código y pruebas actualizados | Ricardo |
| V2-T05.06 | Como PO quiero `orden_servicio` y `orden_refaccion` como en la ficha | Migración lleva `servicio`/`renglon_servicio` a `orden_servicio` (+ `mano_de_obra`, `estatus`, `fecha_cierre`) y `orden_refaccion` (refaccion_id, cantidad, precio) · datos migrados · total = mano de obra + refacciones | Ricardo |
| V2-T05.07 | Como PO quiero `franja_horario` como en la ficha | Tabla con dia_semana, hora_inicio, hora_fin, capacidad · sembrada con el horario hoy en `HorarioTaller` · `cita.franja_id` con FK · cupo por franja y fecha | Ricardo |
| V2-T05.08 | Como equipo queremos eventos en vivo para la app | SSE para órdenes y WebSocket para chat detrás de JWT | Ricardo |
| V2-T05.09 | Como equipo queremos almacenamiento de archivos para evidencias y documentos | Interfaz de almacenamiento con implementación local y en la nube · URLs firmadas de corta vida | Ricardo |
| V2-T05.10 | Como equipo queremos datos de ejemplo para cada módulo nuevo | `scripts/cargar-datos-de-ejemplo.sh` extendido | Ricardo |
| V2-T05.11 | Como equipo queremos desplegar la API en Azure automáticamente en cada merge a `main` | Workflow que construye la imagen en formato OCI, la publica en GHCR y actualiza `ca-agencia360-api` · entrada a Azure por OIDC con una identidad administrada (Contributor solo sobre la app; variables de repositorio, sin secretos) · verificación de `/actuator/health` tras desplegar · ver `docs/despliegue-azure-neon.md` · **Hecha** (`.github/workflows/despliegue.yml`) | Daniel |

#### Diseño de V2-T05.02: contrato v2

**Archivo.** `docs/api/agencia360-api-app-v2.yaml`, versión 2.0.0. Nace como copia de `agencia360-api-app-v1.yaml` en 1.4.0 (todo lo integrado hasta hoy, incluidos los diseños de M02 y M03), con la descripción reescrita para la v2. No agrega ni quita rutas.

**Compatibilidad.** Las rutas siguen bajo `/api/v1/app`: la app v1 instalada sigue funcionando sin cambios. Un cambio incompatible va en una ruta nueva bajo `/api/v2/app` y la anterior sigue respondiendo igual mientras exista la app v1.

**v1 congelado.** `agencia360-api-app-v1.yaml` se queda en 1.4.0 como referencia de la app v1; su descripción lo dice. Desde esta historia, toda ruta o esquema nuevo entra solo en el v2 y sube su versión menor (2.1.0, 2.2.0...).

**Cómo se cambia.** Cada historia escribe sus rutas y esquemas bajo la etiqueta de su módulo antes de programarlas, sin reordenar ni reformatear lo de otros (archivos calientes de `carecar-mobile/plan/trabajo-en-paralelo.md`). Al integrar, se copia el archivo a `carecar-mobile/api/agencia360-api-app-v2.yaml`.

**Copia en la app.** `carecar-mobile/api/agencia360-api-app-v2.yaml` se agrega en un PR del repo de la app. La prueba de contrato de la app sigue leyendo el v1 hasta que V2-T03.06 la cambie al v2.

**Sin código.** No hay migración, entidades, endpoints ni eventos nuevos.
