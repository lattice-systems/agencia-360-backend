package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoValuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TomaValuacion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Una valuación vista por el cliente ({@code Valuacion} del contrato de la app).
 *
 * @param fotos
 *            solo las tomas ya subidas, en el orden de {@link TomaValuacion}
 * @param puedeAgendar
 *            si agendar la inspección se admite ahora, para que la app no repita
 *            la regla
 * @param asesor
 *            nombre del vendedor del prospecto; nulo mientras no lo haya
 */
public record ValuacionApp(Long id, EstadoValuacion estado, Instant creadaEn, Long vehiculoId,
		EstimacionTomaCuentaApp estimacion, List<Foto> fotos, boolean puedeAgendar, Inspeccion cita, Oferta oferta,
		Instant respondidaEn, String asesor) {

	/** {@code url} es temporal y firmada; la clave del archivo no sale. */
	public record Foto(TomaValuacion toma, String url, Instant subidaEn) {
	}

	public record Inspeccion(Long citaId, LocalDate fecha, @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
			@JsonFormat(pattern = "HH:mm") LocalTime horaFin) {
	}

	public record Oferta(BigDecimal monto, LocalDate vigenciaHasta, String nota, Instant ofertadaEn) {
	}
}
