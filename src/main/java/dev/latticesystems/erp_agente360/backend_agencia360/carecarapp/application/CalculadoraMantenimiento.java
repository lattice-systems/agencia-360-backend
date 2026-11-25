package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Estado de cada servicio del plan y cuándo toca el próximo (V2-M05.01 y
 * V2-M05.02). Es puro: no toca la base ni el reloj.
 *
 * <p>
 * Un servicio está {@code hecho} si hay una orden cerrada a menos de
 * {@link #TOLERANCIA_KM} de su kilometraje; {@code vencido} si no se hizo y el
 * auto ya pasó su kilometraje; {@code proximo} el no hecho de menor kilometraje
 * que aún no se alcanza, y {@code pendiente} el resto.
 */
final class CalculadoraMantenimiento {

	static final int TOLERANCIA_KM = 1500;

	private CalculadoraMantenimiento() {
	}

	/** Un servicio del plan del modelo. */
	record PlanDeServicio(Long id, String nombre, int km, int meses, BigDecimal precio, List<String> conceptos) {
	}

	/** Una orden de servicio ya cerrada del vehículo. */
	record OrdenCerrada(LocalDate fecha, int km) {
	}

	record Evaluacion(List<ServicioMantenimientoApp> servicios, ProximoServicioApp proximo) {
	}

	/**
	 * @param kmActual
	 *            nulo si no se conoce el kilometraje
	 * @param promedioKmDiario
	 *            nulo o cero si no hay lecturas suficientes
	 */
	static Evaluacion evaluar(List<PlanDeServicio> plan, List<OrdenCerrada> ordenes, Integer kmActual,
			BigDecimal promedioKmDiario, LocalDate hoy) {
		List<PlanDeServicio> ordenado = plan.stream().sorted(Comparator.comparingInt(PlanDeServicio::km)).toList();
		List<ServicioMantenimientoApp> servicios = new ArrayList<>();
		PlanDeServicio candidato = null;
		PlanDeServicio ultimoHecho = null;
		LocalDate fechaUltimoHecho = null;
		for (PlanDeServicio servicio : ordenado) {
			Optional<LocalDate> hecho = hechoEn(servicio, ordenes);
			String estado;
			if (hecho.isPresent()) {
				estado = "hecho";
				ultimoHecho = servicio;
				fechaUltimoHecho = hecho.get();
			} else if (kmActual != null && kmActual > servicio.km()) {
				estado = "vencido";
			} else if (candidato == null) {
				estado = "proximo";
				candidato = servicio;
			} else {
				estado = "pendiente";
			}
			servicios.add(new ServicioMantenimientoApp(servicio.id(), servicio.nombre(), servicio.km(),
					servicio.meses(), servicio.precio(), servicio.conceptos(), estado, hecho.orElse(null)));
		}
		ProximoServicioApp proximo = candidato == null
				? null
				: estimar(candidato, ultimoHecho, fechaUltimoHecho, kmActual, promedioKmDiario, hoy);
		return new Evaluacion(servicios, proximo);
	}

	private static Optional<LocalDate> hechoEn(PlanDeServicio servicio, List<OrdenCerrada> ordenes) {
		return ordenes.stream().filter(orden -> Math.abs(orden.km() - servicio.km()) <= TOLERANCIA_KM)
				.map(OrdenCerrada::fecha).max(Comparator.naturalOrder());
	}

	private static ProximoServicioApp estimar(PlanDeServicio servicio, PlanDeServicio ultimoHecho,
			LocalDate fechaUltimoHecho, Integer kmActual, BigDecimal promedioKmDiario, LocalDate hoy) {
		int faltanKm = Math.max(0, servicio.km() - (kmActual == null ? 0 : kmActual));
		LocalDate base = ultimoHecho == null ? hoy : fechaUltimoHecho;
		int mesesBase = ultimoHecho == null ? 0 : ultimoHecho.meses();
		LocalDate porTiempo = base.plusMonths(Math.max(1, servicio.meses() - mesesBase));
		LocalDate porKm = null;
		if (promedioKmDiario != null && promedioKmDiario.signum() > 0) {
			long dias = BigDecimal.valueOf(faltanKm).divide(promedioKmDiario, 0, RoundingMode.CEILING).longValue();
			porKm = hoy.plusDays(dias);
		}
		boolean ganaKm = porKm != null && !porKm.isAfter(porTiempo);
		LocalDate fecha = ganaKm ? porKm : porTiempo;
		int faltanDias = (int) Math.max(0, ChronoUnit.DAYS.between(hoy, fecha));
		return new ProximoServicioApp(servicio.id(), servicio.nombre(), servicio.km(), fecha, faltanKm, faltanDias,
				ganaKm ? "km" : "tiempo");
	}
}
