package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.GastoVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoGasto;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gasto de la bitácora del conductor (V2-M18). {@code litros} y {@code km} solo
 * vienen en la gasolina (el km también si el cliente lo capturó);
 * {@code descripcion} es obligatoria en "otro".
 */
public record GastoApp(Long id, Long vehiculoId, TipoGasto tipo, BigDecimal monto, BigDecimal litros, Integer km,
		LocalDate fecha, String descripcion) {

	static GastoApp de(GastoVehiculo gasto) {
		return new GastoApp(gasto.getId(), gasto.getVehiculoClienteId(), gasto.getTipo(), gasto.getMonto(),
				gasto.getLitros(), gasto.getKm(), gasto.getFecha(), gasto.getDescripcion());
	}
}
