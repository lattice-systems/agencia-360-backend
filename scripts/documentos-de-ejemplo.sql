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
