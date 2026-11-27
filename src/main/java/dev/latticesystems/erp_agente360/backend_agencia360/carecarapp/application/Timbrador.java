package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CamposNoProcesablesException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Timbra la factura de un pago de la app (V2-M08.06). Por la ADR 0009 solo hay
 * una implementación, {@code TimbradorSimulado}: arma el XML con la estructura
 * de CFDI 4.0 y un timbre simulado, sin proveedor externo. Un PAC real sería
 * otra implementación con su propia ADR.
 */
public interface Timbrador {

	/**
	 * @throws CamposNoProcesablesException
	 *             con {@code TIMBRADO_RECHAZADO} y qué corregir de cada dato del
	 *             receptor, como lo rechazaría un PAC
	 */
	Timbre timbrar(Comprobante comprobante);

	/**
	 * El timbre que devuelve el PAC.
	 *
	 * @param uuid
	 *            el folio fiscal
	 * @param xml
	 *            el CFDI completo con su complemento de timbre, en UTF-8
	 */
	record Timbre(UUID uuid, Instant timbradoEn, String rfcProveedor, byte[] xml) {

		public Timbre {
			Objects.requireNonNull(uuid, "uuid");
			Objects.requireNonNull(timbradoEn, "timbradoEn");
			Objects.requireNonNull(rfcProveedor, "rfcProveedor");
			Objects.requireNonNull(xml, "xml");
		}
	}
}
