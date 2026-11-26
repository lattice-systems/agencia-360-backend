package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Cobertura de garantía de un componente de un vehículo (V2-M10.01): vale desde
 * {@code iniciaEn} hasta {@code venceEn} y, si tiene {@code kmLimite}, hasta
 * que el odómetro llegue a ese km; lo que pase primero.
 */
@Entity
@Table(name = "garantia")
public class Garantia {

	private static final int LARGO_COMPONENTE = 80;
	private static final int LARGO_DESCRIPCION = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vehiculo_cliente_id", nullable = false)
	private Long vehiculoClienteId;

	@Column(nullable = false, length = LARGO_COMPONENTE)
	private String componente;

	@Column(length = LARGO_DESCRIPCION)
	private String descripcion;

	@Column(name = "inicia_en", nullable = false)
	private LocalDate iniciaEn;

	@Column(name = "vence_en", nullable = false)
	private LocalDate venceEn;

	@Column(name = "km_limite")
	private Integer kmLimite;

	@Column(name = "aviso_60_dias_en")
	private Instant aviso60DiasEn;

	@Column(name = "aviso_15_dias_en")
	private Instant aviso15DiasEn;

	@Column(name = "aviso_km_en")
	private Instant avisoKmEn;

	protected Garantia() {
	}

	private Garantia(Long vehiculoClienteId, String componente, String descripcion, LocalDate iniciaEn,
			LocalDate venceEn, Integer kmLimite) {
		this.vehiculoClienteId = vehiculoClienteId;
		this.componente = componente;
		this.descripcion = descripcion;
		this.iniciaEn = iniciaEn;
		this.venceEn = venceEn;
		this.kmLimite = kmLimite;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta el vehículo, el componente o las fechas, si vence antes
	 *             de iniciar, si el componente o la descripción son muy largos o si
	 *             el km límite no es mayor a cero (400)
	 */
	public static Garantia registrar(Long vehiculoClienteId, String componente, String descripcion, LocalDate iniciaEn,
			LocalDate venceEn, Integer kmLimite) {
		if (vehiculoClienteId == null || iniciaEn == null || venceEn == null) {
			throw new IllegalArgumentException("El vehículo y las fechas de la garantía son obligatorios");
		}
		String nombre = componente == null ? "" : componente.strip();
		if (nombre.isEmpty() || nombre.length() > LARGO_COMPONENTE) {
			throw new IllegalArgumentException(
					"El componente es obligatorio y admite hasta " + LARGO_COMPONENTE + " caracteres");
		}
		String detalle = descripcion == null || descripcion.isBlank() ? null : descripcion.strip();
		if (detalle != null && detalle.length() > LARGO_DESCRIPCION) {
			throw new IllegalArgumentException("La descripción admite hasta " + LARGO_DESCRIPCION + " caracteres");
		}
		if (venceEn.isBefore(iniciaEn)) {
			throw new IllegalArgumentException("La garantía no puede vencer antes de iniciar");
		}
		if (kmLimite != null && kmLimite <= 0) {
			throw new IllegalArgumentException("El km límite debe ser mayor a cero");
		}
		return new Garantia(vehiculoClienteId, nombre, detalle, iniciaEn, venceEn, kmLimite);
	}

	/** Si ese aviso ya se mandó. */
	public boolean avisado(AvisoGarantia aviso) {
		return switch (aviso) {
			case DIAS_60 -> aviso60DiasEn != null;
			case DIAS_15 -> aviso15DiasEn != null;
			case KM_1000 -> avisoKmEn != null;
		};
	}

	/**
	 * Deja el aviso como mandado. El de 15 días también cierra el de 60: si ya
	 * faltan 15, el de 60 ya no tiene caso.
	 */
	public void marcarAvisado(AvisoGarantia aviso, Instant momento) {
		Objects.requireNonNull(momento, "momento");
		if (aviso == AvisoGarantia.KM_1000) {
			avisoKmEn = momento;
			return;
		}
		if (aviso == AvisoGarantia.DIAS_15) {
			aviso15DiasEn = momento;
		}
		if (aviso60DiasEn == null) {
			aviso60DiasEn = momento;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public String getComponente() {
		return componente;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public LocalDate getIniciaEn() {
		return iniciaEn;
	}

	public LocalDate getVenceEn() {
		return venceEn;
	}

	public Integer getKmLimite() {
		return kmLimite;
	}
}
