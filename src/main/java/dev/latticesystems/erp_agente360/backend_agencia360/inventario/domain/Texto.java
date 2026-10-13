package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

/** Limpieza de los textos capturados en el inventario. */
final class Texto {

	private Texto() {
	}

	/** Quita espacios sobrantes; un texto vacío se trata como no capturado. */
	static String limpiar(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		return valor.strip();
	}

	/**
	 * @throws IllegalArgumentException
	 *             con el mensaje dado si el texto falta o está vacío
	 */
	static String obligatorio(String valor, String mensaje) {
		String limpio = limpiar(valor);
		if (limpio == null) {
			throw new IllegalArgumentException(mensaje);
		}
		return limpio;
	}
}
