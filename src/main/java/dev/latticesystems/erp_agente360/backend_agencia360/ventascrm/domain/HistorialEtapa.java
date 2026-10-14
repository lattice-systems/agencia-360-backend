package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Movimiento de un prospecto a una etapa, con su fecha y hora y quién lo hizo.
 * Solo lo crea {@link Prospecto}; no se modifica ni se borra.
 */
@Entity
@Table(name = "historial_etapa")
public class HistorialEtapa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "prospecto_id", nullable = false)
	private Prospecto prospecto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Etapa etapa;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 3)
	private OrigenCambio origen;

	/** Usuario que hizo el cambio; nulo si no se conoce. */
	@Column(name = "usuario_id")
	private Long usuarioId;

	@Column(name = "fecha_hora", nullable = false)
	private Instant fechaHora;

	protected HistorialEtapa() {
		// Requerido por JPA
	}

	HistorialEtapa(Prospecto prospecto, Etapa etapa, OrigenCambio origen, Long usuarioId, Instant fechaHora) {
		this.prospecto = prospecto;
		this.etapa = etapa;
		this.origen = origen;
		this.usuarioId = usuarioId;
		this.fechaHora = fechaHora;
	}

	public Long getId() {
		return id;
	}

	public Etapa getEtapa() {
		return etapa;
	}

	public OrigenCambio getOrigen() {
		return origen;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Instant getFechaHora() {
		return fechaHora;
	}
}
