package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CategoriaTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.annotations.BatchSize;

/**
 * Refacción del inventario, que se maneja por existencia (ERP-2.0). La
 * existencia solo cambia con un movimiento de inventario y nunca queda negativa
 * (ERP-2.1); la base de datos también lo verifica.
 */
@Entity
@Table(name = "refaccion")
public class Refaccion extends Auditable {

	private static final int LARGO_MAXIMO_DESCRIPCION_APP = 1000;
	private static final int LARGO_MAXIMO_FOTO = 500;

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

	@Column(name = "publicada_en_app", nullable = false)
	private boolean publicadaEnApp;

	@Enumerated(EnumType.STRING)
	@Column(name = "categoria_app", length = 12)
	private CategoriaTienda categoriaApp;

	@Column(name = "descripcion_app", length = 1000)
	private String descripcionApp;

	@Column(name = "foto_url", length = 500)
	private String fotoUrl;

	/** Le queda a cualquier vehículo. */
	@Column(name = "universal_app", nullable = false)
	private boolean universalApp;

	@OneToMany(mappedBy = "refaccion", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("modelo, anioDesde, id")
	@BatchSize(size = 50)
	private List<CompatibilidadRefaccion> compatibilidades = new ArrayList<>();

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

	/**
	 * Publica la refacción en la tienda de la app o la retira, con lo que el
	 * cliente ve de ella (V2-M14.01). Reemplaza la compatibilidad completa.
	 *
	 * @throws IllegalArgumentException
	 *             si al publicar falta la categoría, no es universal ni tiene
	 *             compatibilidad, la foto no es una dirección https o un texto es
	 *             demasiado largo
	 */
	public void publicar(PublicacionTienda publicacion) {
		Objects.requireNonNull(publicacion, "publicacion");
		String descripcionLimpia = Texto.limpiar(publicacion.descripcion());
		if (descripcionLimpia != null && descripcionLimpia.length() > LARGO_MAXIMO_DESCRIPCION_APP) {
			throw new IllegalArgumentException("La descripción para la app admite hasta 1000 caracteres");
		}
		String foto = Texto.limpiar(publicacion.fotoUrl());
		if (foto != null && (foto.length() > LARGO_MAXIMO_FOTO || !foto.startsWith("https://"))) {
			throw new IllegalArgumentException("La foto debe ser una dirección https de hasta 500 caracteres");
		}
		if (publicacion.publicada() && publicacion.categoria() == null) {
			throw new IllegalArgumentException("Para publicar falta la categoría");
		}
		List<CompatibilidadRefaccion> nuevas = publicacion.compatibilidad().stream().map(
				modelo -> CompatibilidadRefaccion.de(this, modelo.modelo(), modelo.anioDesde(), modelo.anioHasta()))
				.toList();
		if (publicacion.publicada() && !publicacion.universal() && nuevas.isEmpty()) {
			throw new IllegalArgumentException("Indica a qué modelos le queda o márcala como universal");
		}
		this.publicadaEnApp = publicacion.publicada();
		this.categoriaApp = publicacion.categoria();
		this.descripcionApp = descripcionLimpia;
		this.fotoUrl = foto;
		this.universalApp = publicacion.universal();
		this.compatibilidades.clear();
		this.compatibilidades.addAll(nuevas);
	}

	/**
	 * Aparta piezas para un pedido: resta la existencia y devuelve el movimiento de
	 * salida que la respalda. Quien llama debe tener la fila bloqueada.
	 *
	 * @throws IllegalStateException
	 *             si no alcanzan las piezas
	 * @throws IllegalArgumentException
	 *             si la cantidad no es mayor a cero
	 */
	public MovimientoInventario apartar(int cantidad, Long pedidoAppId, Instant fecha) {
		MovimientoInventario movimiento = MovimientoInventario.dePedido(TipoMovimiento.SALIDA, this, cantidad,
				pedidoAppId, fecha);
		if (cantidad > existencia) {
			throw new IllegalStateException("No alcanzan las piezas de " + codigo);
		}
		existencia -= cantidad;
		return movimiento;
	}

	/**
	 * Devuelve las piezas de un pedido cancelado o vencido, con su movimiento de
	 * entrada. Quien llama debe tener la fila bloqueada.
	 */
	public MovimientoInventario liberar(int cantidad, Long pedidoAppId, Instant fecha) {
		MovimientoInventario movimiento = MovimientoInventario.dePedido(TipoMovimiento.ENTRADA, this, cantidad,
				pedidoAppId, fecha);
		existencia = Math.addExact(existencia, cantidad);
		return movimiento;
	}

	public boolean isPublicadaEnApp() {
		return publicadaEnApp;
	}

	public CategoriaTienda getCategoriaApp() {
		return categoriaApp;
	}

	public String getDescripcionApp() {
		return descripcionApp;
	}

	public String getFotoUrl() {
		return fotoUrl;
	}

	public boolean isUniversalApp() {
		return universalApp;
	}

	public List<CompatibilidadRefaccion> getCompatibilidades() {
		return Collections.unmodifiableList(compatibilidades);
	}

	/**
	 * Indica si la existencia llegó al mínimo o quedó por debajo; en ese caso se
	 * avisa a Refacciones y al Administrador (decisión del PO del 13 de octubre).
	 */
	public boolean estaBajoMinimo() {
		return existencia <= minimo;
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
