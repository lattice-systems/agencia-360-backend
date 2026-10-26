package dev.latticesystems.erp_agente360.backend_agencia360.servicio.web;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TipoRenglon;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Servicio que se registra en el expediente (ERP-3.0). Los precios traen el IVA
 * incluido. La fecha no puede ser posterior al día de hoy en la agencia.
 */
public record RegistrarServicioRequest(@NotNull LocalDate fecha, @NotNull @Min(0) @Max(2_000_000) Integer kilometraje,
		@NotEmpty @Size(max = 100) List<@NotNull @Valid Renglon> renglones) {

	/** Un renglón del servicio, como en el ticket de una tienda. */
	public record Renglon(@NotNull TipoRenglon tipo, @NotBlank @Size(max = 200) String concepto,
			@NotNull @Min(1) @Max(999) Integer cantidad,
			@NotNull @DecimalMin("0.00") @Digits(integer = 7, fraction = 2) BigDecimal precioUnitario) {
	}
}
