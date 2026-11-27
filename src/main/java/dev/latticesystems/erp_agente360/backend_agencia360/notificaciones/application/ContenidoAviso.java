package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import java.util.Objects;

/**
 * Lo que ve el cliente de un aviso: el texto que sale en la notificación y la
 * ruta de la app a la que lleva al tocarla.
 *
 * @param categoria
 *            {@code taller}, {@code citas}, {@code mantenimiento},
 *            {@code documentos}, {@code promociones} o {@code mensajes}
 * @param destino
 *            ruta de la app, por ejemplo {@code /taller/12}
 */
public record ContenidoAviso(String categoria, String titulo, String cuerpo, String destino) {

	public ContenidoAviso {
		Objects.requireNonNull(categoria, "categoria");
		Objects.requireNonNull(titulo, "titulo");
		Objects.requireNonNull(cuerpo, "cuerpo");
		Objects.requireNonNull(destino, "destino");
	}
}
