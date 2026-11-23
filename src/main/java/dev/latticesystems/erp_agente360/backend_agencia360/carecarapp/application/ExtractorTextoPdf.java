package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;

/** Saca el texto de cada página de un PDF. */
public interface ExtractorTextoPdf {

	/**
	 * @return el texto de cada página en orden (la página 1 es el índice 0); una
	 *         página sin texto viene vacía
	 * @throws IllegalArgumentException
	 *             si el archivo no es un PDF legible
	 */
	List<String> paginas(byte[] pdf);
}
