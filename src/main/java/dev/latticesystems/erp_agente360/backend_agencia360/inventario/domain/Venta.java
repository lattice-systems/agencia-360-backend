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
 * Venta de una unidad (ERP-1.0). Ya no existe el apartado (decisión del PO del
 * 19 de octubre): Facturación elige la unidad de un prospecto en Facturación y
 * la unidad pasa a Vendido. Es venta cuando el vendedor lleva el prospecto
 * hasta Finalizado; entonces queda {@link EstatusVenta#REGISTRADA}.
 *
 * <p>
 * El prospecto se guarda solo como id para no cruzar módulos; la llave foránea
 * vive en la base.
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

	/** Precio de la unidad cuando se eligió. */
	@Column(precision = 12, scale = 2)
	private BigDecimal precio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstatusVenta estatus;

	@Column(name = "asignada_por")
	private Long asignadaPor;

	@Column(name = "asignada_en")
	private Instant asignadaEn;

	@Column(name = "registrada_en")
	private Instant registradaEn;

	protected Venta() {
		// Requerido por JPA
	}

	private Venta(Long prospectoId, EstatusVenta estatus) {
		this.prospectoId = Objects.requireNonNull(prospectoId, "prospectoId");
		this.estatus = estatus;
	}

	/**
	 * Facturación elige la unidad para el prospecto: la unidad pasa a Vendido.
	 *
	 * @throws ConflictoException
	 *             si la unidad ya no está disponible (409)
	 */
	public static Venta asignar(Long prospectoId, Vehiculo vehiculo, Long usuarioId, Instant ahora) {
		Venta venta = new Venta(prospectoId, EstatusVenta.POR_ENTREGAR);
		vehiculo.vender();
		venta.ponerUnidad(vehiculo, usuarioId, ahora);
		return venta;
	}

	/**
	 * El prospecto llegó a Finalizado sin que Facturación eligiera su unidad. La
	 * venta se registra igual para no perderla; la unidad se liga después.
	 */
	public static Venta registrarSinUnidad(Long prospectoId, Instant fechaEntrega) {
		Venta venta = new Venta(prospectoId, EstatusVenta.REGISTRADA);
		venta.registradaEn = fechaEntrega;
		return venta;
	}

	/**
	 * Facturación se equivocó de unidad: la nueva pasa a Vendido y la anterior
	 * regresa a Disponible.
	 *
	 * @return {@code false} si ya era la unidad de la venta
	 * @throws ConflictoException
	 *             si la venta ya se registró o la nueva unidad no está disponible
	 *             (409); en ese caso no cambia nada
	 */
	public boolean cambiarUnidad(Vehiculo nueva, Long usuarioId, Instant ahora) {
		if (estatus == EstatusVenta.REGISTRADA) {
			throw new ConflictoException("La venta ya se registró: su unidad no se cambia");
		}
		if (nueva == vehiculo) {
			return false;
		}
		nueva.vender();
		vehiculo.liberar();
		ponerUnidad(nueva, usuarioId, ahora);
		return true;
	}

	/**
	 * El vendedor pasó el prospecto a Finalizado: es venta.
	 */
	public void registrar(Instant fechaEntrega) {
		estatus = EstatusVenta.REGISTRADA;
		registradaEn = fechaEntrega;
	}

	private void ponerUnidad(Vehiculo unidad, Long usuarioId, Instant ahora) {
		this.vehiculo = unidad;
		this.precio = unidad.getPrecio();
		this.asignadaPor = usuarioId;
		this.asignadaEn = ahora;
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
}
