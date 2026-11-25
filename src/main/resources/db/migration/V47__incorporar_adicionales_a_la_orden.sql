alter table trabajo_adicional add column incorporado_en timestamp(6) with time zone;
alter table trabajo_adicional add constraint ck_trabajo_adicional_incorporado
    check (incorporado_en is null or estado = 'APROBADO');
