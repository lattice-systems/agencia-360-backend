package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** {@code solicitud_arco} (V2-M22.02): derechos ARCO de la cuenta de la app. */
public interface SolicitudesArco {

	/** Una solicitud tal como está guardada. */
	record Solicitud(long id, long clienteId, String tipo, String estado, String archivoUrl, Instant creadaEn,
			Instant resueltaEn) {
	}

	/**
	 * Abre una solicitud {@code PENDIENTE}.
	 *
	 * @return la nueva, o vacío si ya hay una pendiente del mismo tipo para esa
	 *         cuenta (lo decide la restricción única, no una lectura previa)
	 */
	Optional<Solicitud> crear(long clienteId, String tipo, Instant ahora);

	Optional<Solicitud> buscar(long id);

	Optional<Solicitud> pendienteDe(long clienteId, String tipo);

	/** Las de la cuenta, más recientes primero. */
	List<Solicitud> delCliente(long clienteId, int limite);

	/** Las de esa cuenta y tipo que todavía guardan un archivo. */
	List<Solicitud> conArchivo(long clienteId, String tipo);

	/** Las que guardan un archivo y se resolvieron en {@code limite} o antes. */
	List<Solicitud> conArchivoResueltasAntesDe(Instant limite);

	/** Pasa a {@code RESUELTA} con su archivo. Solo si seguía pendiente. */
	void resolver(long id, String archivoUrl, Instant ahora);

	/** Pasa a {@code FALLIDA}. Solo si seguía pendiente. */
	void fallar(long id, Instant ahora);

	void quitarArchivo(long id);
}
