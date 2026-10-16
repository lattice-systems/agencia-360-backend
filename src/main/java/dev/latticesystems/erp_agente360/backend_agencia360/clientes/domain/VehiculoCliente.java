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
 * Vehículo de un cliente. Copia el VIN, el modelo y el año de la unidad para
 * que el expediente no dependa del inventario; las placas y el kilometraje los
 * captura después Servicio (ERP-3.0).
 */
@Entity
@Table(name = "vehiculo_cliente")
public class VehiculoCliente extends Auditable {

	private static final int LARGO_VIN = 17;
	private static final int LARGO_MAXIMO_MODELO = 80;
	private static final int ANIO_MINIMO = 1900;

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

	@Column(nullable = false, length = LARGO_MAXIMO_MODELO)
	private String modelo;

	@Column(nullable = false)
	private int anio;

	@Column(length = 15)
	private String placas;

	private Integer kilometraje;

	protected VehiculoCliente() {
		// Requerido por JPA
	}

	private VehiculoCliente(Cliente cliente, Long vehiculoId, String vin, String modelo, int anio) {
		this.cliente = cliente;
		this.vehiculoId = vehiculoId;
		this.vin = vin;
		this.modelo = modelo;
		this.anio = anio;
	}

	static VehiculoCliente de(Cliente cliente, Long vehiculoId, String vin, String modelo, int anio) {
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
		return new VehiculoCliente(cliente, vehiculoId, vinLimpio, modeloLimpio, anio);
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

	public String getModelo() {
		return modelo;
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
