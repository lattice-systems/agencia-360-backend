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
 * Entrada o salida de un vehículo o de una refacción, con su cantidad y
 * responsable (ERP-2.0). El inventario se lleva por movimientos: no se
 * modifican ni se borran.
 *
 * <p>
 * Guarda dos fechas (decisión del PO del 13 de octubre): cuándo llegó la
 * mercancía y cuándo se registró. Casi siempre coinciden; si no, al
 * Administrador le interesa saberlo.
 *
 * <p>
 * El responsable se guarda solo como id: el usuario pertenece al módulo de
 * acceso y aquí no se navega hacia él.
 */
@Entity
@Table(name = "movimiento_inventario")
public class MovimientoInventario {

	private static final int LARGO_MAXIMO_PROVEEDOR = 120;
	private static final int LARGO_MAXIMO_NOTAS = 500;

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

	@Column(name = "fecha_llegada", nullable = false)
	private Instant fechaLlegada;

	@Column(name = "fecha_registro", nullable = false)
	private Instant fechaRegistro;

	@Column(length = 120)
	private String proveedor;

	@Column(length = 500)
	private String notas;

	protected MovimientoInventario() {
		// Requerido por JPA
	}

	private MovimientoInventario(TipoMovimiento tipo, Vehiculo vehiculo, Refaccion refaccion, int cantidad,
			Long responsableId, Instant fechaLlegada, Instant fechaRegistro, String proveedor, String notas) {
		if (cantidad <= 0) {
			throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
		}
		this.tipo = tipo;
		this.vehiculo = vehiculo;
		this.refaccion = refaccion;
		this.cantidad = cantidad;
		this.responsableId = Objects.requireNonNull(responsableId, "responsableId");
		this.fechaLlegada = Objects.requireNonNull(fechaLlegada, "fechaLlegada");
		this.fechaRegistro = Objects.requireNonNull(fechaRegistro, "fechaRegistro");
		this.proveedor = proveedor;
		this.notas = notas;
	}

	/**
	 * Entrada de una unidad al inventario; cada vehículo cuenta como una pieza y
	 * llega cuando se da de alta.
	 */
	public static MovimientoInventario entradaDeVehiculo(Vehiculo vehiculo, Long responsableId, Instant fechaRegistro) {
		return new MovimientoInventario(TipoMovimiento.ENTRADA, Objects.requireNonNull(vehiculo, "vehiculo"), null, 1,
				responsableId, fechaRegistro, fechaRegistro, null, null);
	}

	/**
	 * Salida de una unidad vendida: el vendedor la entregó al cliente (ERP-1.0).
	 */
	public static MovimientoInventario salidaDeVehiculo(Vehiculo vehiculo, Long responsableId, Instant fechaEntrega) {
		return new MovimientoInventario(TipoMovimiento.SALIDA, Objects.requireNonNull(vehiculo, "vehiculo"), null, 1,
				responsableId, fechaEntrega, fechaEntrega, null, null);
	}

	/**
	 * Solo lo usa {@link Refaccion#recibir}, que también actualiza la existencia.
	 *
	 * @throws IllegalArgumentException
	 *             si la cantidad no es mayor a cero, falta el proveedor, un texto
	 *             es demasiado largo o la llegada es posterior al registro
	 */
	static MovimientoInventario entradaDeRefaccion(Refaccion refaccion, Recepcion recepcion, Long responsableId,
			Instant fechaRegistro) {
		Objects.requireNonNull(recepcion, "recepcion");
		Objects.requireNonNull(fechaRegistro, "fechaRegistro");
		String proveedor = Texto.obligatorio(recepcion.proveedor(), "El proveedor es obligatorio");
		if (proveedor.length() > LARGO_MAXIMO_PROVEEDOR) {
			throw new IllegalArgumentException("El proveedor admite hasta 120 caracteres");
		}
		String notas = Texto.limpiar(recepcion.notas());
		if (notas != null && notas.length() > LARGO_MAXIMO_NOTAS) {
			throw new IllegalArgumentException("Las notas admiten hasta 500 caracteres");
		}
		Instant fechaLlegada = recepcion.fechaLlegada() == null ? fechaRegistro : recepcion.fechaLlegada();
		if (fechaLlegada.isAfter(fechaRegistro)) {
			throw new IllegalArgumentException("La fecha de llegada no puede ser posterior al registro");
		}
		return new MovimientoInventario(TipoMovimiento.ENTRADA, null, Objects.requireNonNull(refaccion, "refaccion"),
				recepcion.cantidad(), responsableId, fechaLlegada, fechaRegistro, proveedor, notas);
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

	public Instant getFechaLlegada() {
		return fechaLlegada;
	}

	public Instant getFechaRegistro() {
		return fechaRegistro;
	}

	public String getProveedor() {
		return proveedor;
	}

	public String getNotas() {
		return notas;
	}
}
