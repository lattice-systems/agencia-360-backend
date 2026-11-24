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

	/**
	 * Identifica el testigo del tablero en una foto JPEG ya limpia (V2-M24.05).
	 *
	 * @throws ProveedorIANoDisponibleException
	 *             si el proveedor falla, tarda o no tiene cuota
	 */
	RespuestaTestigo identificarTestigo(String instrucciones, byte[] jpeg);

	/**
	 * Lo que el modelo ve en la foto. Sin reconocer, los demás campos son nulos.
	 *
	 * @param gravedad
	 *            {@code roja}, {@code amarilla} o {@code informativa}
	 */
	record RespuestaTestigo(boolean reconocido, String testigo, String gravedad, String queHacer) {
	}

	/** Lo que devuelve el modelo: texto, si lo encontró y las páginas usadas. */
	record RespuestaIA(String respuesta, boolean encontrado, List<Integer> paginas) {
		public RespuestaIA {
			paginas = paginas == null ? List.of() : List.copyOf(paginas);
		}
	}
}
