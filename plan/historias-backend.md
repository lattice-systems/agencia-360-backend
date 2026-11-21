# Historias de backend (B) de la app CareCar v2

Espejo de las historias marcadas **B** en el plan maestro de `carecar-mobile`. **El estado vive solo en** [`carecar-mobile/plan/tablero.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/tablero.md) para no tener dos verdades; aquí está el diseño técnico.

Al tomar una historia, el planificador o el implementador agrega bajo su sección: migración(es), entidades, eventos publicados/escuchados y endpoints con sus errores.

Total: **110 historias B**.

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
| V2-M02.08 | Como cliente quiero agendar una cita eligiendo vehículo, fecha, franja y motivo | Confirmación con resumen · 409 por cupo lleno → ofrece franjas cercanas · 409 por cita duplicada → muestra la existente | Juan Pablo |
| V2-M02.11 | Como cliente quiero un comprobante con QR de mi cita para recepción rápida | QR con id firmado · PDF · agregable a Wallet (M25) | Juan Pablo |
| V2-M02.13 | Como cliente quiero reprogramar una cita sin cancelarla | Elige nueva franja; la anterior se libera en la misma operación | Juan Pablo |

### M03 · Catálogo y calculadora de toma a cuenta — H2 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M03-catalogo-y-calculadora.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M03-catalogo-y-calculadora.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M03.02 | Como cliente quiero el detalle de un modelo con galería y versiones | Galería deslizable con zoom · versiones con precio · ficha resumida | Cristian |
| V2-M03.04 | Como cliente quiero calcular cuánto me darían por mi auto a cuenta de uno nuevo | Elijo uno de mis vehículos (datos precargados) o capturo modelo/año · ajusto km y estado · veo rango mínimo–máximo · **leyenda "estimación no vinculante" siempre visible** | Cristian |
| V2-M03.06 | Como agencia quiero que la estimación salga de parámetros que puedo ajustar | La fórmula vive en el backend (`parametro_toma_cuenta`), no en la app · pruebas de la fórmula en backend | Cristian |
| V2-M03.07 | Como cliente quiero guardar y volver a ver mis estimaciones | Lista con fecha y modelo · se puede pedir valuación formal (M26) | Cristian |

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
| V2-M11.01 | Como cliente varado quiero pedir ayuda con mi ubicación | Permiso de ubicación explicado · envía vehículo y tipo · confirmación inmediata | Juan Pablo |
| V2-M11.04 | Como cliente quiero ver el estado de mi solicitud | Estados recibida/en camino/atendida con push | Juan Pablo |

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
| V2-M17.01 | Como cliente quiero guardar mis direcciones | Autocompletado y pin en mapa · validación de zona de cobertura | Juan Pablo |
| V2-M17.02 | Como cliente quiero pedir recolección al agendar | Costo y horario visibles · se ligan a la cita | Juan Pablo |
| V2-M17.03 | Como cliente quiero seguir al chofer | Estados con push · mapa en vivo solo durante el traslado | Juan Pablo |
| V2-M17.04 | Como cliente quiero el checklist con fotos del estado de mi auto | Fotos y firma al recoger y al entregar | Juan Pablo |

### M18 · Bitácora del conductor — H6 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M18-bitacora-conductor.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M18-bitacora-conductor.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M18.01 | Como cliente quiero registrar una carga de gasolina | Validaciones numéricas · km no menor al anterior | Juan Pablo |
| V2-M18.04 | Como cliente quiero registrar otros gastos | Tipos fijos + otro | Juan Pablo |

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
| V2-M24.02 | Como equipo queremos indexar los manuales por página | Al subir un manual se extrae texto por página y se generan embeddings | Daniel |
| V2-M24.03 | Como cliente quiero preguntar algo de mi auto y recibir una respuesta citada | Solo usa el manual de su modelo/año · cada respuesta cita páginas · si no está en el manual, lo dice | Daniel |
| V2-M24.05 | Como cliente quiero fotografiar una luz del tablero y saber qué significa | Identificación con gravedad · si es roja sugiere detenerse y SOS (M11) | Daniel |
| V2-M24.07 | Como equipo queremos medir la calidad de respuestas | Calificación 👍/👎 y conjunto de preguntas de evaluación por modelo | Daniel |

### M25 · Widgets, Wallet y accesos rápidos — H6 — módulo `carecarapp`

Detalle: [`carecar-mobile/plan/modulos/M25-widgets-y-wallet.md`](https://github.com/lattice-systems/carecar-mobile/blob/main/plan/modulos/M25-widgets-y-wallet.md)

| ID | Historia | Criterios de aceptación | Dueño |
| --- | --- | --- | --- |
| V2-M25.03 | Como cliente quiero guardar mi cita en Wallet | Pase firmado por el backend con QR del comprobante | Juan Pablo |
| V2-M25.05 | Como cliente de iPhone quiero ver el estado del taller en la pantalla bloqueada | Live Activity durante la orden abierta | Juan Pablo |

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
