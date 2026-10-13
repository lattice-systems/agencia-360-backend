#!/usr/bin/env bash
# Crea un usuario de ejemplo por rol en la API local, para probar el ERP y el CRM.
# SOLO PARA DESARROLLO: las contraseñas están en este archivo y en el README.
#
# Antes, levanta la API con el Administrador y el Gerente de marketing iniciales:
#
#   ADMIN_INICIAL_USUARIO=admin ADMIN_INICIAL_CONTRASENA=Admin12345 \
#   GERENTE_INICIAL_USUARIO=gerente GERENTE_INICIAL_CONTRASENA=Gerente12345 \
#   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
#
# Después, en otra terminal: ./scripts/usuarios-de-ejemplo.sh
# Se puede correr varias veces: los usuarios que ya existen se dejan igual.

set -euo pipefail

API="${API:-http://localhost:8080}"
ADMIN_USUARIO="${ADMIN_USUARIO:-admin}"
ADMIN_CONTRASENA="${ADMIN_CONTRASENA:-Admin12345}"
GERENTE_USUARIO="${GERENTE_USUARIO:-gerente}"
GERENTE_CONTRASENA="${GERENTE_CONTRASENA:-Gerente12345}"

# Inicia sesión y escribe el token. $1 usuario, $2 contraseña, $3 ERP o CRM.
iniciar_sesion() {
  local respuesta
  respuesta=$(curl -sS -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"usuario\":\"$1\",\"contrasena\":\"$2\",\"aplicacion\":\"$3\"}")
  local token
  token=$(printf '%s' "$respuesta" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
  if [ -z "$token" ]; then
    echo "No se pudo iniciar sesión como $1 en el $3: $respuesta" >&2
    echo "¿Levantaste la API con las variables ADMIN_INICIAL_* y GERENTE_INICIAL_*?" >&2
    exit 1
  fi
  printf '%s' "$token"
}

# Crea un usuario. $1 token, $2 usuario, $3 contraseña, $4 nombre, $5 rol.
crear_usuario() {
  local codigo
  codigo=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/api/v1/usuarios" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $1" \
    -d "{\"usuario\":\"$2\",\"contrasena\":\"$3\",\"nombre\":\"$4\",\"rol\":\"$5\"}")
  case "$codigo" in
    201) echo "  creado      $2 ($5)" ;;
    409) echo "  ya existía  $2 ($5)" ;;
    *) echo "  error $codigo al crear $2 ($5)" >&2; exit 1 ;;
  esac
}

echo "ERP (los da de alta el Administrador):"
token_admin=$(iniciar_sesion "$ADMIN_USUARIO" "$ADMIN_CONTRASENA" ERP)
crear_usuario "$token_admin" inventario1 Inventario123 "Andrea Ruiz" INVENTARIO
crear_usuario "$token_admin" refacciones1 Refacciones123 "Daniela León" REFACCIONES
crear_usuario "$token_admin" facturacion1 Facturacion123 "Sofía Méndez" FACTURACION
crear_usuario "$token_admin" servicio1 Servicio123 "Jorge Castillo" SERVICIO
crear_usuario "$token_admin" caja1 Caja12345 "Carla Cajera" CAJA
crear_usuario "$token_admin" contabilidad1 Contabilidad123 "Marco Ortiz" CONTABILIDAD

echo "CRM (los da de alta el Gerente de marketing):"
token_gerente=$(iniciar_sesion "$GERENTE_USUARIO" "$GERENTE_CONTRASENA" CRM)
crear_usuario "$token_gerente" vendedor1 Vendedor123 "Sergio Luna" VENTAS
crear_usuario "$token_gerente" vendedor2 Vendedor123 "Elena Ríos" VENTAS

echo "Listo. Las contraseñas están en el README (sección 5.6)."
