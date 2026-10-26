-- Módulo ventascrm (ERP-1.3): datos fiscales del cliente que recibe la factura.
--
-- Decisión del PO del 21 de octubre: el vendedor captura RFC, nombre completo,
-- domicilio fiscal y régimen fiscal al pasar el prospecto a Pago, y el
-- prospecto no llega a Facturación sin ellos. Los prospectos que ya estaban en
-- Pago quedan sin datos: el vendedor los captura antes de que Contabilidad
-- valide los pagos. regimen_fiscal guarda la clave del catálogo del SAT.
--
-- V15 es de Ricardo y V16 de Pablito (Sprint 3); Flyway acepta el salto.

alter table prospecto add column rfc varchar(13);
alter table prospecto add column nombre_fiscal varchar(200);
alter table prospecto add column domicilio_fiscal varchar(250);
alter table prospecto add column regimen_fiscal varchar(3);

-- Se capturan los cuatro juntos o ninguno.
alter table prospecto add constraint ck_prospecto_datos_fiscales check (
    (rfc is null and nombre_fiscal is null and domicilio_fiscal is null and regimen_fiscal is null)
    or (rfc is not null and nombre_fiscal is not null and domicilio_fiscal is not null and regimen_fiscal is not null)
);
