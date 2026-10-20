-- Módulo ventascrm (CRM-1.2): el motivo de cierre pasa de texto libre a una
-- lista fija que decidió el PO para el Sprint 2:
--   SE_FUE_POR_OTRA_MARCA      "Se fue por otra marca"
--   PERDIO_EL_INTERES          "Perdió el interés"
--   RECHAZO_DE_FINANCIAMIENTO  "Rechazo de financiamiento"
--
-- Los cierres y solicitudes que ya existen tenían texto libre: quedan como
-- SIN_CATEGORIA y su texto original se conserva en *_anterior, que la aplicación
-- ya no usa. Así no se pierde nada mientras el PO decide si se reclasifican.

-- Prospecto
alter table prospecto rename column motivo_cierre to motivo_cierre_anterior;
alter table prospecto add column motivo_cierre varchar(30);
update prospecto set motivo_cierre = 'SIN_CATEGORIA' where motivo_cierre_anterior is not null;
alter table prospecto add constraint ck_prospecto_motivo_cierre check (motivo_cierre in
    ('SE_FUE_POR_OTRA_MARCA', 'PERDIO_EL_INTERES', 'RECHAZO_DE_FINANCIAMIENTO', 'SIN_CATEGORIA'));

-- Solicitud de cierre
alter table solicitud_cierre rename column motivo to motivo_anterior;
alter table solicitud_cierre alter column motivo_anterior drop not null;
alter table solicitud_cierre add column motivo varchar(30);
update solicitud_cierre set motivo = 'SIN_CATEGORIA';
alter table solicitud_cierre alter column motivo set not null;
alter table solicitud_cierre add constraint ck_solicitud_cierre_motivo check (motivo in
    ('SE_FUE_POR_OTRA_MARCA', 'PERDIO_EL_INTERES', 'RECHAZO_DE_FINANCIAMIENTO', 'SIN_CATEGORIA'));
