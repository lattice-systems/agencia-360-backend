-- Imagen de los 22 modelos Nissan del catálogo (APP-4.1): llena `imagen_url`, que
-- la API de la app devuelve como `imagenUrl`. La API solo recibe la imagen al
-- crear el modelo (POST /api/v1/catalogo); en una base que ya los tiene no hay
-- cómo cambiarla, así que va directo a la base.
--
-- Son fotos de Wikimedia Commons con licencia libre (autor y licencia en
-- carecar-mobile/assets/autos/ATRIBUCIONES.md), las mismas que usa la app y que
-- el backend guarda en src/main/resources/fotos-autos para los PDF. Se sirven
-- desde este repo en GitHub, que es público. Otra carpeta pública con los mismos
-- nombres (<clave>.jpg) se pasa con IMAGENES_BASE al correr
-- completar-datos-de-ejemplo-en-base.sh.
--
-- Solo llena los modelos sin imagen: no pisa una que alguien ya puso. Se puede
-- correr varias veces.
update modelo_catalogo m
   set imagen_url = :'base' || '/' || f.clave || '.jpg',
       updated_at = now()
  from (values ('March', 'march'), ('Versa', 'versa'), ('Sentra', 'sentra'), ('Kicks', 'kicks'),
               ('X-Trail', 'xtrail'), ('Frontier', 'frontier'), ('Leaf', 'leaf'), ('Ariya', 'ariya'),
               ('Z', 'z'), ('GT-R', 'gtr'), ('Altima', 'altima'), ('Pathfinder', 'pathfinder'),
               ('Murano', 'murano'), ('Armada', 'armada'), ('Titan', 'titan'), ('Urvan', 'urvan'),
               ('Maxima', 'maxima'), ('Juke', 'juke'), ('Qashqai', 'qashqai'), ('Patrol', 'patrol'),
               ('Tiida', 'tiida'), ('Note', 'note')) as f(modelo, clave)
 where lower(m.marca) = 'nissan'
   and lower(m.modelo) = lower(f.modelo)
   and (m.imagen_url is null or m.imagen_url = '');
