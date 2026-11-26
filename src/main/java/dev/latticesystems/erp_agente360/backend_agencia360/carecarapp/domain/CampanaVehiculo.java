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
 * Una campaña de seguridad para un vehículo que entra en su cruce (V2-M10.02).
 * Nace abierta; al agendar guarda la cita (V2-M10.03) y si esa cita se cancela
 * vuelve a estar abierta. La atendida ya no cambia.
 */
@Entity
@Table(name = "campana_vehiculo")
public class CampanaVehiculo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campana_seguridad_id", nullable = false)
	private Long campanaSeguridadId;

	@Column(name = "vehiculo_cliente_id", nullable = false)
	private Long vehiculoClienteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoCampana estado;

	@Column(name = "cita_id")
	private Long citaId;

	@Column(name = "avisada_en")
	private Instant avisadaEn;

	protected CampanaVehiculo() {
	}

	public CampanaVehiculo(Long campanaSeguridadId, Long vehiculoClienteId) {
		this.campanaSeguridadId = Objects.requireNonNull(campanaSeguridadId, "campanaSeguridadId");
		this.vehiculoClienteId = Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		this.estado = EstadoCampana.ABIERTA;
	}

	/**
	 * @throws IllegalStateException
	 *             si la campaña ya está agendada o atendida
	 */
	public void agendar(Long cita) {
		Objects.requireNonNull(cita, "cita");
		if (estado != EstadoCampana.ABIERTA) {
			throw new IllegalStateException("La campaña ya está " + estado.name().toLowerCase(Locale.ROOT));
		}
		estado = EstadoCampana.AGENDADA;
		citaId = cita;
	}

	/** La cita se canceló: la campaña vuelve a estar abierta para agendar. */
	public void reabrir() {
		if (estado == EstadoCampana.AGENDADA) {
			estado = EstadoCampana.ABIERTA;
			citaId = null;
		}
	}

	/** El taller ya la atendió. */
	public void atender() {
		estado = EstadoCampana.ATENDIDA;
	}

	public void marcarAvisada(Instant momento) {
		avisadaEn = Objects.requireNonNull(momento, "momento");
	}

	public Long getId() {
		return id;
	}

	public Long getCampanaSeguridadId() {
		return campanaSeguridadId;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public EstadoCampana getEstado() {
		return estado;
	}

	public Long getCitaId() {
		return citaId;
	}

	public Instant getAvisadaEn() {
		return avisadaEn;
	}
}
