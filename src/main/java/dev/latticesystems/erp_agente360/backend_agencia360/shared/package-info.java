/**
 * Núcleo compartido (shared kernel).
 *
 * <p>Piezas transversales usadas por todos los módulos de negocio: seguridad
 * (Spring Security + JWT, control de acceso por roles), manejo global de errores,
 * configuración (OpenAPI, CORS) y clases base de dominio (auditoría). No contiene
 * reglas de negocio de ningún área funcional.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Shared Kernel", type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package dev.latticesystems.erp_agente360.backend_agencia360.shared;
