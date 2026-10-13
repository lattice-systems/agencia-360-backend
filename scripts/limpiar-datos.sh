#!/usr/bin/env bash
# Borra los datos de la base local de desarrollo (la del docker-compose.yml):
# usuarios, vehículos, refacciones, movimientos, prospectos y su historial.
# Conserva los roles y el historial de Flyway, así que no hace falta volver a
# migrar.
#
# SOLO PARA DESARROLLO. Uso: ./scripts/limpiar-datos.sh  (pide confirmación)
#                            ./scripts/limpiar-datos.sh --si  (sin preguntar)
#
# Después reinicia la API para que vuelva a crear al Administrador y al Gerente
# de marketing iniciales, y vuelve a cargar los datos con
# ./scripts/cargar-datos-de-ejemplo.sh

set -euo pipefail

cd "$(dirname "$0")/.."

if [ "${1:-}" != "--si" ]; then
  read -r -p "Esto borra todos los datos de la base local de desarrollo. ¿Continuar? (s/N) " respuesta
  if [ "$respuesta" != s ] && [ "$respuesta" != S ]; then
    echo "No se borró nada."
    exit 0
  fi
fi

docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -q -U agencia360 -d agencia360 -c \
  "TRUNCATE TABLE historial_etapa, prospecto, movimiento_inventario, vehiculo, refaccion, usuario,
   event_publication RESTART IDENTITY CASCADE;"

echo "Listo: la base quedó sin datos (solo los roles)."
echo "Reinicia la API (con ADMIN_INICIAL_* y GERENTE_INICIAL_*) y vuelve a cargar los datos:"
echo "  ./scripts/cargar-datos-de-ejemplo.sh"
