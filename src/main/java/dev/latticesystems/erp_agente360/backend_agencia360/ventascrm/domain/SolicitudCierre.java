package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
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
import java.util.Objects;

/**
 * Solicitud para cerrar un prospecto que ya está en Pago, Facturación o Entrega
 * (decisión del PO del 12 de octubre). La envía el vendedor con su motivo y la
 * aprueba o rechaza el Administrador del ERP. Solo puede haber una pendiente
 * por prospecto. La fecha de la solicitud es {@code created_at}.
 */
@Entity
@Table(name = "solicitud_cierre")
public class SolicitudCierre extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "prospecto_id", nullable = false)
	private Prospecto prospecto;

	@Column(nullable = false, length = 200)
	private String motivo;

	@Column(name = "solicitante_id", nullable = false)
	private Long solicitanteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoSolicitud estado;

	@Column(name = "resuelta_por_id")
	private Long resueltaPorId;

	@Column(name = "resuelta_en")
	private Instant resueltaEn;

	protected SolicitudCierre() {
		// Requerido por JPA
	}

	private SolicitudCierre(Prospecto prospecto, String motivo, Long solicitanteId) {
		this.prospecto = prospecto;
		this.motivo = motivo;
		this.solicitanteId = solicitanteId;
		this.estado = EstadoSolicitud.PENDIENTE;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta el motivo o pasa de 200 caracteres
	 * @throws ConflictoException
	 *             si el prospecto no está en Pago, Facturación o Entrega, o ya
	 *             tiene una solicitud pendiente
	 */
	public static SolicitudCierre solicitar(Prospecto prospecto, String motivo, Long solicitanteId) {
		Objects.requireNonNull(solicitanteId, "solicitanteId");
		String motivoLimpio = Prospecto.validarMotivo(motivo);
		prospecto.marcarCierreSolicitado();
		return new SolicitudCierre(prospecto, motivoLimpio, solicitanteId);
	}

	/**
	 * El Administrador acepta: el prospecto pasa a Cerrado con el motivo de la
	 * solicitud.
	 */
	public void aprobar(Long administradorId, Instant ahora) {
		resolver(EstadoSolicitud.APROBADA, administradorId, ahora);
		prospecto.cerrarPorAutorizacion(motivo, administradorId, ahora);
	}

	/** El Administrador rechaza: el prospecto sigue en su etapa. */
	public void rechazar(Long administradorId, Instant ahora) {
		resolver(EstadoSolicitud.RECHAZADA, administradorId, ahora);
		prospecto.descartarCierreSolicitado();
	}

	private void resolver(EstadoSolicitud nuevo, Long administradorId, Instant ahora) {
		if (estado != EstadoSolicitud.PENDIENTE) {
			throw new ConflictoException("La solicitud ya fue resuelta");
		}
		estado = nuevo;
		resueltaPorId = Objects.requireNonNull(administradorId, "administradorId");
		resueltaEn = Objects.requireNonNull(ahora, "ahora");
	}

	public Long getId() {
		return id;
	}

	public Prospecto getProspecto() {
		return prospecto;
	}

	public String getMotivo() {
		return motivo;
	}

	public Long getSolicitanteId() {
		return solicitanteId;
	}

	public EstadoSolicitud getEstado() {
		return estado;
	}

	public Long getResueltaPorId() {
		return resueltaPorId;
	}

	public Instant getResueltaEn() {
		return resueltaEn;
	}
}
