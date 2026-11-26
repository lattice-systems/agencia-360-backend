package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.NuevoPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EntregaPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;

public record NuevoPedidoRequest(@NotEmpty @Size(max = 10) List<@Valid @NotNull Renglon> renglones,
		@NotNull @Pattern(regexp = "mostrador|cita") String entrega, Long citaId) {

	public record Renglon(@NotNull Long productoId, @NotNull @Min(1) @Max(10) Integer cantidad) {
	}

	NuevoPedido comoPedido() {
		return new NuevoPedido(
				renglones.stream().map(uno -> new NuevoPedido.Pieza(uno.productoId(), uno.cantidad())).toList(),
				EntregaPedido.valueOf(entrega.toUpperCase(Locale.ROOT)), citaId);
	}
}
