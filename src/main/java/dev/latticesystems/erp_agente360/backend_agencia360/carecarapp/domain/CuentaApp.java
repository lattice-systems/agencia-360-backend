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

/**
 * Cuenta de la app CareCar de un cliente (APP-2.1). La contraseña se guarda
 * solo como hash. Tras {@value #INTENTOS_PERMITIDOS} intentos fallidos seguidos
 * el login se bloquea {@link #DURACION_BLOQUEO}.
 */
@Entity
@Table(name = "cuenta_app")
public class CuentaApp extends Auditable {

	public static final int INTENTOS_PERMITIDOS = 5;
	public static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false, unique = true)
	private Long clienteId;

	@Column(name = "contrasena_hash", nullable = false, length = 100)
	private String contrasenaHash;

	@Column(name = "debe_cambiar_contrasena", nullable = false)
	private boolean debeCambiarContrasena;

	@Column(nullable = false)
	private boolean activa;

	@Column(name = "intentos_fallidos", nullable = false)
	private int intentosFallidos;

	@Column(name = "bloqueada_hasta")
	private Instant bloqueadaHasta;

	protected CuentaApp() {
		// Requerido por JPA
	}

	private CuentaApp(Long clienteId, String contrasenaHash) {
		this.clienteId = clienteId;
		this.contrasenaHash = contrasenaHash;
		this.debeCambiarContrasena = true;
		this.activa = true;
	}

	/**
	 * Abre la cuenta con una contraseña temporal: el cliente debe cambiarla en su
	 * primer ingreso (APP-2.3).
	 */
	public static CuentaApp abrir(Long clienteId, String hashContrasenaTemporal) {
		Objects.requireNonNull(clienteId, "clienteId");
		Objects.requireNonNull(hashContrasenaTemporal, "hashContrasenaTemporal");
		return new CuentaApp(clienteId, hashContrasenaTemporal);
	}

	public boolean estaBloqueada(Instant ahora) {
		return bloqueadaHasta != null && ahora.isBefore(bloqueadaHasta);
	}

	/** Segundos que faltan para poder intentar de nuevo; 0 si no está bloqueada. */
	public long segundosDeBloqueo(Instant ahora) {
		if (!estaBloqueada(ahora)) {
			return 0;
		}
		long segundos = Duration.between(ahora, bloqueadaHasta).toSeconds();
		return Math.max(segundos, 1);
	}

	/** Cuenta un intento fallido; al llegar al límite bloquea el login. */
	public void registrarIntentoFallido(Instant ahora) {
		intentosFallidos++;
		if (intentosFallidos >= INTENTOS_PERMITIDOS) {
			bloqueadaHasta = ahora.plus(DURACION_BLOQUEO);
			intentosFallidos = 0;
		}
	}

	public void registrarIngreso() {
		intentosFallidos = 0;
		bloqueadaHasta = null;
	}

	/** La contraseña nueva ya no es temporal; también quita el bloqueo. */
	public void cambiarContrasena(String nuevoHash) {
		this.contrasenaHash = Objects.requireNonNull(nuevoHash, "nuevoHash");
		this.debeCambiarContrasena = false;
		registrarIngreso();
	}

	public void desactivar() {
		this.activa = false;
	}

	public void activar() {
		this.activa = true;
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public String getContrasenaHash() {
		return contrasenaHash;
	}

	public boolean isDebeCambiarContrasena() {
		return debeCambiarContrasena;
	}

	public boolean isActiva() {
		return activa;
	}
}
