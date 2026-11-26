package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

import java.util.Map;

/**
 * Datos de entrada que no se aceptan, con qué está mal en cada campo para que
 * la app lo muestre junto al campo. Se responde con 400, el {@code codigo} y
 * {@code campos} (nombre del campo de la petición → por qué no sirve).
 */
public class CamposInvalidosException extends SolicitudInvalidaException {

	private final Map<String, String> campos;

	public CamposInvalidosException(String codigo, Map<String, String> campos) {
		super(String.join("; ", campos.values()), codigo);
		this.campos = Map.copyOf(campos);
	}

	public Map<String, String> getCampos() {
		return campos;
	}
}
