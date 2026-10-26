#!/usr/bin/env bash
# Carga los datos de ejemplo de TEC-3 en la API local: usuarios por rol,
# vehículos, refacciones con sus recepciones y prospectos en todas las etapas.
# Los datos y lo que demuestra cada uno están en docs/datos-de-ejemplo.md.
#
# SOLO PARA DESARROLLO: las contraseñas están en este archivo y en el README.
#
# Carga todo por la API, como lo haría una persona desde el ERP y el CRM, así
# que pasa por las mismas reglas de negocio y deja movimientos e historial.
#
# Antes, levanta la API con el Administrador y el Gerente de marketing iniciales:
#
#   ADMIN_INICIAL_USUARIO=admin ADMIN_INICIAL_CONTRASENA=Admin12345 \
#   GERENTE_INICIAL_USUARIO=gerente GERENTE_INICIAL_CONTRASENA=Gerente12345 \
#   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
#
# Después, en otra terminal: ./scripts/cargar-datos-de-ejemplo.sh
#
# Se puede correr varias veces: lo que ya existe se deja igual y no se duplica.
# Para empezar de cero: ./scripts/limpiar-datos.sh

set -euo pipefail

API="${API:-http://localhost:8080}"
ADMIN_USUARIO="${ADMIN_USUARIO:-admin}"
ADMIN_CONTRASENA="${ADMIN_CONTRASENA:-Admin12345}"
GERENTE_USUARIO="${GERENTE_USUARIO:-gerente}"
GERENTE_CONTRASENA="${GERENTE_CONTRASENA:-Gerente12345}"

RESPUESTA_TMP=$(mktemp)
trap 'rm -f "$RESPUESTA_TMP"' EXIT

# Hace una petición a la API. Deja el código HTTP en CODIGO y el cuerpo en
# RESPUESTA. $1 método, $2 ruta, $3 token (vacío si no hay), $4 cuerpo JSON.
pedir() {
  local argumentos=(-sS -o "$RESPUESTA_TMP" -w '%{http_code}' -X "$1" "$API$2" -H 'Accept: application/json')
  if [ -n "$3" ]; then
    argumentos+=(-H "Authorization: Bearer $3")
  fi
  if [ $# -ge 4 ]; then
    argumentos+=(-H 'Content-Type: application/json' -d "$4")
  fi
  if ! CODIGO=$(curl "${argumentos[@]}"); then
    echo "No hubo respuesta de la API en $API. ¿Está corriendo?" >&2
    exit 1
  fi
  RESPUESTA=$(cat "$RESPUESTA_TMP")
}

# Termina el script mostrando la respuesta de la API.
fallar() {
  echo "  error $CODIGO al $1: $RESPUESTA" >&2
  exit 1
}

# Id del recurso que acaba de responder la API (las vistas empiezan con "id").
id_de_la_respuesta() {
  printf '%s' "$RESPUESTA" | sed -n 's/^{"id":\([0-9][0-9]*\).*/\1/p'
}

# Valor JSON de un texto opcional: null si viene vacío.
texto_o_null() {
  if [ -z "$1" ]; then printf 'null'; else printf '"%s"' "$1"; fi
}

# Fecha y hora de hace $1 días, en ISO 8601 (GNU date o BSD/macOS date).
hace_dias() {
  date -u -d "$1 days ago" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v-"$1"d +%Y-%m-%dT%H:%M:%SZ
}

# Inicia sesión y escribe el token. $1 usuario, $2 contraseña, $3 ERP o CRM.
iniciar_sesion() {
  pedir POST /api/v1/auth/login "" "{\"usuario\":\"$1\",\"contrasena\":\"$2\",\"aplicacion\":\"$3\"}"
  local token
  token=$(printf '%s' "$RESPUESTA" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
  if [ -z "$token" ]; then
    echo "No se pudo iniciar sesión como $1 en el $3: $RESPUESTA" >&2
    echo "¿Levantaste la API con las variables ADMIN_INICIAL_* y GERENTE_INICIAL_*?" >&2
    exit 1
  fi
  printf '%s' "$token"
}

# $1 token, $2 usuario, $3 contraseña, $4 nombre, $5 rol.
crear_usuario() {
  pedir POST /api/v1/usuarios "$1" \
    "{\"usuario\":\"$2\",\"contrasena\":\"$3\",\"nombre\":\"$4\",\"rol\":\"$5\"}"
  case "$CODIGO" in
    201) echo "  creado      $2 ($5)" ;;
    409) echo "  ya existía  $2 ($5)" ;;
    *) fallar "crear el usuario $2" ;;
  esac
}

# $1 VIN, $2 modelo, $3 versión, $4 año, $5 color, $6 precio, $7 estatus.
crear_vehiculo() {
  pedir POST /api/v1/vehiculos "$TOKEN_INVENTARIO" \
    "{\"vin\":\"$1\",\"modelo\":\"$2\",\"version\":\"$3\",\"anio\":$4,\"color\":\"$5\",\"precio\":$6,\"estatus\":\"$7\"}"
  case "$CODIGO" in
    201) echo "  creado      $2 $3 ($1)" ;;
    409) echo "  ya existía  $2 $3 ($1)" ;;
    *) fallar "crear el vehículo $1" ;;
  esac
}

# Da de alta una refacción y deja en REFACCION_ID el id al que hay que agregarle
# sus recepciones. Si ya existía y ya tiene recepciones, lo deja vacío para no
# duplicar su existencia; si existía sin recepciones (por ejemplo, porque una
# carga anterior se cortó), se las agrega.
# $1 código, $2 descripción, $3 costo, $4 precio, $5 mínimo.
crear_refaccion() {
  REFACCION_ID=""
  pedir POST /api/v1/refacciones "$TOKEN_INVENTARIO" \
    "{\"codigo\":\"$1\",\"descripcion\":\"$2\",\"costo\":$3,\"precio\":$4,\"minimo\":$5}"
  case "$CODIGO" in
    201)
      REFACCION_ID=$(id_de_la_respuesta)
      echo "  creada      $1 $2"
      ;;
    409)
      pedir GET /api/v1/refacciones "$TOKEN_INVENTARIO"
      local id
      id=$(sed -n "s/.*{\"id\":\([0-9][0-9]*\),\"codigo\":\"$1\".*/\1/p" <<<"$RESPUESTA")
      [ -n "$id" ] || fallar "encontrar la refacción $1"
      pedir GET "/api/v1/refacciones/$id/recepciones" "$TOKEN_INVENTARIO"
      if [ "$RESPUESTA" = "[]" ]; then
        REFACCION_ID="$id"
        echo "  ya existía  $1 $2 (sin recepciones: se le agregan)"
      else
        echo "  ya existía  $1 $2"
      fi
      ;;
    *) fallar "crear la refacción $1" ;;
  esac
}

# Registra una entrega de la refacción recién creada. $1 cantidad, $2 proveedor,
# $3 notas (opcional), $4 fecha de llegada en ISO 8601 (opcional).
recibir() {
  if [ -z "$REFACCION_ID" ]; then
    return
  fi
  local notas="${3:-}" llegada="${4:-}"
  pedir POST "/api/v1/refacciones/$REFACCION_ID/recepciones" "$TOKEN_INVENTARIO" \
    "{\"cantidad\":$1,\"proveedor\":\"$2\",\"notas\":$(texto_o_null "$notas"),\"fechaLlegada\":$(texto_o_null "$llegada")}"
  [ "$CODIGO" = 201 ] || fallar "registrar la recepción"
  echo "              + $1 de $2${llegada:+ (llegó antes de registrarse)}"
}

# Registra un prospecto y el vendedor lo avanza por sus etapas. Deja su id en PROSPECTO_ID,
# o vacío si ya existía. Los nombres se buscan en la lista del Gerente de
# marketing, que ve todos.
# $1 token del vendedor, $2 nombre, $3 teléfono, $4 correo, $5 etapas separadas
# por espacios (sin Primer contacto), $6 motivo de cierre si termina en CERRADO.
crear_prospecto() {
  PROSPECTO_ID=""
  # grep con <<< y no con printf | grep -q: con pipefail, grep -q cierra la
  # tubería al encontrar el texto y printf fallaría con SIGPIPE.
  if grep -q "\"nombre\":\"$2\"" <<<"$PROSPECTOS_EXISTENTES"; then
    echo "  ya existía  $2"
    return
  fi
  pedir POST /api/v1/prospectos "$1" \
    "{\"nombre\":\"$2\",\"telefono\":$(texto_o_null "$3"),\"correo\":$(texto_o_null "$4")}"
  [ "$CODIGO" = 201 ] || fallar "registrar el prospecto $2"
  PROSPECTO_ID=$(id_de_la_respuesta)
  local aviso=""
  if grep -q '"contactoRepetido":true' <<<"$RESPUESTA"; then
    aviso=" (contacto repetido)"
  fi
  avanzar_etapas "$1" "$PROSPECTO_ID" "$5" "${6:-}"
  echo "  creado      $2 → ${5##* }$aviso"
}

# $1 token del vendedor, $2 id del prospecto, $3 etapas, $4 motivo de cierre.
avanzar_etapas() {
  local etapa
  for etapa in $3; do
    local cuerpo="{\"etapa\":\"$etapa\"}"
    # Al pasar a Pago el vendedor captura el precio total de la venta (ERP-1.1).
    if [ "$etapa" = PAGO ]; then
      cuerpo="{\"etapa\":\"PAGO\",\"precioTotal\":489900}"
    fi
    if [ "$etapa" = CERRADO ]; then
      cuerpo="{\"etapa\":\"CERRADO\",\"motivoCierre\":\"$4\"}"
    fi
    pedir PATCH "/api/v1/prospectos/$2/etapa" "$1" "$cuerpo"
    [ "$CODIGO" = 200 ] || fallar "pasar el prospecto $2 a $etapa"
  done
}

pedir GET /actuator/health ""
[ "$CODIGO" = 200 ] || { echo "La API en $API no está lista ($CODIGO): $RESPUESTA" >&2; exit 1; }

echo "Usuarios del ERP (los da de alta el Administrador):"
TOKEN_ADMIN=$(iniciar_sesion "$ADMIN_USUARIO" "$ADMIN_CONTRASENA" ERP)
crear_usuario "$TOKEN_ADMIN" inventario1 Inventario123 "Andrea Ruiz" INVENTARIO
crear_usuario "$TOKEN_ADMIN" refacciones1 Refacciones123 "Daniela León" REFACCIONES
crear_usuario "$TOKEN_ADMIN" facturacion1 Facturacion123 "Sofía Méndez" FACTURACION
crear_usuario "$TOKEN_ADMIN" servicio1 Servicio123 "Jorge Castillo" SERVICIO
crear_usuario "$TOKEN_ADMIN" caja1 Caja12345 "Carla Cajera" CAJA
crear_usuario "$TOKEN_ADMIN" contabilidad1 Contabilidad123 "Marco Ortiz" CONTABILIDAD

echo "Usuarios del CRM (los da de alta el Gerente de marketing):"
TOKEN_GERENTE=$(iniciar_sesion "$GERENTE_USUARIO" "$GERENTE_CONTRASENA" CRM)
crear_usuario "$TOKEN_GERENTE" vendedor1 Vendedor123 "Sergio Luna" VENTAS
crear_usuario "$TOKEN_GERENTE" vendedor2 Vendedor123 "Elena Ríos" VENTAS

echo "Vehículos (los da de alta Inventario):"
TOKEN_INVENTARIO=$(iniciar_sesion inventario1 Inventario123 ERP)
crear_vehiculo 3N1AB8CV5PY123456 Sentra Advance 2026 Blanco 489900.00 DISPONIBLE
crear_vehiculo 3N1CN8AE4RL123457 Versa Sense 2026 Plata 329900.00 DISPONIBLE
crear_vehiculo 3N1CP5CV6RL123458 Kicks Exclusive 2026 Azul 529900.00 DISPONIBLE
crear_vehiculo 3N6AD33A2RK123459 Frontier LE 2025 Gris 689900.00 NO_DISPONIBLE
crear_vehiculo 3N1CK3CD0RL123460 March Advance 2025 Rojo 279900.00 DISPONIBLE
crear_vehiculo 5N1DR3MN4RC123461 Pathfinder Exclusive 2026 Negro 1049900.00 DISPONIBLE
crear_vehiculo JN1BJ1CW6RW123462 X-Trail Advance 2026 Blanco 699900.00 DISPONIBLE
crear_vehiculo 1N6ED1EJ8RN123463 Frontier PRO-4X 2026 Azul 899900.00 NO_DISPONIBLE

echo "Refacciones y sus recepciones (Inventario):"
crear_refaccion REF-001 "Filtro de aceite" 85.00 149.00 5
recibir 12 "Refacciones del Bajío"
crear_refaccion REF-002 "Balatas delanteras" 620.00 890.00 5
recibir 3 "Refacciones del Bajío" "Una caja golpeada" "$(hace_dias 2)"
crear_refaccion REF-003 "Bujía de iridio" 170.00 259.00 8
recibir 10 "Distribuidora León"
recibir 8 "Refacciones del Bajío"
crear_refaccion REF-004 "Filtro de aire" 120.00 199.00 4
crear_refaccion REF-005 "Limpiaparabrisas" 150.00 249.00 4
recibir 9 "Distribuidora León"
crear_refaccion REF-006 "Aceite sintético 5W-30 (litro)" 160.00 239.00 20
recibir 20 "Lubricantes del Centro"
crear_refaccion REF-007 "Batería 12 V" 1850.00 2590.00 2
recibir 6 "Distribuidora León"
crear_refaccion REF-008 "Amortiguador delantero" 980.00 1450.00 4
recibir 2 "Refacciones del Bajío"

echo "Prospectos de Sergio Luna (vendedor1):"
pedir GET /api/v1/prospectos "$TOKEN_GERENTE"
[ "$CODIGO" = 200 ] || fallar "listar los prospectos"
PROSPECTOS_EXISTENTES="$RESPUESTA"
TOKEN_VENDEDOR1=$(iniciar_sesion vendedor1 Vendedor123 CRM)
TOKEN_VENDEDOR2=$(iniciar_sesion vendedor2 Vendedor123 CRM)
# Desde Pago el proceso se avanza desde el ERP: Caja registra y envía los pagos,
# Contabilidad los valida (ERP-1.1) y Facturación emite la factura. El vendedor
# no mueve esas etapas.
TOKEN_CAJA=$(iniciar_sesion caja1 Caja12345 ERP)
TOKEN_CONTABILIDAD=$(iniciar_sesion contabilidad1 Contabilidad123 ERP)
TOKEN_FACTURACION=$(iniciar_sesion facturacion1 Facturacion123 ERP)

# Lleva un prospecto que ya está en Pago hasta la etapa indicada (FACTURACION o
# ENTREGA), como lo harían Caja, Contabilidad y Facturación.
# $1 id del prospecto, $2 etapa final.
avanzar_desde_erp() {
  [ -n "$1" ] || return 0
  pedir POST "/api/v1/liquidaciones/$1/pagos" "$TOKEN_CAJA"     '{"monto":489900,"fechaPago":"2026-10-15","referencia":"Pago de ejemplo"}'
  [ "$CODIGO" = 201 ] || fallar "registrar el pago del prospecto $1"
  pedir POST "/api/v1/liquidaciones/$1/enviar" "$TOKEN_CAJA"
  [ "$CODIGO" = 200 ] || fallar "enviar los pagos del prospecto $1"
  pedir POST "/api/v1/liquidaciones/$1/validar" "$TOKEN_CONTABILIDAD"
  [ "$CODIGO" = 200 ] || fallar "validar los pagos del prospecto $1"
  if [ "$2" = ENTREGA ]; then
    avanzar_etapas "$TOKEN_FACTURACION" "$1" "ENTREGA" ""
  fi
}
crear_prospecto "$TOKEN_VENDEDOR1" "Ana López" "477 555 0101" "ana.lopez@example.com" "PRIMER_CONTACTO"
crear_prospecto "$TOKEN_VENDEDOR1" "Diego Ramírez" "477 555 0102" "" "VISITA"
crear_prospecto "$TOKEN_VENDEDOR1" "Fernanda Soto" "" "fernanda.soto@example.com" "VISITA COTIZACION"
crear_prospecto "$TOKEN_VENDEDOR1" "Luis Herrera" "477 555 0104" "luis.h@example.com" "VISITA COTIZACION PAGO"
crear_prospecto "$TOKEN_VENDEDOR1" "Valeria Díaz" "477 555 0105" "valeria.d@example.com" \
  "VISITA COTIZACION PAGO"
avanzar_desde_erp "$PROSPECTO_ID" ENTREGA
crear_prospecto "$TOKEN_VENDEDOR1" "Jorge Medina" "477 555 0106" "" "VISITA COTIZACION CERRADO" \
  "SE_FUE_POR_OTRA_MARCA"

echo "Prospecto reasignado por el Gerente de marketing:"
crear_prospecto "$TOKEN_VENDEDOR1" "Ricardo Fuentes" "477 555 0107" "" "PRIMER_CONTACTO"
if [ -n "$PROSPECTO_ID" ]; then
  pedir GET /api/v1/usuarios "$TOKEN_GERENTE"
  ID_VENDEDOR2=$(printf '%s' "$RESPUESTA" | sed -n 's/.*{"id":\([0-9][0-9]*\),"usuario":"vendedor2".*/\1/p')
  [ -n "$ID_VENDEDOR2" ] || fallar "encontrar a vendedor2"
  pedir PUT "/api/v1/prospectos/$PROSPECTO_ID/vendedor" "$TOKEN_GERENTE" "{\"vendedorId\":$ID_VENDEDOR2}"
  [ "$CODIGO" = 200 ] || fallar "reasignar a Ricardo Fuentes"
  avanzar_etapas "$TOKEN_VENDEDOR2" "$PROSPECTO_ID" "VISITA" ""
  echo "              reasignado a Elena Ríos, que lo pasó a VISITA"
fi

echo "Prospectos de Elena Ríos (vendedor2):"
crear_prospecto "$TOKEN_VENDEDOR2" "Mariana Torres" "477 555 0108" "mariana.t@example.com" "PRIMER_CONTACTO"
crear_prospecto "$TOKEN_VENDEDOR2" "Carlos Núñez" "477 555 0109" "" "VISITA COTIZACION PAGO"
avanzar_desde_erp "$PROSPECTO_ID" FACTURACION
if [ -n "$PROSPECTO_ID" ]; then
  pedir POST "/api/v1/prospectos/$PROSPECTO_ID/solicitudes-cierre" "$TOKEN_VENDEDOR2" \
    '{"motivo":"PERDIO_EL_INTERES"}'
  [ "$CODIGO" = 201 ] || fallar "solicitar el cierre de Carlos Núñez"
  echo "              con una solicitud de cierre pendiente para el Administrador"
fi
crear_prospecto "$TOKEN_VENDEDOR2" "Paola Vega" "" "paola.vega@example.com" "VISITA"
crear_prospecto "$TOKEN_VENDEDOR2" "Ana Lucía López" "477 555 0101" "" "PRIMER_CONTACTO"
crear_prospecto "$TOKEN_VENDEDOR2" "Óscar Ramos" "477 555 0111" "" "CERRADO" "RECHAZO_DE_FINANCIAMIENTO"
crear_prospecto "$TOKEN_VENDEDOR2" "Roberto Salas" "477 555 0112" "roberto.s@example.com" "COTIZACION PAGO"
if [ -n "$PROSPECTO_ID" ]; then
  avanzar_desde_erp "$PROSPECTO_ID" ENTREGA
  avanzar_etapas "$TOKEN_VENDEDOR2" "$PROSPECTO_ID" "FINALIZADO" ""
fi

# Cliente de la app CareCar con sus vehículos (APP-2.1 y APP-3.1), servicios en su
# expediente (ERP-3.0) y el manual de uno de sus vehículos.
CLIENTE_APP_CORREO="laura.mendez@example.com"
VIN_MARCH="3N1CK3CD7RL900001"
VIN_SENTRA="3N1AB8CV9RY900002"

# Fecha de hace $1 días, AAAA-MM-DD (GNU date o BSD/macOS date).
fecha_hace_dias() {
  date -d "$1 days ago" +%Y-%m-%d 2>/dev/null || date -v-"$1"d +%Y-%m-%d
}

# Id del vehículo con ese VIN en el expediente. $1 VIN.
vehiculo_por_vin() {
  pedir GET "/api/v1/expedientes?vin=$1" "$TOKEN_SERVICIO"
  [ "$CODIGO" = 200 ] || fallar "buscar el vehículo $1"
  printf '%s' "$RESPUESTA" | sed -n 's/^\[{"id":\([0-9][0-9]*\).*/\1/p'
}

# Registra un servicio si el vehículo todavía no tiene. $1 id del vehículo,
# $2 días atrás, $3 kilometraje, $4 renglones en JSON.
registrar_servicio() {
  pedir POST "/api/v1/expedientes/$1/servicios" "$TOKEN_SERVICIO" \
    "{\"fecha\":\"$(fecha_hace_dias "$2")\",\"kilometraje\":$3,\"renglones\":$4}"
  [ "$CODIGO" = 201 ] || fallar "registrar el servicio del vehículo $1"
}

echo "Cliente de la app con sus vehículos (Gerente de marketing):"
pedir POST /api/v1/clientes "$TOKEN_GERENTE" \
  "{\"nombre\":\"Laura Méndez\",\"correo\":\"$CLIENTE_APP_CORREO\",\"telefono\":\"477 555 0120\"}"
case "$CODIGO" in
  201)
    CLIENTE_APP_ID=$(id_de_la_respuesta)
    echo "  creada      Laura Méndez ($CLIENTE_APP_CORREO)"
    ;;
  409)
    pedir GET "/api/v1/clientes?buscar=$CLIENTE_APP_CORREO" "$TOKEN_GERENTE"
    CLIENTE_APP_ID=$(printf '%s' "$RESPUESTA" | sed -n 's/^\[{"id":\([0-9][0-9]*\).*/\1/p')
    [ -n "$CLIENTE_APP_ID" ] || fallar "encontrar a Laura Méndez"
    echo "  ya existía  Laura Méndez ($CLIENTE_APP_CORREO)"
    ;;
  *) fallar "registrar a Laura Méndez" ;;
esac
pedir POST "/api/v1/clientes/$CLIENTE_APP_ID/vehiculos" "$TOKEN_GERENTE" \
  "{\"vin\":\"$VIN_MARCH\",\"marca\":\"Nissan\",\"modelo\":\"March\",\"anio\":2026,\"version\":\"Advance\"}"
[ "$CODIGO" = 200 ] || fallar "asignar el March a Laura Méndez"
pedir POST "/api/v1/clientes/$CLIENTE_APP_ID/vehiculos" "$TOKEN_GERENTE" \
  "{\"vin\":\"$VIN_SENTRA\",\"marca\":\"Nissan\",\"modelo\":\"Sentra\",\"anio\":2025,\"version\":\"Sense\"}"
[ "$CODIGO" = 200 ] || fallar "asignar el Sentra a Laura Méndez"
echo "              con un March 2026 y un Sentra 2025"
pedir POST "/api/v1/clientes/$CLIENTE_APP_ID/cuenta-app" "$TOKEN_GERENTE"
case "$CODIGO" in
  201) echo "              cuenta de la app abierta: la contraseña temporal llegó a Mailpit" ;;
  409) echo "              ya tenía cuenta de la app" ;;
  *) fallar "abrir la cuenta de la app de Laura Méndez" ;;
esac

echo "Servicios en el expediente (Servicio):"
TOKEN_SERVICIO=$(iniciar_sesion servicio1 Servicio123 ERP)
ID_MARCH=$(vehiculo_por_vin "$VIN_MARCH")
ID_SENTRA=$(vehiculo_por_vin "$VIN_SENTRA")
[ -n "$ID_MARCH" ] && [ -n "$ID_SENTRA" ] || fallar "encontrar los vehículos de Laura Méndez"
pedir GET "/api/v1/expedientes/$ID_MARCH" "$TOKEN_SERVICIO"
if grep -q '"servicios":\[\]' <<<"$RESPUESTA"; then
  pedir PATCH "/api/v1/expedientes/$ID_MARCH" "$TOKEN_SERVICIO" '{"placas":"GTO-123-A","kilometraje":10250}'
  [ "$CODIGO" = 200 ] || fallar "corregir el March de Laura Méndez"
  registrar_servicio "$ID_MARCH" 150 5100 \
    '[{"tipo":"MANO_DE_OBRA","concepto":"Servicio de 5,000 km","cantidad":1,"precioUnitario":1450.00},
      {"tipo":"REFACCION","concepto":"Filtro de aceite","cantidad":1,"precioUnitario":149.00},
      {"tipo":"REFACCION","concepto":"Aceite sintético 5W-30 (litro)","cantidad":4,"precioUnitario":239.00}]'
  registrar_servicio "$ID_MARCH" 10 10250 \
    '[{"tipo":"MANO_DE_OBRA","concepto":"Servicio de 10,000 km","cantidad":1,"precioUnitario":2350.00},
      {"tipo":"REFACCION","concepto":"Filtro de aire","cantidad":1,"precioUnitario":199.00},
      {"tipo":"REFACCION","concepto":"Bujía de iridio","cantidad":4,"precioUnitario":259.00}]'
  echo "  creados     2 servicios del March ($VIN_MARCH)"
else
  echo "  ya existían los servicios del March ($VIN_MARCH)"
fi
pedir GET "/api/v1/expedientes/$ID_SENTRA" "$TOKEN_SERVICIO"
if grep -q '"servicios":\[\]' <<<"$RESPUESTA"; then
  registrar_servicio "$ID_SENTRA" 30 8300 \
    '[{"tipo":"MANO_DE_OBRA","concepto":"Cambio de balatas delanteras","cantidad":1,"precioUnitario":650.00},
      {"tipo":"REFACCION","concepto":"Balatas delanteras","cantidad":1,"precioUnitario":890.00}]'
  echo "  creado      1 servicio del Sentra ($VIN_SENTRA)"
else
  echo "  ya existía  el servicio del Sentra ($VIN_SENTRA)"
fi

echo "Manual del March 2026 (Gerente de marketing):"
# Con MANUAL_MARCH_PDF se sube un PDF real; si no, uno de ejemplo de una página.
MANUAL_TMP=$(mktemp)
trap 'rm -f "$RESPUESTA_TMP" "$MANUAL_TMP"' EXIT
if [ -n "${MANUAL_MARCH_PDF:-}" ]; then
  cp "$MANUAL_MARCH_PDF" "$MANUAL_TMP"
else
  base64 -d >"$MANUAL_TMP" <<'PDF'
JVBERi0xLjQKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2Jq
CjIgMCBvYmoKPDwgL1R5cGUgL1BhZ2VzIC9LaWRzIFszIDAgUl0gL0NvdW50IDEgPj4KZW5kb2Jq
CjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCA2MTIg
NzkyXSAvQ29udGVudHMgNCAwIFIgL1Jlc291cmNlcyA8PCAvRm9udCA8PCAvRjEgNSAwIFIgPj4g
Pj4gPj4KZW5kb2JqCjQgMCBvYmoKPDwgL0xlbmd0aCAxNDggPj4Kc3RyZWFtCkJUIC9GMSAxOCBU
ZiA3MiA3MjAgVGQgKE5pc3NhbiBNYXJjaCAyMDI2IE1hbnVhbCBkZSBwcm9waWV0YXJpbykgVGog
MCAtMjggVGQgL0YxIDEyIFRmIChFamVtcGxvIHBhcmEgZGVzYXJyb2xsby4gUmVlbXBsYXphbG8g
cG9yIGVsIFBERiByZWFsLikgVGogRVQKZW5kc3RyZWFtCmVuZG9iago1IDAgb2JqCjw8IC9UeXBl
IC9Gb250IC9TdWJ0eXBlIC9UeXBlMSAvQmFzZUZvbnQgL0hlbHZldGljYSA+PgplbmRvYmoKeHJl
ZgowIDYKMDAwMDAwMDAwMCA2NTUzNSBmIAowMDAwMDAwMDA5IDAwMDAwIG4gCjAwMDAwMDAwNTgg
MDAwMDAgbiAKMDAwMDAwMDExNSAwMDAwMCBuIAowMDAwMDAwMjQxIDAwMDAwIG4gCjAwMDAwMDA0
NDAgMDAwMDAgbiAKdHJhaWxlcgo8PCAvU2l6ZSA2IC9Sb290IDEgMCBSID4+CnN0YXJ0eHJlZgo1
MTAKJSVFT0YK
PDF
fi
if ! CODIGO=$(curl -sS -o "$RESPUESTA_TMP" -w '%{http_code}' -X POST "$API/api/v1/manuales" \
  -H "Authorization: Bearer $TOKEN_GERENTE" -F marca=Nissan -F modelo=March -F anio=2026 \
  -F "archivo=@$MANUAL_TMP;type=application/pdf;filename=nissan-march-2026.pdf"); then
  echo "No hubo respuesta de la API en $API. ¿Está corriendo?" >&2
  exit 1
fi
RESPUESTA=$(cat "$RESPUESTA_TMP")
[ "$CODIGO" = 200 ] || fallar "subir el manual del March 2026"
echo "  subido      Nissan March 2026 Manual de propietario"

echo
echo "Listo. Qué demuestra cada dato: docs/datos-de-ejemplo.md. Contraseñas: README, sección 5.6."
