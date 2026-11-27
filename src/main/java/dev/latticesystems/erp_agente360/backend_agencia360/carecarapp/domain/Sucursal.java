package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Sucursal de la agencia (V2-M27.02, ficha sección 23): dónde está, cómo
 * llamarle y cuándo atiende cada departamento. Se retira, no se borra.
 */
@Entity
@Table(name = "sucursal")
public class Sucursal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(nullable = false, length = 200)
	private String direccion;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal lat;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal lng;

	@Column(length = 30)
	private String telefono;

	@Column(length = 120)
	private String correo;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<HorarioSucursal> horarios = new ArrayList<>();

	@Column(nullable = false)
	private boolean activa = true;

	@Column(nullable = false)
	private int orden;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt = OffsetDateTime.now();

	protected Sucursal() {
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta el nombre o la dirección, o la coordenada no cabe en el
	 *             planeta
	 */
	public Sucursal(String nombre, String direccion, BigDecimal lat, BigDecimal lng, String telefono, String correo,
			List<HorarioSucursal> horarios, int orden) {
		if (nombre == null || nombre.isBlank() || direccion == null || direccion.isBlank()) {
			throw new IllegalArgumentException("La sucursal necesita nombre y dirección");
		}
		if (lat == null || lng == null || lat.abs().compareTo(BigDecimal.valueOf(90)) > 0
				|| lng.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
			throw new IllegalArgumentException("La coordenada no es válida");
		}
		this.nombre = nombre.strip();
		this.direccion = direccion.strip();
		this.lat = lat;
		this.lng = lng;
		this.telefono = telefono;
		this.correo = correo;
		this.horarios = new ArrayList<>(horarios);
		this.orden = orden;
	}

	/** Deja de mostrarse en la app; la fila se conserva. */
	public void retirar() {
		this.activa = false;
		this.updatedAt = OffsetDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDireccion() {
		return direccion;
	}

	public BigDecimal getLat() {
		return lat;
	}

	public BigDecimal getLng() {
		return lng;
	}

	public String getTelefono() {
		return telefono;
	}

	public String getCorreo() {
		return correo;
	}

	public List<HorarioSucursal> getHorarios() {
		return List.copyOf(horarios);
	}

	public boolean isActiva() {
		return activa;
	}

	public int getOrden() {
		return orden;
	}
}
