-- Parámetros de la toma a cuenta (V2-M03.06) de ejemplo, para que la calculadora
-- de la app y la valuación formal tengan con qué estimar. Solo para datos de
-- prueba: los valores reales los entrega el PO y todavía no hay API para
-- cargarlos, por eso van directo a la base.
--
-- Un parámetro por modelo Nissan del catálogo y por año de 2012 a 2027. El valor
-- base es el precio del modelo en 2026 (el del catálogo) retrocedido un 4 % por
-- año, que es lo que costaría nuevo ese año; la depreciación anual y el factor de
-- kilometraje cambian con el tipo de auto (los deportivos y los de lujo pierden
-- menos). La fórmula está en CalculadoraTomaCuenta.
--
-- Se puede correr varias veces: lo que ya existe se deja igual.
insert into parametro_toma_cuenta (marca, modelo, anio, valor_base, depreciacion_anual, factor_km, factor_estado,
                                   activo, created_at, updated_at)
select 'Nissan', m.modelo, a.anio,
       round(m.precio / power(1.04, 2026 - a.anio) / 100) * 100,
       m.depreciacion, m.factor_km, 0.0600, true, now(), now()
from (values ('March', 289900, 0.1200, 0.0140),
             ('Versa', 339900, 0.1100, 0.0130),
             ('Sentra', 449900, 0.1100, 0.0130),
             ('Kicks', 489900, 0.1000, 0.0120),
             ('X-Trail', 699900, 0.1000, 0.0110),
             ('Frontier', 899900, 0.0900, 0.0100),
             ('Leaf', 699900, 0.1400, 0.0120),
             ('Ariya', 1099900, 0.1300, 0.0110),
             ('Z', 1399900, 0.0700, 0.0080),
             ('GT-R', 3299900, 0.0500, 0.0060),
             ('Altima', 649900, 0.1100, 0.0120),
             ('Pathfinder', 1149900, 0.0900, 0.0100),
             ('Murano', 1099900, 0.1000, 0.0100),
             ('Armada', 1999900, 0.0800, 0.0090),
             ('Titan', 1299900, 0.0900, 0.0100),
             ('Urvan', 579900, 0.0900, 0.0090),
             ('Maxima', 799900, 0.1100, 0.0110),
             ('Juke', 429900, 0.1100, 0.0130),
             ('Qashqai', 599900, 0.1000, 0.0110),
             ('Patrol', 2699900, 0.0700, 0.0080),
             ('Tiida', 299900, 0.1200, 0.0140),
             ('Note', 389900, 0.1100, 0.0130)) as m(modelo, precio, depreciacion, factor_km)
         cross join generate_series(2012, 2027) as a(anio)
on conflict (lower(marca), lower(modelo), anio) do nothing;
