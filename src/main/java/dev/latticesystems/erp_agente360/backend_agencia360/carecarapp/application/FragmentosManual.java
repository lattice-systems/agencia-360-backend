package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;

/**
 * Texto de cada página de un manual ({@code fragmento_manual}) y su vector,
 * para que el asistente busque solo en el manual del vehículo (V2-M24.02).
 */
public interface FragmentosManual {

	/** Una página del manual con su texto. */
	record Fragmento(long manualId, int pagina, String texto) {
	}

	/** Página pendiente de vector. */
	record Pendiente(long id, String texto) {
	}

	/**
	 * Reemplaza las páginas del manual: actualiza las que cambian (y les quita el
	 * vector), agrega las nuevas y borra las que ya no existen.
	 */
	void reemplazar(long manualId, List<Fragmento> paginas);

	boolean tieneFragmentos(long manualId);

	/** Manuales que todavía no tienen ninguna página guardada. */
	List<Long> manualesSinFragmentos();

	List<Pendiente> sinVector(int limite);

	void ponerVector(long fragmentoId, float[] vector);

	boolean tieneVectores(long manualId);

	/** Las páginas más parecidas al vector, de un solo manual. */
	List<Fragmento> masParecidos(long manualId, float[] vector, int limite);

	/** Las páginas que mejor coinciden con las palabras, de un solo manual. */
	List<Fragmento> porPalabras(long manualId, String texto, int limite);
}
