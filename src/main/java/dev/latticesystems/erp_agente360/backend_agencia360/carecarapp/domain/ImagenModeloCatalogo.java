package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una foto de la galería de un modelo del catálogo (V2-M03.02). */
@Entity
@Table(name = "modelo_catalogo_imagen")
public class ImagenModeloCatalogo extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "modelo_catalogo_id", nullable = false)
	private Long modeloCatalogoId;

	@Column(nullable = false, length = 500)
	private String url;

	@Column(length = 40)
	private String color;

	@Column(length = 120)
	private String descripcion;

	@Column(nullable = false)
	private int orden;

	protected ImagenModeloCatalogo() {
		// Requerido por JPA
	}

	/**
	 * @throws IllegalArgumentException
	 *             si la URL no es https o pasa de 500 caracteres, o el orden es
	 *             negativo (400)
	 */
	public static ImagenModeloCatalogo nueva(Long modeloCatalogoId, String url, String color, String descripcion,
			int orden) {
		if (url == null || !url.strip().startsWith("https://") || url.strip().length() > 500) {
			throw new IllegalArgumentException("La imagen debe ser una URL https de hasta 500 caracteres");
		}
		if (orden < 0) {
			throw new IllegalArgumentException("El orden no puede ser negativo");
		}
		ImagenModeloCatalogo nueva = new ImagenModeloCatalogo();
		nueva.modeloCatalogoId = modeloCatalogoId;
		nueva.url = url.strip();
		nueva.color = recortar(color, 40);
		nueva.descripcion = recortar(descripcion, 120);
		nueva.orden = orden;
		return nueva;
	}

	private static String recortar(String valor, int largo) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.strip();
		if (limpio.length() > largo) {
			throw new IllegalArgumentException("El texto pasa de " + largo + " caracteres");
		}
		return limpio;
	}

	public Long getId() {
		return id;
	}

	public Long getModeloCatalogoId() {
		return modeloCatalogoId;
	}

	public String getUrl() {
		return url;
	}

	public String getColor() {
		return color;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public int getOrden() {
		return orden;
	}
}
