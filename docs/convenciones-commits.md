# Convenciones de commits

Reglas para escribir los mensajes de commit en este repositorio. El objetivo es simple: que cualquiera del equipo entienda, con solo leer el historial, **qué cambió y en qué parte del proyecto**, sin tener que abrir el código.

## Formato del mensaje

```
tipo(módulo): descripción corta
```

Ejemplo:

```
fix(inventario): corrige el descuento de stock al vender una refacción
```

- **tipo**: qué clase de cambio es (ver tabla abajo).
- **módulo**: en qué parte del proyecto fue el cambio (ver tabla abajo). Si el cambio no es de un módulo específico, se puede omitir.
- **descripción corta**: en pocas palabras, qué hace el cambio. Se escribe como una instrucción: "agrega", "corrige", "elimina" (no "agregado", ni "agregando").

## Tipos de commit

| Tipo | Se usa para |
|---|---|
| `feat` | Una funcionalidad nueva |
| `fix` | Corregir un error |
| `docs` | Cambios solo en documentación (README, `docs/`) |
| `refactor` | Reordenar u organizar código sin cambiar lo que hace |
| `test` | Agregar o arreglar pruebas |
| `build` | Cambios en `pom.xml`, dependencias o configuración de compilación |
| `chore` | Tareas varias que no encajan en lo anterior (`.gitignore`, configuración del IDE, etc.) |

## Módulo (scope)

Usa el mismo nombre de la carpeta del módulo que estás tocando:

| Módulo | Qué es |
|---|---|
| `ventascrm` | Ventas y CRM de prospectos |
| `facturacion` | Facturación y Administración |
| `inventario` | Inventario de vehículos y refacciones |
| `servicio` | Taller y Servicio |
| `refacciones` | Ventas directas de refacciones |
| `caja` | Caja (pagos y cobros) |
| `contabilidad` | Balance de ingresos y egresos |
| `carecarapp` | API para la app móvil de clientes |
| `shared` | Código compartido (seguridad, configuración, errores) |

## Reglas simples

1. **Un commit, un cambio.** No mezcles dos cosas distintas en el mismo commit (por ejemplo, un `fix` y un `feat` juntos).
2. **Mensaje corto y claro.** Si necesitas explicar más, agrega un párrafo debajo explicando el *por qué* del cambio, no el *qué* (el código ya muestra el qué).
3. **En español**, para que todo el equipo lo entienda igual.

## Ejemplos

Bien:

```
feat(caja): agrega registro de movimiento inverso al cancelar un pago
```

```
fix(inventario): bloquea la fila al vender una refacción para evitar venta duplicada
```

```
docs: agrega convenciones de commits del repositorio
```

Evitar:

```
fix: arreglos
cambios varios
WIP
update Caja.java
```

## Nombres de rama

Mismo esquema que los commits:

```
tipo/modulo-descripcion-corta
```

Ejemplos: `feat/caja-registro-pagos`, `fix/inventario-bloqueo-fila`.

`main` está protegida: los cambios entran siempre por Pull Request, nunca por push directo.

## Antes de subir cambios

Siempre hay que traer los últimos cambios de `main` **antes de subir tu rama**, para evitar conflictos y que tu PR se quede desactualizado:

```
git pull origin main
git push origin tu-rama
```

Si estás dentro de tu rama y `main` tiene cambios nuevos, tráelos así:

```
git checkout main
git pull origin main
git checkout tu-rama
git merge main
```

## Pull Requests

- El título del PR sigue el mismo formato que un commit: `tipo(módulo): descripción`.
- En la descripción, explica brevemente qué cambia y por qué.
- Evita mezclar cambios de módulos distintos en el mismo PR, salvo que realmente estén relacionados.
