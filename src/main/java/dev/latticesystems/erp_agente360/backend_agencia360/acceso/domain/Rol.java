package dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Rol del personal. El nombre coincide con {@link Role}, que es el valor que
 * viaja en el JWT y el que usan las anotaciones @PreAuthorize.
 */
@Entity
@Table(name = "rol")
public class Rol {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String nombre;

	@Column(nullable = false, length = 150)
	private String descripcion;

	@Column(name = "acceso_erp", nullable = false)
	private boolean accesoErp;

	@Column(name = "acceso_crm", nullable = false)
	private boolean accesoCrm;

	protected Rol() {
		// Requerido por JPA
	}

	public Rol(String nombre, String descripcion, boolean accesoErp, boolean accesoCrm) {
		this.nombre = nombre;
		this.descripcion = descripcion;
		this.accesoErp = accesoErp;
		this.accesoCrm = accesoCrm;
	}

	/** Indica si este rol puede entrar a la aplicación dada. */
	public boolean daAccesoA(Aplicacion aplicacion) {
		return aplicacion == Aplicacion.ERP ? accesoErp : accesoCrm;
	}

	/** El rol como valor del enum de seguridad. */
	public Role comoRole() {
		return Role.valueOf(nombre);
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

	public boolean isAccesoErp() {
		return accesoErp;
	}

	public boolean isAccesoCrm() {
		return accesoCrm;
	}
}
