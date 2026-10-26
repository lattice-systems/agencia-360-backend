package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import java.time.Instant;

/**
 * Datos fiscales válidos para las pruebas que llevan un prospecto más allá de
 * Pago.
 */
public final class DatosFiscalesDePrueba {

	/** Los mismos datos como los manda el CRM, para el cuerpo de una petición. */
	public static final String JSON = "{\"rfc\":\"LOHA850101AB1\",\"nombre\":\"Ana López Hernández\","
			+ "\"domicilio\":\"Blvd. López Mateos 1102, León, Gto.\",\"regimenFiscal\":\"612\"}";

	private DatosFiscalesDePrueba() {
	}

	public static DatosFiscales deAna() {
		return DatosFiscales.de("LOHA850101AB1", "Ana López Hernández",
				"Blvd. López Mateos 1102, Col. Centro, C.P. 37000, León, Gto.", "612");
	}

	/** Lo pasa a Pago con sus datos fiscales, como lo hace el vendedor. */
	public static void pasarAPago(Prospecto prospecto, Long vendedorId, Instant ahora) {
		prospecto.moverPorVendedor(Etapa.PAGO, null, vendedorId, ahora);
		prospecto.capturarDatosFiscales(deAna());
	}
}
