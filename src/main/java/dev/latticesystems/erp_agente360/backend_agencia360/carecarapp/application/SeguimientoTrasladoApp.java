package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoTraslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoTraslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Traslado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Un traslado con su estado y, solo mientras va en camino o de regreso y ya
 * reportó, la ubicación del chofer (V2-M17.03); horas en {@code HH:mm}.
 */
public record SeguimientoTrasladoApp(Long id, Long citaId, TipoTraslado tipo, EstadoTraslado estado, BigDecimal costo,
		LocalDate fecha, @JsonFormat(pattern = "HH:mm") LocalTime ventanaInicio,
		@JsonFormat(pattern = "HH:mm") LocalTime ventanaFin,
		@JsonInclude(JsonInclude.Include.NON_NULL) UbicacionChoferApp ubicacionChofer) {

	static SeguimientoTrasladoApp de(Traslado traslado) {
		boolean visible = traslado.getEstado().compartePosicion() && traslado.getUbicacionEn() != null;
		UbicacionChoferApp ubicacion = visible
				? new UbicacionChoferApp(traslado.getChoferLat(), traslado.getChoferLng(), traslado.getUbicacionEn())
				: null;
		return new SeguimientoTrasladoApp(traslado.getId(), traslado.getCitaId(), traslado.getTipo(),
				traslado.getEstado(), traslado.getCosto(), traslado.getFecha(), traslado.getVentanaInicio(),
				traslado.getVentanaFin(), ubicacion);
	}
}
