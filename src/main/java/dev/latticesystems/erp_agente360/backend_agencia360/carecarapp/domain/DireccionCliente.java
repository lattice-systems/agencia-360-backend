package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Dirección guardada del cliente para pedir recolección (V2-M17.01). El pin del
 * mapa ({@code lat}, {@code lng}) es obligatorio: con él se revisa la zona de
 * cobertura. {@code clienteId} solo se mapea como id para no cruzar módulos.
 */
@Entity
@Table(name = "direccion_cliente")
public class DireccionCliente extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false, updatable = false)
	private Long clienteId;

	@Column(nullable = false, length = 40)
	private String alias;

	@Column(nullable = false, length = 120)
	private String calle;

	@Column(nullable = false, length = 80)
	private String colonia;

	@Column(nullable = false, length = 5)
	private String cp;

	@Column(nullable = false, length = 80)
	private String ciudad;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal lat;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal lng;

	protected DireccionCliente() {
		// Requerido por JPA
	}

	private DireccionCliente(Long clienteId) {
		this.clienteId = clienteId;
	}

	/**
	 * Una dirección del cliente.
	 *
	 * @throws IllegalArgumentException
	 *             si falta un dato, se pasa de largo, el CP no tiene 5 dígitos o el
	 *             pin está fuera de rango (400)
	 */
	public static DireccionCliente crear(Long clienteId, String alias, String calle, String colonia, String cp,
			String ciudad, BigDecimal lat, BigDecimal lng) {
		Objects.requireNonNull(clienteId, "clienteId");
		DireccionCliente direccion = new DireccionCliente(clienteId);
		direccion.corregir(alias, calle, colonia, cp, ciudad, lat, lng);
		return direccion;
	}

	/** Reemplaza los datos de la dirección con las mismas reglas del alta. */
	public void corregir(String alias, String calle, String colonia, String cp, String ciudad, BigDecimal lat,
			BigDecimal lng) {
		this.alias = texto(alias, "El alias", 40);
		this.calle = texto(calle, "La calle", 120);
		this.colonia = texto(colonia, "La colonia", 80);
		this.cp = texto(cp, "El código postal", 5);
		this.ciudad = texto(ciudad, "La ciudad", 80);
		if (!this.cp.matches("[0-9]{5}")) {
			throw new IllegalArgumentException("El código postal debe tener 5 dígitos");
		}
		if (lat == null || lng == null || lat.abs().compareTo(BigDecimal.valueOf(90)) > 0
				|| lng.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
			throw new IllegalArgumentException("El pin del mapa es obligatorio y debe estar en rango");
		}
		this.lat = lat;
		this.lng = lng;
	}

	private static String texto(String valor, String nombre, int maximo) {
		if (valor == null || valor.isBlank()) {
			throw new IllegalArgumentException(nombre + " es obligatorio");
		}
		String limpio = valor.strip();
		if (limpio.length() > maximo) {
			throw new IllegalArgumentException(nombre + " pasa de " + maximo + " caracteres");
		}
		return limpio;
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public String getAlias() {
		return alias;
	}

	public String getCalle() {
		return calle;
	}

	public String getColonia() {
		return colonia;
	}

	public String getCp() {
		return cp;
	}

	public String getCiudad() {
		return ciudad;
	}

	public BigDecimal getLat() {
		return lat;
	}

	public BigDecimal getLng() {
		return lng;
	}
}
