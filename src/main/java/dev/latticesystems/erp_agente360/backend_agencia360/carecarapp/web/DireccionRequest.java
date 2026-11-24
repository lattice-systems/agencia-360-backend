package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dirección que el cliente guarda o edita (V2-M17.01); el pin es obligatorio.
 */
public record DireccionRequest(@NotBlank @Size(max = 40) String alias, @NotBlank @Size(max = 120) String calle,
		@NotBlank @Size(max = 80) String colonia, @NotBlank @Pattern(regexp = "[0-9]{5}") String cp,
		@NotBlank @Size(max = 80) String ciudad, @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lng) {
}
