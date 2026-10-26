package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Manual de propietario de una marca, un tipo y un modelo (APP-3.1), por
 * ejemplo Nissan March 2026; la versión no cambia el manual. Como en el
 * inventario, {@code modelo} es el Tipo (March) y {@code anio} el Modelo
 * (2026). El PDF vive en el almacenamiento con la clave {@code archivo}.
 */
@Entity
@Table(name = "manual")
public class Manual extends Auditable {

	/** 20 MB. */
	public static final long TAMANO_MAXIMO = 20L * 1024 * 1024;
	private static final int LARGO_MAXIMO_MARCA = 40;
	private static final int LARGO_MAXIMO_MODELO = 80;
	private static final int ANIO_MINIMO = 1900;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = LARGO_MAXIMO_MARCA)
	private String marca;

	@Column(nullable = false, length = LARGO_MAXIMO_MODELO)
	private String modelo;

	@Column(nullable = false)
	private int anio;

	@Column(nullable = false, unique = true, length = 200)
	private String archivo;

	@Column(name = "tamano_bytes", nullable = false)
	private long tamanoBytes;

	@Column(name = "subido_por", nullable = false)
	private Long subidoPor;

	protected Manual() {
		// Requerido por JPA
	}

	private Manual(String marca, String modelo, int anio) {
		this.marca = marca;
		this.modelo = modelo;
		this.anio = anio;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta la marca o el tipo, o el modelo es anterior a 1900 (400)
	 */
	public static Manual nuevo(String marca, String modelo, int anio) {
		String marcaLimpia = obligatorio(marca, LARGO_MAXIMO_MARCA, "La marca");
		String modeloLimpio = obligatorio(modelo, LARGO_MAXIMO_MODELO, "El tipo");
		if (anio < ANIO_MINIMO) {
			throw new IllegalArgumentException("El modelo debe ser " + ANIO_MINIMO + " o posterior");
		}
		return new Manual(marcaLimpia, modeloLimpio, anio);
	}

	/**
	 * Pone el PDF del manual; si ya tenía uno, lo reemplaza.
	 *
	 * @throws IllegalArgumentException
	 *             si el archivo está vacío o pasa de 20 MB (400)
	 */
	public void ponerArchivo(String archivo, long tamanoBytes, Long subidoPor) {
		Objects.requireNonNull(archivo, "archivo");
		Objects.requireNonNull(subidoPor, "subidoPor");
		if (tamanoBytes <= 0 || tamanoBytes > TAMANO_MAXIMO) {
			throw new IllegalArgumentException("El manual debe ser un PDF de hasta 20 MB");
		}
		this.archivo = archivo;
		this.tamanoBytes = tamanoBytes;
		this.subidoPor = subidoPor;
	}

	/** "Nissan March 2026 Manual de propietario". */
	public String titulo() {
		return marca + " " + modelo + " " + anio + " Manual de propietario";
	}

	private static String obligatorio(String valor, int largoMaximo, String campo) {
		String limpio = valor == null ? "" : valor.strip();
		if (limpio.isEmpty() || limpio.length() > largoMaximo) {
			throw new IllegalArgumentException(campo + " es obligatorio y admite hasta " + largoMaximo + " caracteres");
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

	public String getArchivo() {
		return archivo;
	}

	public long getTamanoBytes() {
		return tamanoBytes;
	}

	public Long getSubidoPor() {
		return subidoPor;
	}
}
