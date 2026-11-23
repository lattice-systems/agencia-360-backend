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
import java.util.Objects;

@Entity
@Table(name = "token_recuperacion")
public class TokenRecuperacion extends Auditable {

	public static final Duration VIGENCIA = Duration.ofMinutes(15);
	public static final int INTENTOS_PERMITIDOS = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cuenta_app_id", nullable = false)
	private Long cuentaAppId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "codigo_hash", nullable = false, length = 100)
	private String codigoHash;

	@Column(nullable = false)
	private Instant expira;

	@Column(nullable = false)
	private boolean usado;

	@Column(name = "usado_en")
	private Instant usadoEn;

	@Column(nullable = false)
	private int intentos;

	protected TokenRecuperacion() {
	}

	private TokenRecuperacion(Long cuentaAppId, String tokenHash, String codigoHash, Instant expira) {
		this.cuentaAppId = cuentaAppId;
		this.tokenHash = tokenHash;
		this.codigoHash = codigoHash;
		this.expira = expira;
	}

	public static TokenRecuperacion emitir(Long cuentaAppId, String tokenHash, String codigoHash, Instant ahora) {
		Objects.requireNonNull(cuentaAppId, "cuentaAppId");
		Objects.requireNonNull(tokenHash, "tokenHash");
		Objects.requireNonNull(codigoHash, "codigoHash");
		return new TokenRecuperacion(cuentaAppId, tokenHash, codigoHash, ahora.plus(VIGENCIA));
	}

	public boolean estaVigente(Instant ahora) {
		return !usado && intentos < INTENTOS_PERMITIDOS && ahora.isBefore(expira);
	}

	public void registrarIntentoFallido() {
		intentos++;
	}

	public void cerrar(Instant ahora) {
		if (!usado) {
			usado = true;
			usadoEn = ahora;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getCuentaAppId() {
		return cuentaAppId;
	}

	public String getCodigoHash() {
		return codigoHash;
	}

	public Instant getExpira() {
		return expira;
	}

	public boolean isUsado() {
		return usado;
	}
}
