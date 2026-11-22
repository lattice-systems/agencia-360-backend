# Despliegue: Azure Container Apps + Neon

El backend corre en la nube para que la app móvil (y quien la pruebe) no dependa de la IP de una laptop.

| Pieza | Dónde | Detalle |
| --- | --- | --- |
| API | Azure Container Apps, grupo `rg-agencia360`, entorno `cae-agencia360-c`, app `ca-agencia360-api`, región **East US 2** | `https://ca-agencia360-api.calmcliff-d0e7fc3b.eastus2.azurecontainerapps.io` · se apaga sin tráfico (0–1 réplicas, 0.75 vCPU / 1.5 GiB) |
| Base de datos | Neon, organización "Ojeda Luna", proyecto `carecar`, rama `production`, base `neondb`, región `aws-us-east-2` | PostgreSQL 18 · conexión directa con `sslmode=require` |
| Imagen | `ghcr.io/lattice-systems/agencia-360-backend` (paquete público) | Etiqueta con el commit, p. ej. `980766a-azure` |
| Correo | Gmail (`smtp.gmail.com:587`, STARTTLS) | Contraseña de aplicación en el secreto `smtp-password` |

La app móvil usa `API_BASE_URL=https://ca-agencia360-api.calmcliff-d0e7fc3b.eastus2.azurecontainerapps.io/api/v1/app` (variable de repositorio de `carecar-mobile`, la usa el workflow `Release`).

## Por qué así

- **Se apaga sin uso**: entra en la capa gratuita de Container Apps. La primera petición después de un rato tarda 20–40 s en despertar (arranque de Spring Boot); después responde normal.
- **Conexión directa a Neon, no el pooler**: la API ya tiene su pool (HikariCP) y Flyway usa bloqueos de sesión que el pooler en modo transacción no garantiza.
- **Sin Redis**: hoy ningún código usa Redis (solo la dependencia y su configuración; `AvisosDeVehiculos` lo menciona como mejora futura). La suscripción de estudiante solo permite entornos *express*, que no aceptan un segundo contenedor. Se apaga su revisión de salud con `MANAGEMENT_HEALTH_REDIS_ENABLED=false`. Si un módulo llega a necesitar Redis, hay que usar uno administrado (p. ej. Upstash) con `SPRING_DATA_REDIS_URL`.
- **Regiones**: la suscripción "Azure for Students" solo permite `southcentralus`, `canadacentral`, `centralus`, `mexicocentral` y `eastus2`; `eastus2` es la más cercana a Neon `us-east-2`.

## Variables y secretos

Perfil `prod`. Los valores sensibles son **secretos de la Container App** (nunca en el repo); Daniel tiene la copia local en `lattice-systems/.secretos-azure.env` (fuera de los repos).

| Variable | Origen |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | secretos `db-url`, `db-username`, `db-password` (Neon) |
| `JWT_SECRET`, `MANUALES_SECRETO` | secretos aleatorios |
| `ADMIN_INICIAL_USUARIO` / `ADMIN_INICIAL_CONTRASENA` | `admin` / secreto `admin-contrasena` |
| `GERENTE_INICIAL_USUARIO` / `GERENTE_INICIAL_CONTRASENA` | `gerente` / secreto `gerente-contrasena` |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME` | `smtp.gmail.com`, `587`, la cuenta de Gmail |
| `SPRING_MAIL_PASSWORD` | secreto `smtp-password` |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `..._STARTTLS_ENABLE` | `true` |
| `CORREO_REMITENTE` | `CareCar <cuenta de Gmail>` (Gmail exige que coincida con la cuenta) |
| `MANUALES_DIRECTORIO` | `/home/api/manuales` |
| `MANAGEMENT_HEALTH_REDIS_ENABLED` | `false` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` |

Cambiar un secreto (p. ej. la contraseña de aplicación de Gmail, que son 16 letras) y aplicarlo. Los entornos *express* no permiten `revision restart`; se crea una revisión nueva cambiando una variable inofensiva, y la revisión nueva lee los secretos al arrancar:

```bash
az containerapp secret set -n ca-agencia360-api -g rg-agencia360 --secrets smtp-password=<valor>
az containerapp update -n ca-agencia360-api -g rg-agencia360 --set-env-vars REINICIO_SECRETOS=$(date +%s)
```

Para ver los registros (no hay Log Analytics): `az containerapp logs show -n ca-agencia360-api -g rg-agencia360 --tail 100 --format text`.

## Publicar una versión nueva de la API

Hoy es manual (el despliegue automático es `V2-T05.11`).

1. Construir la imagen: `docker build -t ghcr.io/lattice-systems/agencia-360-backend:<sha> .`
2. **Subirla en formato OCI puro.** El constructor clásico de Docker deja la capa del `.jar` con tipo Docker dentro de un manifiesto OCI, y el entorno *express* de Azure la rechaza (`unsupported MIME type for compression: "application/vnd.docker.image.rootfs.diff.tar.gzip"`). Se exporta y se sube con skopeo:
   ```bash
   docker save -o img.tar ghcr.io/lattice-systems/agencia-360-backend:<sha>
   docker run --rm -v "$PWD:/w:ro" quay.io/skopeo/stable copy --authfile /w/auth.json \
     --format oci --dest-compress-format gzip \
     docker-archive:/w/img.tar docker://ghcr.io/lattice-systems/agencia-360-backend:<sha>-azure
   ```
   (`auth.json` con un token de GitHub con `write:packages`; bórralo al terminar.)
3. Actualizar la app: `az containerapp update -n ca-agencia360-api -g rg-agencia360 --image ghcr.io/lattice-systems/agencia-360-backend:<sha>-azure`
4. Comprobar: `curl https://ca-agencia360-api.calmcliff-d0e7fc3b.eastus2.azurecontainerapps.io/actuator/health` → `{"status":"UP"}`.

Flyway aplica las migraciones nuevas al arrancar.

## Datos

La base de Neon tiene los **datos de ejemplo** (`scripts/cargar-datos-de-ejemplo.sh`, sección 5.6 del README): clientes con vehículos y servicios, catálogo, y la clienta de la app `laura.mendez@example.com` con sus citas. Las contraseñas de esos usuarios de ejemplo son las del README (públicas); el administrador y el gerente iniciales tienen contraseñas aleatorias.

## Limitaciones conocidas

- **Manuales PDF**: se guardan en el disco del contenedor (`MANUALES_DIRECTORIO`), que se borra en cada reinicio; la app usa su respaldo incluido. Lo resuelve el almacenamiento de archivos de `V2-T05.09`.
- **Usuarios de ejemplo con contraseñas públicas**: aceptable mientras la base solo tenga datos de prueba; antes de datos reales hay que borrarlos (`scripts/limpiar-datos.sh`) y rotar contraseñas.
- **Sin logs en Azure**: el entorno se creó sin Log Analytics (cuesta por GB). Para depurar: `az containerapp logs show -n ca-agencia360-api -g rg-agencia360 --follow`.
- **Arranque en frío** de 20–40 s tras inactividad.
