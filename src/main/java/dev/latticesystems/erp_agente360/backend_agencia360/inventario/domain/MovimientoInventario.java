package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Entrada o salida de un vehículo o de una refacción, con su fecha, cantidad y
 * responsable (ERP-2.0). El inventario se lleva por movimientos: no se
 * modifican ni se borran.
 *
 * <p>
 * El responsable se guarda solo como id: el usuario pertenece al módulo de
 * acceso y aquí no se navega hacia él.
 */
@Entity
@Table(name = "movimiento_inventario")
public class MovimientoInventario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 7)
	private TipoMovimiento tipo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vehiculo_id")
	private Vehiculo vehiculo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "refaccion_id")
	private Refaccion refaccion;

	@Column(nullable = false)
	private int cantidad;

	@Column(name = "responsable_id", nullable = false)
	private Long responsableId;

	@Column(nullable = false)
	private Instant fecha;

	protected MovimientoInventario() {
		// Requerido por JPA
	}

	private MovimientoInventario(TipoMovimiento tipo, Vehiculo vehiculo, Refaccion refaccion, int cantidad,
			Long responsableId, Instant fecha) {
		if (cantidad <= 0) {
			throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
		}
		this.tipo = tipo;
		this.vehiculo = vehiculo;
		this.refaccion = refaccion;
		this.cantidad = cantidad;
		this.responsableId = Objects.requireNonNull(responsableId, "responsableId");
		this.fecha = Objects.requireNonNull(fecha, "fecha");
	}

	/** Entrada de una unidad al inventario; cada vehículo cuenta como una pieza. */
	public static MovimientoInventario entradaDeVehiculo(Vehiculo vehiculo, Long responsableId, Instant fecha) {
		return new MovimientoInventario(TipoMovimiento.ENTRADA, Objects.requireNonNull(vehiculo, "vehiculo"), null, 1,
				responsableId, fecha);
	}

	/**
	 * Solo lo usa {@link Refaccion#recibir}, que también actualiza la existencia.
	 */
	static MovimientoInventario entradaDeRefaccion(Refaccion refaccion, int cantidad, Long responsableId,
			Instant fecha) {
		return new MovimientoInventario(TipoMovimiento.ENTRADA, null, Objects.requireNonNull(refaccion, "refaccion"),
				cantidad, responsableId, fecha);
	}

	public Long getId() {
		return id;
	}

	public TipoMovimiento getTipo() {
		return tipo;
	}

	public Vehiculo getVehiculo() {
		return vehiculo;
	}

	public Refaccion getRefaccion() {
		return refaccion;
	}

	public int getCantidad() {
		return cantidad;
	}

	public Long getResponsableId() {
		return responsableId;
	}

	public Instant getFecha() {
		return fecha;
	}
}
