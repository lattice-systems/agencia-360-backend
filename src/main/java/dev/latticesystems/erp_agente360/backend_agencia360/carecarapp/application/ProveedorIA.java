package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;
import java.util.Optional;

/**
 * Modelo de IA del asistente (ADR 0007). La llamada siempre sale del backend:
 * la app nunca tiene llaves. Implementaciones: Gemini (nivel gratis) y un
 * proveedor falso, sin red, para pruebas.
 */
public interface ProveedorIA {

	/** Dimensión de los vectores de {@code fragmento_manual.embedding}. */
	int DIMENSION = 768;

	/** Nombre corto que se guarda en {@code consulta_asistente.proveedor}. */
	String nombre();

	/**
	 * Vector normalizado de {@link #DIMENSION} valores para buscar por parecido.
	 *
	 * @return vacío si este proveedor no calcula vectores
	 * @throws ProveedorIANoDisponibleException
	 *             si el proveedor falla, tarda o no tiene cuota
	 */
	Optional<float[]> embeber(String texto);

	/**
	 * Responde con base en el contenido (pregunta y fragmentos del manual).
	 *
	 * @throws ProveedorIANoDisponibleException
	 *             si el proveedor falla, tarda o no tiene cuota
	 */
	RespuestaIA responder(String instrucciones, String contenido);

	/** Lo que devuelve el modelo: texto, si lo encontró y las páginas usadas. */
	record RespuestaIA(String respuesta, boolean encontrado, List<Integer> paginas) {
		public RespuestaIA {
			paginas = paginas == null ? List.of() : List.copyOf(paginas);
		}
	}
}
