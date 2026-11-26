package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Una pieza del pedido con el precio que tenía al apartarla. */
@Entity
@Table(name = "pedido_app_renglon")
public class PedidoRenglon {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_app_id")
	private PedidoApp pedido;

	@Column(name = "refaccion_id", nullable = false)
	private Long refaccionId;

	@Column(nullable = false)
	private int cantidad;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precio;

	protected PedidoRenglon() {
		// Requerido por JPA
	}

	PedidoRenglon(PedidoApp pedido, Long refaccionId, int cantidad, BigDecimal precio) {
		this.pedido = pedido;
		this.refaccionId = refaccionId;
		this.cantidad = cantidad;
		this.precio = precio;
	}

	public Long getRefaccionId() {
		return refaccionId;
	}

	public int getCantidad() {
		return cantidad;
	}

	public BigDecimal getPrecio() {
		return precio;
	}

	public BigDecimal getImporte() {
		return precio.multiply(BigDecimal.valueOf(cantidad));
	}
}
