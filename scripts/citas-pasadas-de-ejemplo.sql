-- Historial de citas de los clientes de ejemplo: atendidas, una que no se
-- presentó y canceladas, todas en el pasado. La API no puede agendar en el
-- pasado y ninguna ruta marca una cita como atendida (eso lo hace el taller al
-- recibir el auto), así que van directo a la base. Las próximas se agendan por
-- la API de la app (cargar-datos-de-ejemplo.sh).
--
-- Las fechas son relativas a hoy (sin domingos, que el taller no abre). Qué
-- vehículo tiene cuál se reparte con su id, para que no todos se vean igual.
-- Se puede correr varias veces: no duplica.
with demo as (
    select v.id, v.cliente_id
    from vehiculo_cliente v
             join cliente k on k.id = v.cliente_id
    where lower(k.correo) in ('laura.mendez@example.com', 'carlos.ramirez@example.com', 'ana.torres@example.com',
                              'luis.hernandez@example.com', 'sofia.morales@example.com',
                              'diego.castillo@example.com')
),
     plan(n, estado, dias, motivo) as (values (1, 'ATENDIDA', 160, 'MANTENIMIENTO'),
                                              (2, 'ATENDIDA', 45, 'REVISION'),
                                              (3, 'NO_ASISTIO', 80, 'MANTENIMIENTO'),
                                              (4, 'CANCELADA', 25, 'REPARACION')),
     cita_nueva as (
         select d.id as vehiculo_id, d.cliente_id, p.estado, p.motivo,
                case when extract(isodow from current_date - p.dias) = 7 then current_date - p.dias - 1
                     else current_date - p.dias end as fecha,
                d.id % 6 + 1 as lugar
         from demo d
                  cross join plan p
         where case p.n when 1 then true when 2 then d.id % 2 = 0 when 3 then d.id % 5 = 0 else d.id % 3 = 0 end
     )
insert into cita (cliente_id, vehiculo_cliente_id, fecha, franja_id, hora_inicio, hora_fin, estado, cancelada_en,
                  motivo, created_at, updated_at)
select c.cliente_id, c.vehiculo_id, c.fecha, f.id, f.hora_inicio, f.hora_fin, c.estado,
       case when c.estado = 'CANCELADA' then (c.fecha - 2)::timestamp + time '10:00' end,
       c.motivo, (c.fecha - 7)::timestamp + time '12:00', c.fecha::timestamp + time '09:00'
from cita_nueva c
         join lateral (select x.id, x.hora_inicio, x.hora_fin
                       from (select ff.id, ff.hora_inicio, ff.hora_fin,
                                    row_number() over (order by ff.hora_inicio) as rn
                             from franja_horario ff
                             where ff.dia_semana = extract(isodow from c.fecha)) x
                       where x.rn = c.lugar) f on true
where not exists (select 1
                  from cita e
                  where e.vehiculo_cliente_id = c.vehiculo_id and e.fecha = c.fecha and e.estado = c.estado);
