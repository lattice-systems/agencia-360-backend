package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoVehiculo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cómo cambia el valor estimado de un vehículo ({@code ValorEstimado} del
 * contrato de la app).
 *
 * @param puntos
 *            del mes más antiguo al más reciente; un mes sin estimación no
 *            aparece
 */
public record ValorEstimadoApp(Long vehiculoId, List<Punto> puntos) {

	/**
	 * @param mes
	 *            {@code AAAA-MM} en la hora de la agencia
	 * @param medio
	 *            promedio de mínimo y máximo, que es lo que se grafica
	 */
	public record Punto(String mes, Long estimacionTomaCuentaId, Instant creadaEn, BigDecimal minimo, BigDecimal maximo,
			BigDecimal medio, int km, EstadoVehiculo estado) {
	}
}
