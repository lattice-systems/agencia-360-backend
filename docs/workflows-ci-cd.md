# Workflows de GitHub Actions (CI/CD)

Todo vive en `.github/workflows/` y corre automáticamente en GitHub. Este documento explica qué hace cada uno y, sobre todo, **qué correr en tu máquina antes de subir cambios** para no descubrir un error hasta que ya subiste el PR.

## Resumen

| Workflow | Archivo | Cuándo corre | Qué revisa |
|---|---|---|---|
| CI | `ci.yml` | Push/PR a `main` | Que el proyecto compile y todas las pruebas pasen |
| Lint | `lint.yml` | Push/PR a `main` | Formato de código (Spotless) y reglas de estilo (Checkstyle) |
| CodeQL | `codeql.yml` | Push/PR a `main` + todos los lunes | Vulnerabilidades y patrones de código inseguro |
| Dependency Review | `dependency-review.yml` | Solo en PR | Que no se agregue una dependencia con una vulnerabilidad conocida |
| Dependabot | `dependabot.yml` (config, no es un workflow) | Semanal | Abre PRs para actualizar dependencias de Maven y de las Actions |
| Publicar paquete | `publish.yml` | Al publicar un Release en GitHub | Compila, prueba y sube el `.jar` a GitHub Packages |

## 1. CI: build y pruebas (`ci.yml`)

Corre `./mvnw verify`: compila el proyecto y ejecuta todas las pruebas (unitarias, de Spring Modulith y de integración con Testcontainers). Si algo falla, el PR queda marcado en rojo y se suben los reportes de `surefire`/`failsafe` como artefacto para poder revisarlos sin tener que reproducir el fallo en local.

## 2. Lint: formato y estilo (`lint.yml`)

Tiene dos jobs:

- **Formato (Spotless)**: revisa indentación, imports desordenados, espacios en blanco al final de línea, etc. Usa el formateador de Eclipse (no `google-java-format`, que todavía no funciona bien con Java 25).
- **Linting (Checkstyle)**: revisa errores reales de código con las reglas de `config/checkstyle/checkstyle.xml` — imports sin usar, `equals`/`hashCode` inconsistentes, bloques `if`/`for` sin llaves, líneas de más de 140 caracteres, etc. A propósito **no** exige Javadoc en todo ni prohíbe tabs, para no generar ruido sin encontrar bugs reales.

### Cómo lintear tu código antes de subirlo

Antes de hacer `git push`, corre esto desde la raíz del proyecto:

```bash
# 1. Corrige el formato automáticamente
./mvnw spotless:apply

# 2. Revisa las reglas de estilo (esto no autocorrige, hay que arreglar a mano lo que marque)
./mvnw checkstyle:check

# 3. Corre las pruebas
./mvnw test
```

Si los tres comandos pasan en tu máquina, el workflow de CI y el de Lint van a pasar en GitHub. Si `spotless:apply` cambió archivos, agrégalos al commit antes de subir.

## 3. Seguridad: CodeQL y dependencias

- **CodeQL** (`codeql.yml`): análisis estático de GitHub para encontrar vulnerabilidades (inyección, deserialización insegura, etc.). Corre en cada push/PR a `main` y también una vez por semana (lunes) para atrapar vulnerabilidades reportadas después de que el código ya estaba en el repo. Los hallazgos aparecen en la pestaña **Security → Code scanning** del repositorio, no en el PR directamente.
- **Dependency Review** (`dependency-review.yml`): en cada PR, compara las dependencias que se agregan o cambian contra la base de datos de vulnerabilidades de GitHub. Si se introduce una dependencia con severidad **alta o crítica**, bloquea el PR y deja un comentario explicando cuál es.
- **Dependabot** (`.github/dependabot.yml`): no es un workflow, es una revisión semanal (lunes) que abre PRs solo para actualizar versiones de dependencias de Maven (`pom.xml`) y de las Actions usadas en los workflows. Esos PRs pasan por CI y Lint como cualquier otro.

## 4. Publicación del paquete Maven (`publish.yml`)

Se publica el `.jar` a **GitHub Packages** (el registro Maven del propio repositorio), pero **solo cuando se crea un Release en GitHub** — no en cada push, para no llenar el registro de snapshots de cada commit.

Se publican tres artefactos:

- El `.jar` plano (por si algún día se necesita como librería).
- El `.jar` ejecutable de Spring Boot, con el clasificador `exec` (`backend-agencia360-<version>-exec.jar`) — este es el que se corre con `java -jar`.
- El `.jar` de fuentes.

**Antes de crear un Release**, hay que quitar el `-SNAPSHOT` de la versión en `pom.xml` (línea `<version>0.0.1-SNAPSHOT</version>`) y subir ese cambio; si no, GitHub Packages queda con una versión `-SNAPSHOT` publicada como release, lo cual es confuso. El workflow no hace este cambio automáticamente — es una decisión manual del equipo (qué versión es cada release).

El paquete publicado queda visible en `https://github.com/lattice-systems/agencia-360-backend/packages`. Para consumirlo desde otro proyecto Maven hace falta autenticarse contra GitHub Packages con un token que tenga permiso `read:packages` (esto no aplica todavía porque nada más lo consume).

## Dónde ver los resultados

- **Pestaña "Actions"** del repo: corridas de CI y Lint, con logs completos.
- **Pestaña "Security" → "Code scanning"**: alertas de CodeQL.
- **Comentario automático en el PR**: resumen de Dependency Review.
- **Pestaña "Pull requests"**: PRs automáticos de Dependabot.
- **Pestaña "Packages"** (barra lateral del repo): versiones publicadas del `.jar`.

## Pendiente / recomendación para el equipo

Los workflows corren y marcan el PR en rojo o verde, pero **nada impide todavía mergear un PR en rojo**. Para que de verdad sean obligatorios, hay que activar en GitHub, en `Settings → Branches → Branch protection rules` para `main`, la opción de exigir que los checks de CI y Lint pasen antes de poder mergear. No se activó automáticamente porque es una regla que afecta a todo el equipo (nadie podría mergear si un check falla), así que conviene decidirlo en conjunto antes de prenderla.
