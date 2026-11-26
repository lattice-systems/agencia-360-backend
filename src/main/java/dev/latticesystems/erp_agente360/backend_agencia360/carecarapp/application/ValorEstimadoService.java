package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstimacionTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.EstimacionTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El valor del auto en el tiempo (V2-M26.05), con las estimaciones que el
 * cliente ya guardó. No interpola ni recalcula hacia atrás: eso mostraría
 * valores que nadie estimó.
 */
@Service
public class ValorEstimadoService {

	private static final int MESES_POR_OMISION = 12;
	private static final int MESES_MAXIMOS = 36;
	private static final BigDecimal DOS = BigDecimal.valueOf(2);

	private final EstimacionTomaCuentaRepository estimaciones;
	private final VehiculosDeClientes vehiculos;
	private final Clock reloj;

	@Autowired
	public ValorEstimadoService(EstimacionTomaCuentaRepository estimaciones, VehiculosDeClientes vehiculos) {
		this(estimaciones, vehiculos, Clock.system(AgendaAppService.ZONA_AGENCIA));
	}

	ValorEstimadoService(EstimacionTomaCuentaRepository estimaciones, VehiculosDeClientes vehiculos, Clock reloj) {
		this.estimaciones = estimaciones;
		this.vehiculos = vehiculos;
		this.reloj = reloj;
	}

	/**
	 * Un punto por mes con la última estimación de ese mes.
	 *
	 * @param meses
	 *            cuántos hacia atrás contando el actual; nulo usa 12
	 * @throws IllegalArgumentException
	 *             si {@code meses} no está entre 1 y 36 (400)
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 */
	@Transactional(readOnly = true)
	public ValorEstimadoApp porMes(Long clienteId, Long vehiculoId, Integer meses) {
		int pedidos = meses == null ? MESES_POR_OMISION : meses;
		if (pedidos < 1 || pedidos > MESES_MAXIMOS) {
			throw new IllegalArgumentException("Los meses deben estar entre 1 y " + MESES_MAXIMOS);
		}
		if (vehiculos.buscarPorId(vehiculoId).filter(propio -> propio.propietario().id().equals(clienteId)).isEmpty()) {
			throw new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId);
		}
		Instant desde = YearMonth.from(LocalDate.now(reloj)).minusMonths(pedidos - 1L).atDay(1)
				.atStartOfDay(reloj.getZone()).toInstant();
		// Vienen de la más antigua a la más reciente: la última de cada mes gana.
		Map<YearMonth, EstimacionTomaCuenta> porMes = new LinkedHashMap<>();
		estimaciones.delVehiculoDesde(clienteId, vehiculoId, desde)
				.forEach(estimacion -> porMes.put(mesDe(estimacion), estimacion));
		return new ValorEstimadoApp(vehiculoId,
				porMes.entrySet().stream().map(mes -> punto(mes.getKey(), mes.getValue())).toList());
	}

	private YearMonth mesDe(EstimacionTomaCuenta estimacion) {
		return YearMonth.from(estimacion.getCreatedAt().atZone(reloj.getZone()));
	}

	private static ValorEstimadoApp.Punto punto(YearMonth mes, EstimacionTomaCuenta estimacion) {
		BigDecimal medio = estimacion.getMinimo().add(estimacion.getMaximo()).divide(DOS, 2, RoundingMode.HALF_UP);
		return new ValorEstimadoApp.Punto(mes.toString(), estimacion.getId(), estimacion.getCreatedAt(),
				estimacion.getMinimo(), estimacion.getMaximo(), medio, estimacion.getKm(), estimacion.getEstado());
	}
}
