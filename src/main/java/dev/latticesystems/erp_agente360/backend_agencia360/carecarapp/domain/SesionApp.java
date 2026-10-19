package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * Refresh token de la app. Solo se guarda su SHA-256 y se usa una vez: al
 * refrescar se revoca y se emite otro.
 */
@Entity
@Table(name = "sesion_app")
public class SesionApp extends Auditable {

	public static final Duration VIGENCIA = Duration.ofDays(30);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cuenta_id", nullable = false)
	private Long cuentaId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expira_en", nullable = false)
	private Instant expiraEn;

	@Column(name = "revocada_en")
	private Instant revocadaEn;

	protected SesionApp() {
		// Requerido por JPA
	}

	private SesionApp(Long cuentaId, String tokenHash, Instant expiraEn) {
		this.cuentaId = cuentaId;
		this.tokenHash = tokenHash;
		this.expiraEn = expiraEn;
	}

	public static SesionApp iniciar(Long cuentaId, String tokenHash, Instant ahora) {
		return new SesionApp(cuentaId, tokenHash, ahora.plus(VIGENCIA));
	}

	public boolean estaVigente(Instant ahora) {
		return revocadaEn == null && ahora.isBefore(expiraEn);
	}

	public boolean estaRevocada() {
		return revocadaEn != null;
	}

	public void revocar(Instant ahora) {
		if (revocadaEn == null) {
			revocadaEn = ahora;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getCuentaId() {
		return cuentaId;
	}
}
