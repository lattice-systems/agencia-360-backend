package dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cliente de la agencia (CRM-1.5), separado de los usuarios internos. Su correo
 * no se repite y es con el que entra a la app; puede no tenerlo si rechazó la
 * app.
 */
@Entity
@Table(name = "cliente")
public class Cliente extends Auditable {

	private static final Pattern CORREO_VALIDO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
	private static final int LARGO_MAXIMO_NOMBRE = 120;
	private static final int LARGO_MAXIMO_CORREO = 120;
	private static final int LARGO_MAXIMO_TELEFONO = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = LARGO_MAXIMO_NOMBRE)
	private String nombre;

	@Column(length = LARGO_MAXIMO_CORREO)
	private String correo;

	@Column(length = LARGO_MAXIMO_TELEFONO)
	private String telefono;

	@Column(name = "prospecto_id", unique = true)
	private Long prospectoId;

	@OneToMany(mappedBy = "cliente", cascade = CascadeType.PERSIST)
	@OrderBy("createdAt asc, id asc")
	private List<VehiculoCliente> vehiculos = new ArrayList<>();

	protected Cliente() {
		// Requerido por JPA
	}

	private Cliente(String nombre, String correo, String telefono, Long prospectoId) {
		this.nombre = nombre;
		this.correo = correo;
		this.telefono = telefono;
		this.prospectoId = prospectoId;
	}

	/**
	 * Registra un cliente. Quita espacios sobrantes y guarda el correo en
	 * minúsculas para poder comparar repetidos.
	 *
	 * @param prospectoId
	 *            prospecto del que viene, o {@code null} si se da de alta a mano
	 * @throws IllegalArgumentException
	 *             si falta el nombre o un dato no es válido (400)
	 */
	public static Cliente registrar(String nombre, String correo, String telefono, Long prospectoId) {
		String nombreLimpio = limpiar(nombre);
		if (nombreLimpio == null) {
			throw new IllegalArgumentException("El nombre es obligatorio");
		}
		if (nombreLimpio.length() > LARGO_MAXIMO_NOMBRE) {
			throw new IllegalArgumentException("El nombre admite hasta " + LARGO_MAXIMO_NOMBRE + " caracteres");
		}
		String telefonoLimpio = limpiar(telefono);
		if (telefonoLimpio != null && telefonoLimpio.length() > LARGO_MAXIMO_TELEFONO) {
			throw new IllegalArgumentException("El teléfono admite hasta " + LARGO_MAXIMO_TELEFONO + " caracteres");
		}
		return new Cliente(nombreLimpio, normalizarCorreo(correo), telefonoLimpio, prospectoId);
	}

	/**
	 * Deja el correo como se guarda: sin espacios y en minúsculas.
	 *
	 * @return {@code null} si viene vacío
	 * @throws IllegalArgumentException
	 *             si no tiene formato de correo (400)
	 */
	public static String normalizarCorreo(String correo) {
		String limpio = limpiar(correo);
		if (limpio == null) {
			return null;
		}
		String normalizado = limpio.toLowerCase(Locale.ROOT);
		if (normalizado.length() > LARGO_MAXIMO_CORREO || !CORREO_VALIDO.matcher(normalizado).matches()) {
			throw new IllegalArgumentException("El correo no es válido");
		}
		return normalizado;
	}

	/**
	 * Le asigna un vehículo. Si ya tiene uno con ese VIN no hace nada, para que un
	 * evento repetido no lo duplique.
	 *
	 * @param vehiculoId
	 *            unidad del inventario, o {@code null} si no la vendió la agencia
	 * @return {@code true} si lo agregó
	 */
	public boolean asignarVehiculo(Long vehiculoId, String vin, String modelo, int anio) {
		return asignarVehiculo(vehiculoId, vin, null, modelo, anio, null);
	}

	/**
	 * Le asigna un vehículo con su marca y versión (ERP-3.0). Si ya tiene uno con
	 * ese VIN no hace nada.
	 *
	 * @param vehiculoId
	 *            unidad del inventario, o {@code null} si no la vendió la agencia
	 * @param marca
	 *            puede faltar
	 * @param version
	 *            puede faltar
	 * @return {@code true} si lo agregó
	 */
	public boolean asignarVehiculo(Long vehiculoId, String vin, String marca, String modelo, int anio, String version) {
		VehiculoCliente nuevo = VehiculoCliente.de(this, vehiculoId, vin, marca, modelo, anio, version);
		if (vehiculos.stream().anyMatch(v -> v.getVin().equals(nuevo.getVin()))) {
			return false;
		}
		vehiculos.add(nuevo);
		return true;
	}

	private static String limpiar(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		return valor.strip();
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getCorreo() {
		return correo;
	}

	public String getTelefono() {
		return telefono;
	}

	public Long getProspectoId() {
		return prospectoId;
	}

	public List<VehiculoCliente> getVehiculos() {
		return Collections.unmodifiableList(vehiculos);
	}
}
