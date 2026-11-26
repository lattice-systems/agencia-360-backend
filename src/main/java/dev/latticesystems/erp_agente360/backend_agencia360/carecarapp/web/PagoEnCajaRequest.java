package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.Size;

/** El folio del cobro en caja; opcional. */
public record PagoEnCajaRequest(@Size(max = 40) String referencia) {
}
