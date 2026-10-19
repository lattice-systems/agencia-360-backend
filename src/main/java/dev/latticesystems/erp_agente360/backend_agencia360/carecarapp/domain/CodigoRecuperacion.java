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
 * Código de 6 dígitos para restablecer la contraseña (APP-2.3). Vence a los
 * {@link #VIGENCIA}, sirve una vez y admite {@value #INTENTOS_PERMITIDOS}
 * intentos. Solo se guarda su hash.
 */
@Entity
@Table(name = "codigo_recuperacion")
public class CodigoRecuperacion extends Auditable {

	public static final Duration VIGENCIA = Duration.ofMinutes(15);
	public static final int INTENTOS_PERMITIDOS = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cuenta_id", nullable = false)
	private Long cuentaId;

	@Column(name = "codigo_hash", nullable = false, length = 100)
	private String codigoHash;

	@Column(name = "expira_en", nullable = false)
	private Instant expiraEn;

	@Column(name = "usado_en")
	private Instant usadoEn;

	@Column(nullable = false)
	private int intentos;

	protected CodigoRecuperacion() {
		// Requerido por JPA
	}

	private CodigoRecuperacion(Long cuentaId, String codigoHash, Instant expiraEn) {
		this.cuentaId = cuentaId;
		this.codigoHash = codigoHash;
		this.expiraEn = expiraEn;
	}

	public static CodigoRecuperacion emitir(Long cuentaId, String codigoHash, Instant ahora) {
		return new CodigoRecuperacion(cuentaId, codigoHash, ahora.plus(VIGENCIA));
	}

	public boolean estaVigente(Instant ahora) {
		return usadoEn == null && intentos < INTENTOS_PERMITIDOS && ahora.isBefore(expiraEn);
	}

	public void registrarIntentoFallido() {
		intentos++;
	}

	/** Lo deja sin uso posible: se usó o se pidió uno nuevo. */
	public void cerrar(Instant ahora) {
		if (usadoEn == null) {
			usadoEn = ahora;
		}
	}

	public Long getId() {
		return id;
	}

	public String getCodigoHash() {
		return codigoHash;
	}
}
