package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoGasto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gasto que el cliente registra o corrige (V2-M18.01 y V2-M18.04). Las reglas
 * que dependen del tipo (litros y km en gasolina, descripción en otro) y la
 * fecha no futura las valida {@code GastoVehiculo}.
 */
public record NuevoGastoRequest(@NotNull TipoGasto tipo,
		@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal monto, BigDecimal litros, Integer km,
		@NotNull LocalDate fecha, @Size(max = 200) String descripcion) {
}
