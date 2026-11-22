# ADR 0001: Descartar Redis del backend

- **Estado**: Aceptada
- **Decidida por**: Scrum Master del equipo
- **Fecha**: 2026-11-22

## Contexto

El backend incluía `spring-boot-starter-data-redis`, la configuración `spring.data.redis.*`, un servicio `redis` en `docker-compose.yml` y la mención de Redis en el README como caché y colas. Ningún código lo usa: solo `AvisosDeVehiculos` lo nombraba, en un comentario, como mejora futura si la API corre en varias instancias.

En Azure el entorno es *express* (suscripción de estudiante), que no acepta un segundo contenedor, así que Redis no puede correr ahí. Para que el health check no marcara `DOWN` se apagaba con `MANAGEMENT_HEALTH_REDIS_ENABLED=false`.

Además, la dependencia traía Lettuce y Netty, y obligaba a mantener un override de versión de Netty por CVE (`netty.version`).

## Opciones

1. **Mantener Redis** sin uso: cuesta un servicio local, configuración, health check y parches de CVE sin dar nada.
2. **Redis administrado** (p. ej. Upstash): resuelve Azure, pero añade costo, secretos y una dependencia externa sin necesidad real hoy.
3. **Descartar Redis**: quitar dependencia, configuración y servicio; reabrir la decisión si aparece una necesidad concreta.

## Decisión

Se descarta Redis (opción 3).

## Consecuencias

- Se elimina la dependencia, `spring.data.redis.*`, el servicio `redis` del `docker-compose.yml`, `REDIS_HOST`/`REDIS_PORT` y el badge y las secciones del README.
- Al desaparecer Lettuce ya no hay Netty en el árbol de dependencias (verificado con `./mvnw dependency:tree`), por lo que se quita el override `netty.version`.
- `MANAGEMENT_HEALTH_REDIS_ENABLED` deja de ser necesaria en Azure; la Container App puede quitarla.
- Si la API llega a correr en varias réplicas, el aviso de `AvisosDeVehiculos` necesitará un bus externo; se evaluará entonces con su propio ADR.
- Si en el futuro se necesita caché o colas, se abre un ADR nuevo que sustituya a este.
