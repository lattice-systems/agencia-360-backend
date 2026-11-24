package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoTraslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoTraslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Traslado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Traslado de recolección o entrega visto por el cliente; horas en
 * {@code HH:mm}.
 */
public record TrasladoApp(Long id, TipoTraslado tipo, EstadoTraslado estado, BigDecimal costo, LocalDate fecha,
		@JsonFormat(pattern = "HH:mm") LocalTime ventanaInicio, @JsonFormat(pattern = "HH:mm") LocalTime ventanaFin) {

	static TrasladoApp de(Traslado traslado) {
		return new TrasladoApp(traslado.getId(), traslado.getTipo(), traslado.getEstado(), traslado.getCosto(),
				traslado.getFecha(), traslado.getVentanaInicio(), traslado.getVentanaFin());
	}
}
