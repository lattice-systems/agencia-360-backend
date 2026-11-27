package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

import java.util.Map;

/**
 * Una regla de negocio rechaza datos que estaban bien formados y se sabe qué
 * corregir de cada uno (por ejemplo el timbrado de una factura). Se responde
 * con 422, el {@code codigo} y {@code campos} (nombre del campo → por qué no
 * sirve).
 */
public class CamposNoProcesablesException extends NoProcesableException {

	private final Map<String, String> campos;

	public CamposNoProcesablesException(String codigo, Map<String, String> campos) {
		super(String.join("; ", campos.values()), codigo);
		this.campos = Map.copyOf(campos);
	}

	public Map<String, String> getCampos() {
		return campos;
	}
}
