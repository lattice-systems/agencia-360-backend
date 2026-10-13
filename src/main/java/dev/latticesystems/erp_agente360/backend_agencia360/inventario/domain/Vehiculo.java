package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unidad del inventario (ERP-2.0). Los vehículos se registran uno por uno y se
 * identifican por su VIN, que no se repite.
 *
 * <p>
 * No guarda precio: varía mucho mes con mes (decisión del PO del 13 de
 * octubre); el precio se fija en la venta.
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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstatusVehiculo estatus;

	protected Vehiculo() {
		// Requerido por JPA
	}

	private Vehiculo(String vin, String modelo, String version, int anio, String color, EstatusVehiculo estatus) {
		this.vin = vin;
		this.modelo = modelo;
		this.version = version;
		this.anio = anio;
		this.color = color;
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
	 *             es menor a 1900 o el estatus no es de entrada
	 */
	public static Vehiculo registrar(String vin, String modelo, String version, int anio, String color,
			EstatusVehiculo estatus) {
		String vinLimpio = Texto.limpiar(vin);
		vinLimpio = vinLimpio == null ? null : vinLimpio.toUpperCase(Locale.ROOT);
		if (vinLimpio == null || !VIN_VALIDO.matcher(vinLimpio).matches()) {
			throw new IllegalArgumentException("El VIN debe tener 17 caracteres, sin las letras I, O ni Q");
		}
		if (anio < ANIO_MINIMO) {
			throw new IllegalArgumentException("El año no es válido");
		}
		EstatusVehiculo estatusDeEntrada = estatus == null ? EstatusVehiculo.DISPONIBLE : estatus;
		if (!ESTATUS_DE_ENTRADA.contains(estatusDeEntrada)) {
			throw new IllegalArgumentException("Una unidad solo puede entrar como Disponible o No disponible");
		}
		return new Vehiculo(vinLimpio, Texto.obligatorio(modelo, "El modelo es obligatorio"),
				Texto.obligatorio(version, "La versión es obligatoria"), anio,
				Texto.obligatorio(color, "El color es obligatorio"), estatusDeEntrada);
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

	public EstatusVehiculo getEstatus() {
		return estatus;
	}
}
