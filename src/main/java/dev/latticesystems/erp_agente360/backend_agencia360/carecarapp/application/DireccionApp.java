package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DireccionCliente;
import java.math.BigDecimal;

/** Dirección guardada del cliente (V2-M17.01). */
public record DireccionApp(Long id, String alias, String calle, String colonia, String cp, String ciudad,
		BigDecimal lat, BigDecimal lng) {

	static DireccionApp de(DireccionCliente direccion) {
		return new DireccionApp(direccion.getId(), direccion.getAlias(), direccion.getCalle(), direccion.getColonia(),
				direccion.getCp(), direccion.getCiudad(), direccion.getLat(), direccion.getLng());
	}
}
