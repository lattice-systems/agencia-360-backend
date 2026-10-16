-- CRM-0.2: Contabilidad valida los pagos desde el ERP (ERP-1.1) y no entra al
-- CRM. El Gerente de marketing deja de poder asignar este rol.

update rol
   set acceso_crm  = false,
       descripcion = 'Valida los pagos de cada venta, autoriza o rechaza pagos y consulta el balance'
 where nombre = 'CONTABILIDAD';
