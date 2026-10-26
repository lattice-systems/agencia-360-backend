package dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;

/**
 * Vehículo de un cliente. Copia el VIN, la marca, el modelo, el año y la
 * versión de la unidad para que el expediente no dependa del inventario; las
 * placas y el kilometraje los corrige después Servicio (ERP-3.0).
 *
 * <p>
 * Como en el inventario y la factura, {@code modelo} se muestra como Tipo
 * (Sentra) y {@code anio} como Modelo (2026).
 */
@Entity
@Table(name = "vehiculo_cliente")
public class VehiculoCliente extends Auditable {

	private static final int LARGO_VIN = 17;
	private static final int LARGO_MAXIMO_MODELO = 80;
	private static final int ANIO_MINIMO = 1900;
	private static final int LARGO_MAXIMO_MARCA = 40;
	private static final int LARGO_MAXIMO_VERSION = 80;
	private static final int LARGO_MAXIMO_PLACAS = 15;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@Column(name = "vehiculo_id", unique = true)
	private Long vehiculoId;

	@Column(nullable = false, unique = true, length = LARGO_VIN)
	private String vin;

	@Column(length = LARGO_MAXIMO_MARCA)
	private String marca;

	@Column(nullable = false, length = LARGO_MAXIMO_MODELO)
	private String modelo;

	@Column(length = LARGO_MAXIMO_VERSION)
	private String version;

	@Column(nullable = false)
	private int anio;

	@Column(length = LARGO_MAXIMO_PLACAS)
	private String placas;

	private Integer kilometraje;

	protected VehiculoCliente() {
		// Requerido por JPA
	}

	private VehiculoCliente(Cliente cliente, Long vehiculoId, String vin, String marca, String modelo, int anio,
			String version) {
		this.cliente = cliente;
		this.vehiculoId = vehiculoId;
		this.vin = vin;
		this.marca = marca;
		this.modelo = modelo;
		this.anio = anio;
		this.version = version;
	}

	/**
	 * @param marca
	 *            puede faltar
	 * @param version
	 *            puede faltar
	 * @throws IllegalArgumentException
	 *             si un dato no es válido (400)
	 */
	static VehiculoCliente de(Cliente cliente, Long vehiculoId, String vin, String marca, String modelo, int anio,
			String version) {
		String vinLimpio = vin == null ? "" : vin.strip().toUpperCase(Locale.ROOT);
		if (vinLimpio.length() != LARGO_VIN) {
			throw new IllegalArgumentException("El VIN debe tener " + LARGO_VIN + " caracteres");
		}
		String modeloLimpio = modelo == null ? "" : modelo.strip();
		if (modeloLimpio.isEmpty() || modeloLimpio.length() > LARGO_MAXIMO_MODELO) {
			throw new IllegalArgumentException(
					"El modelo es obligatorio y admite hasta " + LARGO_MAXIMO_MODELO + " caracteres");
		}
		if (anio < ANIO_MINIMO) {
			throw new IllegalArgumentException("El año debe ser " + ANIO_MINIMO + " o posterior");
		}
		return new VehiculoCliente(cliente, vehiculoId, vinLimpio,
				opcional(marca, LARGO_MAXIMO_MARCA, "La marca admite"), modeloLimpio, anio,
				opcional(version, LARGO_MAXIMO_VERSION, "La versión admite"));
	}

	/**
	 * Servicio corrige las placas y el kilometraje (ERP-3.0). Un valor vacío los
	 * deja sin capturar.
	 *
	 * @throws IllegalArgumentException
	 *             si las placas pasan de 15 caracteres o el kilometraje es negativo
	 *             (400)
	 */
	public void corregir(String placas, Integer kilometraje) {
		if (kilometraje != null && kilometraje < 0) {
			throw new IllegalArgumentException("El kilometraje no puede ser negativo");
		}
		String placasLimpias = opcional(placas, LARGO_MAXIMO_PLACAS, "Las placas admiten");
		this.placas = placasLimpias == null ? null : placasLimpias.toUpperCase(Locale.ROOT);
		this.kilometraje = kilometraje;
	}

	private static String opcional(String valor, int largoMaximo, String mensaje) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.strip();
		if (limpio.length() > largoMaximo) {
			throw new IllegalArgumentException(mensaje + " hasta " + largoMaximo + " caracteres");
		}
		return limpio;
	}

	public Long getId() {
		return id;
	}

	public Long getVehiculoId() {
		return vehiculoId;
	}

	public String getVin() {
		return vin;
	}

	public Cliente getCliente() {
		return cliente;
	}

	public String getMarca() {
		return marca;
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

	public String getPlacas() {
		return placas;
	}

	public Integer getKilometraje() {
		return kilometraje;
	}
}
