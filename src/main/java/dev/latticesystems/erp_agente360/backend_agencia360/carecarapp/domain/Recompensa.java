package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Algo que el cliente puede canjear por puntos (V2-M12.03). Sin existencia es
 * ilimitada; con existencia, cada canje descuenta una y un canje vencido la
 * regresa.
 */
@Entity
@Table(name = "recompensa")
public class Recompensa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(length = 300)
	private String descripcion;

	@Column(nullable = false)
	private int puntos;

	@Column(nullable = false)
	private boolean activa;

	private Integer existencia;

	protected Recompensa() {
	}

	public Recompensa(String nombre, String descripcion, int puntos, Integer existencia) {
		if (nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("La recompensa necesita un nombre");
		}
		if (puntos <= 0) {
			throw new IllegalArgumentException("Una recompensa cuesta más de cero puntos");
		}
		if (existencia != null && existencia < 0) {
			throw new IllegalArgumentException("La existencia no puede ser negativa");
		}
		this.nombre = nombre.strip();
		this.descripcion = descripcion;
		this.puntos = puntos;
		this.existencia = existencia;
		this.activa = true;
	}

	public boolean agotada() {
		return existencia != null && existencia == 0;
	}

	/**
	 * @throws IllegalStateException
	 *             si ya no hay existencia
	 */
	public void descontar() {
		if (agotada()) {
			throw new IllegalStateException("La recompensa " + nombre + " está agotada");
		}
		if (existencia != null) {
			existencia--;
		}
	}

	/** Un canje que venció sin usarse regresa su unidad. */
	public void devolver() {
		if (existencia != null) {
			existencia++;
		}
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public int getPuntos() {
		return puntos;
	}

	public boolean isActiva() {
		return activa;
	}

	public Integer getExistencia() {
		return existencia;
	}
}
