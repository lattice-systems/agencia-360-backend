-- Módulo ventascrm (ERP-1.1): precio total de la venta.
--
-- Decisión del PO del 19 de octubre: el vendedor captura el precio total al
-- pasar el prospecto a Pago, y Contabilidad lo puede corregir al validar los
-- pagos (lo revisa contra los catálogos). Con él se valida que los pagos cubran
-- la venta. Los prospectos que ya estaban en Pago quedan sin precio hasta que
-- Contabilidad lo capture al validar.

alter table prospecto add column precio_total numeric(12, 2);
alter table prospecto add constraint ck_prospecto_precio_total check (precio_total > 0);
