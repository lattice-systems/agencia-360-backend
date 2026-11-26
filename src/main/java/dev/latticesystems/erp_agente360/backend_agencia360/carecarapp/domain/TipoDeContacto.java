package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Qué dato de contacto se está verificando (V2-M01.07).
 *
 * <p>
 * {@link #TELEFONO} existe desde ahora pero <b>nadie lo emite todavía</b>:
 * verificarlo exige mandar un código al teléfono y no hay SMS. Está aquí para
 * que esa mitad no obligue a otra migración cuando el equipo decida el
 * proveedor.
 */
public enum TipoDeContacto {
	CORREO, TELEFONO
}
