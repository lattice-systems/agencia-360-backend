package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.NuevaRefaccion;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta de una refacción. El dominio valida que el costo no sea negativo, que el
 * precio sea mayor a cero y que el mínimo no sea negativo; aquí solo se exigen
 * los datos y se limita su largo. Los montos admiten hasta 10 enteros y 2
 * decimales, como las columnas. No recibe existencia: la refacción empieza en
 * cero y crece con las recepciones.
 */
public record RegistrarRefaccionRequest(@NotBlank @Size(max = 40) String codigo,
		@NotBlank @Size(max = 200) String descripcion, @NotNull @Digits(integer = 10, fraction = 2) BigDecimal costo,
		@NotNull @Digits(integer = 10, fraction = 2) BigDecimal precio, @NotNull Integer minimo) {

	NuevaRefaccion comoNuevaRefaccion() {
		return new NuevaRefaccion(codigo, descripcion, costo, precio, minimo);
	}
}
