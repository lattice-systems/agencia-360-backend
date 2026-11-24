package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Traslado de recolección o entrega de un vehículo ligado a una cita
 * (V2-M17.02). Nace {@code PROGRAMADO} sin chofer. La fecha y la ventana son
 * hora local de la agencia. Los ids de cita y dirección se mapean como ids; las
 * llaves foráneas viven en la base.
 */
@Entity
@Table(name = "traslado")
public class Traslado extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cita_id", nullable = false, updatable = false)
	private Long citaId;

	@Column(name = "direccion_cliente_id", nullable = false, updatable = false)
	private Long direccionClienteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10, updatable = false)
	private TipoTraslado tipo;

	@Column(name = "chofer_id")
	private Long choferId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private EstadoTraslado estado;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal costo;

	@Column(nullable = false)
	private LocalDate fecha;

	@Column(name = "ventana_inicio", nullable = false)
	private LocalTime ventanaInicio;

	@Column(name = "ventana_fin", nullable = false)
	private LocalTime ventanaFin;

	protected Traslado() {
		// Requerido por JPA
	}

	private Traslado(Long citaId, Long direccionClienteId, TipoTraslado tipo, BigDecimal costo, LocalDate fecha,
			LocalTime ventanaInicio, LocalTime ventanaFin) {
		this.citaId = citaId;
		this.direccionClienteId = direccionClienteId;
		this.tipo = tipo;
		this.estado = EstadoTraslado.PROGRAMADO;
		this.costo = costo;
		this.fecha = fecha;
		this.ventanaInicio = ventanaInicio;
		this.ventanaFin = ventanaFin;
	}

	/**
	 * Un traslado programado.
	 *
	 * @throws IllegalArgumentException
	 *             si el costo es negativo o la ventana no termina después de
	 *             empezar
	 */
	public static Traslado programar(Long citaId, Long direccionClienteId, TipoTraslado tipo, BigDecimal costo,
			LocalDate fecha, LocalTime ventanaInicio, LocalTime ventanaFin) {
		Objects.requireNonNull(citaId, "citaId");
		Objects.requireNonNull(direccionClienteId, "direccionClienteId");
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(fecha, "fecha");
		if (costo == null || costo.signum() < 0) {
			throw new IllegalArgumentException("El costo no puede ser negativo");
		}
		if (!ventanaFin.isAfter(ventanaInicio)) {
			throw new IllegalArgumentException("La ventana debe terminar después de empezar");
		}
		return new Traslado(citaId, direccionClienteId, tipo, costo, fecha, ventanaInicio, ventanaFin);
	}

	public Long getId() {
		return id;
	}

	public Long getCitaId() {
		return citaId;
	}

	public Long getDireccionClienteId() {
		return direccionClienteId;
	}

	public TipoTraslado getTipo() {
		return tipo;
	}

	public Long getChoferId() {
		return choferId;
	}

	public EstadoTraslado getEstado() {
		return estado;
	}

	public BigDecimal getCosto() {
		return costo;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public LocalTime getVentanaInicio() {
		return ventanaInicio;
	}

	public LocalTime getVentanaFin() {
		return ventanaFin;
	}
}
