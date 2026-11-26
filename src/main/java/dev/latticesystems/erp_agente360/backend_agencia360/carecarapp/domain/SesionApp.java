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

	/**
	 * Por qué se revocó. Nulo mientras está viva, y también en las filas revocadas
	 * antes de que existiera la columna.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_revocacion", length = 20)
	private MotivoRevocacion motivoRevocacion;

	/**
	 * Nombre que mandó la app al entrar, para que el cliente reconozca sus
	 * sesiones. Nulo en las abiertas antes de V2-M01.09 y en las de un cliente que
	 * no lo manda.
	 */
	@Column(name = "dispositivo", length = 80)
	private String dispositivo;

	/** Cuándo se refrescó por última vez. Nulo si nunca se ha refrescado. */
	@Column(name = "ultimo_uso")
	private Instant ultimoUso;

	/**
	 * Cuándo empezó la sesión de verdad. No basta {@code created_at}: el refresco
	 * revoca la fila y crea otra, así que ese sería el momento del último refresco
	 * y una sesión de 20 días se vería iniciada hace un rato.
	 */
	@Column(name = "iniciada_en")
	private Instant iniciadaEn;

	protected SesionApp() {
		// Requerido por JPA
	}

	private SesionApp(Long cuentaId, String tokenHash, Instant expiraEn) {
		this.cuentaId = cuentaId;
		this.tokenHash = tokenHash;
		this.expiraEn = expiraEn;
	}

	/** Primera sesión del dispositivo: la del login. */
	public static SesionApp iniciar(Long cuentaId, String tokenHash, Instant ahora, String dispositivo) {
		SesionApp sesion = new SesionApp(cuentaId, tokenHash, ahora.plus(VIGENCIA));
		sesion.dispositivo = dispositivo;
		sesion.iniciadaEn = ahora;
		return sesion;
	}

	/**
	 * La sesión que sustituye a {@code previa} al refrescar.
	 *
	 * <p>
	 * <strong>Arrastra el nombre del dispositivo y el inicio real.</strong> El
	 * refresh token es de un solo uso, así que refrescar revoca la fila y guarda
	 * otra; si no se copiaran, el nombre se perdería en el primer refresco -a la
	 * hora de haber entrado- y la lista de «mis sesiones» quedaría inservible.
	 */
	public static SesionApp continuar(SesionApp previa, String tokenHash, Instant ahora) {
		SesionApp sesion = new SesionApp(previa.cuentaId, tokenHash, ahora.plus(VIGENCIA));
		sesion.dispositivo = previa.dispositivo;
		sesion.iniciadaEn = previa.iniciadaEn;
		sesion.ultimoUso = ahora;
		return sesion;
	}

	public boolean estaVigente(Instant ahora) {
		return revocadaEn == null && ahora.isBefore(expiraEn);
	}

	public boolean estaRevocada() {
		return revocadaEn != null;
	}

	/**
	 * Revoca la sesión diciendo por qué. El motivo no es adorno: decide si reusar
	 * este token se toma como posible robo (ver {@link MotivoRevocacion}).
	 *
	 * <p>
	 * No hace nada si ya estaba revocada, así que el primer motivo es el que queda:
	 * el que de verdad la cerró.
	 */
	public void revocar(Instant ahora, MotivoRevocacion motivo) {
		if (revocadaEn == null) {
			revocadaEn = ahora;
			motivoRevocacion = motivo;
		}
	}

	/**
	 * Si reusar este token tiene que tomarse como posible robo.
	 *
	 * <p>
	 * El nulo cuenta como que sí: son las filas revocadas antes de que existiera la
	 * columna, casi todas consumidas por un refresco, y ante la duda conviene
	 * equivocarse del lado de cerrar sesiones y no del de dejar viva una copia
	 * robada.
	 */
	public boolean reusarlaDelataRobo() {
		return motivoRevocacion == null || motivoRevocacion == MotivoRevocacion.REFRESCO;
	}

	public Long getId() {
		return id;
	}

	public Long getCuentaId() {
		return cuentaId;
	}

	public String getDispositivo() {
		return dispositivo;
	}

	public Instant getUltimoUso() {
		return ultimoUso;
	}

	public Instant getIniciadaEn() {
		return iniciadaEn;
	}

	public Instant getExpiraEn() {
		return expiraEn;
	}
}
