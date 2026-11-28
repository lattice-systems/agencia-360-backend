-- Galería y ficha resumida de los 22 modelos del catálogo (V2-M03.02): llena
-- modelo_catalogo_imagen y modelo_catalogo_especificacion, que la API de la app
-- devuelve como `imagenes` y `especificaciones` en GET /api/v1/app/catalogo/{id}.
-- No hay endpoint para capturarlas (el ERP y el CRM web están congelados), así
-- que van directo a la base.
--
-- La galería tiene la foto de portada (modelo_catalogo.imagen_url) como única
-- imagen. La ficha son datos aproximados de ejemplo, no una ficha oficial.
--
-- Solo llena los modelos que aún no tienen galería o ficha: no pisa lo que alguien
-- ya capturó. Se puede correr varias veces.
insert into modelo_catalogo_imagen (modelo_catalogo_id, url, color, descripcion, orden, created_at, updated_at)
select m.id, m.imagen_url, null, left('Nissan ' || m.modelo || ' ' || m.anio || ', vista frontal', 120), 0, now(), now()
  from modelo_catalogo m
 where m.imagen_url is not null
   and lower(m.marca) = 'nissan'
   and not exists (select 1 from modelo_catalogo_imagen i where i.modelo_catalogo_id = m.id);

insert into modelo_catalogo_especificacion
       (modelo_catalogo_id, etiqueta, valor, orden, created_at, updated_at)
select m.id, e.etiqueta, e.valor, e.orden, now(), now()
  from modelo_catalogo m
  join (
    select f.modelo, x.etiqueta, x.valor, x.orden
      from (values
        ('March',      '1.6 L, 4 cilindros',            'Manual de 5 velocidades o CVT', '106 hp',   'Gasolina',   '5'),
        ('Versa',      '1.6 L, 4 cilindros',            'Xtronic CVT',                   '118 hp',   'Gasolina',   '5'),
        ('Sentra',     '2.0 L, 4 cilindros',            'Xtronic CVT',                   '149 hp',   'Gasolina',   '5'),
        ('Kicks',      '1.6 L, 4 cilindros',            'Xtronic CVT',                   '122 hp',   'Gasolina',   '5'),
        ('X-Trail',    '2.5 L, 4 cilindros',            'Xtronic CVT',                   '181 hp',   'Gasolina',   '5 o 7'),
        ('Frontier',   '2.5 L, 4 cilindros',            'Automática de 7 velocidades',   '161 hp',   'Gasolina',   '5'),
        ('Leaf',       'Motor eléctrico',               'Un solo cambio',                '214 hp',   'Eléctrico, autonomía de unos 385 km', '5'),
        ('Ariya',      'Motor eléctrico',               'Un solo cambio',                '238 hp',   'Eléctrico, autonomía de unos 400 km', '5'),
        ('Z',          '3.0 L, V6 biturbo',             'Automática de 9 velocidades',   '400 hp',   'Gasolina',   '2'),
        ('GT-R',       '3.8 L, V6 biturbo',             'Doble embrague de 6 velocidades', '565 hp', 'Gasolina',   '4'),
        ('Altima',     '2.5 L, 4 cilindros',            'Xtronic CVT',                   '188 hp',   'Gasolina',   '5'),
        ('Pathfinder', '3.5 L, V6',                     'Automática de 9 velocidades',   '284 hp',   'Gasolina',   '7'),
        ('Murano',     '3.5 L, V6',                     'Xtronic CVT',                   '260 hp',   'Gasolina',   '5'),
        ('Armada',     '5.6 L, V8',                     'Automática de 7 velocidades',   '400 hp',   'Gasolina',   '8'),
        ('Titan',      '5.6 L, V8',                     'Automática de 9 velocidades',   '400 hp',   'Gasolina',   '5'),
        ('Urvan',      '2.5 L, 4 cilindros',            'Manual de 5 velocidades',       '129 hp',   'Gasolina',   '3'),
        ('Maxima',     '3.5 L, V6',                     'Xtronic CVT',                   '300 hp',   'Gasolina',   '5'),
        ('Juke',       '1.6 L, 4 cilindros',            'Xtronic CVT',                   '117 hp',   'Gasolina',   '5'),
        ('Qashqai',    '1.3 L, turbo con sistema híbrido ligero', 'Xtronic CVT',         '158 hp',   'Gasolina',   '5'),
        ('Patrol',     '5.6 L, V8',                     'Automática de 7 velocidades',   '400 hp',   'Gasolina',   '8'),
        ('Tiida',      '1.8 L, 4 cilindros',            'Xtronic CVT',                   '125 hp',   'Gasolina',   '5'),
        ('Note',       '1.2 L, 3 cilindros como generador y motor eléctrico', 'Un solo cambio', '134 hp', 'e-Power: híbrido que se mueve siempre con motor eléctrico', '5')
      ) as f(modelo, motor, transmision, potencia, combustible, asientos)
      cross join lateral (values
        ('Motor', f.motor, 0), ('Transmisión', f.transmision, 1), ('Potencia', f.potencia, 2),
        ('Combustible', f.combustible, 3), ('Asientos', f.asientos, 4)
      ) as x(etiqueta, valor, orden)
  ) e on lower(e.modelo) = lower(m.modelo)
 where lower(m.marca) = 'nissan'
   and not exists (select 1 from modelo_catalogo_especificacion s where s.modelo_catalogo_id = m.id);
