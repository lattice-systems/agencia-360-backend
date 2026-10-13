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

	/**
	 * Da de alta una refacción sin existencia; la existencia llega con las
	 * recepciones (decisión del PO del 13 de octubre). Quita espacios sobrantes y
	 * guarda el código en mayúsculas para poder comparar repetidos.
	 *
	 * @throws IllegalArgumentException
	 *             si falta el código o la descripción, el costo es negativo, el
	 *             precio no es mayor a cero o el mínimo es negativo
	 */
	public static Refaccion registrar(String codigo, String descripcion, BigDecimal costo, BigDecimal precio,
			int minimo) {
		Refaccion refaccion = new Refaccion();
		refaccion.asignarDatos(codigo, descripcion, costo, precio, minimo);
		refaccion.existencia = 0;
		return refaccion;
	}

	/**
	 * Corrige los datos de la refacción (decisión del PO del 13 de octubre: se
	 * edita, no se elimina). Aplica las mismas reglas que el alta. La existencia no
	 * se edita: solo cambia con movimientos.
	 *
	 * <p>
	 * Quien llama debe tener la fila bloqueada para no pisar la existencia que sume
	 * una recepción al mismo tiempo.
	 *
	 * @throws IllegalArgumentException
	 *             si un dato no es válido
	 */
	public void editar(String codigo, String descripcion, BigDecimal costo, BigDecimal precio, int minimo) {
		asignarDatos(codigo, descripcion, costo, precio, minimo);
	}

	/**
	 * Quita espacios y pasa el código a mayúsculas, como se guarda.
	 *
	 * @throws IllegalArgumentException
	 *             si falta el código
	 */
	public static String normalizarCodigo(String codigo) {
		return Texto.obligatorio(codigo, "El código es obligatorio").toUpperCase(Locale.ROOT);
	}

	/** Valida todo antes de cambiar algo, para no dejar la refacción a medias. */
	private void asignarDatos(String codigo, String descripcion, BigDecimal costo, BigDecimal precio, int minimo) {
		String codigoLimpio = normalizarCodigo(codigo);
		String descripcionLimpia = Texto.obligatorio(descripcion, "La descripción es obligatoria");
		if (costo == null || costo.signum() < 0) {
			throw new IllegalArgumentException("El costo no puede ser negativo");
		}
		BigDecimal precioValido = Montos.mayorACero(precio);
		if (minimo < 0) {
			throw new IllegalArgumentException("El mínimo no puede ser negativo");
		}
		this.codigo = codigoLimpio;
		this.descripcion = descripcionLimpia;
		this.costo = costo;
		this.precio = precioValido;
		this.minimo = minimo;
	}

	/**
	 * Registra una entrega de piezas: suma la cantidad a la existencia y devuelve
	 * el movimiento de entrada que la respalda, que se debe guardar en la misma
	 * transacción.
	 *
	 * <p>
	 * Quien llama debe tener la fila bloqueada (ver
	 * {@code RefaccionRepository.buscarParaActualizar}) para que dos recepciones
	 * simultáneas no se pisen.
	 *
	 * @param fechaRegistro
	 *            momento en que se registra; la llegada no puede ser posterior
	 * @throws IllegalArgumentException
	 *             si la recepción no es válida o la existencia rebasaría el máximo
	 *             que cabe en la columna
	 */
	public MovimientoInventario recibir(Recepcion recepcion, Long responsableId, Instant fechaRegistro) {
		MovimientoInventario movimiento = MovimientoInventario.entradaDeRefaccion(this, recepcion, responsableId,
				fechaRegistro);
		try {
			existencia = Math.addExact(existencia, movimiento.getCantidad());
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
