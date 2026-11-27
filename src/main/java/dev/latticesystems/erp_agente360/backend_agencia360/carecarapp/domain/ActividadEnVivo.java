package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Una Live Activity de la orden en la pantalla bloqueada del cliente
 * (V2-M25.05). {@code pushToken} es el que entrega ActivityKit al iniciar la
 * actividad; con él el servidor la actualiza o la termina sin que la app esté
 * abierta.
 */
@Entity
@Table(name = "actividad_en_vivo")
public class ActividadEnVivo extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false, updatable = false)
	private Long clienteId;

	@Column(name = "orden_servicio_id", nullable = false, updatable = false)
	private Long ordenServicioId;

	@Column(name = "push_token", nullable = false, length = 300, updatable = false)
	private String pushToken;

	@Column(nullable = false)
	private boolean activa = true;

	protected ActividadEnVivo() {
	}

	public ActividadEnVivo(Long clienteId, Long ordenServicioId, String pushToken) {
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		this.ordenServicioId = Objects.requireNonNull(ordenServicioId, "ordenServicioId");
		this.pushToken = Objects.requireNonNull(pushToken, "pushToken");
	}

	public void terminar() {
		this.activa = false;
	}

	/** Vuelve a recibir pushes, por ejemplo si la app reinicia la actividad. */
	public void reactivar() {
		this.activa = true;
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public Long getOrdenServicioId() {
		return ordenServicioId;
	}

	public String getPushToken() {
		return pushToken;
	}

	public boolean isActiva() {
		return activa;
	}
}
