package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Teléfono donde el cliente inició sesión, con su token de Firebase Cloud
 * Messaging (APP-6). Un token es de un solo teléfono y de un solo cliente a la
 * vez.
 */
@Entity
@Table(name = "dispositivo_app")
public class DispositivoApp extends Auditable {

	public static final int LARGO_MAXIMO_TOKEN = 4096;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Column(nullable = false, unique = true, length = LARGO_MAXIMO_TOKEN)
	private String token;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Plataforma plataforma;

	protected DispositivoApp() {
		// Requerido por JPA
	}

	public DispositivoApp(Long clienteId, String token, Plataforma plataforma) {
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		this.token = Objects.requireNonNull(token, "token");
		this.plataforma = Objects.requireNonNull(plataforma, "plataforma");
	}

	/** El teléfono ahora es de este cliente (otro inició sesión en él). */
	public void asignar(Long clienteId, Plataforma plataforma) {
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		this.plataforma = Objects.requireNonNull(plataforma, "plataforma");
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public String getToken() {
		return token;
	}

	public Plataforma getPlataforma() {
		return plataforma;
	}
}
