package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

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
import java.math.RoundingMode;

/**
 * Un renglón del servicio, como en el ticket de una tienda: qué se hizo o qué
 * refacción se puso, cuántas veces, a qué precio (con IVA) y su importe.
 */
@Entity
@Table(name = "renglon_servicio")
public class RenglonServicio {

	static final int LARGO_MAXIMO_CONCEPTO = 200;
	static final int CANTIDAD_MAXIMA = 999;
	static final BigDecimal PRECIO_MAXIMO = new BigDecimal("9999999.99");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "servicio_id", nullable = false)
	private Servicio servicio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private TipoRenglon tipo;

	@Column(nullable = false, length = LARGO_MAXIMO_CONCEPTO)
	private String concepto;

	@Column(nullable = false)
	private int cantidad;

	@Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
	private BigDecimal precioUnitario;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal importe;

	protected RenglonServicio() {
		// Requerido por JPA
	}

	private RenglonServicio(Servicio servicio, TipoRenglon tipo, String concepto, int cantidad,
			BigDecimal precioUnitario) {
		this.servicio = servicio;
		this.tipo = tipo;
		this.concepto = concepto;
		this.cantidad = cantidad;
		this.precioUnitario = precioUnitario;
		this.importe = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta el tipo o el concepto, la cantidad no está entre 1 y 999
	 *             o el precio es negativo o tiene más de dos decimales (400)
	 */
	static RenglonServicio de(Servicio servicio, RenglonNuevo nuevo) {
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
		return new RenglonServicio(servicio, nuevo.tipo(), concepto, nuevo.cantidad(), conCentavos);
	}

	public Long getId() {
		return id;
	}

	public TipoRenglon getTipo() {
		return tipo;
	}

	public String getConcepto() {
		return concepto;
	}

	public int getCantidad() {
		return cantidad;
	}

	public BigDecimal getPrecioUnitario() {
		return precioUnitario;
	}

	public BigDecimal getImporte() {
		return importe;
	}
}
