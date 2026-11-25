-- Aviso de privacidad versión 2 (V2-M22.10).
--
-- La versión 1 decía que los datos solo se compartían con autoridades, pero el
-- mapa, el geocodificador del sistema y el asistente (Gemini, ADR 0007) mandan
-- datos a terceros, y la información vive en Azure y Neon. La versión 2 los
-- nombra. Un aviso publicado no se edita (trigger de V43): se publica otra
-- versión, y como es la mayor con vigente_desde ya llegado, todas las cuentas
-- deben leerla y aceptarla otra vez. La agencia puede publicar después su texto
-- definitivo como versión 3 con POST /api/v1/avisos-privacidad, sin desplegar.

insert into aviso_privacidad (version, texto, vigente_desde, created_at)
values (2, $aviso$Agencia360, con domicilio en el indicado en esta app, es responsable del tratamiento de tus datos personales.

Datos que usamos: nombre, correo electrónico, teléfono y los datos de tus vehículos (marca, modelo, placas, número de serie y kilometraje), así como el historial de servicios y citas.

Finalidades: identificarte al iniciar sesión, mostrarte tus vehículos, manuales, servicios y tickets, agendar y recordarte tus citas en el taller y atender tus solicitudes. No usamos tus datos con fines publicitarios ni los vendemos a terceros.

Proveedores externos: para darte el servicio, parte de tus datos llega a estos proveedores.

Alojamiento: Microsoft Azure y Neon guardan y procesan tu información en sus servidores, por cuenta de la agencia.

Mapa: OpenStreetMap, el proveedor del mapa, recibe tu dirección IP y la zona que estás viendo cuando abres un mapa.

Direcciones: al buscar una dirección o ubicarla en el mapa, el servicio de ubicación de tu teléfono (por lo general Google en Android y Apple en iPhone) recibe el texto de la dirección o las coordenadas.

Asistente CareCar: solo si lo usas, Gemini (de Google) recibe tu pregunta (le quitamos lo que parezca correo, teléfono, placas o número de serie), el modelo y el año de tu auto y, si la mandas, la foto del tablero sin ubicación ni datos de la cámara. Google puede usar ese contenido para mejorar sus productos.

Transferencias: fuera de estos proveedores, solo compartimos tus datos con autoridades cuando la ley lo exige.

Derechos ARCO: puedes acceder, rectificar, cancelar u oponerte al uso de tus datos, o revocar tu consentimiento, escribiendo al correo de contacto de la agencia. Te responderemos en un plazo máximo de 20 días hábiles.

Seguridad: tu sesión se guarda cifrada en tu dispositivo y la comunicación con la agencia requiere autenticación.

Cambios: cualquier modificación a este aviso se publicará en esta misma sección de la app.

Última actualización: noviembre de 2026.$aviso$, now(), now());
