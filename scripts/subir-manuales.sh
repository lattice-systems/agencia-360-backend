#!/usr/bin/env bash
# Sube (o vuelve a subir) solo los manuales de propietario de ejemplo, sin tocar
# el resto de los datos. Sirve para reponer los PDF en un servidor ya cargado,
# por ejemplo el de Azure. La API reemplaza el manual de la misma marca, modelo
# y año, así que repetirlo no duplica nada.
#
# Uso (las credenciales del Gerente de marketing van por variables de entorno,
# nunca en la línea de comandos):
#   API=https://<tu-api> GERENTE_USUARIO=gerente GERENTE_CONTRASENA=... scripts/subir-manuales.sh
#
# Los PDF se generan con scripts/generar-manuales.py (python3 + Google Chrome) si
# no existen en MANUALES_DIR.
set -euo pipefail

API="${API:-http://localhost:8080}"
: "${GERENTE_USUARIO:?Falta GERENTE_USUARIO}"
: "${GERENTE_CONTRASENA:?Falta GERENTE_CONTRASENA}"
DIR_SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANUALES_DIR="${MANUALES_DIR:-$DIR_SCRIPT/manuales-generados}"

if [ ! -f "$MANUALES_DIR/indice.txt" ]; then
  echo "Generando los PDF en $MANUALES_DIR ..."
  python3 "$DIR_SCRIPT/generar-manuales.py" --salida "$MANUALES_DIR" >/dev/null
fi

CUERPO=$(python3 -c 'import json,os;print(json.dumps({"usuario":os.environ["GERENTE_USUARIO"],"contrasena":os.environ["GERENTE_CONTRASENA"],"aplicacion":"CRM"}))')
TOKEN=$(curl -sS --max-time 120 -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' -d "$CUERPO" \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "No se pudo iniciar sesión como Gerente de marketing en $API" >&2; exit 1; }

SUBIDOS=0; FALLIDOS=0
while IFS='|' read -r modelo anio archivo; do
  [ -n "$modelo" ] || continue
  CODIGO=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 120 -X POST "$API/api/v1/manuales" \
    -H "Authorization: Bearer $TOKEN" -F marca=Nissan -F "modelo=$modelo" -F "anio=$anio" \
    -F "archivo=@$MANUALES_DIR/$archivo;type=application/pdf;filename=$archivo")
  if [ "$CODIGO" = 200 ] || [ "$CODIGO" = 409 ]; then
    SUBIDOS=$((SUBIDOS + 1))
  else
    FALLIDOS=$((FALLIDOS + 1)); echo "  falló $modelo $anio (HTTP $CODIGO)" >&2
  fi
done <"$MANUALES_DIR/indice.txt"
echo "Manuales subidos: $SUBIDOS · fallidos: $FALLIDOS"
[ "$FALLIDOS" = 0 ]
