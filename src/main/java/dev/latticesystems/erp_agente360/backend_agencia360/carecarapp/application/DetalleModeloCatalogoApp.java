package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalle de un modelo del catálogo ({@code DetalleModeloCatalogo} del
 * contrato): los campos de {@link ModeloCatalogoApp} más la galería, las
 * versiones y la ficha resumida (V2-M03.02). Las tres listas pueden venir
 * vacías.
 */
public record DetalleModeloCatalogoApp(Long id, String marca, String modelo, int anio, String version,
		BigDecimal precioDesde, String imagenUrl, String descripcion, boolean activo, List<Imagen> imagenes,
		List<Version> versiones, List<Especificacion> especificaciones) {

	public record Imagen(Long id, String url, String color, String descripcion, int orden) {
	}

	/** {@code id} sirve para pedir el detalle de esa versión. */
	public record Version(Long id, String nombre, BigDecimal precioDesde) {
	}

	public record Especificacion(String etiqueta, String valor, int orden) {
	}
}
