-- V2-M05.01 · Planes de mantenimiento de ejemplo para los modelos de la
-- agencia (Nissan). Tres servicios típicos por modelo: 10 000 km o 6 meses,
-- 20 000 km o 12 meses y 40 000 km o 24 meses. Los precios son una estimación
-- no vinculante que la agencia ajusta después.
insert into plan_mantenimiento (modelo, anio_desde, anio_hasta, km, meses, nombre, precio_estimado)
select m.modelo, 2015, 2035, s.km, s.meses, s.nombre, round(s.base * m.factor, 2)
from (values ('March', 1.00), ('Versa', 1.10), ('Sentra', 1.20), ('Kicks', 1.25), ('V-Drive', 1.05),
             ('X-Trail', 1.45), ('NP300', 1.35)) as m (modelo, factor)
cross join (values (10000, 6, 'Servicio de 10,000 km', 1800.00), (20000, 12, 'Servicio de 20,000 km', 2900.00),
                   (40000, 24, 'Servicio de 40,000 km', 4800.00)) as s (km, meses, nombre, base);

insert into concepto_mantenimiento (plan_mantenimiento_id, concepto)
select p.id, c.concepto
from plan_mantenimiento p
join (values (10000, 'Cambio de aceite y filtro de aceite'), (10000, 'Rotación y revisión de llantas'),
             (10000, 'Revisión de frenos y niveles'),
             (20000, 'Cambio de aceite y filtro de aceite'), (20000, 'Cambio de filtro de aire'),
             (20000, 'Cambio de filtro de cabina'), (20000, 'Revisión de frenos y suspensión'),
             (40000, 'Cambio de aceite y filtro de aceite'), (40000, 'Cambio de bujías'),
             (40000, 'Cambio de filtro de combustible'), (40000, 'Cambio de líquido de frenos'),
             (40000, 'Servicio de transmisión')) as c (km, concepto) on c.km = p.km;
