package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Recepcion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Entrega de piezas. El dominio valida que la cantidad sea mayor a cero y que
 * la llegada no sea posterior al registro; el responsable y la fecha de
 * registro los pone el servidor.
 *
 * @param fechaLlegada
 *            opcional, en ISO 8601 (por ejemplo {@code 2026-10-12T15:30:00Z});
 *            sin valor, se toma el momento del registro
 */
public record RegistrarRecepcionRequest(@NotNull Integer cantidad, @NotBlank @Size(max = 120) String proveedor,
		@Size(max = 500) String notas, Instant fechaLlegada) {

	Recepcion comoRecepcion() {
		return new Recepcion(cantidad, proveedor, notas, fechaLlegada);
	}
}
