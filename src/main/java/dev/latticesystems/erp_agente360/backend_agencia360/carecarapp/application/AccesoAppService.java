package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ReglasContrasena;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.SesionApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.SesionAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes.ClienteRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.DemasiadosIntentosException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoAutenticadoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ProhibidoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sesión del cliente en la app CareCar (APP-2.1 y APP-2.3): login con correo,
 * refresh token de un solo uso, cierre de sesión, cambio de contraseña y
 * perfil. El token de la app lleva el id del cliente y el rol
 * {@code CLIENTE_CARECAR}.
 */
@Service
public class AccesoAppService {

	static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";
	static final String CUENTA_DESACTIVADA = "Tu cuenta de CareCar está desactivada. Comunícate con la agencia.";
	static final String SESION_TERMINADA = "Tu sesión terminó. Inicia sesión de nuevo.";

	private final DirectorioClientes directorioClientes;
	private final CuentaAppRepository cuentaRepository;
	private final SesionAppRepository sesionRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final Clock reloj;

	@Autowired
	public AccesoAppService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			SesionAppRepository sesionRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this(directorioClientes, cuentaRepository, sesionRepository, passwordEncoder, jwtService, Clock.systemUTC());
	}

	AccesoAppService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			SesionAppRepository sesionRepository, PasswordEncoder passwordEncoder, JwtService jwtService, Clock reloj) {
		this.directorioClientes = directorioClientes;
		this.cuentaRepository = cuentaRepository;
		this.sesionRepository = sesionRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.reloj = reloj;
	}

	/**
	 * La cuenta desactivada solo se informa cuando la contraseña es correcta.
	 *
	 * @throws NoAutenticadoException
	 *             correo o contraseña incorrectos (401)
	 * @throws DemasiadosIntentosException
	 *             login bloqueado tras 5 intentos fallidos (429)
	 * @throws ProhibidoException
	 *             cuenta desactivada (403)
	 */
	@Transactional(noRollbackFor = NoAutenticadoException.class)
	public SesionIniciadaApp iniciarSesion(String correo, String contrasena) {
		Instant ahora = Instant.now(reloj);
		ClienteRegistrado cliente = directorioClientes.buscarPorCorreo(correo).orElseThrow(this::credencialesInvalidas);
		CuentaApp cuenta = cuentaRepository.findByClienteId(cliente.id()).orElseThrow(this::credencialesInvalidas);
		if (cuenta.estaBloqueada(ahora)) {
			throw new DemasiadosIntentosException(
					"Demasiados intentos. Intenta de nuevo en " + minutos(cuenta.segundosDeBloqueo(ahora)) + ".",
					cuenta.segundosDeBloqueo(ahora));
		}
		if (!passwordEncoder.matches(contrasena, cuenta.getContrasenaHash())) {
			cuenta.registrarIntentoFallido(ahora);
			throw credencialesInvalidas();
		}
		exigirActiva(cuenta);
		cuenta.registrarIngreso();
		return emitirSesion(cuenta, cliente, ahora);
	}

	/**
	 * Cambia el refresh token por un par nuevo. Si llega uno ya revocado, alguien
	 * lo reutilizó: se cierran todas las sesiones de la cuenta.
	 *
	 * @throws NoAutenticadoException
	 *             token inválido, usado, revocado o vencido (401)
	 * @throws ProhibidoException
	 *             cuenta desactivada (403)
	 */
	@Transactional(noRollbackFor = NoAutenticadoException.class)
	public SesionIniciadaApp refrescar(String refreshToken) {
		Instant ahora = Instant.now(reloj);
		SesionApp sesion = sesionRepository.findByTokenHash(Secretos.sha256(refreshToken))
				.orElseThrow(() -> new NoAutenticadoException(SESION_TERMINADA));
		if (sesion.estaRevocada()) {
			cerrarTodas(sesion.getCuentaId(), ahora);
			throw new NoAutenticadoException(SESION_TERMINADA);
		}
		if (!sesion.estaVigente(ahora)) {
			throw new NoAutenticadoException(SESION_TERMINADA);
		}
		CuentaApp cuenta = cuentaRepository.findById(sesion.getCuentaId())
				.orElseThrow(() -> new NoAutenticadoException(SESION_TERMINADA));
		exigirActiva(cuenta);
		ClienteRegistrado cliente = directorioClientes.buscarPorId(cuenta.getClienteId())
				.orElseThrow(() -> new NoAutenticadoException(SESION_TERMINADA));
		sesion.revocar(ahora);
		return emitirSesion(cuenta, cliente, ahora);
	}

	/** Revoca el refresh token. No falla si ya no era válido. */
	@Transactional
	public void cerrarSesion(String refreshToken) {
		sesionRepository.findByTokenHash(Secretos.sha256(refreshToken))
				.ifPresent(sesion -> sesion.revocar(Instant.now(reloj)));
	}

	/**
	 * @throws SolicitudInvalidaException
	 *             contraseña actual incorrecta, nueva que no cumple las reglas o
	 *             igual a la actual (400)
	 */
	@Transactional
	public void cambiarContrasena(Long clienteId, String actual, String nueva) {
		CuentaApp cuenta = cuentaDe(clienteId);
		if (!passwordEncoder.matches(actual, cuenta.getContrasenaHash())) {
			throw new SolicitudInvalidaException("La contraseña actual no es correcta", CodigosError.DATOS_INVALIDOS);
		}
		validarNueva(nueva);
		if (passwordEncoder.matches(nueva, cuenta.getContrasenaHash())) {
			throw new SolicitudInvalidaException("La contraseña nueva debe ser distinta de la actual",
					CodigosError.DATOS_INVALIDOS);
		}
		cuenta.cambiarContrasena(passwordEncoder.encode(nueva));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el cliente ya no existe (404)
	 */
	@Transactional(readOnly = true)
	public PerfilApp perfil(Long clienteId) {
		ClienteRegistrado cliente = directorioClientes.buscarPorId(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId));
		return new PerfilApp(cliente.id(), cliente.nombre(), cliente.correo());
	}

	/**
	 * Se revisa en cada petición de la app, para que una cuenta desactivada no siga
	 * entrando con un token vigente.
	 *
	 * @throws ProhibidoException
	 *             si la cuenta no existe o está desactivada (403)
	 */
	@Transactional(readOnly = true)
	public void exigirCuentaActiva(Long clienteId) {
		CuentaApp cuenta = cuentaRepository.findByClienteId(clienteId)
				.orElseThrow(() -> new ProhibidoException(CUENTA_DESACTIVADA, CodigosError.CUENTA_DESACTIVADA));
		exigirActiva(cuenta);
	}

	static void validarNueva(String nueva) {
		try {
			ReglasContrasena.validar(nueva);
		} catch (IllegalArgumentException ex) {
			throw new SolicitudInvalidaException(ex.getMessage(), CodigosError.DATOS_INVALIDOS);
		}
	}

	private SesionIniciadaApp emitirSesion(CuentaApp cuenta, ClienteRegistrado cliente, Instant ahora) {
		String accessToken = jwtService.generateToken(cliente.id(), cliente.correo(), List.of(Role.CLIENTE_CARECAR));
		String refreshToken = Secretos.nuevoRefreshToken();
		sesionRepository.save(SesionApp.iniciar(cuenta.getId(), Secretos.sha256(refreshToken), ahora));
		return new SesionIniciadaApp(accessToken, jwtService.segundosDeVigencia(), refreshToken,
				cuenta.isDebeCambiarContrasena());
	}

	private void cerrarTodas(Long cuentaId, Instant ahora) {
		sesionRepository.findByCuentaIdAndRevocadaEnIsNull(cuentaId).forEach(s -> s.revocar(ahora));
	}

	private CuentaApp cuentaDe(Long clienteId) {
		return cuentaRepository.findByClienteId(clienteId)
				.orElseThrow(() -> new ProhibidoException(CUENTA_DESACTIVADA, CodigosError.CUENTA_DESACTIVADA));
	}

	private static void exigirActiva(CuentaApp cuenta) {
		if (!cuenta.isActiva()) {
			throw new ProhibidoException(CUENTA_DESACTIVADA, CodigosError.CUENTA_DESACTIVADA);
		}
	}

	private NoAutenticadoException credencialesInvalidas() {
		return new NoAutenticadoException(CREDENCIALES_INVALIDAS, CodigosError.CREDENCIALES_INVALIDAS);
	}

	private static String minutos(long segundos) {
		long minutos = Math.max(1, (segundos + 59) / 60);
		return minutos == 1 ? "1 minuto" : minutos + " minutos";
	}
}
