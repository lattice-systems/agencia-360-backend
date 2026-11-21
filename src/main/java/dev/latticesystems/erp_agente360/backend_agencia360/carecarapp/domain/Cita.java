package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Cita de servicio que el cliente agenda desde la app (APP-3.3). Nace
 * confirmada y ocupa un lugar de su franja hasta que se cancela. La fecha y las
 * horas son hora local de la agencia.
 *
 * <p>
 * {@code clienteId} y {@code vehiculoClienteId} solo se mapean como ids para no
 * cruzar módulos; las llaves foráneas sí viven en la base.
 */
@Entity
@Table(name = "cita")
public class Cita extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Column(name = "vehiculo_cliente_id", nullable = false)
	private Long vehiculoClienteId;

	@Column(nullable = false)
	private LocalDate fecha;

	@Column(name = "franja_id", nullable = false)
	private Long franjaId;

	@Column(name = "hora_inicio", nullable = false)
	private LocalTime horaInicio;

	@Column(name = "hora_fin", nullable = false)
	private LocalTime horaFin;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private EstadoCita estado;

	@Column(name = "cancelada_en")
	private Instant canceladaEn;

	protected Cita() {
		// Requerido por JPA
	}

	private Cita(Long clienteId, Long vehiculoClienteId, LocalDate fecha, Long franjaId, LocalTime horaInicio,
			LocalTime horaFin) {
		this.clienteId = clienteId;
		this.vehiculoClienteId = vehiculoClienteId;
		this.fecha = fecha;
		this.franjaId = franjaId;
		this.horaInicio = horaInicio;
		this.horaFin = horaFin;
		this.estado = EstadoCita.CONFIRMADA;
	}

	/** Una cita confirmada en la franja; el cupo lo revisa quien la agenda. */
	public static Cita confirmar(Long clienteId, Long vehiculoClienteId, LocalDate fecha, Long franjaId,
			LocalTime horaInicio, LocalTime horaFin) {
		Objects.requireNonNull(clienteId, "clienteId");
		Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		Objects.requireNonNull(fecha, "fecha");
		Objects.requireNonNull(franjaId, "franjaId");
		if (!horaFin.isAfter(horaInicio)) {
			throw new IllegalArgumentException("La franja debe terminar después de empezar");
		}
		return new Cita(clienteId, vehiculoClienteId, fecha, franjaId, horaInicio, horaFin);
	}

	/**
	 * Cancela la cita si sigue confirmada y faltan al menos {@code plazo} para que
	 * empiece.
	 *
	 * @param ahora
	 *            hora local de la agencia
	 * @throws ConflictoException
	 *             con {@code CITA_NO_CANCELABLE} si ya no está confirmada, o con
	 *             {@code FUERA_DE_PLAZO} si ya no hay tiempo (409)
	 */
	public void cancelar(LocalDateTime ahora, Duration plazo, Instant en) {
		if (estado != EstadoCita.CONFIRMADA) {
			throw new ConflictoException("La cita ya no está confirmada, no se puede cancelar",
					CodigosError.CITA_NO_CANCELABLE);
		}
		if (ahora.plus(plazo).isAfter(fecha.atTime(horaInicio))) {
			throw new ConflictoException(
					"La cita solo se puede cancelar hasta " + plazo.toHours() + " horas antes de su inicio",
					CodigosError.FUERA_DE_PLAZO);
		}
		estado = EstadoCita.CANCELADA;
		canceladaEn = en;
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public Long getFranjaId() {
		return franjaId;
	}

	public LocalTime getHoraInicio() {
		return horaInicio;
	}

	public LocalTime getHoraFin() {
		return horaFin;
	}

	public EstadoCita getEstado() {
		return estado;
	}

	public Instant getCanceladaEn() {
		return canceladaEn;
	}
}
