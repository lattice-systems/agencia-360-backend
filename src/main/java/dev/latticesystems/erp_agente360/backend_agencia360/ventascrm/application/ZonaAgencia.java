package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import java.time.ZoneId;

/**
 * La agencia está en León, Guanajuato: la fecha de un pago es la de allá, no la
 * de UTC.
 */
final class ZonaAgencia {

	static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

	private ZonaAgencia() {
	}
}
