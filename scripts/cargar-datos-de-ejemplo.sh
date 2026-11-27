#!/usr/bin/env bash
# Carga los datos de ejemplo de TEC-3 en la API local: usuarios por rol,
# vehículos, refacciones con sus recepciones y prospectos en todas las etapas.
# Desde V2-T05.10 también los de los módulos de la app v2: taller en vivo,
# garantías y campañas, tienda, pagos y facturas, lealtad y bitácora.
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
CUERPO_TMP=$(mktemp)
trap 'rm -f "$RESPUESTA_TMP" "$CUERPO_TMP"' EXIT

# Ruta que entiende curl. En Git Bash de Windows, curl es un programa de Windows
# y no encuentra /tmp/...; cygpath la convierte a C:/Users/.../Temp/...
ruta_para_curl() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -m "$1"
  else
    printf '%s' "$1"
  fi
}

# Hace una petición a la API. Deja el código HTTP en CODIGO y el cuerpo en
# RESPUESTA. $1 método, $2 ruta, $3 token (vacío si no hay), $4 cuerpo JSON,
# $5 un encabezado más (opcional, como "Idempotency-Key: ...").
# El cuerpo va en un archivo y no como argumento: en Windows los argumentos
# pierden los acentos (por ejemplo "Daniela León") y la API responde 400.
pedir() {
  local argumentos=(-sS -o "$RESPUESTA_TMP" -w '%{http_code}' -X "$1" "$API$2" -H 'Accept: application/json')
  if [ -n "$3" ]; then
    argumentos+=(-H "Authorization: Bearer $3")
  fi
  if [ $# -ge 4 ]; then
    printf '%s' "$4" >"$CUERPO_TMP"
    argumentos+=(-H 'Content-Type: application/json' --data-binary "@$(ruta_para_curl "$CUERPO_TMP")")
  fi
  if [ $# -ge 5 ] && [ -n "$5" ]; then
    argumentos+=(-H "$5")
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
  PROSPECTO_NOMBRE="$2"
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
    # Al pasar a Pago el vendedor captura el precio total de la venta (ERP-1.1)
    # y los datos fiscales del cliente para la factura (ERP-1.3). El RFC es de
    # ejemplo; solo tiene que tener un formato válido.
    if [ "$etapa" = PAGO ]; then
      cuerpo="{\"etapa\":\"PAGO\",\"precioTotal\":489900,\"datosFiscales\":{\"rfc\":\"EJEM850101AB1\","
      cuerpo+="\"nombre\":\"${PROSPECTO_NOMBRE:-Cliente de ejemplo}\",\"domicilio\":{\"estado\":\"Guanajuato\","
      cuerpo+="\"ciudad\":\"León\",\"colonia\":\"Centro\",\"calleNumero\":\"Blvd. Adolfo López Mateos 1102\","
      cuerpo+="\"codigoPostal\":\"37000\"},\"regimenFiscal\":\"612\"}}"
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

# Id de la unidad Disponible con ese VIN, como la ve Facturación. $1 VIN.
unidad_disponible() {
  pedir GET "/api/v1/vehiculos?estatus=DISPONIBLE" "$TOKEN_FACTURACION"
  [ "$CODIGO" = 200 ] || fallar "listar las unidades disponibles"
  printf '%s' "$RESPUESTA" | sed -n "s/.*{\"id\":\([0-9][0-9]*\),\"vin\":\"$1\".*/\1/p"
}

# Espera a que el prospecto llegue a la etapa: ventascrm lo mueve al recibir un
# evento, después de que termina la petición que lo publicó. $1 id, $2 etapa.
esperar_etapa() {
  local intento
  for intento in 1 2 3 4 5 6 7 8 9 10; do
    pedir GET "/api/v1/prospectos?etapa=$2" "$TOKEN_GERENTE"
    [ "$CODIGO" = 200 ] || fallar "listar los prospectos en $2"
    grep -q "{\"id\":$1," <<<"$RESPUESTA" && return 0
    sleep 0.5
  done
  echo "El prospecto $1 no llegó a $2" >&2
  exit 1
}

# Facturación emite la factura con una unidad Disponible (ERP-1.3): la unidad
# pasa a Vendido y el prospecto pasa solo a Entrega (ERP-1.2).
# $1 id del prospecto, $2 VIN de la unidad.
emitir_factura() {
  local unidad
  unidad=$(unidad_disponible "$2")
  [ -n "$unidad" ] || fallar "encontrar la unidad disponible $2"
  pedir POST /api/v1/facturas "$TOKEN_FACTURACION" "{\"prospectoId\":$1,\"vehiculoId\":$unidad}"
  [ "$CODIGO" = 201 ] || fallar "emitir la factura del prospecto $1"
  esperar_etapa "$1" ENTREGA
}

# Lleva un prospecto que ya está en Pago hasta la etapa indicada (FACTURACION o
# ENTREGA), como lo harían Caja, Contabilidad y Facturación.
# $1 id del prospecto, $2 etapa final, $3 VIN de la unidad que se factura (solo
# para ENTREGA).
avanzar_desde_erp() {
  [ -n "$1" ] || return 0
  pedir POST "/api/v1/liquidaciones/$1/pagos" "$TOKEN_CAJA"     '{"monto":489900,"fechaPago":"2026-10-15","referencia":"Pago de ejemplo"}'
  [ "$CODIGO" = 201 ] || fallar "registrar el pago del prospecto $1"
  pedir POST "/api/v1/liquidaciones/$1/enviar" "$TOKEN_CAJA"
  [ "$CODIGO" = 200 ] || fallar "enviar los pagos del prospecto $1"
  pedir POST "/api/v1/liquidaciones/$1/validar" "$TOKEN_CONTABILIDAD"
  [ "$CODIGO" = 200 ] || fallar "validar los pagos del prospecto $1"
  if [ "$2" = ENTREGA ]; then
    emitir_factura "$1" "$3"
  fi
}
crear_prospecto "$TOKEN_VENDEDOR1" "Ana López" "477 555 0101" "ana.lopez@example.com" "PRIMER_CONTACTO"
crear_prospecto "$TOKEN_VENDEDOR1" "Diego Ramírez" "477 555 0102" "" "VISITA"
crear_prospecto "$TOKEN_VENDEDOR1" "Fernanda Soto" "" "fernanda.soto@example.com" "VISITA COTIZACION"
crear_prospecto "$TOKEN_VENDEDOR1" "Luis Herrera" "477 555 0104" "luis.h@example.com" "VISITA COTIZACION PAGO"
crear_prospecto "$TOKEN_VENDEDOR1" "Valeria Díaz" "477 555 0105" "valeria.d@example.com" \
  "VISITA COTIZACION PAGO"
avanzar_desde_erp "$PROSPECTO_ID" ENTREGA 3N1AB8CV5PY123456
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
  avanzar_desde_erp "$PROSPECTO_ID" ENTREGA 3N1CP5CV6RL123458
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

# Color del vehículo según el modelo (el de la foto que usa la app). $1 modelo.
color_del_modelo() {
  case "$1" in
    March) echo "Blanco" ;; Versa) echo "Rojo" ;; Kicks) echo "Gris plata" ;;
    Sentra) echo "Azul eléctrico" ;; X-Trail) echo "Beige champagne" ;; *) echo "Gris cemento" ;;
  esac
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
# Placas, kilometraje y color: se fijan siempre (es idempotente).
pedir PATCH "/api/v1/expedientes/$ID_MARCH" "$TOKEN_SERVICIO" '{"placas":"GTO-123-A","kilometraje":10250,"color":"Blanco"}'
[ "$CODIGO" = 200 ] || fallar "corregir el March de Laura Méndez"
pedir PATCH "/api/v1/expedientes/$ID_SENTRA" "$TOKEN_SERVICIO" '{"placas":"GTO-456-B","kilometraje":14800,"color":"Azul eléctrico"}'
[ "$CODIGO" = 200 ] || fallar "corregir el Sentra de Laura Méndez"
pedir GET "/api/v1/expedientes/$ID_MARCH" "$TOKEN_SERVICIO"
if grep -q '"servicios":\[\]' <<<"$RESPUESTA"; then
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

# Más clientes de la app CareCar, cada uno con 3 a 5 vehículos, cuenta abierta y
# la contraseña conocida CLIENTES_CONTRASENA. La contraseña temporal solo llega a
# Mailpit, así que se restablece con el código de 6 dígitos del correo (el mismo
# flujo "Olvidé mi contraseña" de la app). Todo por la API; no duplica al repetirse.
MAILPIT="${MAILPIT:-http://localhost:8025}"
CLIENTES_CONTRASENA="${CLIENTES_CONTRASENA:-CareCar2026!}"
SERIE_VIN=90000

# Lleva la cuenta del cliente a CLIENTES_CONTRASENA con el código de Mailpit.
# $1 correo.
restablecer_con_mailpit() {
  pedir POST /api/v1/app/auth/recuperar-contrasena "" "{\"correo\":\"$1\"}"
  [ "$CODIGO" = 202 ] || fallar "pedir el código de $1"
  local codigo="" intento id
  sleep 1
  for intento in 1 2 3 4 5 6 7 8 9 10; do
    id=$(curl -sS "$MAILPIT/api/v1/search?query=to:$1%20subject:restablecer&limit=1" |
      sed -n 's/.*"messages":\[{"ID":"\([^"]*\)".*/\1/p')
    if [ -n "$id" ]; then
      codigo=$(curl -sS "$MAILPIT/api/v1/message/$id" | sed -n 's/.*es: \([0-9]\{6\}\).*/\1/p')
      [ -n "$codigo" ] && break
    fi
    sleep 0.5
  done
  [ -n "$codigo" ] || { echo "No llegó el código de $1 a Mailpit ($MAILPIT)" >&2; exit 1; }
  pedir POST /api/v1/app/auth/restablecer-contrasena "" \
    "{\"correo\":\"$1\",\"codigo\":\"$codigo\",\"contrasenaNueva\":\"$CLIENTES_CONTRASENA\"}"
  [ "$CODIGO" = 204 ] || fallar "restablecer la contraseña de $1"
}

# Crea (o encuentra) un cliente con sus vehículos y su cuenta de app.
# $1 nombre, $2 correo, $3 teléfono; los vehículos llegan por la entrada
# estándar, uno por línea: modelo|versión|año|placas|kilometraje|servicios(0-3).
crear_cliente_con_vehiculos() {
  local nombre="$1" correo="$2" id_cliente linea lineas=()
  while IFS= read -r linea; do [ -n "$linea" ] && lineas+=("$linea"); done
  pedir POST /api/v1/clientes "$TOKEN_GERENTE" \
    "{\"nombre\":\"$nombre\",\"correo\":\"$correo\",\"telefono\":\"$3\"}"
  case "$CODIGO" in
    201) id_cliente=$(id_de_la_respuesta); echo "  creado      $nombre ($correo)" ;;
    409)
      pedir GET "/api/v1/clientes?buscar=$correo" "$TOKEN_GERENTE"
      id_cliente=$(printf '%s' "$RESPUESTA" | sed -n 's/^\[{"id":\([0-9][0-9]*\).*/\1/p')
      [ -n "$id_cliente" ] || fallar "encontrar a $nombre"
      echo "  ya existía  $nombre ($correo)"
      ;;
    *) fallar "registrar a $nombre" ;;
  esac
  local modelo version anio placas km nserv vin codigo_modelo id_veh i dias kmserv renglones
  for linea in "${lineas[@]}"; do
    IFS='|' read -r modelo version anio placas km nserv <<<"$linea"
    SERIE_VIN=$((SERIE_VIN + 1))
    case "$modelo" in
      March) codigo_modelo=MAR ;; Versa) codigo_modelo=VER ;; Kicks) codigo_modelo=KIC ;;
      Sentra) codigo_modelo=SEN ;; X-Trail) codigo_modelo=XTR ;; *) codigo_modelo=FRO ;;
    esac
    vin="3N1${codigo_modelo}CD5RL9${SERIE_VIN}"
    pedir POST "/api/v1/clientes/$id_cliente/vehiculos" "$TOKEN_GERENTE" \
      "{\"vin\":\"$vin\",\"marca\":\"Nissan\",\"modelo\":\"$modelo\",\"anio\":$anio,\"version\":\"$version\"}"
    [ "$CODIGO" = 200 ] || fallar "asignar el $modelo $anio a $nombre"
    id_veh=$(vehiculo_por_vin "$vin")
    [ -n "$id_veh" ] || fallar "encontrar el vehículo $vin"
    pedir PATCH "/api/v1/expedientes/$id_veh" "$TOKEN_SERVICIO" \
      "{\"placas\":\"$placas\",\"kilometraje\":$km,\"color\":\"$(color_del_modelo "$modelo")\"}"
    [ "$CODIGO" = 200 ] || fallar "corregir placas, kilometraje y color de $vin"
    pedir GET "/api/v1/expedientes/$id_veh" "$TOKEN_SERVICIO"
    if grep -q '"servicios":\[\]' <<<"$RESPUESTA"; then
      for ((i = 1; i <= nserv; i++)); do
        dias=$((60 + (nserv - i) * 130 + SERIE_VIN % 40))
        kmserv=$((km * (i * 3 + nserv - 1) / (nserv * 3 + nserv - 1)))
        case $((i % 3)) in
          1) renglones='[{"tipo":"MANO_DE_OBRA","concepto":"Servicio de mantenimiento","cantidad":1,"precioUnitario":1650.00},
              {"tipo":"REFACCION","concepto":"Filtro de aceite","cantidad":1,"precioUnitario":149.00},
              {"tipo":"REFACCION","concepto":"Aceite sintético 5W-30 (litro)","cantidad":4,"precioUnitario":239.00}]' ;;
          2) renglones='[{"tipo":"MANO_DE_OBRA","concepto":"Cambio de balatas delanteras","cantidad":1,"precioUnitario":650.00},
              {"tipo":"REFACCION","concepto":"Balatas delanteras","cantidad":1,"precioUnitario":890.00}]' ;;
          0) renglones='[{"tipo":"MANO_DE_OBRA","concepto":"Afinación mayor","cantidad":1,"precioUnitario":2350.00},
              {"tipo":"REFACCION","concepto":"Filtro de aire","cantidad":1,"precioUnitario":199.00},
              {"tipo":"REFACCION","concepto":"Bujía de iridio","cantidad":4,"precioUnitario":259.00}]' ;;
        esac
        registrar_servicio "$id_veh" "$dias" "$kmserv" "$renglones"
      done
      echo "              + $modelo $version $anio $placas, $km km, $nserv servicio(s)"
    else
      echo "              ya existía $modelo $anio $placas"
    fi
  done
  pedir POST "/api/v1/clientes/$id_cliente/cuenta-app" "$TOKEN_GERENTE"
  case "$CODIGO" in
    201 | 409) ;;
    *) fallar "abrir la cuenta de la app de $nombre" ;;
  esac
  pedir POST /api/v1/app/auth/login "" "{\"correo\":\"$correo\",\"contrasena\":\"$CLIENTES_CONTRASENA\"}"
  if [ "$CODIGO" != 200 ]; then
    restablecer_con_mailpit "$correo"
    echo "              contraseña de prueba definida con el código de Mailpit"
  fi
}

echo "Más clientes de la app con 3 a 5 vehículos (Gerente de marketing y Servicio):"
crear_cliente_con_vehiculos "Carlos Ramírez" carlos.ramirez@example.com "477 555 0131" <<'DATOS'
Sentra|Advance|2024|GTO-482-B|18400|3
Versa|Sense|2022|GTO-715-C|42150|2
Kicks|Exclusive|2025|GTO-309-D|6200|1
March|Sense|2019|GTO-864-A|78900|3
DATOS
crear_cliente_con_vehiculos "Ana Torres" ana.torres@example.com "477 555 0132" <<'DATOS'
X-Trail|Exclusive|2023|MXP-27-83|29700|2
Kicks|Advance|2021|GTO-157-F|51300|3
Versa|Advance|2026|GTO-921-G|2100|1
DATOS
crear_cliente_con_vehiculos "Luis Hernández" luis.hernandez@example.com "462 555 0133" <<'DATOS'
Frontier|PRO-4X|2024|GTO-603-H|23800|2
Frontier|LE|2020|GTO-248-J|96500|3
Sentra|Exclusive|2023|GTO-376-K|35200|2
March|Advance|2018|GTO-590-L|112400|3
X-Trail|Sense|2025|GTO-834-M|8900|1
DATOS
crear_cliente_con_vehiculos "Sofía Morales" sofia.morales@example.com "477 555 0134" <<'DATOS'
Kicks|Sense|2022|NMR-48-19|47800|2
Sentra|Sense|2021|GTO-662-N|61200|3
Versa|Exclusive|2024|GTO-127-P|15300|1
X-Trail|Advance|2019|GTO-945-R|88700|2
DATOS
crear_cliente_con_vehiculos "Diego Castillo" diego.castillo@example.com "462 555 0135" <<'DATOS'
Frontier|PRO-4X|2026|GTO-501-S|3400|1
March|Exclusive|2023|GTO-218-T|27600|2
Sentra|Advance|2020|GTO-776-V|74100|3
DATOS

echo "Manuales de propietario (Gerente de marketing):"
# Los PDF se generan con scripts/generar-manuales.py (Chrome headless): ficha técnica,
# tablero, mantenimiento, seguridad y la foto del coche. Un manual es único por marca,
# modelo y año, y la API reemplaza el que ya exista, así que repetir la carga no duplica.
# Con MANUALES_DIR se usan PDF ya generados en otra carpeta.
# Si no se pueden generar (sin Python 3 o sin Chrome), se omiten y la carga sigue.
DIR_SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANUALES_DIR="${MANUALES_DIR:-$DIR_SCRIPT/manuales-generados}"

# Escribe el Python 3 que de verdad corre. En Windows «python3» puede ser solo el
# atajo a la Microsoft Store, así que se prueban python3, python y py -3.
python_disponible() {
  local comando
  for comando in python3 python "py -3"; do
    if $comando -c 'import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)' >/dev/null 2>&1; then
      printf '%s' "$comando"
      return 0
    fi
  done
  return 1
}

# En Windows, Chrome no está en /usr/bin: si no se pasó CHROME, se busca donde
# lo instala Google y se pasa con la ruta que entiende Python.
if [ -z "${CHROME:-}" ] && command -v cygpath >/dev/null 2>&1; then
  for chrome in "${PROGRAMFILES:-}/Google/Chrome/Application/chrome.exe" \
    "${PROGRAMFILES:-} (x86)/Google/Chrome/Application/chrome.exe" \
    "${LOCALAPPDATA:-}/Google/Chrome/Application/chrome.exe"; do
    if [ -f "$(cygpath -u "$chrome" 2>/dev/null)" ]; then
      CHROME="$(cygpath -m "$chrome")"
      export CHROME
      break
    fi
  done
fi

if [ ! -f "$MANUALES_DIR/indice.txt" ]; then
  if PYTHON=$(python_disponible); then
    $PYTHON "$(ruta_para_curl "$DIR_SCRIPT/generar-manuales.py")" --salida "$(ruta_para_curl "$MANUALES_DIR")" >/dev/null ||
      echo "  omitidos    no se pudieron generar los manuales (necesita Google Chrome; pásalo con CHROME=...)"
  else
    echo "  omitidos    no hay Python 3 para generar los manuales (o pasa MANUALES_DIR con PDF ya generados)"
  fi
fi
SUBIDOS=0
[ -f "$MANUALES_DIR/indice.txt" ] && while IFS='|' read -r modelo anio archivo; do
  [ -n "$modelo" ] || continue
  if ! CODIGO=$(curl -sS -o "$RESPUESTA_TMP" -w '%{http_code}' -X POST "$API/api/v1/manuales" \
    -H "Authorization: Bearer $TOKEN_GERENTE" -F marca=Nissan -F "modelo=$modelo" -F "anio=$anio" \
    -F "archivo=@$(ruta_para_curl "$MANUALES_DIR/$archivo");type=application/pdf;filename=$archivo"); then
    echo "No hubo respuesta de la API en $API. ¿Está corriendo?" >&2
    exit 1
  fi
  RESPUESTA=$(cat "$RESPUESTA_TMP")
  # 409: ya existía, no es error.
  if [ "$CODIGO" != 200 ] && [ "$CODIGO" != 409 ]; then
    fallar "subir el manual de $modelo $anio"
  fi
  SUBIDOS=$((SUBIDOS + 1))
done <"$MANUALES_DIR/indice.txt"
if [ "$SUBIDOS" -gt 0 ]; then
  echo "  subidos     $SUBIDOS manuales de propietario (Nissan, título «Manual de propietario»)"
fi

# Catálogo de la app (APP-4.1). $1 modelo, $2 versión, $3 precio desde, $4 descripción.
agregar_al_catalogo() {
  pedir POST /api/v1/catalogo "$TOKEN_GERENTE" \
    "{\"marca\":\"Nissan\",\"modelo\":\"$1\",\"anio\":2026,\"version\":\"$2\",\"precioDesde\":$3,\"descripcion\":\"$4\"}"
  case "$CODIGO" in
    201) echo "  agregado    Nissan $1 2026 $2" ;;
    409) echo "  ya existía  Nissan $1 2026 $2" ;;
    *) fallar "agregar el $1 al catálogo" ;;
  esac
}

echo "Catálogo de la app (Gerente de marketing):"
agregar_al_catalogo March Advance 289900 "Hatchback urbano, económico y fácil de estacionar"
agregar_al_catalogo Versa Sense 339900 "Sedán con amplio espacio interior"
agregar_al_catalogo Kicks Exclusive 489900 "SUV compacta con tecnología de asistencia al conductor"
agregar_al_catalogo Sentra Advance 449900 "Sedán elegante con motor eficiente y pantalla táctil"
agregar_al_catalogo X-Trail Advance 699900 "SUV familiar de tres filas con tracción integral"
agregar_al_catalogo Frontier PRO-4X 899900 "Pick-up todoterreno para trabajo y aventura"
agregar_al_catalogo Leaf "e+" 699900 "Hatchback eléctrico con gran autonomía y conducción silenciosa"
agregar_al_catalogo Ariya Evolve+ 1099900 "SUV eléctrica con diseño futurista y tecnología avanzada"
agregar_al_catalogo Z Performance 1399900 "Deportivo biturbo de tracción trasera con carácter clásico"
agregar_al_catalogo GT-R Premium 3299900 "Superdeportivo legendario con tracción integral y motor V6 biturbo"
agregar_al_catalogo Altima Platinum 649900 "Sedán mediano con tracción integral y asistencias de manejo"
agregar_al_catalogo Pathfinder Platinum 1149900 "SUV de tres filas con espacio, potencia y gran confort"
agregar_al_catalogo Murano Platinum 1099900 "SUV de lujo con interior refinado y manejo suave"
agregar_al_catalogo Armada "Platinum Reserve" 1999900 "SUV grande con motor V8 y capacidad de remolque sobresaliente"
agregar_al_catalogo Titan Pro-4X 1299900 "Pick-up grande y robusta para trabajo pesado y off-road"
agregar_al_catalogo Urvan Panel 579900 "Van de carga con amplio espacio y bajo costo de operación"
agregar_al_catalogo Maxima SR 799900 "Sedán deportivo de cuatro puertas con motor V6"
agregar_al_catalogo Juke Exclusive 429900 "Crossover compacto de diseño atrevido y personalidad urbana"
agregar_al_catalogo Qashqai Exclusive 599900 "Crossover versátil con tecnología e-Power y gran habitabilidad"
agregar_al_catalogo Patrol Platinum 2699900 "SUV de lujo todoterreno con motor V6 biturbo y gran capacidad"
agregar_al_catalogo Tiida Advance 299900 "Hatchback espacioso y práctico para la ciudad"
agregar_al_catalogo Note e-Power 389900 "Compacto híbrido e-Power eficiente y de manejo ágil"

# Citas de Laura Méndez, agendadas por la API de la app como lo hace el celular
# (APP-3.3): una confirmada y una cancelada. Si ya tiene citas, no agrega más.
# Su contraseña es LAURA_CONTRASENA (por defecto la que usa la app en desarrollo).
echo "Citas de Laura Méndez (app):"
LAURA_CONTRASENA="${LAURA_CONTRASENA:-CareCar2026!}"
pedir POST /api/v1/app/auth/login "" "{\"correo\":\"$CLIENTE_APP_CORREO\",\"contrasena\":\"$LAURA_CONTRASENA\"}"
TOKEN_LAURA=$(printf '%s' "$RESPUESTA" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN_LAURA" ]; then
  echo "  omitido     Laura no inicia sesión con LAURA_CONTRASENA (¿cambió su contraseña?). Pásala: LAURA_CONTRASENA=... $0"
else
  pedir GET /api/v1/app/citas "$TOKEN_LAURA"
  [ "$CODIGO" = 200 ] || fallar "listar las citas de Laura"
  if grep -q '"totalElementos":0' <<<"$RESPUESTA"; then
    pedir GET "/api/v1/app/agenda/disponibilidad?desde=$(date -d '+3 days' +%F)&hasta=$(date -d '+9 days' +%F)" "$TOKEN_LAURA"
    [ "$CODIGO" = 200 ] || fallar "ver la disponibilidad"
    mapfile -t HUECOS < <(printf '%s' "$RESPUESTA" | grep -o '"fecha":"[^"]*","franjaId":[0-9]*' | sed 's/"fecha":"\(.*\)","franjaId":\(.*\)/\1 \2/')
    [ "${#HUECOS[@]}" -ge 2 ] || fallar "encontrar horarios con cupo"
    FECHA1="${HUECOS[0]% *}"; FRANJA1="${HUECOS[0]#* }"
    FECHA2=""
    for hueco in "${HUECOS[@]}"; do
      if [ "${hueco% *}" != "$FECHA1" ]; then FECHA2="${hueco% *}"; FRANJA2="${hueco#* }"; break; fi
    done
    [ -n "$FECHA2" ] || fallar "encontrar un segundo día con cupo"
    pedir POST /api/v1/app/citas "$TOKEN_LAURA" "{\"vehiculoId\":$ID_MARCH,\"franjaId\":$FRANJA1,\"fecha\":\"$FECHA1\"}"
    [ "$CODIGO" = 201 ] || fallar "agendar la cita confirmada"
    echo "  confirmada  March el $FECHA1 (franja $FRANJA1)"
    pedir POST /api/v1/app/citas "$TOKEN_LAURA" "{\"vehiculoId\":$ID_SENTRA,\"franjaId\":$FRANJA2,\"fecha\":\"$FECHA2\"}"
    [ "$CODIGO" = 201 ] || fallar "agendar la cita a cancelar"
    ID_CITA=$(id_de_la_respuesta)
    pedir POST "/api/v1/app/citas/$ID_CITA/cancelar" "$TOKEN_LAURA"
    [ "$CODIGO" = 200 ] || fallar "cancelar la cita"
    echo "  cancelada   Sentra el $FECHA2 (franja $FRANJA2)"
  else
    echo "  ya existían las citas de Laura"
  fi
fi

# Valuación formal de ejemplo (V2-M26): Laura estima su March y pide la
# valuación. Necesita parámetros de toma a cuenta, que todavía no tienen API: sin
# ellos la estimación responde 422 y el paso se omite. No duplica al repetirse.
echo "Valuación formal de Laura Méndez (app):"
if [ -z "$TOKEN_LAURA" ]; then
  echo "  omitida     Laura no inició sesión"
else
  pedir GET /api/v1/app/valuaciones "$TOKEN_LAURA"
  [ "$CODIGO" = 200 ] || fallar "listar las valuaciones de Laura"
  if grep -q '"totalElementos":0' <<<"$RESPUESTA"; then
    pedir POST /api/v1/app/toma-a-cuenta/estimaciones "$TOKEN_LAURA" \
      "{\"vehiculoId\":$ID_MARCH,\"km\":10250,\"estado\":\"BUENO\"}"
    case "$CODIGO" in
      201)
        pedir POST /api/v1/app/valuaciones "$TOKEN_LAURA" "{\"estimacionTomaCuentaId\":$(id_de_la_respuesta)}"
        [ "$CODIGO" = 201 ] || [ "$CODIGO" = 200 ] || fallar "pedir la valuación del March"
        echo "  solicitada  valuación del March"
        ;;
      422) echo "  omitida     faltan parámetros de toma a cuenta para el March (parametro_toma_cuenta)" ;;
      *) fallar "estimar el March" ;;
    esac
  else
    echo "  ya existía  la valuación de Laura"
  fi
fi

# ── Módulos de la app v2 (V2-T05.10) ──
# Garantías y campañas (M10), la tienda (M14), el taller en vivo (M04), pagos y
# facturas simulados (M08), puntos, canjes y referidos (M12), bitácora de
# gastos (M18), direcciones para la recolección (M17) y una prueba de manejo
# (M16). Lo hace el personal por sus rutas y cada cliente por la API de la app
# con CLIENTES_CONTRASENA, como desde el celular. A Laura solo le llega la
# campaña de su March: su primer ingreso sigue pendiente para la demostración.
# Si un cliente ya no entra con esa contraseña, se omiten sus datos. No
# duplica al repetirse.

# Fecha dentro de $1 días, AAAA-MM-DD (GNU date o BSD/macOS date).
fecha_en_dias() {
  date -d "+$1 days" +%Y-%m-%d 2>/dev/null || date -v+"$1"d +%Y-%m-%d
}

# Mañana a las $1 horas en la hora de la agencia, como la pide el taller.
manana_a_las() {
  date -d '+1 day' +%Y-%m-%dT"$1":00:00 2>/dev/null || date -v+1d +%Y-%m-%dT"$1":00:00
}

# Escribe el token de la app del cliente, o nada si no entra con esa
# contraseña. $1 correo, $2 contraseña.
token_de_la_app() {
  pedir POST /api/v1/app/auth/login "" "{\"correo\":\"$1\",\"contrasena\":\"$2\"}"
  printf '%s' "$RESPUESTA" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

# Id del vehículo del cliente con esas placas. Usa la respuesta de
# GET /api/v1/app/vehiculos que dejó pedir. $1 placas.
vehiculo_por_placas() {
  printf '%s' "$RESPUESTA" | sed 's/},{/}\n{/g' | grep -F "\"placas\":\"$1\"" | head -1 |
    sed -n 's/^\[\{0,1\}{"id":\([0-9][0-9]*\),.*/\1/p'
}

# Id del primer objeto de una lista que empieza con ese texto después del id,
# por ejemplo el de una refacción por su código. $1 texto (como "codigo":"REF-005").
id_en_la_lista() {
  printf '%s' "$RESPUESTA" | grep -o "{\"id\":[0-9][0-9]*,$1" | head -1 | sed 's/{"id":\([0-9][0-9]*\),.*/\1/'
}

# Abre una orden en el taller y deja su id en ORDEN_ID. Si el vehículo ya tiene
# una orden abierta, no la toca y deja ORDEN_ID vacío. $1 token del cliente,
# $2 id del vehículo, $3 kilometraje.
abrir_orden() {
  ORDEN_ID=""
  pedir GET /api/v1/app/ordenes/activas "$1"
  [ "$CODIGO" = 200 ] || fallar "ver las órdenes abiertas"
  if grep -q "\"vehiculoId\":$2," <<<"$RESPUESTA"; then
    return
  fi
  pedir POST /api/v1/taller/ordenes "$TOKEN_TALLER" \
    "{\"vehiculoId\":$2,\"kilometraje\":$3,\"entregaEstimada\":\"$(manana_a_las 18)\"}"
  [ "$CODIGO" = 201 ] || fallar "abrir la orden del vehículo $2"
  ORDEN_ID=$(id_de_la_respuesta)
}

# $1 orden, $2 MANO_DE_OBRA o REFACCION, $3 concepto, $4 cantidad, $5 precio con IVA.
agregar_renglon() {
  pedir POST "/api/v1/taller/ordenes/$1/renglones" "$TOKEN_TALLER" \
    "{\"tipo\":\"$2\",\"concepto\":\"$3\",\"cantidad\":$4,\"precioUnitario\":$5}"
  [ "$CODIGO" = 201 ] || fallar "agregar «$3» a la orden $1"
}

# $1 orden, $2 etapa, $3 nota para el cliente.
avanzar_orden() {
  pedir POST "/api/v1/taller/ordenes/$1/etapas" "$TOKEN_TALLER" "{\"etapa\":\"$2\",\"nota\":\"$3\"}"
  [ "$CODIGO" = 200 ] || fallar "pasar la orden $1 a $2"
}

# $1 orden, $2 punto, $3 VERDE, AMARILLO o ROJO, $4 comentario.
revisar_punto() {
  pedir POST "/api/v1/taller/ordenes/$1/inspeccion" "$TOKEN_TALLER" \
    "{\"punto\":\"$2\",\"resultado\":\"$3\",\"comentario\":\"$4\"}"
  [ "$CODIGO" = 200 ] || fallar "revisar «$2» en la orden $1"
}

# Paga una orden desde la app con su llave de idempotencia. $1 token del
# cliente, $2 orden, $3 cuerpo de NuevoPago.
pagar_orden() {
  pedir POST "/api/v1/app/ordenes/$2/pagos" "$1" "$3" "Idempotency-Key: datos-de-ejemplo-orden-$2"
  case "$CODIGO" in
    200 | 201) ;;
    *) fallar "pagar la orden $2" ;;
  esac
  grep -q '"estado":"PAGADO"' <<<"$RESPUESTA" || fallar "pagar la orden $2 (no quedó pagada)"
}

# Da de alta un perfil fiscal si el cliente no tiene ninguno y deja en
# PERFIL_ID el predeterminado. $1 token, $2 cuerpo de EntradaPerfilFiscal.
perfil_fiscal() {
  pedir GET /api/v1/app/perfiles-fiscales "$1"
  [ "$CODIGO" = 200 ] || fallar "ver los datos fiscales"
  if [ "$RESPUESTA" = "[]" ]; then
    pedir POST /api/v1/app/perfiles-fiscales "$1" "$2"
    case "$CODIGO" in
      200 | 201) ;;
      *) fallar "agregar los datos fiscales" ;;
    esac
    echo "              + datos fiscales"
  fi
  PERFIL_ID=$(printf '%s' "$RESPUESTA" | sed -n 's/^\[\{0,1\}{"id":\([0-9][0-9]*\),.*/\1/p')
  [ -n "$PERFIL_ID" ] || fallar "leer el id de los datos fiscales"
}

# Espera a que el saldo de puntos llegue a $2 (se suman en segundo plano al
# pagar). $1 token.
esperar_puntos() {
  local saldo
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    pedir GET /api/v1/app/lealtad "$1"
    saldo=$(printf '%s' "$RESPUESTA" | sed -n 's/^{"saldo":\([0-9][0-9]*\).*/\1/p')
    if [ -n "$saldo" ] && [ "$saldo" -ge "$2" ]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

TOKEN_TALLER=$(iniciar_sesion servicio1 Servicio123 ERP)
TOKEN_INVENTARIO=$(iniciar_sesion inventario1 Inventario123 ERP)
TOKEN_CARLOS=$(token_de_la_app carlos.ramirez@example.com "$CLIENTES_CONTRASENA")
TOKEN_ANA=$(token_de_la_app ana.torres@example.com "$CLIENTES_CONTRASENA")
TOKEN_DIEGO=$(token_de_la_app diego.castillo@example.com "$CLIENTES_CONTRASENA")
TOKEN_SOFIA=$(token_de_la_app sofia.morales@example.com "$CLIENTES_CONTRASENA")

echo "Campañas de seguridad (Servicio):"
# $1 código, $2 título, $3 descripción, $4 qué hacer, $5 gravedad, $6 modelo,
# $7 año desde, $8 año hasta.
publicar_campana() {
  pedir POST /api/v1/campanas "$TOKEN_TALLER" \
    "{\"codigo\":\"$1\",\"titulo\":\"$2\",\"descripcion\":\"$3\",\"queHacer\":\"$4\",\"gravedad\":\"$5\",\"modelo\":\"$6\",\"anioDesde\":$7,\"anioHasta\":$8}"
  case "$CODIGO" in
    201) echo "  publicada   $1 $2 ($6 $7 a $8)" ;;
    400)
      grep -q "Ya existe la campa" <<<"$RESPUESTA" || fallar "publicar la campaña $1"
      echo "  ya existía  $1 $2"
      ;;
    *) fallar "publicar la campaña $1" ;;
  esac
}
publicar_campana RC-26-101 "Bolsa de aire del conductor" \
  "El inflador de la bolsa de aire del conductor puede activarse con menos fuerza de la debida." \
  "Agenda una cita: se reemplaza el módulo sin costo en unos 60 minutos." ALTA March 2023 2026
publicar_campana RC-26-102 "Sensor de presión de llantas" \
  "El sensor puede dejar de avisar cuando una llanta pierde presión." \
  "Agenda una cita para actualizar el sensor sin costo; mientras, revisa la presión cada semana." \
  MEDIA Kicks 2021 2022

echo "Garantías (Servicio):"
# $1 token del cliente, $2 vehículo, $3 componente, $4 descripción, $5 inicia
# (AAAA-MM-DD), $6 vence, $7 km límite o null.
registrar_garantia() {
  pedir GET "/api/v1/app/vehiculos/$2/garantia" "$1"
  [ "$CODIGO" = 200 ] || fallar "ver la garantía del vehículo $2"
  if grep -qF "\"componente\":\"$3\"" <<<"$RESPUESTA"; then
    echo "  ya existía  $3 del vehículo $2"
    return
  fi
  pedir POST /api/v1/garantias "$TOKEN_TALLER" \
    "{\"vehiculoId\":$2,\"componente\":\"$3\",\"descripcion\":\"$4\",\"iniciaEn\":\"$5\",\"venceEn\":\"$6\",\"kmLimite\":$7}"
  [ "$CODIGO" = 201 ] || fallar "registrar la garantía $3 del vehículo $2"
  echo "  registrada  $3 del vehículo $2"
}
if [ -n "$TOKEN_DIEGO" ]; then
  pedir GET /api/v1/app/vehiculos "$TOKEN_DIEGO"
  [ "$CODIGO" = 200 ] || fallar "listar los vehículos de Diego"
  ID_FRONTIER_DIEGO=$(vehiculo_por_placas GTO-501-S)
  [ -n "$ID_FRONTIER_DIEGO" ] || fallar "encontrar la Frontier de Diego"
  registrar_garantia "$TOKEN_DIEGO" "$ID_FRONTIER_DIEGO" "Garantía de fábrica" \
    "Defensa a defensa: motor, transmisión, suspensión y sistema eléctrico." \
    "$(fecha_hace_dias 120)" "$(fecha_en_dias 975)" 60000
  registrar_garantia "$TOKEN_DIEGO" "$ID_FRONTIER_DIEGO" "Batería" \
    "Batería de origen; vence en unas semanas para ver el aviso de garantía por vencer." \
    "$(fecha_hace_dias 330)" "$(fecha_en_dias 35)" null
else
  echo "  omitidas    Diego no inicia sesión con CLIENTES_CONTRASENA"
fi
if [ -n "$TOKEN_CARLOS" ]; then
  pedir GET /api/v1/app/vehiculos "$TOKEN_CARLOS"
  [ "$CODIGO" = 200 ] || fallar "listar los vehículos de Carlos"
  ID_KICKS_CARLOS=$(vehiculo_por_placas GTO-309-D)
  [ -n "$ID_KICKS_CARLOS" ] || fallar "encontrar el Kicks de Carlos"
  registrar_garantia "$TOKEN_CARLOS" "$ID_KICKS_CARLOS" "Tren motriz" \
    "Motor, transmisión y diferencial." "$(fecha_hace_dias 200)" "$(fecha_en_dias 1625)" 100000
fi

echo "Productos de la tienda (Inventario):"
pedir GET /api/v1/refacciones "$TOKEN_INVENTARIO"
[ "$CODIGO" = 200 ] || fallar "listar las refacciones"
REFACCIONES="$RESPUESTA"
# Publica una refacción en la tienda (siempre deja la misma publicación).
# $1 código, $2 categoría, $3 descripción, $4 universal (true o false), $5
# compatibilidad en JSON.
publicar_en_tienda() {
  local id
  RESPUESTA="$REFACCIONES"
  id=$(id_en_la_lista "\"codigo\":\"$1\"")
  [ -n "$id" ] || fallar "encontrar la refacción $1"
  pedir PUT "/api/v1/tienda/productos/$id" "$TOKEN_INVENTARIO" \
    "{\"publicada\":true,\"categoria\":\"$2\",\"descripcion\":\"$3\",\"fotoUrl\":null,\"universal\":$4,\"compatibilidad\":$5}"
  [ "$CODIGO" = 200 ] || fallar "publicar $1 en la tienda"
  echo "  publicada   $1 ($2)"
}
COMPATIBLES_COMPACTOS='[{"modelo":"March","anioDesde":2018,"anioHasta":2026},
  {"modelo":"Versa","anioDesde":2018,"anioHasta":2026},{"modelo":"Kicks","anioDesde":2018,"anioHasta":2026},
  {"modelo":"Sentra","anioDesde":2018,"anioHasta":2026}]'
publicar_en_tienda REF-005 ACCESORIO "Par de plumas limpiaparabrisas de silicón para todo clima." true '[]'
publicar_en_tienda REF-006 CUIDADO "Aceite sintético 5W-30, el que usa el taller en tu servicio." true '[]'
publicar_en_tienda REF-001 REFACCION "Filtro de aceite original para motores de 1.6 y 2.0 litros." false \
  "$COMPATIBLES_COMPACTOS"
publicar_en_tienda REF-007 REFACCION "Batería de 12 V libre de mantenimiento con instalación en el taller." false \
  "$COMPATIBLES_COMPACTOS"
publicar_en_tienda REF-008 REFACCION "Amortiguador delantero de gas para X-Trail." false \
  '[{"modelo":"X-Trail","anioDesde":2019,"anioHasta":2026}]'

echo "Carlos Ramírez: orden entregada, pagada con tarjeta, facturada y con un canje (app):"
if [ -z "$TOKEN_CARLOS" ]; then
  echo "  omitido     Carlos no inicia sesión con CLIENTES_CONTRASENA"
else
  perfil_fiscal "$TOKEN_CARLOS" \
    '{"rfc":"RACC880704H33","razonSocial":"CARLOS RAMIREZ","regimenFiscal":"612","codigoPostal":"37160","usoCfdi":"G03","correo":"carlos.ramirez@example.com","predeterminado":true}'
  pedir GET /api/v1/app/pagos "$TOKEN_CARLOS"
  [ "$CODIGO" = 200 ] || fallar "ver los pagos de Carlos"
  if grep -q '"totalElementos":0' <<<"$RESPUESTA"; then
    pedir GET /api/v1/app/vehiculos "$TOKEN_CARLOS"
    [ "$CODIGO" = 200 ] || fallar "listar los vehículos de Carlos"
    ID_SENTRA_CARLOS=$(vehiculo_por_placas GTO-482-B)
    [ -n "$ID_SENTRA_CARLOS" ] || fallar "encontrar el Sentra de Carlos"
    abrir_orden "$TOKEN_CARLOS" "$ID_SENTRA_CARLOS" 20150
    if [ -n "$ORDEN_ID" ]; then
      agregar_renglon "$ORDEN_ID" MANO_DE_OBRA "Servicio de 20,000 km" 1 2450.00
      agregar_renglon "$ORDEN_ID" REFACCION "Filtro de aceite" 1 149.00
      agregar_renglon "$ORDEN_ID" REFACCION "Aceite sintético 5W-30 (litro)" 4 239.00
      agregar_renglon "$ORDEN_ID" REFACCION "Filtro de aire" 1 199.00
      avanzar_orden "$ORDEN_ID" DIAGNOSTICO "Revisamos niveles, frenos y suspensión."
      revisar_punto "$ORDEN_ID" "Frenos" VERDE "Balatas al 70 %."
      revisar_punto "$ORDEN_ID" "Llantas" VERDE "Presión y desgaste correctos."
      avanzar_orden "$ORDEN_ID" EN_REPARACION "Cambiamos aceite y filtros."
      avanzar_orden "$ORDEN_ID" CONTROL_DE_CALIDAD "Prueba de manejo sin observaciones."
      avanzar_orden "$ORDEN_ID" LISTO_PARA_ENTREGA "Tu Sentra está listo."
      pagar_orden "$TOKEN_CARLOS" "$ORDEN_ID" \
        "{\"tarjeta\":{\"token\":\"tok_sim_visa\",\"mesVence\":12,\"anioVence\":2030},\"guardarTarjeta\":true,\"perfilFiscalId\":$PERFIL_ID}"
      avanzar_orden "$ORDEN_ID" ENTREGADO "Gracias por tu visita."
      echo "  pagada      orden $ORDEN_ID del Sentra GTO-482-B con la Visa de prueba (factura simulada en camino)"
    else
      echo "  ya existía  una orden abierta del Sentra GTO-482-B"
    fi
  else
    echo "  ya existían los pagos de Carlos"
  fi
  pedir GET /api/v1/app/lealtad/canjes "$TOKEN_CARLOS"
  [ "$CODIGO" = 200 ] || fallar "ver los canjes de Carlos"
  if [ "$RESPUESTA" = "[]" ]; then
    if esperar_puntos "$TOKEN_CARLOS" 300; then
      pedir GET /api/v1/app/lealtad/recompensas "$TOKEN_CARLOS"
      [ "$CODIGO" = 200 ] || fallar "ver las recompensas"
      ID_LAVADO=$(id_en_la_lista '"nombre":"Lavado expr')
      [ -n "$ID_LAVADO" ] || fallar "encontrar la recompensa Lavado exprés"
      pedir POST /api/v1/app/lealtad/canjes "$TOKEN_CARLOS" "{\"recompensaId\":$ID_LAVADO}"
      case "$CODIGO" in
        200 | 201) echo "  canjeado    Lavado exprés (300 puntos)" ;;
        *) fallar "canjear el lavado exprés" ;;
      esac
    else
      echo "  omitido     el canje: los puntos del pago aún no llegan (vuelve a correr el script)"
    fi
  else
    echo "  ya existía  el canje de Carlos"
  fi
  pedir GET /api/v1/app/lealtad/referidos "$TOKEN_CARLOS"
  [ "$CODIGO" = 200 ] || fallar "ver los referidos de Carlos"
  if [ "$RESPUESTA" = "[]" ]; then
    pedir POST /api/v1/app/lealtad/referidos "$TOKEN_CARLOS" \
      '{"nombre":"Mario Pérez","telefono":"4775550199","correo":null,"consentimiento":true}'
    [ "$CODIGO" = 201 ] || fallar "referir a Mario Pérez"
    echo "  referido    Mario Pérez (477 555 0199)"
  else
    echo "  ya existía  el referido de Carlos"
  fi
fi

echo "Ana Torres: pago con Google Pay simulado y datos fiscales que el timbrado rechaza (app):"
if [ -z "$TOKEN_ANA" ]; then
  echo "  omitida     Ana no inicia sesión con CLIENTES_CONTRASENA"
else
  perfil_fiscal "$TOKEN_ANA" \
    '{"rfc":"TAS150310QW3","razonSocial":"TORRES Y ASOCIADOS SA DE CV","regimenFiscal":"601","codigoPostal":"37500","usoCfdi":"G03","correo":"ana.torres@example.com","predeterminado":true}'
  pedir GET /api/v1/app/pagos "$TOKEN_ANA"
  [ "$CODIGO" = 200 ] || fallar "ver los pagos de Ana"
  if grep -q '"totalElementos":0' <<<"$RESPUESTA"; then
    pedir GET /api/v1/app/vehiculos "$TOKEN_ANA"
    [ "$CODIGO" = 200 ] || fallar "listar los vehículos de Ana"
    ID_KICKS_ANA=$(vehiculo_por_placas GTO-157-F)
    [ -n "$ID_KICKS_ANA" ] || fallar "encontrar el Kicks de Ana"
    abrir_orden "$TOKEN_ANA" "$ID_KICKS_ANA" 51600
    if [ -n "$ORDEN_ID" ]; then
      agregar_renglon "$ORDEN_ID" MANO_DE_OBRA "Cambio de balatas delanteras" 1 650.00
      agregar_renglon "$ORDEN_ID" REFACCION "Balatas delanteras" 1 890.00
      avanzar_orden "$ORDEN_ID" EN_REPARACION "Cambiamos las balatas delanteras."
      avanzar_orden "$ORDEN_ID" LISTO_PARA_ENTREGA "Tu Kicks está listo."
      pagar_orden "$TOKEN_ANA" "$ORDEN_ID" '{"billetera":"GOOGLE_PAY","perfilFiscalId":null}'
      avanzar_orden "$ORDEN_ID" ENTREGADO "Gracias por tu visita."
      echo "  pagada      orden $ORDEN_ID del Kicks GTO-157-F con Google Pay, sin factura todavía"
    else
      echo "  ya existía  una orden abierta del Kicks GTO-157-F"
    fi
  else
    echo "  ya existían los pagos de Ana"
  fi
fi

echo "Diego Castillo: orden lista para entrega, por pagar en la app:"
if [ -z "$TOKEN_DIEGO" ]; then
  echo "  omitido     Diego no inicia sesión con CLIENTES_CONTRASENA"
else
  pedir GET /api/v1/app/pagos "$TOKEN_DIEGO"
  [ "$CODIGO" = 200 ] || fallar "ver los pagos de Diego"
  if grep -q '"totalElementos":0' <<<"$RESPUESTA"; then
    pedir GET /api/v1/app/vehiculos "$TOKEN_DIEGO"
    [ "$CODIGO" = 200 ] || fallar "listar los vehículos de Diego"
    ID_MARCH_DIEGO=$(vehiculo_por_placas GTO-218-T)
    [ -n "$ID_MARCH_DIEGO" ] || fallar "encontrar el March de Diego"
    abrir_orden "$TOKEN_DIEGO" "$ID_MARCH_DIEGO" 27900
    if [ -n "$ORDEN_ID" ]; then
      agregar_renglon "$ORDEN_ID" MANO_DE_OBRA "Afinación mayor" 1 2350.00
      agregar_renglon "$ORDEN_ID" REFACCION "Bujía de iridio" 4 259.00
      avanzar_orden "$ORDEN_ID" DIAGNOSTICO "Revisamos encendido e inyectores."
      revisar_punto "$ORDEN_ID" "Bujías" ROJO "Desgaste fuerte; se cambian en la afinación."
      revisar_punto "$ORDEN_ID" "Batería" AMARILLO "Al 60 %; conviene cambiarla en tu siguiente servicio."
      revisar_punto "$ORDEN_ID" "Luces" VERDE ""
      avanzar_orden "$ORDEN_ID" EN_REPARACION "Hacemos la afinación mayor."
      avanzar_orden "$ORDEN_ID" LAVADO "Lavado de cortesía."
      avanzar_orden "$ORDEN_ID" LISTO_PARA_ENTREGA "Tu March está listo; puedes pagar desde la app."
      echo "  lista       orden $ORDEN_ID del March GTO-218-T, por pagar"
    else
      echo "  ya existía  la orden del March GTO-218-T"
    fi
  else
    echo "  ya existían los pagos de Diego"
  fi
fi

echo "Sofía Morales: orden en el taller con un trabajo adicional, apartado, gastos y más (app):"
if [ -z "$TOKEN_SOFIA" ]; then
  echo "  omitida     Sofía no inicia sesión con CLIENTES_CONTRASENA"
else
  pedir GET /api/v1/app/vehiculos "$TOKEN_SOFIA"
  [ "$CODIGO" = 200 ] || fallar "listar los vehículos de Sofía"
  ID_KICKS_SOFIA=$(vehiculo_por_placas NMR-48-19)
  ID_VERSA_SOFIA=$(vehiculo_por_placas GTO-127-P)
  [ -n "$ID_KICKS_SOFIA" ] && [ -n "$ID_VERSA_SOFIA" ] || fallar "encontrar los vehículos de Sofía"
  abrir_orden "$TOKEN_SOFIA" "$ID_KICKS_SOFIA" 48100
  if [ -n "$ORDEN_ID" ]; then
    agregar_renglon "$ORDEN_ID" MANO_DE_OBRA "Diagnóstico de frenos y suspensión" 1 450.00
    avanzar_orden "$ORDEN_ID" DIAGNOSTICO "Revisamos frenos, suspensión y llantas."
    revisar_punto "$ORDEN_ID" "Frenos traseros" ROJO "Balatas traseras al 15 %."
    revisar_punto "$ORDEN_ID" "Llantas" AMARILLO "Desgaste parejo; cambiarlas en unos 5,000 km."
    revisar_punto "$ORDEN_ID" "Suspensión" VERDE "Sin ruidos ni fugas."
    revisar_punto "$ORDEN_ID" "Niveles de fluidos" VERDE ""
    avanzar_orden "$ORDEN_ID" ESPERANDO_APROBACION "Esperamos tu respuesta sobre las balatas traseras."
    pedir POST "/api/v1/taller/ordenes/$ORDEN_ID/adicionales" "$TOKEN_TALLER" \
      '{"concepto":"Cambio de balatas traseras","manoDeObra":650.00,"refacciones":890.00}'
    [ "$CODIGO" = 201 ] || fallar "proponer el cambio de balatas traseras"
    echo "  en taller   orden $ORDEN_ID del Kicks NMR-48-19, con un trabajo adicional por aprobar"
  else
    echo "  ya existía  la orden del Kicks NMR-48-19"
  fi

  pedir GET /api/v1/app/tienda/pedidos "$TOKEN_SOFIA"
  [ "$CODIGO" = 200 ] || fallar "ver los pedidos de Sofía"
  if grep -q '"totalElementos":0' <<<"$RESPUESTA"; then
    RESPUESTA="$REFACCIONES"
    ID_PLUMAS=$(id_en_la_lista '"codigo":"REF-005"')
    ID_ACEITE=$(id_en_la_lista '"codigo":"REF-006"')
    pedir POST /api/v1/app/tienda/pedidos "$TOKEN_SOFIA" \
      "{\"renglones\":[{\"productoId\":$ID_PLUMAS,\"cantidad\":1},{\"productoId\":$ID_ACEITE,\"cantidad\":4}],\"entrega\":\"mostrador\",\"citaId\":null}" \
      "Idempotency-Key: datos-de-ejemplo-apartado-sofia"
    case "$CODIGO" in
      200 | 201) echo "  apartado    plumas y 4 litros de aceite, para recoger en mostrador (vence en 48 h)" ;;
      *) fallar "apartar en la tienda" ;;
    esac
  else
    echo "  ya existía  el apartado de Sofía"
  fi

  pedir GET "/api/v1/app/vehiculos/$ID_VERSA_SOFIA/gastos" "$TOKEN_SOFIA"
  [ "$CODIGO" = 200 ] || fallar "ver los gastos del Versa de Sofía"
  if grep -q '"totalElementos":0' <<<"$RESPUESTA"; then
    # $1 cuerpo de NuevoGasto.
    registrar_gasto() {
      pedir POST "/api/v1/app/vehiculos/$ID_VERSA_SOFIA/gastos" "$TOKEN_SOFIA" "$1"
      case "$CODIGO" in
        200 | 201) ;;
        *) fallar "registrar un gasto del Versa" ;;
      esac
    }
    registrar_gasto "{\"tipo\":\"GASOLINA\",\"monto\":850.00,\"litros\":36.5,\"km\":15600,\"fecha\":\"$(fecha_hace_dias 20)\",\"descripcion\":\"Tanque lleno\"}"
    registrar_gasto "{\"tipo\":\"CASETAS\",\"monto\":245.00,\"litros\":null,\"km\":null,\"fecha\":\"$(fecha_hace_dias 12)\",\"descripcion\":\"León-Querétaro\"}"
    registrar_gasto "{\"tipo\":\"GASOLINA\",\"monto\":780.00,\"litros\":33.2,\"km\":16050,\"fecha\":\"$(fecha_hace_dias 5)\",\"descripcion\":null}"
    registrar_gasto "{\"tipo\":\"LAVADO\",\"monto\":150.00,\"litros\":null,\"km\":null,\"fecha\":\"$(fecha_hace_dias 2)\",\"descripcion\":null}"
    echo "  registrados 4 gastos del Versa GTO-127-P (2 cargas de gasolina, casetas y lavado)"
  else
    echo "  ya existían los gastos del Versa GTO-127-P"
  fi

  pedir GET /api/v1/app/direcciones "$TOKEN_SOFIA"
  [ "$CODIGO" = 200 ] || fallar "ver las direcciones de Sofía"
  if [ "$RESPUESTA" = "[]" ]; then
    pedir POST /api/v1/app/direcciones "$TOKEN_SOFIA" \
      '{"alias":"Casa","calle":"Blvd. Campestre 1520","colonia":"Jardines del Moral","cp":"37160","ciudad":"León","lat":21.1520,"lng":-101.7105}'
    [ "$CODIGO" = 201 ] || fallar "guardar la dirección de Sofía"
    echo "  guardada    dirección Casa, para pedir la recolección"
  else
    echo "  ya existía  la dirección de Sofía"
  fi

  pedir GET /api/v1/app/catalogo "$TOKEN_SOFIA"
  [ "$CODIGO" = 200 ] || fallar "ver el catálogo"
  ID_XTRAIL=$(id_en_la_lista '"marca":"Nissan","modelo":"X-Trail"')
  [ -n "$ID_XTRAIL" ] || fallar "encontrar la X-Trail en el catálogo"
  pedir POST /api/v1/app/estreno/solicitudes "$TOKEN_SOFIA" \
    "{\"tipo\":\"PRUEBA_MANEJO\",\"modeloCatalogoId\":$ID_XTRAIL,\"preferenciaFecha\":\"$(fecha_en_dias 3)\",\"preferenciaFranja\":\"MANANA\"}"
  case "$CODIGO" in
    201) echo "  pedida      prueba de manejo de la X-Trail (llega al CRM como prospecto de la app)" ;;
    409) echo "  ya existía  la prueba de manejo de la X-Trail" ;;
    *) fallar "pedir la prueba de manejo" ;;
  esac
fi

echo
echo "Listo. Qué demuestra cada dato: docs/datos-de-ejemplo.md. Contraseñas: README, sección 5.6."
