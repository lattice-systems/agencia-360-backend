-- V2-M14.04: pagar en la app un pedido de la tienda con el mismo cobro simulado
-- de las órdenes (V2-M08.02). Un pago es de una orden o de un pedido, nunca de
-- los dos. El pedido no tiene vehículo cuando se recoge en mostrador, así que el
-- vehículo solo es obligatorio en el pago de una orden. Ninguna columna de la
-- ficha se quita ni se renombra.

alter table pago_app alter column orden_servicio_id drop not null;
alter table pago_app alter column vehiculo_cliente_id drop not null;
alter table pago_app add column pedido_app_id bigint references pedido_app (id);

alter table pago_app add constraint ck_pago_app_objeto check
    ((orden_servicio_id is null) <> (pedido_app_id is null));
alter table pago_app add constraint ck_pago_app_vehiculo check
    (orden_servicio_id is null or vehiculo_cliente_id is not null);

create index ix_pago_app_pedido on pago_app (pedido_app_id, id);

create unique index uq_pago_app_pedido_pagado on pago_app (pedido_app_id) where estado = 'PAGADO';

create unique index uq_pago_app_pedido_pendiente on pago_app (pedido_app_id) where estado = 'PENDIENTE';

-- Un pedido pagado ya no vence y solo lo cancela la agencia.
alter table pedido_app add column pagado_en timestamp(6) with time zone;
