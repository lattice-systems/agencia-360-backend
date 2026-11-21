package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Modelo del catálogo de vehículos nuevos de la app (APP-4.1), aparte del
 * inventario. Como en el inventario, {@code modelo} es el Tipo (Sentra) y
 * {@code anio} el Modelo (2026). Lo que no es marca, modelo y año es opcional
 * mientras el PO define qué muestra el catálogo.
 */
@Entity
@Table(name = "modelo_catalogo")
public class ModeloCatalogo extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 40)
	private String marca;

	@Column(nullable = false, length = 80)
	private String modelo;

	@Column(nullable = false)
	private int anio;

	@Column(length = 80)
	private String version;

	@Column(name = "precio_desde", precision = 12, scale = 2)
	private BigDecimal precioDesde;

	@Column(name = "imagen_url", length = 500)
	private String imagenUrl;

	@Column(length = 500)
	private String descripcion;

	@Column(nullable = false)
	private boolean activo;

	protected ModeloCatalogo() {
		// Requerido por JPA
	}

	/**
	 * Un modelo activo en el catálogo.
	 *
	 * @throws IllegalArgumentException
	 *             si falta la marca o el modelo, el año es menor a 1900, el precio
	 *             es negativo o la imagen no es una URL https (400)
	 */
	public static ModeloCatalogo nuevo(String marca, String modelo, int anio, String version, BigDecimal precioDesde,
			String imagenUrl, String descripcion) {
		ModeloCatalogo nuevo = new ModeloCatalogo();
		nuevo.marca = obligatorio(marca, "La marca", 40);
		nuevo.modelo = obligatorio(modelo, "El modelo", 80);
		if (anio < 1900) {
			throw new IllegalArgumentException("El año no es válido");
		}
		nuevo.anio = anio;
		nuevo.version = opcional(version, "La versión", 80);
		if (precioDesde != null && precioDesde.signum() < 0) {
			throw new IllegalArgumentException("El precio no puede ser negativo");
		}
		nuevo.precioDesde = precioDesde;
		nuevo.imagenUrl = opcional(imagenUrl, "La URL de la imagen", 500);
		if (nuevo.imagenUrl != null && !nuevo.imagenUrl.startsWith("https://")) {
			throw new IllegalArgumentException("La imagen debe ser una URL https");
		}
		nuevo.descripcion = opcional(descripcion, "La descripción", 500);
		nuevo.activo = true;
		return nuevo;
	}

	/** Deja de mostrarse en la app. */
	public void retirar() {
		activo = false;
	}

	private static String obligatorio(String valor, String campo, int largo) {
		String limpio = opcional(valor, campo, largo);
		if (limpio == null) {
			throw new IllegalArgumentException(campo + " es obligatorio");
		}
		return limpio;
	}

	private static String opcional(String valor, String campo, int largo) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.strip();
		if (limpio.length() > largo) {
			throw new IllegalArgumentException(campo + " pasa de " + largo + " caracteres");
		}
		return limpio;
	}

	public Long getId() {
		return id;
	}

	public String getMarca() {
		return marca;
	}

	public String getModelo() {
		return modelo;
	}

	public int getAnio() {
		return anio;
	}

	public String getVersion() {
		return version;
	}

	public BigDecimal getPrecioDesde() {
		return precioDesde;
	}

	public String getImagenUrl() {
		return imagenUrl;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public boolean isActivo() {
		return activo;
	}
}
