package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** Plazos de los apartados de la tienda. */
final class ReglasTienda {

	static final int PEDIDOS_ABIERTOS_MAXIMOS = 3;
	static final int RENGLONES_MAXIMOS = 10;
	static final int PIEZAS_MAXIMAS_POR_RENGLON = 10;

	private ReglasTienda() {
	}

	/**
	 * Un apartado para instalar aguanta hasta que acabe el día siguiente a la cita.
	 */
	static Instant venceConCita(LocalDate fechaDeCita) {
		return fechaDeCita.plusDays(2).atStartOfDay(AgendaAppService.ZONA_AGENCIA).toInstant();
	}

	static Instant venceEnMostrador(Instant ahora, Duration vigencia) {
		return ahora.plus(vigencia);
	}
}
