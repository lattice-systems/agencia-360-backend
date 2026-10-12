package dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain;

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

/**
 * Usuario del personal de la agencia. Tiene un solo rol. La contraseña se
 * guarda únicamente como hash.
 */
@Entity
@Table(name = "usuario")
public class Usuario extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String usuario;

	@Column(name = "contrasena_hash", nullable = false, length = 100)
	private String contrasenaHash;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(nullable = false)
	private boolean activo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rol_id", nullable = false)
	private Rol rol;

	protected Usuario() {
		// Requerido por JPA
	}

	public Usuario(String usuario, String contrasenaHash, String nombre, Rol rol) {
		this.usuario = usuario;
		this.contrasenaHash = contrasenaHash;
		this.nombre = nombre;
		this.rol = rol;
		this.activo = true;
	}

	public void cambiarRol(Rol nuevoRol) {
		this.rol = nuevoRol;
	}

	public Long getId() {
		return id;
	}

	public String getUsuario() {
		return usuario;
	}

	public String getContrasenaHash() {
		return contrasenaHash;
	}

	public String getNombre() {
		return nombre;
	}

	public boolean isActivo() {
		return activo;
	}

	public Rol getRol() {
		return rol;
	}
}
