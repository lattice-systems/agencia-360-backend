# Historias de backend (B) de la app CareCar v2

Espejo de las historias marcadas **B** en el plan maestro de `carecar-mobile`. **El estado vive solo en** [`carecar-mobile/plan/tablero.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/tablero.md) para no tener dos verdades; aquí está el diseño técnico.

Al tomar una historia, el planificador o el implementador agrega bajo su sección: migración(es), entidades, eventos publicados/escuchados y endpoints con sus errores.

Total: **111 historias B**.

## Alineación con la ficha técnica v2.0 (prioridad máxima)

Antes que cualquier módulo nuevo: `V2-T05.03`–`V2-T05.07` dejan las tablas con los nombres y campos de la ficha. Ver la matriz en [`carecar-mobile/plan/cumplimiento-ficha.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/cumplimiento-ficha.md).

| Ficha | Hoy | Historia |
| --- | --- | --- |
| `cuenta_app.usuario` | Columna `cuenta_app.usuario`; el login la usa | V2-T05.03 |
| `token_recuperacion` | Tabla `token_recuperacion` (enlace y código); `codigo_recuperacion` retirada | V2-T05.04 |
| `manual.archivo_pdf` | Columna `manual.archivo_pdf` | V2-T05.05 |
| `orden_servicio`, `orden_refaccion` | Tablas `orden_servicio`, `orden_refaccion` y `orden_mano_de_obra` | V2-T05.06 |
| `franja_horario` | Tabla `franja_horario`; `cita.franja_id` con FK | V2-T05.07 |

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

- `POST /api/v1/app/citas/{citaId}/reprogramar` con `{ "franjaId": 5, "fecha": "2026-12-03" }`. `franjaId` es obligatorio; `fecha` es opcional y, si falta, se queda el día de la cita. Desde V2-T05.07 `franjaId` es el id de `franja_horario`, que es de un solo día de la semana: cambiar de día pide la fecha y una franja de ese día.
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

#### Diseño técnico — V2-M02.05 y V2-M02.06 (Ricardo)

**V2-M02.05 · Historial con filtro por año**

- `GET /api/v1/app/vehiculos/{vehiculoId}/servicios?anio=&pagina=&tamano=`: `anio` opcional (1900 a 2100, si no, 400 `DATOS_INVALIDOS`) filtra por el año de `orden_servicio.fecha`. Cada orden suma `fechaCierre` (hora de la agencia, sin zona, `yyyy-MM-ddTHH:mm:ss`; nula si sigue abierta) a `id`, `fecha`, `estatus` y `total`. La paginación no cambia: la app pide la siguiente página al llegar al final.
- `GET /api/v1/app/vehiculos/{vehiculoId}/servicios/anios` → `[2026, 2025]`: años con servicios, del más reciente al más antiguo, para el filtro.
- 404 si el vehículo no es del cliente. Sin migración: lee `orden_servicio` (V2-T05.06).
- `servicio.HistorialDeServicios` suma `deVehiculo(vehiculoId, anio, pagina)`, `aniosDeVehiculo` y `fechaCierre` en `ServicioDelVehiculo`.

**V2-M02.06 · Ticket en la app**

- `GET /api/v1/app/servicios/{servicioId}` → `ServicioDetalle`: `manoDeObra` y `refacciones` (cada renglón con `concepto`, `cantidad`, `precio` e `importe` = cantidad × precio), `totalManoDeObra`, `totalRefacciones`, `subtotal`, `iva` y `total` = mano de obra + refacciones. 404 si el servicio no existe o es de un vehículo ajeno.
- El PDF sigue en `GET /servicios/{servicioId}/ticket`; la app lo descarga para compartirlo.
- `servicio.HistorialDeServicios.detalle(servicioId)` lee `orden_mano_de_obra` y `orden_refaccion`.

**Pruebas.** `ServiciosAppIntegrationTest`: filtro por año, años con servicios, año fuera de rango 400, vehículo ajeno 404, ticket con mano de obra y refacciones que suman el total y ticket ajeno 404.

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

**Tablas nuevas** (migración `V34__crear_tablas_de_toma_a_cuenta.sql`; el diseño decía V25, pero V24 y V25 ya los tomaron otras historias y V34 fue el siguiente número libre al construirla):

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

**Construido** (V2-M03.06, junto con el backend de V2-M03.04 y V2-M03.07). `CalculadoraTomaCuenta` usa `BigDecimal` para que los resultados sean exactos y expone el rango con sus supuestos. Un vehículo registrado sin marca se busca solo por modelo y año, y solo sirve si hay un único parámetro activo. El año actual sale del reloj del servidor en UTC. Las pruebas están en `CalculadoraTomaCuentaTest`, `ParametroTomaCuentaTest` y `TomaCuentaIntegrationTest`. Falta que el PO entregue los valores reales de `parametro_toma_cuenta`: hasta entonces la app recibe 422 para todo auto.

**Antes de construirse.** La app trabajó contra el contrato y `mock_api` de `carecar-mobile` (dependencia simulable). El mock aplica la misma fórmula con un puñado de parámetros de ejemplo.

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

#### Diseño técnico — V2-M04.01, V2-M04.02, V2-M04.04 y V2-M04.05 (Ricardo)

**Tablas.** Una migración nueva (número al integrar):

- `orden_servicio` suma `cita_id` (FK a `cita`, opcional), `asesor_id` (FK a `usuario`, quien recibe el auto) y `entrega_estimada`.
- `etapa_orden` (`id`, `orden_servicio_id`, `etapa`, `inicio`, `fin`, `nota`): una fila por etapa que ya empezó; `fin` vacío mientras siga en curso. Etapas en orden: `RECIBIDO`, `DIAGNOSTICO`, `ESPERANDO_APROBACION`, `EN_REPARACION`, `CONTROL_DE_CALIDAD`, `LAVADO`, `LISTO_PARA_ENTREGA`, `ENTREGADO`. Una sola fila por etapa y orden.
- `punto_inspeccion` (`id`, `orden_servicio_id`, `punto`, `resultado`, `comentario`): `resultado` es `VERDE`, `AMARILLO` o `ROJO`.
- `evidencia_orden` (`id`, `orden_servicio_id`, `punto_inspeccion_id` opcional, `tipo`, `url`): `tipo` es `FOTO` o `VIDEO`; `url` guarda la clave del archivo en `archivos` (V2-T05.09) y la app recibe una URL temporal firmada.
- `trabajo_adicional` (`id`, `orden_servicio_id`, `concepto`, `mano_de_obra`, `refacciones`, `total`, `estado`, `respondido_en`, `firma_url`) y, para la auditoría, `respondido_por` (FK a `cliente`). `total = mano_de_obra + refacciones`, con IVA incluido; `estado` es `PENDIENTE`, `APROBADO` o `RECHAZADO`; una respuesta siempre guarda quién, cuándo y la firma, y ya no cambia (`check` en la tabla).

**Reglas.** La etapa fija el estatus de la orden: `RECIBIDO` → `RECIBIDO`; de `DIAGNOSTICO` a `LAVADO` → `EN_PROCESO`; `LISTO_PARA_ENTREGA` → `TERMINADO` con `fecha_cierre`; `ENTREGADO` → `ENTREGADO`. Se pueden saltar etapas (sin lavado), nunca volver. Una orden «en curso» es la que tiene línea de tiempo y no se ha entregado; las registradas en el expediente (ya hechas, sin etapas) no aparecen en Inicio. Un vehículo tiene a lo más una orden en curso. El avance va de 0 a 100 según la etapa. `archivos` acepta además `video/mp4` para las evidencias.

**Endpoints de la app** (`/api/v1/app`, rol `CLIENTE_CARECAR`):

- `GET /ordenes/activas` → `[OrdenEnCurso]`: `id`, `vehiculoId`, `vehiculo` («Nissan March 2026»), `etapa`, `estatus`, `entregaEstimada`, `asesor`, `avance`, `adicionalesPendientes`.
- `GET /ordenes/{id}` → `OrdenEnVivo`: lo anterior más `etapas` (cada una con `inicio`, `fin` y `nota`), `inspeccion` (puntos con sus `evidencias`), `evidencias` de la orden, `adicionales` y `totalOrden`, `totalAdicionalesAprobados`, `totalAdicionalesPendientes` y `totalEstimado` (orden más aprobados). 404 si no existe o es de un vehículo ajeno.
- `POST /ordenes/{id}/adicionales/{aid}/respuesta` `{aprobado, firma}` (firma en PNG en Base64, hasta 1 MB) → `OrdenEnVivo` con los totales recalculados. 400 `DATOS_INVALIDOS` si la firma no es un PNG; 404 si la orden o el trabajo no existen o son ajenos; 409 `ADICIONAL_YA_RESPONDIDO` o `ORDEN_CERRADA` (ya `TERMINADO` o `ENTREGADO`).

Horas en hora de la agencia, sin zona (`yyyy-MM-ddTHH:mm:ss`), como el resto de la app.

**Endpoints del taller** (`/api/v1/taller/ordenes`, rol `SERVICIO`; el ERP está congelado y no tiene pantalla): `POST` abre la orden (409 `ORDEN_ABIERTA`), `POST /{id}/etapas` la pasa de etapa con nota y nueva entrega estimada opcionales (400 si la etapa no va después; 409 `ORDEN_CERRADA` si ya se entregó), `POST /{id}/inspeccion`, `POST /{id}/evidencias` (multipart `archivo` y `puntoInspeccionId` opcional; 413 si pasa de 20 MB) y `POST /{id}/adicionales` (409 `ORDEN_CERRADA`). `GET /{id}` también para Administrador.

**Código.** Módulo `servicio`: entidades `EtapaOrden`, `PuntoInspeccion`, `EvidenciaOrden` y `TrabajoAdicional`; `OrdenServicio.abrir`, `pasarA` y `estimarEntrega`; `TallerService` (con candado de fila sobre la orden al pasar de etapa, proponer y responder) y la interfaz pública `servicio.TallerEnVivo`. Módulo `carecarapp`: `TallerAppService` revisa que el vehículo sea del cliente y `TallerAppController`.

**Eventos.** `servicio.OrdenServicioCambiada` al abrir la orden y en cada cambio de etapa; `carecarapp` ya lo manda por `GET /eventos` (la app lo aprovecha en V2-M04.03).

**Pruebas.** `TallerDominioTest`, `TallerServiceTest` (Mockito) y `TallerAppIntegrationTest`: la orden aparece en Inicio y desaparece al entregarse; etapas con hora y entrega estimada; no se regresa de etapa; inspección con semáforo, foto y video descargables por URL firmada; aprobar con firma recalcula el total y deja la auditoría en la tabla; responder dos veces, orden ajena, firma inválida u orden terminada; roles.

### M05 · Mantenimiento inteligente — H3 — módulo `servicio`

Detalle: [`carecar-mobile/plan/modulos/M05-mantenimiento-inteligente.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M05-mantenimiento-inteligente.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M05.01 | Como cliente quiero ver el plan de mantenimiento de mi modelo | Lista por km/meses con hechos (cruzados con historial) y pendientes | Juan Pablo |
| V2-M05.02 | Como cliente quiero saber cuándo toca mi próximo servicio | Estimación por km promedio diario (lecturas) y por tiempo; gana lo que llegue primero | Juan Pablo |
| V2-M05.03 | Como cliente quiero registrar mi kilometraje actual | Validación: no menor a la última lectura oficial · foto opcional · el km oficial (taller) no se sobrescribe | Juan Pablo |
| V2-M05.05 | Como cliente quiero un recordatorio cuando se acerque mi servicio | Push a 1 000 km o 2 semanas antes (configurable) | Juan Pablo |
| V2-M05.06 | Como cliente quiero ver qué incluye cada servicio y su precio estimado | Conceptos y precio con leyenda de estimado | Juan Pablo |

#### Diseño técnico — V2-M05.01, M05.02, M05.03 y M05.06 (parte B de Juan Pablo)

Vive en `carecarapp` (la historia lo ubica en `servicio`, pero solo consume su API pública `HistorialDeServicios`; evita un módulo más mientras el ERP sigue congelado). Migraciones `V40` (tablas) y `V41` (planes de ejemplo).

- `plan_mantenimiento`: `id`, `modelo` (Tipo, p. ej. March; sin distinguir mayúsculas), `anio_desde`, `anio_hasta`, `km`, `meses`, `nombre`, `precio_estimado`. `concepto_mantenimiento`: `id`, `plan_mantenimiento_id` (FK), `concepto`. Como en el resto del repo, `modelo` es el Tipo y el año del vehículo cae entre `anio_desde` y `anio_hasta`.
- `lectura_km`: `id`, `vehiculo_cliente_id` (FK), `km`, `fuente` (`TALLER`, `CLIENTE`), `foto_url` (nula; la foto opcional se sube con V2-T05.09), `registrada_en`. El km oficial del taller no se sobrescribe: las lecturas del cliente son filas aparte.
- **Km oficial** = el mayor entre `vehiculo_cliente.kilometraje`, el km de las órdenes cerradas y las lecturas `TALLER`. **kmActual** = el mayor entre el oficial y la última lectura del cliente; `kmFuente` dice cuál ganó (`taller` si empatan) y `kmActualizadoEn` su fecha (nula si viene solo del vehículo).
- `POST /vehiculos/{id}/lecturas-km` `{km}` → 201 `LecturaKm`. 422 `KM_INVALIDO` si `km` es menor al oficial o a la última lectura del cliente (igual sí se acepta); 400 si falta o no es mayor a 0.
- `GET /vehiculos/{id}/mantenimiento` → 200 `PlanMantenimiento`. Sin plan para el modelo/año: `servicios` vacío y `proximo` nulo.
- **Hecho** (cruce con `orden_servicio`, V2-M02.05): un servicio del plan se marca `hecho` si existe una orden **cerrada** (`TERMINADO` o `ENTREGADO`) con kilometraje a ±1 500 km del `km` del plan; `hechoEn` es la fecha de esa orden. **vencido**: no hecho y `kmActual` ya pasó su `km`. **proximo**: el no hecho de menor `km` que aún no se alcanza. El resto, `pendiente`.
- **Promedio diario**: con las lecturas del cliente más el km oficial fechado (órdenes y lecturas del taller), (último − primero) / días entre ambos; nulo con menos de dos puntos o sin días de diferencia.
- **Próximo servicio**: por km, `hoy + faltanKm / promedio` (solo con promedio); por tiempo, fecha base (la del último servicio hecho; si no hay, hoy) más los meses del plan menos los del último hecho. Gana la fecha más cercana y `motivo` es `km` o `tiempo`.
- Errores: 401, 403, 404 (vehículo ajeno), 400, 422 `KM_INVALIDO`. Los precios son una estimación no vinculante.
- Fuera de esta entrega: M05.05 (recordatorio push) espera a M06.


**Foto de la lectura (V2-M05.03, parte B):** `POST /vehiculos/{id}/lecturas-km` sigue aceptando el JSON `{km}` y suma `multipart/form-data` con `km` y `foto` (imagen JPEG/PNG/WebP opcional) en la misma ruta. El km se valida primero (400/422) y solo después se guarda la foto con `AlmacenArchivos` (carpeta `lecturas-km`); `lectura_km.foto_url` guarda la clave. `LecturaKm` devuelve `fotoUrl` firmada de 15 min cuando hay foto (se omite si no). Foto no imagen o vacía: 400.

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

#### Diseño técnico — V2-M11.01 (parte B de Juan Pablo)

Vive en `carecarapp` (la tabla es de la ficha; el módulo `asistencia` nuevo queda para V2-M11.04 cuando haya estados y eventos).

**Tabla `solicitud_asistencia`** (migración V35, nombres de la ficha): `id`, `cliente_id` (FK `cliente`), `vehiculo_cliente_id` (FK `vehiculo_cliente`), `tipo` (`LLANTA`, `BATERIA`, `CHOQUE`, `SIN_GASOLINA`, `OTRO`), `lat` y `lng` (`numeric(9,6)`, nulos juntos), `comentario` (≤ 500), `estado` (`RECIBIDA`, `EN_CAMINO`, `ATENDIDA`, `CANCELADA`), `creada_en`. Índice único parcial por `cliente_id` donde `estado in ('RECIBIDA','EN_CAMINO')`: una sola abierta por cliente, también bajo concurrencia.

**Endpoints** (rol `CLIENTE_CARECAR`; en JSON `tipo` y `estado` viajan en minúsculas):

| Método y ruta | Cuerpo | Respuestas |
| --- | --- | --- |
| `POST /asistencia` | `{vehiculoId, tipo, lat?, lng?, comentario?}` | 201 `{id, vehiculoId, tipo, lat, lng, comentario, estado: "recibida", creadaEn}` · 400 `DATOS_INVALIDOS` (tipo desconocido, lat fuera de -90..90, lng fuera de -180..180, solo una de las dos) · 404 si el vehículo no es del cliente · 409 `SOLICITUD_ABIERTA` si ya tiene una `recibida` o `en_camino` |
| `GET /asistencia?activa=true` | - | 200 con la solicitud abierta · 204 si no hay · 400 si `activa` no es `true` |

La ubicación se conserva mientras la solicitud está abierta; V2-M11.04 la borra al cerrarse.

#### Diseño técnico — V2-M11.04 (parte B de Juan Pablo)

Sigue en `carecarapp`. Sin tablas nuevas: `solicitud_asistencia` gana `cerrada_en` (migración V42, que comparte con M17.03 y M17.04).

**Transiciones** (`EstadoAsistencia`): `recibida` → `en_camino` o `cancelada`; `en_camino` → `atendida` o `cancelada`; `atendida` y `cancelada` son finales. Al cerrarse (`atendida`/`cancelada`) se guarda `cerrada_en` y se **borran `lat` y `lng`** de la fila (privacidad: la ubicación solo vive mientras hay ayuda por llegar).

| Método y ruta | Quién | Cuerpo | Respuestas |
| --- | --- | --- | --- |
| `GET /app/asistencia/{id}` | `CLIENTE_CARECAR`, solo el dueño | - | 200 `SolicitudAsistencia` (sin `lat`/`lng` si ya se cerró) · 404 si no existe o es ajena |
| `PATCH /asistencia/{id}/estado` (fuera de `/app`) | personal `GERENTE_MARKETING` (rol interno que el repo ya usa para endpoints de personal mientras el ERP siga congelado) | `{estado}` en minúsculas: `en_camino`, `atendida`, `cancelada` | 200 `SolicitudAsistencia` (con ubicación si sigue abierta) · 400 `DATOS_INVALIDOS` (estado desconocido) · 404 · 409 `TRANSICION_INVALIDA` · 401/403 |

**Evento** (cada cambio, al cliente dueño, por el SSE de `GET /app/eventos`): nombre `asistencia.estado`, `data` `{"solicitudId":12,"estado":"en_camino"}`. Se publica un `AvisoAlCliente(clienteId, evento, datos)` de Spring (módulo `carecarapp`); `EventosEnVivoApp` lo escucha con `@TransactionalEventListener(fallbackExecution = true)` y lo manda solo a las conexiones de ese cliente. El push (M06) escuchará el mismo evento cuando exista.

Pendiente (historia nueva, `Borrador`): bandeja del personal para listar solicitudes abiertas con su ubicación.

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
| V2-M16.01 | Como cliente quiero pedir una prueba de manejo | `POST /estreno/solicitudes` tipo `PRUEBA_MANEJO` con modelo y preferencia de día y franja · evento `SolicitudEstrenoCreada` → `ventascrm` crea el prospecto (etapa `PRIMER_CONTACTO`, historial origen `APP`) asignado al vendedor activo con menos prospectos abiertos · duplicado abierto del mismo modelo y tipo → 409 `ESTRENO_DUPLICADA` | Daniel |
| V2-M16.02 | Como cliente quiero cotizar un auto nuevo con mensualidad estimada | `POST /estreno/cotizaciones/calculo` (no guarda) con modelo, enganche y plazo → precio, monto financiado, tasa, mensualidad y leyenda no vinculante · parámetros en `parametro_financiamiento` · sin precio → 409 `ESTRENO_SIN_PRECIO`, enganche bajo o plazo no permitido → 400 · la solicitud `COTIZACION` guarda la cotización y crea el prospecto igual que la prueba (en `PRIMER_CONTACTO`: el vendedor lo mueve, para no saltar las reglas de etapas del CRM) | Daniel |
| V2-M16.03 | Como cliente quiero adjuntar mi estimación de toma a cuenta | `estimacionTomaCuentaId` opcional en la solicitud; debe ser del cliente (si no, 404) · queda en `solicitud_estreno` junto al `prospecto_id`, así el prospecto llega a ella | Daniel |
| V2-M16.04 | Como cliente quiero ver el estado de mis solicitudes | `GET /estreno/solicitudes` paginado, más recientes primero · estado `RECIBIDA` (sin prospecto aún), `EN_ATENCION` (con nombre del vendedor) o `CERRADA` (prospecto cerrado) calculado de `ventascrm` | Daniel |

#### Diseño de M16 (V2-M16.01 a V2-M16.04) — implementado

- **Migración `V39`:**
  - `solicitud_estreno` (id, cliente_id → `cliente`, modelo_catalogo_id → `modelo_catalogo`, tipo `PRUEBA_MANEJO`|`COTIZACION`, preferencia_fecha date, preferencia_franja `MANANA`|`TARDE` (solo prueba), enganche numeric(12,2), plazo_meses int, mensualidad numeric(12,2), tasa_anual numeric(6,4) (solo cotización), estimacion_toma_cuenta_id → `estimacion_toma_cuenta` (nullable), prospecto_id → `prospecto` (nullable hasta que `ventascrm` lo crea), created_at). Índice (cliente_id, created_at desc).
  - `parametro_financiamiento` (una fila): tasa_anual 0.1499, enganche_minimo_pct 0.10, plazos `12,24,36,48,60`, updated_at. Se edita después desde el CRM (congelado): por ahora solo por migración.
  - `historial_etapa.origen` acepta `APP` además de `CRM` y `ERP` (se amplía el check; nada se quita, R1).
- **Cálculo:** pago fijo (sistema francés) `M = P·r / (1 − (1 + r)^−n)` con `r = tasa_anual / 12`, `P = precio_desde − enganche`, redondeado a centavos. Leyenda fija: «Estimación no vinculante. No incluye seguro, comisión por apertura ni IVA de intereses; tu asesor te da la cotización formal.»
- **Eventos (Spring Modulith):** `carecarapp` publica `SolicitudEstrenoCreada(solicitudId, clienteId, modeloCatalogoId, tipo)` en `shared/eventos`; `ventascrm` la escucha con `@ApplicationModuleListener`, crea el prospecto (nombre, teléfono y correo del cliente) asignado al vendedor `VENTAS` activo con menos prospectos abiertos (empate: menor id) y publica `ProspectoDeEstrenoCreado(solicitudId, prospectoId)`; `carecarapp` guarda el `prospecto_id`. Si no hay vendedores activos, no crea nada y la solicitud sigue `RECIBIDA`; se reintenta al reiniciar (registro de publicaciones de Modulith).
- **Estado:** `carecarapp` lo calcula con la interfaz pública `ventascrm.ConsultaProspectos` (etapa y nombre del vendedor), sin cruzar tablas.
- **Contrato 2.10.0:** `POST /estreno/cotizaciones/calculo`, `POST /estreno/solicitudes`, `GET /estreno/solicitudes`. Errores: 400 `DATOS_INVALIDOS`, 404 modelo o estimación ajena, 409 `ESTRENO_SIN_PRECIO` y `ESTRENO_DUPLICADA`. Desde el contrato 2.12.1 se declaran en el enum de `Problema.codigo`; de la 2.10.0 a la 2.12.0 solo los nombraban en las descripciones.
- **Fuera de alcance:** sucursal (llega con V2-M27.02); el CRM web no cambia (R9): ya lista los prospectos y su historial muestra el origen `APP`.

### M17 · Recolección y entrega a domicilio — H5 — módulo `servicio`

Detalle: [`carecar-mobile/plan/modulos/M17-recoleccion-y-entrega.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M17-recoleccion-y-entrega.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M17.01 | Como cliente quiero guardar mis direcciones para pedir recolección sin escribirlas cada vez | Tabla `direccion_cliente` · `GET/POST /direcciones` y `PUT/DELETE /direcciones/{id}` solo del dueño · 422 si queda fuera de la zona de cobertura (configurable) | Juan Pablo |
| V2-M17.02 | Como cliente quiero pedir que recojan mi auto al agendar para no tener que llevarlo | Tabla `traslado` · `GET /recoleccion/cotizacion?direccionId=&fecha=` da costo y franja · `POST /citas` acepta `recoleccion.direccionId` y crea los traslados de recoger y entregar en la misma transacción | Juan Pablo |
| V2-M17.03 | Como cliente quiero seguir al chofer para saber cuándo llega | `GET /traslados/{id}` con estado y ubicación del chofer solo en `en_camino`/`de_regreso` · endpoint interno para el chofer mientras el ERP siga congelado · evento por cambio de estado para el push | Juan Pablo |
| V2-M17.04 | Como cliente quiero ver el checklist con fotos del estado de mi auto para comprobar cómo lo recogieron y entregaron | Tabla `checklist_traslado` · `GET /traslados/{id}/checklist` con km, gasolina, fotos y firma con URL firmadas de vida corta (V2-T05.09) · carga por endpoint interno | Juan Pablo |

#### Diseño técnico — V2-M17.01 y V2-M17.02 (parte B de Juan Pablo)

**Tablas** (migraciones V36 y V37, nombres de la ficha):

- `direccion_cliente`: `id`, `cliente_id` (FK), `alias` (≤ 40), `calle`, `colonia`, `cp` (5 dígitos), `ciudad`, `lat`, `lng` (obligatorias: el pin en el mapa es lo que valida la cobertura), más `created_at`/`updated_at`.
- `traslado`: `id`, `cita_id` (FK `cita`), `direccion_cliente_id` (FK), `tipo` (`RECOGER`, `ENTREGAR`), `chofer_id` (nulo hasta que se asigne; sin FK mientras el ERP siga congelado), `estado` (`PROGRAMADO` al nacer; el resto lo agrega V2-M17.03), `costo`, más `fecha`, `ventana_inicio`, `ventana_fin` y `created_at`/`updated_at`. Un traslado de cada tipo por cita (único `cita_id, tipo`).

**Cobertura y tarifa** (propiedades, README): `app.recoleccion.cobertura.centro-lat|centro-lng|radio-km` (por omisión León, Gto., 15 km; distancia haversine), `app.recoleccion.tarifa.base` (80) y `tarifa.por-km` (8), `app.recoleccion.ventana-recoleccion` (`07:00-09:00`, antes de abrir el taller) y `ventana-entrega` (`17:00-19:00`, después de cerrar). Costo de un traslado = base + por-km × distancia redondeada a 2 decimales; el total es el doble (ida y vuelta).

**Endpoints** (rol `CLIENTE_CARECAR`, solo del dueño; un id ajeno responde 404):

| Método y ruta | Cuerpo | Respuestas |
| --- | --- | --- |
| `GET /direcciones` | - | 200 lista `[{id, alias, calle, colonia, cp, ciudad, lat, lng}]` |
| `POST /direcciones` | `{alias, calle, colonia, cp, ciudad, lat, lng}` | 201 la dirección · 400 `DATOS_INVALIDOS` · 422 `FUERA_DE_COBERTURA` |
| `PUT /direcciones/{id}` | igual | 200 · 400 · 404 · 422 `FUERA_DE_COBERTURA` |
| `DELETE /direcciones/{id}` | - | 204 · 404 · 409 `DIRECCION_EN_USO` si tiene traslados |
| `GET /recoleccion/cotizacion?direccionId=&fecha=` | - | 200 `{direccionId, fecha, costoPorTraslado, costoTotal, recoleccion: {inicio, fin}, entrega: {inicio, fin}}` · 400 (fecha sin servicio o fuera de anticipación) · 404 · 422 `FUERA_DE_COBERTURA` |
| `POST /citas` | suma `recoleccion: {direccionId}` | 201 la cita con `recoleccion: {direccionId, costoTotal, traslados: [{id, tipo, estado, costo, fecha, ventanaInicio, ventanaFin}]}` (nulo si no se pidió) · 404 dirección ajena · 422 `FUERA_DE_COBERTURA` · los traslados nacen en la misma transacción: si algo falla no queda ni cita ni traslados |

Cancelar o reprogramar la cita cancela o mueve sus traslados: lo resuelve V2-M17.05 (ver el diseño de M17.03 y M17.04 más abajo).

#### Diseño técnico — V2-M17.03 y V2-M17.04 (parte B de Juan Pablo)

**Migración V42** (compartida con M11.04 y M05.03):

- `traslado` gana `chofer_lat`, `chofer_lng` (`numeric(9,6)`, nulos juntos) y `ubicacion_en`. Solo tienen valor mientras el estado es `en_camino` o `de_regreso`; al salir de ellos se borran.
- `checklist_traslado` (nombres de la ficha): `id`, `traslado_id` (FK, único: un checklist por traslado), `km`, `gasolina` (`VACIO`, `UN_CUARTO`, `MEDIO`, `TRES_CUARTOS`, `LLENO`), `notas` (≤ 500), `fotos` (`jsonb`, arreglo de claves de `AlmacenArchivos`), `firma_url` (clave del archivo de la firma), más `created_at`/`updated_at`.
- `solicitud_asistencia.cerrada_en` (M11.04).

**Estados por tipo** (`EstadoTraslado`, el chofer los mueve en orden; lo demás es 409 `TRANSICION_INVALIDA`):

- `recoger`: `programado` → `en_camino` → `recogido` → `en_taller` (final).
- `entregar`: `programado` → `de_regreso` → `entregado` (final).
- `cancelado` (final) solo desde `programado`, y solo lo pone el sistema cuando se cancela la cita.

La primera vez que el chofer mueve el traslado se guarda su id en `chofer_id` (el usuario del personal que llama; sigue sin FK).

| Método y ruta | Quién | Cuerpo | Respuestas |
| --- | --- | --- | --- |
| `GET /app/traslados/{id}` | `CLIENTE_CARECAR`, dueño de la cita | - | 200 `{id, citaId, tipo, estado, costo, fecha, ventanaInicio, ventanaFin, ubicacionChofer?: {lat, lng, actualizadaEn}}`; `ubicacionChofer` solo en `en_camino`/`de_regreso` y con al menos un reporte · 404 si es ajeno |
| `PATCH /traslados/{id}/estado` | personal `GERENTE_MARKETING` | `{estado}` | 200 el mismo objeto · 400 · 404 · 409 `TRANSICION_INVALIDA` |
| `PUT /traslados/{id}/ubicacion` | personal `GERENTE_MARKETING` | `{lat, lng}` | 204 · 400 (fuera de rango) · 404 · 409 `TRANSICION_INVALIDA` si el estado no es `en_camino`/`de_regreso` |
| `GET /app/traslados/{id}/checklist` | `CLIENTE_CARECAR`, dueño | - | 200 `{trasladoId, km, gasolina, notas, fotos: [url], firmaUrl, registradoEn}` con URL firmadas de 15 min (`AlmacenArchivos.urlTemporal`) · 404 si es ajeno o no hay checklist |
| `PUT /traslados/{id}/checklist` | personal `GERENTE_MARKETING` | `multipart/form-data`: `km` (1..2 000 000), `gasolina`, `notas?`, `fotos` (0 a 10 imágenes), `firma?` (imagen) | 200 el checklist · 400 · 404 · 409 `TRANSICION_INVALIDA` si el traslado sigue `programado` o está `cancelado`. Reemplaza el anterior y borra sus archivos |

El módulo `archivos` no tiene endpoint de carga, solo `AlmacenArchivos.guardar`; por eso el personal sube por multipart al endpoint del módulo dueño y este guarda con las carpetas `checklist-fotos` y `checklist-firmas`.

**Eventos SSE** (al cliente dueño de la cita): `traslado.estado` con `{"trasladoId":5,"citaId":9,"tipo":"recoger","estado":"en_camino"}` en cada cambio de estado, y `traslado.ubicacion` con `{"trasladoId":5,"lat":21.13,"lng":-101.69,"actualizadaEn":"2026-01-01T10:00:00Z"}` en cada reporte.

**Cita ↔ traslados (V2-M17.05, antes deuda de M17.02):** al cancelar una cita, sus traslados `programado` pasan a `cancelado`; al reprogramarla, sus traslados `programado` toman la nueva `fecha` (la ventana horaria no cambia). Los que ya salieron no se tocan. Todo ocurre en la transacción de la cita (`RecoleccionService.cancelarDeCita` y `reprogramarDeCita`, llamados desde `AgendaAppService`).

**Implementación (decisiones al programar):** `gasolina` viaja en minúsculas en el JSON (`vacio`, `un_cuarto`, `medio`, `tres_cuartos`, `lleno`) y en mayúsculas en la base, como el resto de los enumerados. `registradoEn` del checklist es `updated_at`. Las carpetas de `AlmacenArchivos` son `checklist-fotos` y `checklist-firmas`; si la carga falla se borran los archivos recién guardados y, al reemplazar, los del checklist anterior. Fotos y firma deben ser JPEG, PNG o WebP (el almacén también admite PDF, aquí no). `GET /app/traslados/{id}` responde con `SeguimientoTrasladoApp` (agrega `citaId` y `ubicacionChofer`) y no cambia el `Traslado` que ya devuelve la cita.

**Nota V2-M17.03 · `recoleccion` en toda respuesta de `Cita`:** `POST /citas` ya devolvía `recoleccion`, pero `GET /citas` no, y la app no podía abrir el seguimiento del chofer desde «Mis citas». Ahora `Cita.recoleccion` (`{direccionId, costoTotal, traslados}`, el mismo `RecoleccionApp`) viene en `POST /citas`, `GET /citas`, `POST /citas/{id}/cancelar` y `POST /citas/{id}/reprogramar`; es `null` si la cita no tiene traslados. Cada traslado trae su estado actual (un cancelado sigue en la lista con `estado: cancelado`; `costoTotal` es la suma de los costos de todos sus traslados). Sin migración. Para no hacer N+1, `GET /citas` carga los traslados de toda la página en una sola consulta (`TrasladoRepository.findByCitaIdInOrderByIdAsc`) y los agrupa por cita (`RecoleccionService.deCitas`). Cambio aditivo del contrato (versión menor).

### M18 · Bitácora del conductor — H6 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M18-bitacora-conductor.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M18-bitacora-conductor.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M18.01 | Como cliente quiero registrar una carga de gasolina para llevar el control de lo que gasto | Tabla `gasto_vehiculo` · `POST /vehiculos/{id}/gastos` tipo gasolina con litros, monto, km y fecha · 422 si el km es menor a la última `lectura_km` · crea una `lectura_km` de fuente cliente · `GET` paginado por mes | Juan Pablo |
| V2-M18.04 | Como cliente quiero registrar otros gastos para conocer el costo total de mi auto | Mismo `POST /vehiculos/{id}/gastos` con tipo estacionamiento, casetas, lavado u otro (descripción obligatoria en otro) · `PUT/DELETE /gastos/{id}` solo del dueño | Juan Pablo |

#### Diseño técnico — V2-M18.01 y V2-M18.04 (parte B de Juan Pablo)

Todo vive en `carecarapp` (no cruza módulos: el kilometraje oficial se lee por `VehiculosDeClientes` y `HistorialDeServicios`). Migración `V40` (junto con M05).

- `gasto_vehiculo`: `id`, `vehiculo_cliente_id` (FK), `tipo` (`GASOLINA`, `ESTACIONAMIENTO`, `CASETAS`, `LAVADO`, `OTRO`), `monto` (> 0), `litros` (nulo salvo gasolina), `km` (nulo salvo gasolina; opcional en los demás), `fecha`, `ticket_url` (de la ficha; sin uso hasta que haya carga de tickets), `descripcion` (≤ 200; se agrega a lo que pide la ficha porque el tipo `otro` la exige), `lectura_km_id` (FK nula: la lectura que creó la carga) y `created_at`/`updated_at`.
- Reglas: gasolina exige `litros > 0` y `km`; `otro` exige `descripcion`; la fecha no puede ser futura (hora de México); todo lo demás es 400 `DATOS_INVALIDOS`.
- `POST /vehiculos/{id}/gastos` → 201 `Gasto`. Una carga de gasolina valida el km con la misma regla que `POST /vehiculos/{id}/lecturas-km` (422 `KM_INVALIDO` si es menor al oficial o a la última lectura del cliente) y crea una `lectura_km` de fuente `CLIENTE`.
- `GET /vehiculos/{id}/gastos?mes=YYYY-MM&pagina=&tamano=` → `PaginaGastos` (`contenido`, `pagina`, `totalPaginas`), más recientes primero (fecha e id descendentes); sin `mes` devuelve todos. Mes mal formado: 400.
- `PUT /gastos/{id}` reemplaza el gasto (mismas reglas). Si es gasolina, actualiza o crea su lectura; la validación del km ignora la lectura propia. `DELETE /gastos/{id}` → 204 y **conserva** la lectura (el km ya ocurrió). Ajeno o inexistente: 404.
- Las estadísticas (km/l, $/km, gasto por mes) las calcula la app con estos datos.
- Errores: 400 `DATOS_INVALIDOS`, 401, 403, 404, 422 `KM_INVALIDO` (código nuevo en `CodigosError` y en el enum de `Problema`).

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

#### Diseño de V2-M22.01 — aviso de privacidad versionado

- **Migración `V43`** (número provisional; se toma al integrar):
  - `aviso_privacidad` (id, version int único ≥ 1, texto, vigente_desde, created_at). Un disparador rechaza `UPDATE` y `DELETE`: un aviso publicado nunca se edita, se publica otro.
  - `aceptacion_aviso` (id, cuenta_app_id → `cuenta_app`, aviso_privacidad_id → `aviso_privacidad`, aceptado_en), única por (cuenta, aviso): una fila por versión aceptada.
  - Versión 1: el aviso simplificado que la app traía en `Agencia.avisoPrivacidad`, tal cual. El texto definitivo es de la agencia: lo publica con el endpoint de abajo, sin desplegar.
- **Vigente:** la versión mayor con `vigente_desde <= ahora`. `vigente_desde` en el futuro programa una versión sin que la app la vea antes.
- **Endpoints de la app** (`CLIENTE_CARECAR`, contrato 2.13.0):
  - `GET /privacidad/aviso` → `{id, version, texto, vigenteDesde, aceptado, aceptadoEn}` del vigente.
  - `POST /privacidad/aceptaciones` `{avisoId}` → `{avisoId, version, aceptadoEn}`. Idempotente: repetir devuelve la primera aceptación. 404 si el aviso no existe; 409 `AVISO_NO_VIGENTE` si ya hay una versión más nueva.
- **Endpoint del personal** (`GERENTE_MARKETING`, fuera del contrato de la app como los de manuales y cuentas): `POST /api/v1/avisos-privacidad` `{texto, vigenteDesde?}` → 201 con la versión nueva (mayor + 1). `vigenteDesde` omitido es ahora; no puede ser anterior al de la versión previa. 400 `DATOS_INVALIDOS` si el texto va vacío o pasa de 20 000 caracteres.
- **Quién bloquea:** el servidor no rechaza el resto de la API por falta de aceptación (la app v1 instalada no la conoce); la puerta la pone la app. Lo que queda registrado, con fecha, es lo que cuenta ante una reclamación.
- **Fuera de alcance:** idiomas del aviso, y un listado de versiones con conteo de aceptaciones para el CRM (congelado, R9).

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

#### Diseño de V2-M24.09: respuesta mientras se escribe — implementado

- **Endpoint:** `POST /api/v1/app/asistente/preguntas/flujo` (`text/event-stream`). Eventos `texto` `{texto}`, `fin` (`RespuestaAsistente`) y `error` `{codigo}`. `POST /asistente/preguntas` no cambia. Contrato 2.9.0.
- **Antes de abrir el flujo** `AsistenteService.prepararPregunta` valida vehículo, manual y límites y busca las páginas: esos errores salen como JSON normal.
- **Modelo:** `ProveedorIA.responderEnFlujo` (Gemini `streamGenerateContent?alt=sse`, texto simple). El prompt pide terminar con la línea `PAGINAS: 2, 7` o `PAGINAS: ninguna`; `FiltroMarcaPaginas` solo deja pasar líneas completas, así la marca nunca llega a la app, y de ella salen las fuentes del evento `fin`.
- **Corte:** si la app se desconecta o el modelo falla a la mitad, se guarda lo mostrado en `consulta_asistente` con proveedor `<nombre>-parcial` y `encontrado=false`.
- **Hilos:** el flujo corre en un hilo virtual; el `SseEmitter` espera hasta 90 s.

#### Diseño de V2-M24.05: testigos del tablero por foto — implementado

- **Endpoint:** `POST /api/v1/app/asistente/testigos` (multipart `vehiculoId`, `imagen`), solo `CLIENTE_CARECAR`. JPEG o PNG hasta 5 MB; otra cosa → `400 DATOS_INVALIDOS`. Vehículo ajeno → `404`. Contrato 2.8.0.
- **Privacidad:** la imagen se decodifica y se vuelve a codificar como JPEG de máximo 1024 px de lado (se pierde EXIF, ubicación y miniaturas) y vive solo en memoria; no se guarda en disco ni en la base.
- **Modelo:** `ProveedorIA.identificarTestigo` manda la imagen a Gemini (`inline_data`) con salida JSON: `reconocido`, `testigo`, `gravedad` (`roja`, `amarilla`, `informativa`), `queHacer`. El proveedor falso reconoce rojo (presión de aceite) y ámbar (check engine) por el color dominante, para pruebas y local.
- **Página del manual:** si el vehículo tiene manual con texto, busca el nombre del testigo con `porPalabras` y cita la primera página; sin manual no falla.
- **Registro y límites:** guarda en `consulta_asistente` (pregunta `[foto] <testigo>`) y cuenta para el límite diario por cliente y el global, igual que las preguntas.

#### Diseño de V2-M24.07: calidad de respuestas — implementado

- **Calificar:** `POST /api/v1/app/asistente/consultas/{consultaId}/calificacion` con `{"util": true|false}`, solo `CLIENTE_CARECAR`. Responde `204`; calificar otra vez reemplaza. `404` si la consulta no existe o es de otro cliente (no se revela cuál). `400` sin `util`. Guarda en `consulta_asistente.util` (columna de V27, sin migración nueva). Contrato 2.7.0.
- **Conjunto de evaluación:** [`docs/asistente/evaluacion/preguntas.json`](../docs/asistente/evaluacion/preguntas.json), 20 preguntas con su página esperada. Vale para todos los manuales de `scripts/generar-manuales.py`, que comparten páginas.
- **Script:** `scripts/evaluar-asistente.py` (solo biblioteca estándar) pregunta con una cuenta de cliente y reporta el % de respuestas con alguna cita en la página esperada contra el umbral del ADR 0007 (80 %). Sale con 0 si pasa, 1 si no y 2 si faltan preguntas. Como el cliente tiene 10 preguntas al día, guarda en un `.jsonl` y la siguiente corrida sigue donde se quedó. Credenciales solo por variables de entorno.
- **Pendiente:** correr el conjunto contra Azure (lo corre Daniel con su cuenta) y anotar el resultado en el ADR antes de cerrar H6.

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

#### Diseño de V2-T05.01: módulos de Modulith de la v2

**Catálogo.** Queda en `plan/README.md`. Lo que esta historia llamaba `postventa` vive en `servicio` (ya tiene `orden_servicio`), `pagosapp` en `caja` y `facturacion`, y `asistente` dentro de `carecarapp` mientras no crezca. Los módulos nuevos se crean con su primera historia, no como carpetas vacías.

**Módulo nuevo.** `archivos` (V2-T05.09), el primero que hace falta: lo usarán evidencias de la orden (M04), documentos (M09) y traslados (M17). Su API pública es `archivos.AlmacenArchivos`; lo demás (`application`, `infrastructure`, `web`) es interno.

**Pruebas.** `ModularityTests`: `verify()` en verde y el módulo `archivos` registrado.

#### Diseño de V2-T05.02: contrato v2

**Archivo.** `docs/api/agencia360-api-app-v2.yaml`, versión 2.0.0. Nace como copia de `agencia360-api-app-v1.yaml` en 1.4.0 (todo lo integrado hasta hoy, incluidos los diseños de M02 y M03), con la descripción reescrita para la v2. No agrega ni quita rutas.

**Compatibilidad.** Las rutas siguen bajo `/api/v1/app`: la app v1 instalada sigue funcionando sin cambios. Un cambio incompatible va en una ruta nueva bajo `/api/v2/app` y la anterior sigue respondiendo igual mientras exista la app v1.

**v1 congelado.** `agencia360-api-app-v1.yaml` se queda en 1.4.0 como referencia de la app v1; su descripción lo dice. Desde esta historia, toda ruta o esquema nuevo entra solo en el v2 y sube su versión menor (2.1.0, 2.2.0...).

**Cómo se cambia.** Cada historia escribe sus rutas y esquemas bajo la etiqueta de su módulo antes de programarlas, sin reordenar ni reformatear lo de otros (archivos calientes de `carecar-mobile/plan/trabajo-en-paralelo.md`). Al integrar, se copia el archivo a `carecar-mobile/api/agencia360-api-app-v2.yaml`.

**Copia en la app.** `carecar-mobile/api/agencia360-api-app-v2.yaml` se agrega en un PR del repo de la app. La prueba de contrato de la app sigue leyendo el v1 hasta que V2-T03.06 la cambie al v2.

**Sin código.** No hay migración, entidades, endpoints ni eventos nuevos.

#### Diseño de V2-T05.03: `cuenta_app.usuario`

**Tabla.** `cuenta_app.usuario varchar(120) not null`, único (`uq_cuenta_app_usuario`): el correo del cliente en minúsculas y sin espacios.

**Migración.** Una nueva (número al integrar) que agrega la columna, la llena con `cliente.correo` y, si el cliente no tiene correo, con `cliente-<id>` (esa cuenta no puede entrar hasta que el cliente tenga correo), y después la deja `not null` y única.

**Código.** `CuentaApp` guarda `usuario` al abrirse y lo cambia con `cambiarUsuario`. El login y la recuperación buscan la cuenta por `usuario` y después al cliente por su id. Para cambiar el correo, el módulo de clientes expone `DirectorioClientes.cambiarCorreo(clienteId, correo)`: normaliza, responde 409 si el correo ya es de otro cliente y publica `CorreoDeClienteCambiado(clienteId, correo)`; `CuentasAppService` lo escucha con `@EventListener` en la misma transacción y cambia el usuario. V2-M01.07 usa ese método al confirmar el correo nuevo.

**Endpoints.** Sin cambios de forma: `POST /auth/login` sigue recibiendo `correo`. Contrato v2 en 2.3.0 con la descripción.

**Eventos.** `clientes.CorreoDeClienteCambiado` (publica clientes, escucha carecarapp).

**Errores.** Los mismos del login. `cambiarCorreo`: 404 si el cliente no existe, 400 si el correo no es válido, 409 si ya es de otro cliente.

**Pruebas.** `CuentaAppTest` (usuario normalizado y obligatorio) y `AccesoAppIntegrationTest` (la cuenta guarda el correo como usuario, al cambiar el correo entra con el nuevo y no con el anterior, no cambia a un correo de otro cliente).

#### Diseño de V2-T05.04: `token_recuperacion`

**Tabla.** `token_recuperacion` con los campos de la historia (`id`, `cuenta_app_id`, `token_hash`, `expira`, `usado`) y tres agregados: `codigo_hash` (el código de 6 dígitos para la app v1, en BCrypt), `usado_en` e `intentos`, más auditoría. `token_hash` es el SHA-256 del token (único); `check` de `usado = (usado_en is not null)`.

**Migración.** Una nueva (número al integrar) que crea la tabla, pasa los códigos de `codigo_recuperacion` (con un `token_hash` aleatorio, así el código pendiente sigue sirviendo y el enlace no existe) y borra `codigo_recuperacion`.

**Flujo.** `POST /auth/recuperar-contrasena` cierra los tokens abiertos de la cuenta y emite uno: un token aleatorio de 32 bytes y un código de 6 dígitos en la misma fila. El correo trae el enlace `RECUPERACION_ENLACE?token=<token>` (por omisión `carecar://restablecer-contrasena`) y el código. `POST /auth/restablecer-contrasena` acepta `token` (app v2) o `correo` y `codigo` (app v1, sin cambios); usar cualquiera de los dos marca la fila como usada, así que el otro deja de servir. Ambos vencen a los 15 minutos; el código admite 5 intentos.

**Endpoints.** `RestablecerContrasenaRequest` gana `token` y `correo`/`codigo` pasan a opcionales (compatible con la app v1). Contrato v2 en 2.3.0.

**Errores.** 400 `CODIGO_INVALIDO` si el enlace o el código no es válido, ya se usó o venció; 400 `DATOS_INVALIDOS` si no llega `token` ni `correo` con `codigo`, o la contraseña no cumple las reglas. `recuperar-contrasena` responde 202 exista o no el correo.

**Configuración.** `RECUPERACION_ENLACE` (opcional) en `docs/despliegue-azure-neon.md`.

**Eventos.** Ninguno.

**Pruebas.** `TokenRecuperacionTest` (vence, sirve una vez, cinco intentos) y `AccesoAppIntegrationTest` (enlace de un solo uso, código de la app v1, usar el código cierra el enlace, pedir otro cierra el anterior, sin token ni código responde 400).

#### Diseño de V2-T05.05: `manual.archivo_pdf`

**Migración.** Una nueva (número al integrar) que renombra `manual.archivo` a `archivo_pdf` y su restricción a `uq_manual_archivo_pdf`. Sigue siendo la clave del PDF en `archivo_manual` o en la carpeta local.

**Código.** `Manual.archivoPdf` con `getArchivoPdf()`; `ManualesService` e `IndexadorManuales` lo usan. El campo `archivo` del multipart de `POST /manuales` no cambia: es el nombre del archivo que se sube, no la columna.

**Endpoints, eventos y errores.** Sin cambios.

**Pruebas.** `VehiculosAppIntegrationTest` revisa que `manual.archivo_pdf` guarde la clave del PDF y que el PDF exista en `archivo_manual`.

#### Diseño de V2-T05.06: `orden_servicio` y `orden_refaccion`

**Tablas.** Lo que hoy es un servicio del expediente pasa a ser una orden de servicio, con los campos del modelo de datos (TEC-1) y los de la historia:

- `orden_servicio`: la tabla `servicio` renombrada, con sus mismos ids y columnas (`vehiculo_cliente_id`, `fecha`, `kilometraje`, `atendido_por`, `subtotal`, `iva`, `total`) y cuatro nuevas: `mano_de_obra`, `refacciones`, `estatus` y `fecha_cierre`. `check`: `total = mano_de_obra + refacciones` y `total = subtotal + iva`. `estatus` es uno de `RECIBIDO`, `EN_PROCESO`, `TERMINADO` o `ENTREGADO` (los mismos de PO-5 que ya devuelve la app); `fecha_cierre` existe solo cuando la orden está `TERMINADO` o `ENTREGADO`.
- `orden_refaccion`: `id`, `orden_servicio_id`, `refaccion_id` (opcional, con FK a `refaccion`), `concepto`, `cantidad`, `precio`, `importe` (`importe = cantidad * precio`). `refaccion_id` queda vacío mientras la orden no descuente inventario; se llena cuando el taller tome las refacciones del almacén (ERP-3.1, M04).
- `orden_mano_de_obra` (agregada): `id`, `orden_servicio_id`, `concepto`, `cantidad`, `precio`, `importe`. Guarda el detalle de la mano de obra para que el ticket siga mostrando cada concepto; `orden_servicio.mano_de_obra` es su suma.

Los precios siguen con IVA incluido: `mano_de_obra` y `refacciones` suman los importes y de su suma se desglosan `subtotal` e `iva` (16 %), como antes.

**Migración.** Una nueva (número al integrar) que renombra `servicio` (con su llave, índices, restricciones y secuencia), crea `orden_refaccion` y `orden_mano_de_obra`, reparte los renglones de `renglon_servicio` según su `tipo`, calcula `mano_de_obra` y `refacciones` de cada orden, deja las existentes en `TERMINADO` con `fecha_cierre = created_at` y borra `renglon_servicio`. Ningún dinero cambia: cada `total` existente es igual a la suma de sus renglones.

**Código.** Módulo `servicio`: `OrdenServicio` reemplaza a `Servicio`; `OrdenManoDeObra` y `OrdenRefaccion` (con la base común `RenglonOrden`) reemplazan a `RenglonServicio`; `OrdenServicioRepository` a `ServicioRepository`. `EstatusOrden` vive en la raíz del módulo porque `HistorialDeServicios.ServicioDelVehiculo` lo expone; la app lo traduce a su `EstatusServicio`. Registrar un servicio en el expediente crea la orden `TERMINADO` con `fecha_cierre` en ese momento.

**Endpoints.** Sin rutas nuevas ni cambios de forma. El ERP (`/api/v1/expedientes/...`) sigue recibiendo y devolviendo `renglones` con `tipo`; en el detalle salen primero los de mano de obra y después las refacciones. En la app, `Servicio.estatus` sale de `orden_servicio.estatus` (hoy siempre `TERMINADO`); el contrato v2 sube a 2.2.0 solo por esa descripción.

**Errores.** Los mismos de antes (400 por datos no válidos, 404 si el vehículo o la orden no existen).

**Eventos.** Ninguno.

**Pruebas.** `OrdenServicioTest` (dominio: total = mano de obra + refacciones, orden de los renglones, estatus y cierre), `ExpedienteServiceTest` (Mockito) y `ExpedienteIntegrationTest`, que revisa en las tablas reales el desglose, el estatus y los renglones de cada tabla.

#### Diseño de V2-T05.07: `franja_horario`

**Tabla.** `franja_horario` con los campos de la ficha (`id`, `dia_semana`, `hora_inicio`, `hora_fin`, `capacidad`) y dos agregados: `activa` (una franja se desactiva, no se borra, porque las citas la referencian) y la auditoría `created_at`/`updated_at`. `dia_semana` va de 1 (lunes) a 7 (domingo), como `isodow` de PostgreSQL y `DayOfWeek` de Java. Restricciones: `hora_fin > hora_inicio`, `capacidad > 0` y una sola franja por día y hora de inicio.

**Migración.** Una nueva (número al integrar) que:

1. Crea `franja_horario`.
2. La siembra con el horario que tenía `HorarioTaller` en `app.agenda.*`: lunes a sábado, de 9:00 a 17:00, ocho franjas de una hora con capacidad 3 (48 filas).
3. Agrega como inactivas (capacidad 1) las combinaciones de día y hora de inicio que tengan citas y no estén en el horario, para que ninguna cita quede sin franja.
4. Reescribe `cita.franja_id`, que era el número de la franja dentro del día (1 a 8), con el id de la fila del mismo día de la semana y hora de inicio.
5. Quita el `check` `ck_cita_franja` y agrega `fk_cita_franja_horario` hacia `franja_horario(id)`.

**Código.** Entidad `FranjaHorario` y `FranjaHorarioRepository` en `carecarapp`. `HorarioTaller` deja de calcular franjas y las lee de la tabla (solo activas, por día y hora de inicio); conserva `app.agenda.horas-para-cancelar` y `app.agenda.dias-de-anticipacion`. Se retiran `app.agenda.dias`, `apertura`, `cierre`, `duracion-franja-minutos` y `cupo-por-franja`. El cupo de una franja en una fecha es su `capacidad` menos sus citas confirmadas de esa fecha. El candado de agendar y reprogramar sigue siendo por fecha y franja, ahora con el id de la tabla.

**Endpoints.** Mismas rutas y formas. `franjaId` pasa a ser el id de `franja_horario`; la app ya lo toma de `GET /agenda/disponibilidad` y lo devuelve tal cual, así que sigue funcionando. Contrato v2 en 2.1.0 con la descripción de `franjaId`.

**Errores.** 400 `DATOS_INVALIDOS` si la franja no existe, está inactiva o no es del día de la fecha pedida; los demás (ya empezó, fuera de anticipación, `FRANJA_SIN_CUPO`, `CITA_DUPLICADA`, `CITA_NO_REPROGRAMABLE`) no cambian.

**Administración.** No hay pantalla ni endpoint para editar franjas (el ERP está congelado): cambiar capacidad, agregar o desactivar franjas se hace con SQL sobre `franja_horario`. La disponibilidad lo refleja al momento.

**Eventos.** Ninguno.

**Pruebas.** `FranjaHorarioTest` (dominio), `HorarioTallerTest` (Mockito) y `AgendaAppIntegrationTest`: las franjas sembradas, el cupo desde `capacidad`, una franja de otro día o inactiva responde 400 y una franja nueva aparece en la disponibilidad de su día.

#### Diseño de V2-T05.08: eventos en vivo

**Órdenes (SSE).** `GET /api/v1/app/eventos` (`text/event-stream`, rol `CLIENTE_CARECAR`; la cuenta desactivada responde 403 como el resto de la app). `servicio` publica `servicio.OrdenServicioCambiada(ordenId, vehiculoId, clienteId, estatus)` al registrar una orden; M04 lo publicará en cada cambio de etapa. `carecarapp` lo escucha después del commit y manda el evento `orden` con `ordenId`, `vehiculoId` y `estatus` solo a las conexiones de ese cliente. Comentario de latido cada 25 s; la conexión se cierra a los 30 min para que la app se reconecte con su token.

**Chat (WebSocket).** STOMP en `/ws` con broker simple (`/queue`), prefijo de aplicación `/app` y de usuario `/user`. `shared.security.AutenticacionStomp` valida el JWT del `CONNECT` (`Authorization: Bearer <token>`) y pone el usuario de la sesión; sin token o con uno inválido el `CONNECT` se rechaza. Solo se puede suscribir a `/user/queue/...` y mandar a `/app/...`. Los orígenes permitidos son los de CORS (`CORS_ALLOWED_ORIGINS`); la app móvil no manda `Origin`. Dependencia nueva: `spring-boot-starter-websocket`.

**Contrato.** v2 en 2.4.0 con `GET /eventos` bajo la etiqueta «En vivo» y el WebSocket descrito en `info`.

**Errores.** SSE: 401 sin token, 403 con rol que no es de la app o cuenta desactivada. WebSocket: frame `ERROR` y cierre.

**Límite.** Conexiones y broker en la memoria de la réplica; con varias réplicas hará falta un bus externo.

**Pruebas.** `EventosEnVivoAppIntegrationTest` (el cliente recibe su orden y no la de otro, 401 y 403) y `WebSocketIntegrationTest` (recibe sus avisos y no los de otro usuario, sin token o con token falso no conecta, no se suscribe fuera de `/user/queue`); `ExpedienteServiceTest` revisa que registrar publique el evento.

#### Diseño de V2-T05.09: almacenamiento de archivos

**API.** `archivos.AlmacenArchivos`: `guardar(carpeta, tipoContenido, contenido)` → `ArchivoGuardado(clave, tipoContenido, tamanoBytes)`; `leer(clave)`; `borrar(clave)`; `urlTemporal(clave)` → `/api/v1/archivos/{carpeta}/{id}?expira=&firma=`. La clave es `carpeta/uuid`. Solo JPEG, PNG, WebP, PDF y video MP4 (este último desde V2-M04.04, para las evidencias), hasta 20 MB.

**Implementaciones.** `ARCHIVOS_ALMACEN=base` (por omisión): tabla `archivo` en PostgreSQL, que en producción es Neon, en la nube. `ARCHIVOS_ALMACEN=local`: carpeta `ARCHIVOS_DIRECTORIO`, para desarrollo. Las dos detrás del puerto `Deposito`; un almacén de objetos (Azure Blob Storage) se agrega como otra implementación sin tocar a quien usa `AlmacenArchivos`, cuando el equipo abra esa cuenta.

**Migración.** Una nueva (número al integrar) que crea `archivo` (`clave` llave primaria, `tipo_contenido`, `tamano_bytes`, `contenido`, `created_at`; `check` de que el tamaño coincide con el contenido).

**URL firmadas.** HMAC-SHA256 con `ARCHIVOS_SECRETO` sobre `archivo:<clave>:<expira>`; vencen a los 15 minutos. `GET /api/v1/archivos/{carpeta}/{id}` es público (sin token) y responde 404 si no existe, la firma no es válida o venció, con `Cache-Control: no-store`. En `prod`, si falta `ARCHIVOS_SECRETO`, se firma con `MANUALES_SECRETO`; el prefijo evita que una firma de manual sirva para un archivo.

**Eventos.** Ninguno.

**Pruebas.** `FirmaArchivosTest`, `ArchivosServiceTest` (Mockito), `DepositoLocalTest` y `ArchivosIntegrationTest` (guarda en la base, descarga sin token, firma alterada o sin firma 404, borrado 404).
