-- V2-M08.06: la factura simulada de un pago de la app (ADR 0009). La ficha pide
-- la tabla factura, así que se reutiliza la del ERP: solo se agregan columnas y
-- ninguna se quita ni se renombra. Las de la venta de un auto dejan de ser
-- obligatorias y ck_factura_tipo exige las de cada tipo. El tipo por omisión es
-- VENTA_UNIDAD para que el ERP siga insertando como antes.

alter table factura add column tipo varchar(15) not null default 'VENTA_UNIDAD';
alter table factura add column cliente_id bigint references cliente (id);
alter table factura add column orden_servicio_id bigint references orden_servicio (id);
alter table factura add column pedido_app_id bigint references pedido_app (id);
alter table factura add column pago_app_id bigint references pago_app (id);
alter table factura add column perfil_fiscal_id bigint references perfil_fiscal (id) on delete set null;
alter table factura add column uuid_fiscal uuid;
alter table factura add column xml_archivo_id varchar(80);
alter table factura add column pdf_archivo_id varchar(80);
alter table factura add column timbrada_en timestamp(6) with time zone;
alter table factura add column receptor_uso_cfdi varchar(4);
alter table factura add column forma_pago varchar(2);

alter table factura alter column prospecto_id drop not null;
alter table factura alter column venta_id drop not null;
alter table factura alter column vehiculo_id drop not null;
alter table factura alter column unidad_vin drop not null;
alter table factura alter column unidad_marca drop not null;
alter table factura alter column unidad_modelo drop not null;
alter table factura alter column unidad_version drop not null;
alter table factura alter column unidad_anio drop not null;
alter table factura alter column unidad_color drop not null;
alter table factura alter column pdf drop not null;
alter table factura alter column emitida_por drop not null;

alter table factura add constraint ck_factura_tipo check (
    (tipo = 'VENTA_UNIDAD'
        and prospecto_id is not null and venta_id is not null and vehiculo_id is not null
        and unidad_vin is not null and unidad_marca is not null and unidad_modelo is not null
        and unidad_version is not null and unidad_anio is not null and unidad_color is not null
        and pdf is not null and emitida_por is not null)
    or (tipo in ('SERVICIO', 'PEDIDO_APP')
        and cliente_id is not null and pago_app_id is not null and uuid_fiscal is not null
        and xml_archivo_id is not null and pdf_archivo_id is not null and timbrada_en is not null
        and receptor_uso_cfdi is not null and forma_pago is not null
        and (tipo <> 'SERVICIO' or orden_servicio_id is not null)
        and (tipo <> 'PEDIDO_APP' or pedido_app_id is not null)));

alter table factura add constraint uq_factura_pago_app unique (pago_app_id);
alter table factura add constraint uq_factura_uuid unique (uuid_fiscal);

create index ix_factura_cliente on factura (cliente_id, id);
