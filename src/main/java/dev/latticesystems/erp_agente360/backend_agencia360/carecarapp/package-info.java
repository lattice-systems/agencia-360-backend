/**
 * App CareCar (clientes).
 *
 * <p>
 * API consumida por la app móvil nativa de servicio postventa (Flutter, Android
 * e iOS) para clientes que ya adquirieron una unidad: autenticación cerrada sin
 * registro público: el cliente entra con su correo y una contraseña temporal
 * que da la agencia tras validar la compra, y la cambia en su primer ingreso o
 * la recupera con un código por correo (CareCar HU 2.1 y 2.3); gestión de
 * perfil (HU 2.2); vehículos propios con manuales e historial digital de
 * servicios (HU 3.1, 3.2); agenda de citas de servicio sin empalmes de horario
 * (HU 3.3); y catálogo de vehículos nuevos con mini calculadora (HU 4.1).
 *
 * <p>
 * La división final de módulos está pendiente de definir (ver sección 9 del
 * documento de arquitectura); este límite es una propuesta inicial basada en
 * las áreas funcionales del backlog.
 */
@org.springframework.modulith.ApplicationModule(displayName = "App CareCar")
package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;
