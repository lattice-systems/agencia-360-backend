# Despliegue: Azure Container Apps + Neon

El backend corre en la nube para que la app móvil (y quien la pruebe) no dependa de la IP de una laptop.

| Pieza | Dónde | Detalle |
| --- | --- | --- |
| API | Azure Container Apps, grupo `rg-agencia360`, entorno `cae-agencia360-c`, app `ca-agencia360-api`, región **East US 2** | `https://ca-agencia360-api.calmcliff-d0e7fc3b.eastus2.azurecontainerapps.io` · se apaga sin tráfico (0–1 réplicas, 0.75 vCPU / 1.5 GiB) |
| Base de datos | Neon, organización "Ojeda Luna", proyecto `carecar`, rama `production`, base `neondb`, región `aws-us-east-2` | PostgreSQL 18 · conexión directa con `sslmode=require` |
| Imagen | `ghcr.io/lattice-systems/agencia-360-backend` (paquete público) | Etiqueta con el sha corto del commit de `main` (p. ej. `5c3ba08`) y `latest`; la publica el workflow `Despliegue a Azure` |
| Correo | Gmail (`smtp.gmail.com:587`, STARTTLS) | Contraseña de aplicación en el secreto `smtp-password` |

La app móvil usa `API_BASE_URL=https://ca-agencia360-api.calmcliff-d0e7fc3b.eastus2.azurecontainerapps.io/api/v1/app` (variable de repositorio de `carecar-mobile`, la usa el workflow `Release`).

## Por qué así

- **Se apaga sin uso**: entra en la capa gratuita de Container Apps. La primera petición después de un rato tarda 20–40 s en despertar (arranque de Spring Boot); después responde normal.
- **Conexión directa a Neon, no el pooler**: la API ya tiene su pool (HikariCP) y Flyway usa bloqueos de sesión que el pooler en modo transacción no garantiza.
- **Sin Redis**: se descartó del proyecto ([ADR 0001](decisiones/0001-descartar-redis.md)); la variable `MANAGEMENT_HEALTH_REDIS_ENABLED` se quitó de la Container App tras el primer despliegue automático.
- **Regiones**: la suscripción "Azure for Students" solo permite `southcentralus`, `canadacentral`, `centralus`, `mexicocentral` y `eastus2`; `eastus2` es la más cercana a Neon `us-east-2`.

## Variables y secretos

Perfil `prod`. Los valores sensibles son **secretos de la Container App** (nunca en el repo); Daniel tiene la copia local en `lattice-systems/.secretos-azure.env` (fuera de los repos).

| Variable | Origen |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | secretos `db-url`, `db-username`, `db-password` (Neon) |
| `JWT_SECRET`, `MANUALES_SECRETO`, `CITAS_QR_SECRETO` | secretos aleatorios `jwt-secret`, `manuales-secreto`, `citas-qr-secreto` |
| `ADMIN_INICIAL_USUARIO` / `ADMIN_INICIAL_CONTRASENA` | `admin` / secreto `admin-contrasena` |
| `GERENTE_INICIAL_USUARIO` / `GERENTE_INICIAL_CONTRASENA` | `gerente` / secreto `gerente-contrasena` |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME` | `smtp.gmail.com`, `587`, la cuenta de Gmail |
| `SPRING_MAIL_PASSWORD` | secreto `smtp-password` |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `..._STARTTLS_ENABLE` | `true` |
| `CORREO_REMITENTE` | `CareCar <cuenta de Gmail>` (Gmail exige que coincida con la cuenta) |
| `WALLET_APPLE_PASS_TYPE_ID`, `WALLET_APPLE_TEAM_ID` | ID del Pass Type ID y Team ID de la cuenta de desarrollador de Apple (opcionales) |
| `WALLET_APPLE_CERT_P12_BASE64`, `WALLET_APPLE_CERT_PASSWORD`, `WALLET_APPLE_WWDR_PEM_BASE64` | secretos `wallet-apple-cert-p12`, `wallet-apple-cert-password`, `wallet-apple-wwdr-pem` (base64 de los archivos) |
| `WALLET_GOOGLE_ISSUER_ID` | ID de emisor de Google Wallet (opcional) |
| `WALLET_GOOGLE_SERVICE_ACCOUNT_JSON_BASE64` | secreto `wallet-google-service-account` (base64 del JSON de la cuenta de servicio) |
| `MANUALES_ALMACEN` | sin definir (por omisión `base`: los PDF de los manuales van a la tabla `archivo_manual` de Neon). `MANUALES_DIRECTORIO` solo se usa con `MANUALES_ALMACEN=local` |
| `GEMINI_API_KEY` | secreto `gemini-api-key`: llave del proyecto de Google AI Studio del equipo en plan **Free, sin facturación** (costo cero; ver ADR [0007](https://github.com/lattice-systems/carecar-mobile/blob/main/docs/decisiones/0007-asistente-ia.md)). La usará el asistente (M24); nunca vincular facturación a ese proyecto |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` |

Cambiar un secreto (p. ej. la contraseña de aplicación de Gmail, que son 16 letras) y aplicarlo. Los entornos *express* no permiten `revision restart`; se crea una revisión nueva cambiando una variable inofensiva, y la revisión nueva lee los secretos al arrancar:

```bash
az containerapp secret set -n ca-agencia360-api -g rg-agencia360 --secrets smtp-password=<valor>
az containerapp update -n ca-agencia360-api -g rg-agencia360 --set-env-vars REINICIO_SECRETOS=$(date +%s)
```

Para ver los registros (no hay Log Analytics): `az containerapp logs show -n ca-agencia360-api -g rg-agencia360 --tail 100 --format text`.

## Despliegue automático (`V2-T05.11`)

Cada merge a `main` que toque código despliega la API solo, con el workflow [`.github/workflows/despliegue.yml`](../.github/workflows/despliegue.yml) (`Despliegue a Azure`). Los cambios que solo tocan `docs/**`, `plan/**` o archivos `.md` no despliegan. Las pruebas no se repiten: el PR ya pasó CI antes de integrarse.

1. **Construye y publica la imagen** con Buildx en `ghcr.io/lattice-systems/agencia-360-backend:<sha corto>` y `:latest`, con el `GITHUB_TOKEN` del workflow (`packages: write`).
2. **Verifica el formato.** El entorno *express* de Azure rechaza un manifiesto OCI con capas de tipo Docker (`unsupported MIME type for compression: "application/vnd.docker.image.rootfs.diff.tar.gzip"`). Por eso la imagen sale en OCI puro (`oci-mediatypes=true`, `compression=gzip`, `force-compression=true`, sin provenance ni SBOM) y un paso revisa con `docker buildx imagetools inspect --raw` que el manifiesto, la configuración y todas las capas sean OCI; si no, el job falla antes de tocar Azure.
3. **Entra a Azure por OIDC** (`azure/login`), sin contraseñas ni secretos guardados en GitHub.
4. **Actualiza la app**: `az containerapp update -n ca-agencia360-api -g rg-agencia360 --image <imagen>` (con `-o none`: el registro de Actions es público y la salida incluiría valores de variables de entorno).
5. **Comprueba** durante hasta 4 minutos (el arranque en frío tarda ~35 s) que `/actuator/health` responde `UP` y que la revisión activa usa la imagen nueva y está corriendo. Si no, el job falla y muestra el estado de las revisiones.

Solo corre un despliegue a la vez (`concurrency: despliegue-azure`); si se integran varios PR seguidos, el que está en curso termina y luego se despliega el más nuevo.

### Identidad y permisos

| Pieza | Valor |
| --- | --- |
| Identidad administrada (asignada por el usuario) | `id-agencia360-deploy`, en `rg-agencia360` |
| Credencial federada | `github-main`: emisor `https://token.actions.githubusercontent.com`, sujeto `repo:lattice-systems@259185439/agencia-360-backend@1391219760:ref:refs/heads/main` (la org usa el formato de sujeto inmutable de GitHub, con los ID de org y repo; el formato corto da `AADSTS700213`), audiencia `api://AzureADTokenExchange` |
| Rol | **Contributor solo sobre la Container App** `ca-agencia360-api` (no sobre el grupo ni la suscripción) |
| Variables del repositorio (no son secretos) | `AZURE_CLIENT_ID` (client id de la identidad), `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` |

El sujeto de la credencial federada solo acepta tokens de la rama `main` de este repositorio: un PR, otra rama o un fork no pueden entrar a Azure. Si el workflow llegara a usar `environment:`, el sujeto cambia (`repo:…:environment:<nombre>`) y hay que crear otra credencial federada.

Para revisar o recrear la configuración:

```bash
az identity show -n id-agencia360-deploy -g rg-agencia360 --query clientId -o tsv
az identity federated-credential list --identity-name id-agencia360-deploy -g rg-agencia360 -o table
az role assignment list --assignee <clientId> --all -o table
gh variable list -R lattice-systems/agencia-360-backend
```

### Relanzarlo a mano

Desde la pestaña **Actions → Despliegue a Azure → Run workflow** (rama `main`), o:

```bash
gh workflow run despliegue.yml -R lattice-systems/agencia-360-backend --ref main
gh run watch -R lattice-systems/agencia-360-backend $(gh run list -R lattice-systems/agencia-360-backend -w despliegue.yml -L 1 --json databaseId -q '.[0].databaseId')
```

Solo funciona con `--ref main` (la credencial federada no acepta otras ramas). Construye y despliega el último commit de `main`.

### Volver a una imagen anterior

Cada despliegue deja en GHCR la imagen etiquetada con su sha corto (ver las versiones en <https://github.com/orgs/lattice-systems/packages/container/package/agencia-360-backend>). Para regresar a una:

```bash
az containerapp update -n ca-agencia360-api -g rg-agencia360 \
  --image ghcr.io/lattice-systems/agencia-360-backend:<sha corto anterior> -o none
curl https://ca-agencia360-api.calmcliff-d0e7fc3b.eastus2.azurecontainerapps.io/actuator/health
```

Ojo: Flyway no deshace migraciones. Si entre las dos versiones hubo una migración nueva, la imagen vieja arranca con `validate` contra un esquema más nuevo y puede fallar; en ese caso lo correcto es un PR que revierta el cambio (con una migración nueva si hace falta) y dejar que el despliegue automático lo suba. La reversión a mano dura hasta el siguiente merge a `main`, que vuelve a desplegar lo último.

### Publicar a mano (solo si GitHub Actions no está disponible)

```bash
docker buildx build --provenance=false --sbom=false \
  --output type=image,name=ghcr.io/lattice-systems/agencia-360-backend:<sha>,push=true,oci-mediatypes=true,compression=gzip,force-compression=true .
az containerapp update -n ca-agencia360-api -g rg-agencia360 --image ghcr.io/lattice-systems/agencia-360-backend:<sha> -o none
```

Hace falta `docker login ghcr.io` con un token con `write:packages` (cierra sesión con `docker logout ghcr.io` al terminar). Flyway aplica las migraciones nuevas al arrancar.

## Datos

La base de Neon tiene los **datos de ejemplo** (`scripts/cargar-datos-de-ejemplo.sh`, sección 5.6 del README): clientes con vehículos y servicios, catálogo, y la clienta de la app `laura.mendez@example.com` con sus citas. Las contraseñas de esos usuarios de ejemplo son las del README (públicas); el administrador y el gerente iniciales tienen contraseñas aleatorias.

## Limitaciones conocidas

- **Manuales PDF**: se guardan en la base (tabla `archivo_manual`), porque el disco del contenedor se borra en cada revisión. Cabe en el plan gratis de Neon mientras sean pocos manuales; si crecen, el almacenamiento de archivos de `V2-T05.09` los mueve a otro lado sin cambiar el contrato. Para reponerlos: `API=<url> GERENTE_USUARIO=… GERENTE_CONTRASENA=… scripts/subir-manuales.sh`.
- **Usuarios de ejemplo con contraseñas públicas**: aceptable mientras la base solo tenga datos de prueba; antes de datos reales hay que borrarlos (`scripts/limpiar-datos.sh`) y rotar contraseñas.
- **Sin logs en Azure**: el entorno se creó sin Log Analytics (cuesta por GB). Para depurar: `az containerapp logs show -n ca-agencia360-api -g rg-agencia360 --follow`.
- **Arranque en frío** de 20–40 s tras inactividad.
- **Sin Swagger en producción**: el perfil `prod` apaga Swagger UI y `/v3/api-docs` porque la API es pública. El contrato se consulta levantando el backend en local.
