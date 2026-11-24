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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Traslado de recolección o entrega de un vehículo ligado a una cita
 * (V2-M17.02) y su seguimiento (V2-M17.03). Nace {@code PROGRAMADO} sin chofer.
 * La fecha y la ventana son hora local de la agencia. Los ids de cita y
 * dirección se mapean como ids; las llaves foráneas viven en la base.
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

	@Column(name = "chofer_lat", precision = 9, scale = 6)
	private BigDecimal choferLat;

	@Column(name = "chofer_lng", precision = 9, scale = 6)
	private BigDecimal choferLng;

	@Column(name = "ubicacion_en")
	private Instant ubicacionEn;

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

	/**
	 * El chofer mueve el traslado al siguiente paso de su tipo: recoger va
	 * programado, en camino, recogido y en taller; entregar va programado, de
	 * regreso y entregado. La primera vez queda su id como chofer. Al salir de en
	 * camino o de regreso se borra su última ubicación.
	 *
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} si el paso no sigue al estado
	 *             actual (409); cancelar no es un paso del chofer
	 */
	public void cambiarEstado(EstadoTraslado nuevo, Long choferId) {
		Objects.requireNonNull(nuevo, "nuevo");
		if (siguiente() != nuevo) {
			throw new ConflictoException(
					"Un traslado de " + tipo.valor() + " " + estado.valor() + " no puede pasar a " + nuevo.valor(),
					CodigosError.TRANSICION_INVALIDA);
		}
		if (choferId != null && this.choferId == null) {
			this.choferId = choferId;
		}
		this.estado = nuevo;
		if (!nuevo.compartePosicion()) {
			borrarUbicacion();
		}
	}

	/**
	 * Guarda la última ubicación del chofer.
	 *
	 * @throws IllegalArgumentException
	 *             si falta un dato o está fuera de rango (400)
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} si el traslado no va en camino ni
	 *             de regreso (409)
	 */
	public void reportarUbicacion(BigDecimal lat, BigDecimal lng, Instant en) {
		Objects.requireNonNull(en, "en");
		if (lat == null || lng == null) {
			throw new IllegalArgumentException("La latitud y la longitud son obligatorias");
		}
		if (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lng.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
			throw new IllegalArgumentException("La ubicación está fuera de rango");
		}
		if (!estado.compartePosicion()) {
			throw new ConflictoException("Solo se comparte la ubicación en camino o de regreso",
					CodigosError.TRANSICION_INVALIDA);
		}
		this.choferLat = lat;
		this.choferLng = lng;
		this.ubicacionEn = en;
	}

	/**
	 * La cita se canceló: lo programado se cancela y lo que ya salió sigue.
	 *
	 * @return si el traslado cambió
	 */
	public boolean cancelarPorCita() {
		if (estado != EstadoTraslado.PROGRAMADO) {
			return false;
		}
		this.estado = EstadoTraslado.CANCELADO;
		return true;
	}

	/**
	 * La cita se reprogramó: lo programado toma el día nuevo, con la misma ventana.
	 */
	public void reprogramar(LocalDate nuevaFecha) {
		Objects.requireNonNull(nuevaFecha, "nuevaFecha");
		if (estado == EstadoTraslado.PROGRAMADO) {
			this.fecha = nuevaFecha;
		}
	}

	private EstadoTraslado siguiente() {
		return switch (estado) {
			case PROGRAMADO -> tipo == TipoTraslado.RECOGER ? EstadoTraslado.EN_CAMINO : EstadoTraslado.DE_REGRESO;
			case EN_CAMINO -> EstadoTraslado.RECOGIDO;
			case RECOGIDO -> EstadoTraslado.EN_TALLER;
			case DE_REGRESO -> EstadoTraslado.ENTREGADO;
			case EN_TALLER, ENTREGADO, CANCELADO -> null;
		};
	}

	private void borrarUbicacion() {
		this.choferLat = null;
		this.choferLng = null;
		this.ubicacionEn = null;
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

	public BigDecimal getChoferLat() {
		return choferLat;
	}

	public BigDecimal getChoferLng() {
		return choferLng;
	}

	public Instant getUbicacionEn() {
		return ubicacionEn;
	}
}
