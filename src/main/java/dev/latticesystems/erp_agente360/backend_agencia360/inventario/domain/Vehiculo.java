package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unidad del inventario (ERP-2.0). Los vehículos se registran uno por uno y se
 * identifican por su VIN, que no se repite.
 */
@Entity
@Table(name = "vehiculo")
public class Vehiculo extends Auditable {

	/** 17 caracteres sin I, O ni Q, que el estándar del VIN excluye. */
	private static final Pattern VIN_VALIDO = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");
	private static final int ANIO_MINIMO = 1900;

	/**
	 * Estatus con los que una unidad puede entrar al inventario: No disponible es
	 * para unidades con daños u otros motivos (decisión del PO del 13 de octubre).
	 * Los demás los pone la venta.
	 */
	private static final Set<EstatusVehiculo> ESTATUS_DE_ENTRADA = Set.of(EstatusVehiculo.DISPONIBLE,
			EstatusVehiculo.NO_DISPONIBLE);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 17)
	private String vin;

	@Column(nullable = false, length = 80)
	private String modelo;

	@Column(nullable = false, length = 80)
	private String version;

	@Column(nullable = false)
	private int anio;

	@Column(nullable = false, length = 40)
	private String color;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstatusVehiculo estatus;

	protected Vehiculo() {
		// Requerido por JPA
	}

	private Vehiculo(String vin, String modelo, String version, int anio, String color, BigDecimal precio,
			EstatusVehiculo estatus) {
		this.vin = vin;
		this.modelo = modelo;
		this.version = version;
		this.anio = anio;
		this.color = color;
		this.precio = precio;
		this.estatus = estatus;
	}

	/**
	 * Registra una unidad. Quita espacios sobrantes y guarda el VIN en mayúsculas
	 * para poder comparar repetidos.
	 *
	 * @param estatus
	 *            Disponible o No disponible; {@code null} para Disponible
	 * @throws IllegalArgumentException
	 *             si falta un dato, el VIN no tiene el formato del estándar, el año
	 *             es menor a 1900, el precio no es mayor a cero o el estatus no es
	 *             de entrada
	 */
	public static Vehiculo registrar(String vin, String modelo, String version, int anio, String color,
			BigDecimal precio, EstatusVehiculo estatus) {
		String vinLimpio = normalizarVin(vin);
		validarAnio(anio);
		EstatusVehiculo estatusDeEntrada = estatus == null ? EstatusVehiculo.DISPONIBLE : estatus;
		if (!ESTATUS_DE_ENTRADA.contains(estatusDeEntrada)) {
			throw new IllegalArgumentException("Una unidad solo puede entrar como Disponible o No disponible");
		}
		return new Vehiculo(vinLimpio, Texto.obligatorio(modelo, "El modelo es obligatorio"),
				Texto.obligatorio(version, "La versión es obligatoria"), anio,
				Texto.obligatorio(color, "El color es obligatorio"), Montos.mayorACero(precio), estatusDeEntrada);
	}

	/**
	 * Corrige los datos de la unidad (decisión del PO del 13 de octubre: se edita,
	 * no se elimina). Aplica las mismas reglas que el alta.
	 *
	 * <p>
	 * Inventario solo cambia el estatus entre Disponible y No disponible; Vendido y
	 * Entregado los pone la venta.
	 *
	 * @param estatus
	 *            nuevo estatus; {@code null} para conservar el actual
	 * @throws IllegalArgumentException
	 *             si un dato no es válido o el cambio de estatus no le toca a
	 *             Inventario
	 */
	public void editar(String vin, String modelo, String version, int anio, String color, BigDecimal precio,
			EstatusVehiculo estatus) {
		String vinLimpio = normalizarVin(vin);
		validarAnio(anio);
		String modeloLimpio = Texto.obligatorio(modelo, "El modelo es obligatorio");
		String versionLimpia = Texto.obligatorio(version, "La versión es obligatoria");
		String colorLimpio = Texto.obligatorio(color, "El color es obligatorio");
		BigDecimal precioValido = Montos.mayorACero(precio);
		EstatusVehiculo nuevoEstatus = estatus == null ? this.estatus : estatus;
		if (nuevoEstatus != this.estatus
				&& !(ESTATUS_DE_ENTRADA.contains(this.estatus) && ESTATUS_DE_ENTRADA.contains(nuevoEstatus))) {
			throw new IllegalArgumentException("Inventario solo cambia el estatus entre Disponible y No disponible");
		}
		this.vin = vinLimpio;
		this.modelo = modeloLimpio;
		this.version = versionLimpia;
		this.anio = anio;
		this.color = colorLimpio;
		this.precio = precioValido;
		this.estatus = nuevoEstatus;
	}

	/**
	 * Facturación eligió la unidad para una venta (ERP-1.0): pasa de Disponible a
	 * Vendido.
	 *
	 * @throws ConflictoException
	 *             si la unidad ya no está disponible (409)
	 */
	public void vender() {
		if (estatus != EstatusVehiculo.DISPONIBLE) {
			throw new ConflictoException("La unidad " + vin + " ya no está disponible");
		}
		estatus = EstatusVehiculo.VENDIDO;
	}

	/**
	 * Facturación cambió la unidad de una venta que todavía no se registra: la
	 * unidad regresa a Disponible.
	 *
	 * @throws IllegalStateException
	 *             si la unidad no estaba vendida
	 */
	public void liberar() {
		if (estatus != EstatusVehiculo.VENDIDO) {
			throw new IllegalStateException("Solo se libera una unidad vendida");
		}
		estatus = EstatusVehiculo.DISPONIBLE;
	}

	/**
	 * Quita espacios y pasa el VIN a mayúsculas, como se guarda.
	 *
	 * @throws IllegalArgumentException
	 *             si no tiene el formato del estándar
	 */
	public static String normalizarVin(String vin) {
		String vinLimpio = Texto.limpiar(vin);
		vinLimpio = vinLimpio == null ? null : vinLimpio.toUpperCase(Locale.ROOT);
		if (vinLimpio == null || !VIN_VALIDO.matcher(vinLimpio).matches()) {
			throw new IllegalArgumentException("El VIN debe tener 17 caracteres, sin las letras I, O ni Q");
		}
		return vinLimpio;
	}

	private static void validarAnio(int anio) {
		if (anio < ANIO_MINIMO) {
			throw new IllegalArgumentException("El año no es válido");
		}
	}

	public Long getId() {
		return id;
	}

	public String getVin() {
		return vin;
	}

	public String getModelo() {
		return modelo;
	}

	public String getVersion() {
		return version;
	}

	public int getAnio() {
		return anio;
	}

	public String getColor() {
		return color;
	}

	public BigDecimal getPrecio() {
		return precio;
	}

	public EstatusVehiculo getEstatus() {
		return estatus;
	}
}
