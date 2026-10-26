-- Módulo ventascrm (ERP-1.3): datos fiscales del cliente que recibe la factura.
--
-- Decisión del PO del 21 de octubre: el vendedor captura RFC, nombre completo,
-- domicilio fiscal y régimen fiscal al pasar el prospecto a Pago, y el
-- prospecto no llega a Facturación sin ellos. El 26 de octubre pidió el
-- domicilio separado en estado, ciudad, colonia, calle y número, y código
-- postal. Los prospectos que ya estaban en Pago quedan sin datos: el vendedor
-- los captura antes de que Contabilidad valide los pagos. regimen_fiscal guarda
-- la clave del catálogo del SAT.
--
-- Los largos del domicilio caben en una línea de hasta 300 caracteres, lo que
-- guarda la factura (V16) como domicilio del receptor.

alter table prospecto add column rfc varchar(13);
alter table prospecto add column nombre_fiscal varchar(200);
alter table prospecto add column fiscal_estado varchar(40);
alter table prospecto add column fiscal_ciudad varchar(60);
alter table prospecto add column fiscal_colonia varchar(80);
alter table prospecto add column fiscal_calle_numero varchar(90);
alter table prospecto add column fiscal_codigo_postal varchar(5);
alter table prospecto add column regimen_fiscal varchar(3);

-- Se capturan todos juntos o ninguno.
alter table prospecto add constraint ck_prospecto_datos_fiscales check (
    (rfc is null and nombre_fiscal is null and fiscal_estado is null and fiscal_ciudad is null
        and fiscal_colonia is null and fiscal_calle_numero is null and fiscal_codigo_postal is null
        and regimen_fiscal is null)
    or (rfc is not null and nombre_fiscal is not null and fiscal_estado is not null and fiscal_ciudad is not null
        and fiscal_colonia is not null and fiscal_calle_numero is not null and fiscal_codigo_postal is not null
        and regimen_fiscal is not null)
);
