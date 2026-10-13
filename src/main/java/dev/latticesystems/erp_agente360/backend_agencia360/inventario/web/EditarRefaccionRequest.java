package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RefaccionEditada;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Corrección de una refacción. Se mandan todos los datos, como en el alta. La
 * existencia no se edita: solo cambia con movimientos.
 */
public record EditarRefaccionRequest(@NotBlank @Size(max = 40) String codigo,
		@NotBlank @Size(max = 200) String descripcion, @NotNull @Digits(integer = 10, fraction = 2) BigDecimal costo,
		@NotNull @Digits(integer = 10, fraction = 2) BigDecimal precio, @NotNull Integer minimo) {

	RefaccionEditada comoRefaccionEditada() {
		return new RefaccionEditada(codigo, descripcion, costo, precio, minimo);
	}
}
