package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * Refacción del inventario, que se maneja por existencia (ERP-2.0). La
 * existencia solo cambia con un movimiento de inventario y nunca queda negativa
 * (ERP-2.1); la base de datos también lo verifica.
 */
@Entity
@Table(name = "refaccion")
public class Refaccion extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 40)
	private String codigo;

	@Column(nullable = false, length = 200)
	private String descripcion;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal costo;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precio;

	@Column(nullable = false)
	private int existencia;

	/** Existencia mínima deseada; sirve para avisar cuando hay que resurtir. */
	@Column(nullable = false)
	private int minimo;

	protected Refaccion() {
		// Requerido por JPA
	}

	private Refaccion(String codigo, String descripcion, BigDecimal costo, BigDecimal precio, int minimo) {
		this.codigo = codigo;
		this.descripcion = descripcion;
		this.costo = costo;
		this.precio = precio;
		this.minimo = minimo;
		this.existencia = 0;
	}

	/**
	 * Da de alta una refacción sin existencia; la existencia llega con las
	 * recepciones. Quita espacios sobrantes y guarda el código en mayúsculas para
	 * poder comparar repetidos.
	 *
	 * @throws IllegalArgumentException
	 *             si falta el código o la descripción, el costo es negativo, el
	 *             precio no es mayor a cero o el mínimo es negativo
	 */
	public static Refaccion registrar(String codigo, String descripcion, BigDecimal costo, BigDecimal precio,
			int minimo) {
		String codigoLimpio = Texto.obligatorio(codigo, "El código es obligatorio").toUpperCase(Locale.ROOT);
		String descripcionLimpia = Texto.obligatorio(descripcion, "La descripción es obligatoria");
		if (costo == null || costo.signum() < 0) {
			throw new IllegalArgumentException("El costo no puede ser negativo");
		}
		BigDecimal precioValido = Montos.mayorACero(precio);
		if (minimo < 0) {
			throw new IllegalArgumentException("El mínimo no puede ser negativo");
		}
		return new Refaccion(codigoLimpio, descripcionLimpia, costo, precioValido, minimo);
	}

	/**
	 * Registra la recepción de piezas: suma la cantidad a la existencia y devuelve
	 * el movimiento de entrada que la respalda, que se debe guardar en la misma
	 * transacción.
	 *
	 * <p>
	 * Quien llama debe tener la fila bloqueada (ver
	 * {@code RefaccionRepository.buscarParaActualizar}) para que dos recepciones
	 * simultáneas no se pisen.
	 *
	 * @throws IllegalArgumentException
	 *             si la cantidad no es mayor a cero o la existencia rebasaría el
	 *             máximo que cabe en la columna
	 */
	public MovimientoInventario recibir(int cantidad, Long responsableId, Instant fecha) {
		MovimientoInventario movimiento = MovimientoInventario.entradaDeRefaccion(this, cantidad, responsableId, fecha);
		try {
			existencia = Math.addExact(existencia, cantidad);
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("La existencia rebasaría el máximo permitido", e);
		}
		return movimiento;
	}

	public Long getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public BigDecimal getCosto() {
		return costo;
	}

	public BigDecimal getPrecio() {
		return precio;
	}

	public int getExistencia() {
		return existencia;
	}

	public int getMinimo() {
		return minimo;
	}
}
