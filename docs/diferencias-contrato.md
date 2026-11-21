# Diferencias con el contrato de la app (v1.1)

Cambios hechos en `docs/api/agencia360-api-app-v1.yaml` en la rama
`feat/app-backend-completo`. La copia en `carecar-mobile/api/` **no** se tocó:
hay que sincronizarla.

| Esquema / ruta | Cambio | Compatible |
|---|---|---|
| `Cita` | Nuevo campo opcional `vehiculo` (texto, p. ej. "Nissan March 2026 Advance") | Sí (aditivo) |
| `Servicio.estatus` | Pasa de texto libre a enum cerrado `RECIBIDO, EN_PROCESO, TERMINADO, ENTREGADO` (PO-5, provisional). Hoy siempre `TERMINADO` | Sí |
| `ModeloCatalogo` | Agrega `marca`, `anio`, `activo` (requeridos) y `version`, `precioDesde`, `imagenUrl`, `descripcion` (opcionales) | Sí (aditivo) |
| `Dispositivo` | Esquema nuevo `{token, plataforma: ANDROID|IOS}` | Nuevo |
| `PUT /dispositivos`, `DELETE /dispositivos` | Rutas nuevas (registrar y borrar token de FCM) | Nuevo |

Fuera del contrato de la app (ERP/CRM): `GET/POST /api/v1/catalogo` y
`POST /api/v1/catalogo/{id}/retirar` (Administrador y Gerente de marketing).
