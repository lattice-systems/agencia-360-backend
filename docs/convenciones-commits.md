# Contribuir a Agencia360

Estas reglas aplican al backend, al ERP frontend y al CRM. Si el CRM vive en otro repositorio, conserva el mismo flujo y configura los checks de ese proyecto.

## Regla principal

`main` debe permanecer estable. Integramos cambios únicamente mediante pull request (PR), con al menos una aprobación de otra persona y todos los checks requeridos en verde. No hagas push directo a `main`.

## Flujo de trabajo

1. Actualiza tu copia local de `main`.
2. Crea una rama corta para una sola tarea.
3. Haz commits con mensajes claros y publica la rama.
4. Abre un PR hacia `main`, completa la descripción y espera a que termine CI.
5. Atiende los comentarios y consigue al menos una aprobación.
6. Cuando todos los checks estén verdes y no queden conversaciones pendientes, integra el PR con **Squash and merge** y borra la rama.

## Nombres de ramas

Usa este formato:

```text
<tipo>/<issue>-<descripcion-corta>
```

Ejemplos:

```text
feat/tec-2-git-workflow
fix/api-validar-vin
docs/tec-2-guia-git
```

Tipos comunes: `feat` para una función nueva, `fix` para una corrección, `docs` para documentación, `test` para pruebas, `build` para `pom.xml` y dependencias, y `ci` o `chore` para automatización o mantenimiento.

## Mensajes de commit

Usa este formato:

```text
<tipo>(<alcance>): <verbo + cambio>
```

Ejemplos:

```text
feat(inventario): agregar recepción de refacciones
fix(api): validar formato del VIN
docs(tec-2): documentar flujo de PR
test(erp): cubrir validación del formulario
ci(backend): ejecutar pruebas en cada push
```

Escribe mensajes breves y concretos. Evita mensajes como `cambios`, `update` o `fix` sin contexto. El alcance entre paréntesis puede ser un módulo o área; se puede omitir si no aporta claridad.

## Pull requests

El título del PR debe resumir el cambio. Puedes usar el mismo formato que un commit:

```text
docs(TEC-2): documentar flujo de ramas y PR
```

Incluye en la descripción:

- **Qué cambió:** resumen breve y issue relacionada.
- **Cómo se probó:** pruebas ejecutadas o checks de CI que cubren el cambio.
- **Capturas:** cuando el cambio afecta una pantalla.

Antes de pedir revisión, comprueba que el PR tiene un alcance claro, que CI terminó y que no hay conflictos. Resuelve los comentarios antes de integrar.

## Revisión y checks requeridos

- Cada PR necesita al menos **una aprobación de otra persona**.
- Todos los checks requeridos deben terminar correctamente.
- Los workflows deben ejecutarse en cada push y en los PRs dirigidos a `main`.
- Backend: build y pruebas, incluidas las pruebas con Testcontainers.
- ERP frontend: lint, build y pruebas con Vitest y React Testing Library.
- CRM: ejecutar los checks definidos para su repositorio.

Los nombres exactos de los checks requeridos se configuran en GitHub después de que los workflows hayan corrido al menos una vez.

### Backend: CI en cada push

El workflow `.github/workflows/ci.yml` (check **`CI / Build y pruebas (JDK 25)`**) corre `./mvnw verify` en cada push a cualquier rama y en cada PR hacia `main`. Compila el proyecto y ejecuta las pruebas unitarias, las de Spring Modulith y las de integración con Testcontainers (PostgreSQL real en Docker). Si falla, los reportes de pruebas quedan como artefacto de la corrida en la pestaña **Actions**.

El workflow `.github/workflows/lint.yml` (checks **`Lint / Formato (Spotless)`** y **`Lint / Linting (Checkstyle)`**) también corre en cada push a cualquier rama y en cada PR hacia `main`.

Antes de hacer push, corre lo mismo en local (necesitas Docker o Podman activo para Testcontainers):

```bash
./mvnw spotless:apply      # corrige el formato
./mvnw checkstyle:check    # reglas de estilo
./mvnw verify              # build + todas las pruebas
```

Las dependencias no se actualizan con bots: los cambios de versión en `pom.xml` o en las Actions se hacen a mano en un PR propio (`build(deps): ...` o `ci(deps): ...`). Más detalle de cada workflow en `docs/workflows-ci-cd.md`.

## Protección de `main` en GitHub

Configura una regla de protección para `main` que:

- Exija un PR antes de integrar.
- Exija al menos una aprobación y checks requeridos en verde.
- Exija resolver las conversaciones de revisión.
- Bloquee push directo, force-push y borrado de la rama.
- Evite excepciones de bypass para el trabajo normal.

## Resumen

```text
actualizar main -> crear rama -> commit y push -> abrir PR -> aprobación + CI verde -> squash and merge
```
