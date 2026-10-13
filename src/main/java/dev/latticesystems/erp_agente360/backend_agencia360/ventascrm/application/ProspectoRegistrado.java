package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Resultado del registro (CRM-1.0). En JSON los datos del prospecto van al
 * mismo nivel que el aviso.
 *
 * @param contactoRepetido
 *            {@code true} si el teléfono o el correo ya existían en otro
 *            prospecto. Solo avisa: no dice de quién es
 */
public record ProspectoRegistrado(@JsonUnwrapped ProspectoVista prospecto, boolean contactoRepetido) {
}
