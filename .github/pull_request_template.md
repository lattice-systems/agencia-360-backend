## Qué cambió

<!-- Resumen breve. Historia: V2-xxx.nn (enlace a carecar-mobile/plan/modulos/...). -->

## Por qué

<!-- La intención: qué problema resuelve o qué historia de la app desbloquea. -->

## Cómo se probó

- [ ] `./mvnw spotless:apply`
- [ ] `./mvnw checkstyle:check`
- [ ] `./mvnw verify` (incluye Testcontainers y `ModularityTests`)

## Checklist (AGENTS.md)

- [ ] Tablas y campos de la ficha técnica intactos (solo se agregan)
- [ ] Migración Flyway nueva; ninguna migración integrada fue editada
- [ ] Contrato OpenAPI actualizado (y copiado a `carecar-mobile/api/` si cambió)
- [ ] Documentación y `plan/` actualizados; tablero en `carecar-mobile` al día
- [ ] Commits atómicos con intención, sin co-autor de IA ni "Generated with"
