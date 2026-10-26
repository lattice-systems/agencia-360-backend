package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import java.time.Instant;

/**
 * Datos fiscales válidos para las pruebas que llevan un prospecto más allá de
 * Pago.
 */
public final class DatosFiscalesDePrueba {

	/** Los mismos datos como los manda el CRM, para el cuerpo de una petición. */
	public static final String JSON = "{\"rfc\":\"LOHA850101AB1\",\"nombre\":\"Ana López Hernández\","
			+ "\"domicilio\":" + DatosFiscalesDePrueba.DOMICILIO_JSON + ",\"regimenFiscal\":\"612\"}";

	/** El domicilio fiscal de Ana como lo manda el CRM. */
	public static final String DOMICILIO_JSON = "{\"estado\":\"Guanajuato\",\"ciudad\":\"León\","
			+ "\"colonia\":\"Centro\",\"calleNumero\":\"Blvd. López Mateos 1102\",\"codigoPostal\":\"37000\"}";

	private DatosFiscalesDePrueba() {
	}

	public static DatosFiscales deAna() {
		return DatosFiscales.de("LOHA850101AB1", "Ana López Hernández", domicilioDeAna(), "612");
	}

	public static DomicilioFiscal domicilioDeAna() {
		return DomicilioFiscal.de("Guanajuato", "León", "Centro", "Blvd. López Mateos 1102", "37000");
	}

	/** Lo pasa a Pago con sus datos fiscales, como lo hace el vendedor. */
	public static void pasarAPago(Prospecto prospecto, Long vendedorId, Instant ahora) {
		prospecto.moverPorVendedor(Etapa.PAGO, null, vendedorId, ahora);
		prospecto.capturarDatosFiscales(deAna());
	}
}
