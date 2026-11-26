package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Campaña de seguridad (recall) del fabricante (V2-M10.02). Aplica a los
 * vehículos de un modelo (el Tipo, sin distinguir mayúsculas) con año entre
 * {@code anioDesde} y {@code anioHasta} y, si se da, con VIN dentro del rango.
 */
@Entity
@Table(name = "campana_seguridad")
public class CampanaSeguridad {

	private static final int LARGO_VIN = 17;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 30)
	private String codigo;

	@Column(nullable = false, length = 120)
	private String titulo;

	@Column(nullable = false, length = 1000)
	private String descripcion;

	@Column(name = "que_hacer", nullable = false, length = 500)
	private String queHacer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private GravedadCampana gravedad;

	@Column(nullable = false, length = 60)
	private String modelo;

	@Column(name = "anio_desde", nullable = false)
	private int anioDesde;

	@Column(name = "anio_hasta", nullable = false)
	private int anioHasta;

	@Column(name = "vin_desde", length = LARGO_VIN)
	private String vinDesde;

	@Column(name = "vin_hasta", length = LARGO_VIN)
	private String vinHasta;

	@Column(name = "publicada_en", nullable = false)
	private Instant publicadaEn;

	protected CampanaSeguridad() {
	}

	/** Datos de una campaña nueva, tal como los captura el personal. */
	public record Datos(String codigo, String titulo, String descripcion, String queHacer, GravedadCampana gravedad,
			String modelo, Integer anioDesde, Integer anioHasta, String vinDesde, String vinHasta) {
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta un dato, un texto es muy largo, los años o el rango de
	 *             VIN están al revés o solo viene un extremo del rango (400)
	 */
	public static CampanaSeguridad publicar(Datos datos, Instant momento) {
		Objects.requireNonNull(datos, "datos");
		Objects.requireNonNull(momento, "momento");
		CampanaSeguridad campana = new CampanaSeguridad();
		campana.codigo = texto(datos.codigo(), "código", 30).toUpperCase(Locale.ROOT);
		campana.titulo = texto(datos.titulo(), "título", 120);
		campana.descripcion = texto(datos.descripcion(), "descripción", 1000);
		campana.queHacer = texto(datos.queHacer(), "qué hacer", 500);
		campana.modelo = texto(datos.modelo(), "modelo", 60);
		if (datos.gravedad() == null) {
			throw new IllegalArgumentException("La gravedad es obligatoria");
		}
		campana.gravedad = datos.gravedad();
		if (datos.anioDesde() == null || datos.anioHasta() == null || datos.anioHasta() < datos.anioDesde()) {
			throw new IllegalArgumentException("Los años de la campaña son obligatorios y van de menor a mayor");
		}
		campana.anioDesde = datos.anioDesde();
		campana.anioHasta = datos.anioHasta();
		String desde = vin(datos.vinDesde());
		String hasta = vin(datos.vinHasta());
		if ((desde == null) != (hasta == null) || desde != null && hasta.compareTo(desde) < 0) {
			throw new IllegalArgumentException("El rango de VIN lleva los dos extremos y va de menor a mayor");
		}
		campana.vinDesde = desde;
		campana.vinHasta = hasta;
		campana.publicadaEn = momento;
		return campana;
	}

	/**
	 * Si la campaña aplica a ese vehículo: mismo modelo (sin distinguir
	 * mayúsculas), año dentro del rango y, si hay rango de VIN, VIN dentro.
	 */
	public boolean aplicaA(String modeloVehiculo, int anio, String vin) {
		if (modeloVehiculo == null || !modelo.equalsIgnoreCase(modeloVehiculo.strip())) {
			return false;
		}
		if (anio < anioDesde || anio > anioHasta) {
			return false;
		}
		if (vinDesde == null) {
			return true;
		}
		String propio = vin == null ? "" : vin.strip().toUpperCase(Locale.ROOT);
		return propio.compareTo(vinDesde) >= 0 && propio.compareTo(vinHasta) <= 0;
	}

	private static String texto(String valor, String campo, int largo) {
		String limpio = valor == null ? "" : valor.strip();
		if (limpio.isEmpty() || limpio.length() > largo) {
			throw new IllegalArgumentException(
					"El campo " + campo + " es obligatorio y admite hasta " + largo + " caracteres");
		}
		return limpio;
	}

	private static String vin(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.strip().toUpperCase(Locale.ROOT);
		if (limpio.length() != LARGO_VIN) {
			throw new IllegalArgumentException("Cada extremo del rango de VIN lleva " + LARGO_VIN + " caracteres");
		}
		return limpio;
	}

	public Long getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getQueHacer() {
		return queHacer;
	}

	public GravedadCampana getGravedad() {
		return gravedad;
	}

	public String getModelo() {
		return modelo;
	}

	public int getAnioDesde() {
		return anioDesde;
	}

	public int getAnioHasta() {
		return anioHasta;
	}

	public Instant getPublicadaEn() {
		return publicadaEn;
	}
}
