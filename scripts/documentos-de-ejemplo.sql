-- Documentos de ejemplo del vehículo (V2-M09.01). Solo para desarrollo.
--
-- La migración V61 los siembra si los vehículos ya existen al migrar; en una
-- base nueva no existen todavía, así que esto los repite después de
-- scripts/cargar-datos-de-ejemplo.sh. Se puede correr varias veces: no
-- duplica. Las fechas son relativas a hoy para que siempre haya uno por
-- vencer, uno vencido, uno vigente y uno sin fecha.
--
--   docker compose exec -T postgres psql -U agencia360 -d agencia360 < scripts/documentos-de-ejemplo.sql
insert into documento_vehiculo (vehiculo_cliente_id, tipo, numero, emitido_en, vence_en, origen, aseguradora,
                                telefono_aseguradora)
select v.id, 'POLIZA', 'POL-501-2026', current_date - 335, current_date + 30, 'CLIENTE', 'Seguros Ejemplo',
       '+52 800 000 1234'
from vehiculo_cliente v
where v.placas = 'GTO-501-S'
  and not exists (select 1 from documento_vehiculo d where d.vehiculo_cliente_id = v.id and d.tipo = 'POLIZA');

insert into documento_vehiculo (vehiculo_cliente_id, tipo, numero, emitido_en, vence_en, origen)
select v.id, 'TARJETA_CIRCULACION', 'TC-GTO-501-S', current_date - 200, current_date + 530, 'CLIENTE'
from vehiculo_cliente v
where v.placas = 'GTO-501-S'
  and not exists (select 1 from documento_vehiculo d
                  where d.vehiculo_cliente_id = v.id and d.tipo = 'TARJETA_CIRCULACION');

insert into documento_vehiculo (vehiculo_cliente_id, tipo, numero, emitido_en, vence_en, origen)
select v.id, 'VERIFICACION', 'VER-0501', current_date - 400, current_date - 35, 'CLIENTE'
from vehiculo_cliente v
where v.placas = 'GTO-501-S'
  and not exists (select 1 from documento_vehiculo d where d.vehiculo_cliente_id = v.id and d.tipo = 'VERIFICACION');

insert into documento_vehiculo (vehiculo_cliente_id, tipo, numero, emitido_en, origen)
select v.id, 'FACTURA', 'A-1042', current_date - 120, 'AGENCIA'
from vehiculo_cliente v
where v.placas = 'GTO-501-S'
  and not exists (select 1 from documento_vehiculo d where d.vehiculo_cliente_id = v.id and d.tipo = 'FACTURA');

insert into documento_vehiculo (vehiculo_cliente_id, tipo, numero, emitido_en, vence_en, origen)
select v.id, 'TARJETA_CIRCULACION', 'TC-GTO-218-T', current_date - 90, current_date + 640, 'CLIENTE'
from vehiculo_cliente v
where v.placas = 'GTO-218-T'
  and not exists (select 1 from documento_vehiculo d
                  where d.vehiculo_cliente_id = v.id and d.tipo = 'TARJETA_CIRCULACION');

-- Documentos para todos los vehículos de los clientes de ejemplo (placas ya
-- capturadas), con los cuatro estados repartidos de forma fija según el id del
-- vehículo: vigente, por vencer (menos de 30 días), vencido y sin fecha. Va
-- después de los de arriba, que son de Diego, y no duplica: solo agrega el tipo
-- que el vehículo no tenga.
with demo as (
    select v.id::int as id, v.anio, v.placas
    from vehiculo_cliente v
             join cliente k on k.id = v.cliente_id
    where v.placas is not null
      and lower(k.correo) in ('laura.mendez@example.com', 'carlos.ramirez@example.com', 'ana.torres@example.com',
                              'luis.hernandez@example.com', 'sofia.morales@example.com',
                              'diego.castillo@example.com')
)
insert into documento_vehiculo (vehiculo_cliente_id, tipo, numero, emitido_en, vence_en, origen, aseguradora,
                                telefono_aseguradora)
select d.id, x.tipo, x.numero, x.emitido, x.vence, x.origen, x.aseguradora, x.telefono
from demo d
         cross join lateral (
    -- Tarjeta de circulación: vigente en todos.
    select 'TARJETA_CIRCULACION' as tipo, 'TC-' || d.placas as numero, current_date - (60 + d.id * 23 % 500) as emitido,
           current_date + (120 + d.id * 41 % 900) as vence, 'CLIENTE' as origen, null::varchar as aseguradora,
           null::varchar as telefono
    union all
    -- Póliza: vigente, por vencer, vencida o ninguna según el id.
    select 'POLIZA', 'POL-' || d.placas,
           case d.id % 4 when 0 then current_date - 165 when 1 then current_date - 345 else current_date - 377 end,
           case d.id % 4 when 0 then current_date + 200 when 1 then current_date + 20 else current_date - 12 end,
           'CLIENTE',
           (array ['Seguros Ejemplo', 'Qualitas Demo', 'GNP Ejemplo', 'AXA Demo'])[d.id % 4 + 1],
           '+52 800 000 12' || lpad((d.id % 90 + 10)::text, 2, '0')
    where d.id % 4 <> 3
    union all
    -- Verificación: vigente, por vencer o vencida.
    select 'VERIFICACION', 'VER-' || lpad((d.id * 17 % 9000 + 1000)::text, 4, '0'),
           case d.id % 3 when 0 then current_date - 215 when 1 then current_date - 350 else current_date - 405 end,
           case d.id % 3 when 0 then current_date + 150 when 1 then current_date + 15 else current_date - 40 end,
           'CLIENTE', null, null
    union all
    -- Tenencia: solo en los de id par.
    select 'TENENCIA', 'TEN-' || d.placas, current_date - 100, current_date + 250, 'CLIENTE', null, null
    where d.id % 2 = 0
    union all
    -- Factura de compra, de la agencia y sin vencimiento, en los de 2023 en adelante.
    select 'FACTURA', 'A-' || (1000 + d.id), current_date - (30 + d.id * 13 % 400), null, 'AGENCIA', null, null
    where d.anio >= 2023
    ) x
where not exists (select 1 from documento_vehiculo e where e.vehiculo_cliente_id = d.id and e.tipo = x.tipo);
