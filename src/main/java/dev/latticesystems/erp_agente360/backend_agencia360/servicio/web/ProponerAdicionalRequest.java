package dev.latticesystems.erp_agente360.backend_agencia360.servicio.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Trabajo adicional que el cliente aprueba o rechaza desde la app (V2-M04.05).
 * Los montos traen el IVA incluido.
 */
public record ProponerAdicionalRequest(@NotBlank @Size(max = 200) String concepto,
		@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal manoDeObra,
		@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal refacciones) {
}
