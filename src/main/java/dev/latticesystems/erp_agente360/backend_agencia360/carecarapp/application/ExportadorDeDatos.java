package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.Instant;

/**
 * Arma la copia de los datos personales de una cuenta (acceso ARCO, V2-M22.02).
 */
public interface ExportadorDeDatos {

	/**
	 * @return el documento JSON, ya en bytes, con los datos del cliente y el
	 *         alcance de lo que incluye y de lo que no
	 */
	byte[] exportar(long clienteId, Instant ahora);
}
