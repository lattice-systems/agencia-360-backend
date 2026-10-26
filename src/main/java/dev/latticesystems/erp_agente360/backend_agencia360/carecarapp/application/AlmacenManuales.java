package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.Optional;

/**
 * Dónde viven los PDF de los manuales. En desarrollo es una carpeta local
 * ({@code carecarapp.infrastructure.AlmacenManualesLocal}); en la nube será
 * Azure Blob Storage, sin cambiar el contrato de la app.
 */
public interface AlmacenManuales {

	/**
	 * Guarda el archivo con una clave nueva.
	 *
	 * @return la clave con la que se vuelve a leer
	 */
	String guardar(byte[] contenido);

	/** @return vacío si no existe */
	Optional<byte[]> leer(String clave);

	/** Borra el archivo; si no existe no hace nada. */
	void borrar(String clave);
}
