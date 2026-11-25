package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudesArco.Solicitud;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.DemasiadosIntentosException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ProhibidoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancelación de la cuenta por su titular, derecho de cancelación ARCO
 * (V2-M22.03). No se puede deshacer: pide la contraseña, desactiva la cuenta,
 * borra o anonimiza los datos personales que ninguna ley obliga a conservar y
 * deja constancia en {@code solicitud_arco}. Todo en una transacción: si algo
 * falla, no cambia nada.
 */
@Service
public class CancelacionService {

	static final String CANCELACION = "CANCELACION";
	static final String RESUELTA = "RESUELTA";

	private static final String CUENTA_DESACTIVADA = "Tu cuenta de CareCar está desactivada.";

	private final CuentaAppRepository cuentas;
	private final PasswordEncoder encoder;
	private final SolicitudesArco solicitudes;
	private final AnonimizadorDeCuenta anonimizador;
	private final DirectorioClientes directorio;
	private final Clock reloj;

	@Autowired
	public CancelacionService(CuentaAppRepository cuentas, PasswordEncoder encoder, SolicitudesArco solicitudes,
			AnonimizadorDeCuenta anonimizador, DirectorioClientes directorio) {
		this(cuentas, encoder, solicitudes, anonimizador, directorio, Clock.systemUTC());
	}

	public CancelacionService(CuentaAppRepository cuentas, PasswordEncoder encoder, SolicitudesArco solicitudes,
			AnonimizadorDeCuenta anonimizador, DirectorioClientes directorio, Clock reloj) {
		this.cuentas = cuentas;
		this.encoder = encoder;
		this.solicitudes = solicitudes;
		this.anonimizador = anonimizador;
		this.directorio = directorio;
		this.reloj = reloj;
	}

	/**
	 * Cancela la cuenta. La contraseña equivocada cuenta como intento fallido (el
	 * mismo contador del login) y se confirma aunque la petición termine con error;
	 * todo lo demás revierte si falla.
	 *
	 * @throws SolicitudInvalidaException
	 *             contraseña ausente o incorrecta (400); nunca 401, porque la app
	 *             lo leería como sesión vencida
	 * @throws DemasiadosIntentosException
	 *             cuenta bloqueada por intentos fallidos (429)
	 * @throws ProhibidoException
	 *             sin cuenta o ya desactivada (403)
	 */
	@Transactional(noRollbackFor = SolicitudInvalidaException.class)
	public ArcoService.SolicitudArcoApp cancelar(long clienteId, String contrasena) {
		Instant ahora = Instant.now(reloj);
		CuentaApp cuenta = cuentas.findByClienteId(clienteId).filter(CuentaApp::isActiva)
				.orElseThrow(() -> new ProhibidoException(CUENTA_DESACTIVADA, CodigosError.CUENTA_DESACTIVADA));
		if (contrasena == null || contrasena.isBlank()) {
			throw new SolicitudInvalidaException("Escribe tu contraseña para confirmar", CodigosError.DATOS_INVALIDOS);
		}
		if (cuenta.estaBloqueada(ahora)) {
			throw new DemasiadosIntentosException("Demasiados intentos. Intenta de nuevo más tarde.",
					cuenta.segundosDeBloqueo(ahora));
		}
		if (!encoder.matches(contrasena, cuenta.getContrasenaHash())) {
			cuenta.registrarIntentoFallido(ahora);
			throw new SolicitudInvalidaException("La contraseña no es correcta", CodigosError.DATOS_INVALIDOS);
		}

		Solicitud solicitud = solicitudes.crear(clienteId, CANCELACION, ahora)
				.orElseThrow(() -> new IllegalStateException("Ya hay una cancelación pendiente"));
		solicitudes.resolver(solicitud.id(), null, ahora);
		anonimizador.anonimizar(clienteId);
		directorio.anonimizar(clienteId);
		cuenta.cancelar(encoder.encode(UUID.randomUUID().toString()));

		return new ArcoService.SolicitudArcoApp(solicitud.id(), CANCELACION, RESUELTA, ahora, ahora, null);
	}
}
