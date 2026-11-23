package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.math.BigDecimal;
import java.math.RoundingMode;

@MappedSuperclass
public abstract class RenglonOrden {

	static final int LARGO_MAXIMO_CONCEPTO = 200;
	static final int CANTIDAD_MAXIMA = 999;
	static final BigDecimal PRECIO_MAXIMO = new BigDecimal("9999999.99");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "orden_servicio_id", nullable = false)
	private OrdenServicio ordenServicio;

	@Column(nullable = false, length = LARGO_MAXIMO_CONCEPTO)
	private String concepto;

	@Column(nullable = false)
	private int cantidad;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precio;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal importe;

	protected RenglonOrden() {
	}

	protected RenglonOrden(OrdenServicio ordenServicio, String concepto, int cantidad, BigDecimal precio) {
		this.ordenServicio = ordenServicio;
		this.concepto = concepto;
		this.cantidad = cantidad;
		this.precio = precio;
		this.importe = precio.multiply(BigDecimal.valueOf(cantidad));
	}

	static RenglonOrden de(OrdenServicio orden, RenglonNuevo nuevo) {
		if (nuevo == null || nuevo.tipo() == null) {
			throw new IllegalArgumentException("Cada renglón dice si es mano de obra o refacción");
		}
		String concepto = nuevo.concepto() == null ? "" : nuevo.concepto().strip();
		if (concepto.isEmpty() || concepto.length() > LARGO_MAXIMO_CONCEPTO) {
			throw new IllegalArgumentException(
					"El concepto es obligatorio y admite hasta " + LARGO_MAXIMO_CONCEPTO + " caracteres");
		}
		if (nuevo.cantidad() < 1 || nuevo.cantidad() > CANTIDAD_MAXIMA) {
			throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + CANTIDAD_MAXIMA);
		}
		BigDecimal precio = nuevo.precioUnitario();
		if (precio == null || precio.signum() < 0 || precio.compareTo(PRECIO_MAXIMO) > 0) {
			throw new IllegalArgumentException("El precio debe estar entre 0 y " + PRECIO_MAXIMO);
		}
		BigDecimal conCentavos;
		try {
			conCentavos = precio.setScale(2, RoundingMode.UNNECESSARY);
		} catch (ArithmeticException masDeDosDecimales) {
			throw new IllegalArgumentException("El precio admite hasta dos decimales");
		}
		if (nuevo.tipo() == TipoRenglon.MANO_DE_OBRA) {
			return new OrdenManoDeObra(orden, concepto, nuevo.cantidad(), conCentavos);
		}
		return new OrdenRefaccion(orden, concepto, nuevo.cantidad(), conCentavos);
	}

	public abstract TipoRenglon getTipo();

	public Long getId() {
		return id;
	}

	public String getConcepto() {
		return concepto;
	}

	public int getCantidad() {
		return cantidad;
	}

	public BigDecimal getPrecio() {
		return precio;
	}

	public BigDecimal getImporte() {
		return importe;
	}
}
