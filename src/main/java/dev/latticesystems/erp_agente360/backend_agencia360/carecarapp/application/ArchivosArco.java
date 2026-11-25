package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.Optional;

/**
 * Dónde viven las copias de datos que se entregan en el acceso ARCO
 * (V2-M22.02): una por solicitud, aparte del módulo {@code archivos}, cuyas URL
 * firmadas y tipos permitidos no son para datos personales.
 */
public interface ArchivosArco {

	/** Guarda la copia de la solicitud. */
	void guardar(long solicitudId, byte[] contenido);

	Optional<byte[]> leer(long solicitudId);

	/** Borra la copia de la solicitud; si no había, no hace nada. */
	void borrar(long solicitudId);
}
