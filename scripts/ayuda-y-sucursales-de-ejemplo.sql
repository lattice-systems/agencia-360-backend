-- Centro de ayuda y sucursales de ejemplo (V2-M27): la migración V65 trae solo
-- dos preguntas y la V59 una sucursal, y la API no tiene cómo agregar más desde
-- aquí, así que van directo a la base. Las respuestas describen lo que hace la
-- app hoy. Los datos de las sucursales (direcciones, teléfonos, coordenadas) son
-- de ejemplo, como los de la primera: la agencia los reemplaza por los reales.
--
-- Se puede correr varias veces: lo que ya existe (por pregunta o por nombre de
-- sucursal) se deja igual.
insert into pregunta_frecuente (tema, pregunta, respuesta, orden)
select v.tema, v.pregunta, v.respuesta, v.orden
from (values
    ('Cuenta', '¿Qué hago si olvidé mi contraseña?',
     'En la pantalla de acceso toca ¿Olvidaste tu contraseña?, escribe tu correo y te enviamos un código de 6 dígitos para elegir una contraseña nueva.', 3),
    ('Citas', '¿Cómo agendo una cita de servicio?',
     'En Mis citas toca Agendar cita, elige el vehículo, el día, un horario con cupo y el motivo. Al confirmar recibes un comprobante con código QR.', 4),
    ('Citas', '¿Puedo cancelar una cita?',
     'Sí, desde Mis citas, hasta 24 horas antes. Si falta menos tiempo, comunícate con la agencia.', 5),
    ('Taller', '¿Cómo veo el avance de mi auto en el taller?',
     'En Inicio aparece la tarjeta del taller con la etapa y la hora estimada de entrega. Tócala para ver la línea de tiempo, la inspección y los trabajos.', 6),
    ('Taller', '¿Qué es un trabajo adicional?',
     'Si el taller encuentra algo más que conviene reparar, te lo propone con su costo y tú lo apruebas o lo rechazas desde la app, con tu firma.', 7),
    ('Pagos', '¿Cómo pago mi servicio desde la app?',
     'Cuando tu auto está listo, abre la orden y paga con tarjeta o con la billetera de tu teléfono. Puedes guardar la tarjeta para la próxima vez.', 8),
    ('Pagos', '¿Cómo pido mi factura?',
     'Guarda tus datos fiscales en Pagos y toca Facturar en el pago. El PDF y el XML quedan disponibles en la app.', 9),
    ('Lealtad', '¿Cómo uso mis puntos?',
     'Los puntos se suman con tus servicios pagados. En Lealtad ves tu saldo y tus movimientos, canjeas recompensas y puedes referir a un conocido.', 10),
    ('Vehículo', '¿Dónde veo la garantía y los documentos de mi auto?',
     'En el detalle del vehículo abre Garantía para ver qué cubre y cuándo vence, y Documentos para tu póliza, tarjeta de circulación y verificación, marcados como vigentes, por vencer o vencidos.', 11),
    ('Asistencia', '¿Qué hago si mi auto se descompone en el camino?',
     'En Asistencia pide ayuda y comparte tu ubicación: la agencia da seguimiento a tu solicitud. Si hay peligro, llama primero al 911.', 12),
    ('Privacidad', '¿Cómo pido mis datos o que los borren?',
     'En Ajustes, Privacidad y mis datos, puedes ejercer tus derechos de acceso, rectificación, cancelación y oposición (ARCO).', 13)
) as v(tema, pregunta, respuesta, orden)
where not exists (select 1 from pregunta_frecuente p where p.pregunta = v.pregunta);

insert into sucursal (nombre, direccion, lat, lng, telefono, correo, horarios, orden)
select v.nombre, v.direccion, v.lat, v.lng, v.telefono, v.correo, v.horarios::jsonb, v.orden
from (values
    ('CareCar Plaza Mayor', 'Blvd. Ejemplo 450, Col. Plaza Mayor, 37500 León, Gto.', 21.152300::numeric, -101.649800::numeric,
     '+52 477 000 0001', 'plazamayor@agencia.example.com',
     '[{"departamento":"VENTAS","dias":[1,2,3,4,5,6,7],"abre":"10:00","cierra":"20:00"},
       {"departamento":"SERVICIO","dias":[1,2,3,4,5],"abre":"08:00","cierra":"18:00"},
       {"departamento":"REFACCIONES","dias":[1,2,3,4,5],"abre":"09:00","cierra":"17:00"}]', 2),
    ('CareCar Silao', 'Carretera Silao-Guanajuato km 5, Col. Ejemplo, 36100 Silao, Gto.', 20.943700::numeric, -101.428000::numeric,
     '+52 472 000 0002', 'silao@agencia.example.com',
     '[{"departamento":"VENTAS","dias":[1,2,3,4,5,6],"abre":"09:00","cierra":"18:00"},
       {"departamento":"SERVICIO","dias":[1,2,3,4,5],"abre":"08:00","cierra":"17:00"},
       {"departamento":"SERVICIO","dias":[6],"abre":"08:00","cierra":"13:00"}]', 3)
) as v(nombre, direccion, lat, lng, telefono, correo, horarios, orden)
where not exists (select 1 from sucursal s where s.nombre = v.nombre);
