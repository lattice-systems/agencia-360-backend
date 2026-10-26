package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Venta de una unidad (ERP-1.3). Es venta cuando Facturación emite la factura:
 * en esa misma transacción la unidad pasa de Disponible a Vendido y la venta
 * queda {@link EstatusVenta#REGISTRADA}. Cuando el vendedor lleva el prospecto
 * hasta Finalizado, la unidad sale del inventario y la venta queda
 * {@link EstatusVenta#ENTREGADA}.
 *
 * <p>
 * El prospecto se guarda solo como id para no cruzar módulos; la llave foránea
 * vive en la base. Las ventas del Sprint 2 que se finalizaron sin unidad quedan
 * entregadas y sin vehículo.
 */
@Entity
@Table(name = "venta")
public class Venta extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "prospecto_id", nullable = false, unique = true)
	private Long prospectoId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vehiculo_id", unique = true)
	private Vehiculo vehiculo;

	/** Precio de lista de la unidad cuando se vendió. */
	@Column(precision = 12, scale = 2)
	private BigDecimal precio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstatusVenta estatus;

	/** Quién de Facturación emitió la factura con esta unidad. */
	@Column(name = "asignada_por")
	private Long asignadaPor;

	@Column(name = "asignada_en")
	private Instant asignadaEn;

	/** Cuando se emitió la factura. */
	@Column(name = "registrada_en")
	private Instant registradaEn;

	/** Cuando el prospecto llegó a Finalizado; vacío mientras tanto. */
	@Column(name = "entregada_en")
	private Instant entregadaEn;

	protected Venta() {
		// Requerido por JPA
	}

	/**
	 * Facturación emite la factura con esta unidad: la unidad pasa a Vendido y es
	 * venta.
	 *
	 * @throws ConflictoException
	 *             si la unidad ya no está disponible (409)
	 */
	public static Venta registrar(Long prospectoId, Vehiculo vehiculo, Long usuarioId, Instant ahora) {
		Objects.requireNonNull(ahora, "ahora");
		vehiculo.vender();
		Venta venta = new Venta();
		venta.prospectoId = Objects.requireNonNull(prospectoId, "prospectoId");
		venta.estatus = EstatusVenta.REGISTRADA;
		venta.vehiculo = vehiculo;
		venta.precio = vehiculo.getPrecio();
		venta.asignadaPor = usuarioId;
		venta.asignadaEn = ahora;
		venta.registradaEn = ahora;
		return venta;
	}

	/**
	 * El vendedor pasó el prospecto a Finalizado: la unidad ya se entregó.
	 *
	 * @return {@code false} si ya estaba entregada (el evento llegó dos veces)
	 */
	public boolean entregar(Instant fechaEntrega) {
		if (estatus == EstatusVenta.ENTREGADA) {
			return false;
		}
		estatus = EstatusVenta.ENTREGADA;
		entregadaEn = Objects.requireNonNull(fechaEntrega, "fechaEntrega");
		return true;
	}

	public Long getId() {
		return id;
	}

	public Long getProspectoId() {
		return prospectoId;
	}

	public Vehiculo getVehiculo() {
		return vehiculo;
	}

	public BigDecimal getPrecio() {
		return precio;
	}

	public EstatusVenta getEstatus() {
		return estatus;
	}

	public Long getAsignadaPor() {
		return asignadaPor;
	}

	public Instant getAsignadaEn() {
		return asignadaEn;
	}

	public Instant getRegistradaEn() {
		return registradaEn;
	}

	public Instant getEntregadaEn() {
		return entregadaEn;
	}
}
