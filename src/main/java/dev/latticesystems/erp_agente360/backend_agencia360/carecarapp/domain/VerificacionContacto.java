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
 * Confirma un correo o un teléfono antes de guardarlo (V2-M01.07).
 *
 * <p>
 * El dato nuevo vive aquí mientras se verifica, y no en {@code cliente}:
 * guardarlo antes de confirmarlo dejaría al cliente con un correo que no ha
 * probado que es suyo y, como {@code cuenta_app.usuario} sigue al correo,
 * <b>sin poder entrar</b>.
 *
 * <p>
 * Del código solo se guarda su BCrypt, igual que en
 * {@code codigo_recuperacion}.
 */
@Entity
@Table(name = "verificacion_contacto")
public class VerificacionContacto extends Auditable {

	public static final Duration VIGENCIA = Duration.ofMinutes(15);
	public static final int INTENTOS_PERMITIDOS = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 10)
	private TipoDeContacto tipo;

	@Column(name = "valor_nuevo", nullable = false, length = 120)
	private String valorNuevo;

	@Column(name = "codigo_hash", nullable = false, length = 100)
	private String codigoHash;

	@Column(name = "expira", nullable = false)
	private Instant expira;

	@Column(name = "usado", nullable = false)
	private boolean usado;

	@Column(name = "usado_en")
	private Instant usadoEn;

	@Column(name = "intentos", nullable = false)
	private int intentos;

	protected VerificacionContacto() {
		// Requerido por JPA
	}

	private VerificacionContacto(Long clienteId, TipoDeContacto tipo, String valorNuevo, String codigoHash,
			Instant expira) {
		this.clienteId = clienteId;
		this.tipo = tipo;
		this.valorNuevo = valorNuevo;
		this.codigoHash = codigoHash;
		this.expira = expira;
	}

	public static VerificacionContacto emitir(Long clienteId, TipoDeContacto tipo, String valorNuevo, String codigoHash,
			Instant ahora) {
		return new VerificacionContacto(clienteId, tipo, valorNuevo, codigoHash, ahora.plus(VIGENCIA));
	}

	/**
	 * Si todavía sirve: ni usada, ni vencida, ni con los intentos agotados.
	 *
	 * <p>
	 * Los tres casos se responden igual hacia afuera, como al restablecer la
	 * contraseña: responder distinto permitiría averiguar qué códigos existen.
	 */
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

	public Long getClienteId() {
		return clienteId;
	}

	public TipoDeContacto getTipo() {
		return tipo;
	}

	public String getValorNuevo() {
		return valorNuevo;
	}

	public String getCodigoHash() {
		return codigoHash;
	}

	public Instant getExpira() {
		return expira;
	}
}
