#!/usr/bin/env bash
# Completa los datos de ejemplo con lo que no se puede cargar por la API y lo
# escribe directo en la base:
#
#   - La contraseña de prueba (CLIENTES_CONTRASENA) de los clientes de ejemplo de
#     la app. En una API cuyo correo no llega a Mailpit (la de Azure) el código de
#     recuperación sale por Gmail a direcciones @example.com que nadie lee, y
#     cargar-datos-de-ejemplo.sh no puede definirlas por la API.
#   - Los parámetros de la toma a cuenta (sin ellos la calculadora responde 422).
#   - La imagen de los 22 modelos del catálogo (la API solo la recibe al crear el modelo).
#   - Los documentos de todos los vehículos de ejemplo.
#   - El historial de citas pasadas (atendidas, no asistió y canceladas): la API no
#     agenda en el pasado ni marca una cita como atendida.
#
# Orden para una API real (todo lo demás sí pasa por la API y sus reglas):
#
#   1. SIN_MAILPIT=1 API=<url> GERENTE_USUARIO=... ./scripts/cargar-datos-de-ejemplo.sh
#      Crea los clientes, sus vehículos y servicios y lo del personal; los pasos que
#      piden iniciar sesión como cliente se omiten y el script lo dice.
#   2. DB_URL=... DB_USERNAME=... DB_PASSWORD=... CONFIRMAR=si \
#      ./scripts/completar-datos-de-ejemplo-en-base.sh
#   3. Otra vez el paso 1: ya entran, así que se cargan sus citas, pagos y demás.
#      No duplica.
#
# En local (Mailpit y la base de docker-compose) basta cargar-datos-de-ejemplo.sh;
# este script también sirve ahí, sin variables, para los tres últimos puntos.
#
# La conexión es la misma que usa la API: DB_URL (jdbc:postgresql://host/base),
# DB_USERNAME y DB_PASSWORD; sin ellas, la base local de docker-compose. El
# usuario y la contraseña van por variables de entorno de psql, no por
# argumentos, para que no se vean en la lista de procesos. Fuera de localhost
# pide CONFIRMAR=si, porque escribe en esa base. Se puede repetir: nada se duplica.
#
# SOLO PARA DATOS DE PRUEBA: la contraseña queda publicada en el README.

set -euo pipefail

CONTRASENA="${CLIENTES_CONTRASENA:-CareCar2026!}"
# Los correos de los clientes de ejemplo (los que crea cargar-datos-de-ejemplo.sh).
CORREOS=(
  laura.mendez@example.com
  carlos.ramirez@example.com
  ana.torres@example.com
  luis.hernandez@example.com
  sofia.morales@example.com
  diego.castillo@example.com
)

DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/agencia360}"
export PGUSER="${DB_USERNAME:-agencia360}"
export PGPASSWORD="${DB_PASSWORD:-agencia360}"
URL_PSQL="${DB_URL#jdbc:}"

anfitrion="${URL_PSQL#*://}"
anfitrion="${anfitrion%%/*}"
anfitrion="${anfitrion%%:*}"

command -v psql >/dev/null 2>&1 || { echo "Falta psql (cliente de PostgreSQL)." >&2; exit 1; }

if [ "$anfitrion" != "localhost" ] && [ "$anfitrion" != "127.0.0.1" ] && [ "${CONFIRMAR:-}" != "si" ]; then
  echo "Esto escribe en la base de $anfitrion. Si es lo que quieres, repite con CONFIRMAR=si." >&2
  exit 1
fi

# Hash BCrypt de fuerza 10, el que acepta BCryptPasswordEncoder de la API. Sin
# el módulo bcrypt de Python se pide a pgcrypto de la propia base.
HASH=""
if python3 -c 'import bcrypt' >/dev/null 2>&1; then
  HASH=$(CONTRASENA="$CONTRASENA" python3 -c \
    'import bcrypt,os; print(bcrypt.hashpw(os.environ["CONTRASENA"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')
fi

lista=$(IFS=,; printf '%s' "${CORREOS[*]}")
echo "Base: $anfitrion"

# SIN_CONTRASENAS=1 deja las contraseñas como están: úsalo si los clientes ya
# entran (alguien pudo cambiarlas y no hay por qué pisarlas) y solo faltan los SQL.
if [ -n "${SIN_CONTRASENAS:-}" ]; then
  echo "Contraseñas: omitidas (SIN_CONTRASENAS=1)"
elif [ -n "$HASH" ]; then
  echo "Contraseñas de: $lista"
  psql "$URL_PSQL" -v ON_ERROR_STOP=1 -v "hash=$HASH" -v "correos=$lista" <<'SQL'
update cuenta_app c
   set contrasena_hash = :'hash',
       debe_cambiar_contrasena = false,
       intentos_fallidos = 0,
       bloqueada_hasta = null,
       activa = true,
       updated_at = now()
  from cliente k
 where k.id = c.cliente_id
   and lower(k.correo) = any (string_to_array(:'correos', ','));
select k.correo, c.activa, c.debe_cambiar_contrasena as debe_cambiarla
  from cuenta_app c join cliente k on k.id = c.cliente_id
 where lower(k.correo) = any (string_to_array(:'correos', ','))
 order by k.correo;
SQL
else
  echo "Contraseñas de: $lista (con pgcrypto)"
  psql "$URL_PSQL" -v ON_ERROR_STOP=1 -v "pw=$CONTRASENA" -v "correos=$lista" <<'SQL'
create extension if not exists pgcrypto;
update cuenta_app c
   set contrasena_hash = crypt(:'pw', gen_salt('bf', 10)),
       debe_cambiar_contrasena = false,
       intentos_fallidos = 0,
       bloqueada_hasta = null,
       activa = true,
       updated_at = now()
  from cliente k
 where k.id = c.cliente_id
   and lower(k.correo) = any (string_to_array(:'correos', ','));
select k.correo, c.activa, c.debe_cambiar_contrasena as debe_cambiarla
  from cuenta_app c join cliente k on k.id = c.cliente_id
 where lower(k.correo) = any (string_to_array(:'correos', ','))
 order by k.correo;
SQL
fi

# Lo demás: los SQL de esta carpeta, cada uno idempotente.
CARPETA="$(cd "$(dirname "$0")" && pwd)"
IMAGENES_BASE="${IMAGENES_BASE:-https://raw.githubusercontent.com/lattice-systems/agencia-360-backend/main/src/main/resources/fotos-autos}"
for archivo in parametros-toma-cuenta-de-ejemplo.sql imagenes-catalogo-de-ejemplo.sql documentos-de-ejemplo.sql citas-pasadas-de-ejemplo.sql; do
  echo "$archivo:"
  psql "$URL_PSQL" -v ON_ERROR_STOP=1 -v "base=$IMAGENES_BASE" -f "$CARPETA/$archivo" | sed 's/^/  /'
done
