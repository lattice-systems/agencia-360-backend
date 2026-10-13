/**
 * Acceso.
 *
 * <p>
 * Usuarios y roles del personal de la agencia, inicio de sesión en el ERP y en
 * el CRM, y administración de usuarios (ERP HU 0.1 y CRM HU 0.1). Cada usuario
 * tiene un solo rol, y el rol define a qué aplicación entra: ERP, CRM o ambas.
 * El Administrador da de alta usuarios del ERP y el Gerente de marketing, que
 * es el administrador del CRM, da de alta usuarios del CRM.
 *
 * <p>
 * Otros módulos solo usan las interfaces de este paquete, por ejemplo
 * {@link dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores}
 * para validar a quién se asigna un prospecto.
 *
 * <p>
 * Los clientes de la app CareCar no son usuarios de este módulo: entran con su
 * propia cuenta desde el módulo de la app.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Acceso")
package dev.latticesystems.erp_agente360.backend_agencia360.acceso;
