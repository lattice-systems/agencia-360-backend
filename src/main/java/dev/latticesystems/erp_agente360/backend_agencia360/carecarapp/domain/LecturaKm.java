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
import java.util.Objects;

/**
 * Lectura de kilometraje de un vehículo (V2-M05.03). Las del cliente son filas
 * aparte: el kilometraje oficial del taller no se sobrescribe.
 */
@Entity
@Table(name = "lectura_km")
public class LecturaKm {

	/** Un kilometraje que ningún auto alcanza; evita capturas con ceros de más. */
	public static final int KM_MAXIMO = 2_000_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vehiculo_cliente_id", nullable = false)
	private Long vehiculoClienteId;

	@Column(nullable = false)
	private int km;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private FuenteLectura fuente;

	@Column(name = "foto_url", length = 500)
	private String fotoUrl;

	@Column(name = "registrada_en", nullable = false)
	private Instant registradaEn;

	protected LecturaKm() {
		// Requerido por JPA
	}

	private LecturaKm(Long vehiculoClienteId, int km, FuenteLectura fuente, Instant registradaEn) {
		this.vehiculoClienteId = vehiculoClienteId;
		this.km = km;
		this.fuente = fuente;
		this.registradaEn = registradaEn;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el kilometraje no está entre 1 y {@value #KM_MAXIMO} (400)
	 */
	public static LecturaKm delCliente(Long vehiculoClienteId, int km, Instant registradaEn) {
		Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		Objects.requireNonNull(registradaEn, "registradaEn");
		exigirKmValido(km);
		return new LecturaKm(vehiculoClienteId, km, FuenteLectura.CLIENTE, registradaEn);
	}

	/** Guarda la clave del archivo con la foto que probó la lectura. */
	public void adjuntarFoto(String clave) {
		this.fotoUrl = clave;
	}

	public static void exigirKmValido(int km) {
		if (km < 1 || km > KM_MAXIMO) {
			throw new IllegalArgumentException("El kilometraje debe estar entre 1 y " + KM_MAXIMO);
		}
	}

	/** Corrige el kilometraje de la lectura (al corregir la carga que la creó). */
	public void corregir(int nuevoKm) {
		exigirKmValido(nuevoKm);
		this.km = nuevoKm;
	}

	public Long getId() {
		return id;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public int getKm() {
		return km;
	}

	public FuenteLectura getFuente() {
		return fuente;
	}

	public String getFotoUrl() {
		return fotoUrl;
	}

	public Instant getRegistradaEn() {
		return registradaEn;
	}
}
