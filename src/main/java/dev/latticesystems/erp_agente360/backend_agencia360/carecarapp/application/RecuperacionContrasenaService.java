package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TokenRecuperacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.SesionAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.TokenRecuperacionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes.ClienteRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperación de contraseña (APP-2.3, V2-T05.04). El correo trae un enlace de
 * un solo uso que abre la app (V2-M01.04) y, para la app v1, un código de 6
 * dígitos; los dos viven en la misma fila de {@code token_recuperacion} y usar
 * uno cierra el otro. La respuesta no revela si el correo existe.
 */
@Service
public class RecuperacionContrasenaService {

	static final String CODIGO_INVALIDO = "El código no es válido o ya venció. Pide uno nuevo.";
	static final String ENLACE_INVALIDO = "El enlace no es válido o ya venció. Pide uno nuevo.";

	private final DirectorioClientes directorioClientes;
	private final CuentaAppRepository cuentaRepository;
	private final TokenRecuperacionRepository tokenRepository;
	private final SesionAppRepository sesionRepository;
	private final PasswordEncoder passwordEncoder;
	private final EnvioCorreos envioCorreos;
	private final String enlaceBase;
	private final Clock reloj;

	@Autowired
	public RecuperacionContrasenaService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			TokenRecuperacionRepository tokenRepository, SesionAppRepository sesionRepository,
			PasswordEncoder passwordEncoder, EnvioCorreos envioCorreos,
			@Value("${app.recuperacion.enlace:carecar://restablecer-contrasena}") String enlaceBase) {
		this(directorioClientes, cuentaRepository, tokenRepository, sesionRepository, passwordEncoder, envioCorreos,
				enlaceBase, Clock.systemUTC());
	}

	RecuperacionContrasenaService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			TokenRecuperacionRepository tokenRepository, SesionAppRepository sesionRepository,
			PasswordEncoder passwordEncoder, EnvioCorreos envioCorreos, String enlaceBase, Clock reloj) {
		this.directorioClientes = directorioClientes;
		this.cuentaRepository = cuentaRepository;
		this.tokenRepository = tokenRepository;
		this.sesionRepository = sesionRepository;
		this.passwordEncoder = passwordEncoder;
		this.envioCorreos = envioCorreos;
		this.enlaceBase = enlaceBase;
		this.reloj = reloj;
	}

	/**
	 * Emite un enlace nuevo con su código y cierra los anteriores. Si el correo no
	 * tiene una cuenta activa no hace nada, sin avisar.
	 */
	@Transactional
	public void solicitar(String correo) {
		Optional<CuentaApp> cuenta = cuentaActiva(correo);
		Optional<ClienteRegistrado> cliente = cuenta.flatMap(c -> directorioClientes.buscarPorId(c.getClienteId()));
		if (cuenta.isEmpty() || cliente.isEmpty() || cliente.get().correo() == null) {
			return;
		}
		Instant ahora = Instant.now(reloj);
		Long cuentaId = cuenta.get().getId();
		tokenRepository.findByCuentaAppIdAndUsadoFalse(cuentaId).forEach(t -> t.cerrar(ahora));
		String token = Secretos.nuevoRefreshToken();
		String codigo = Secretos.nuevoCodigo();
		tokenRepository.save(
				TokenRecuperacion.emitir(cuentaId, Secretos.sha256(token), passwordEncoder.encode(codigo), ahora));
		envioCorreos.enviarRecuperacion(cliente.get().correo(), cliente.get().nombre(), enlace(token), codigo);
	}

	/**
	 * Restablece con el enlace del correo. Valida la contraseña nueva antes que el
	 * enlace; al restablecer se cierran las sesiones abiertas.
	 *
	 * @throws SolicitudInvalidaException
	 *             contraseña que no cumple las reglas ({@code DATOS_INVALIDOS}) o
	 *             enlace inválido, usado o vencido ({@code CODIGO_INVALIDO}) (400)
	 */
	@Transactional(noRollbackFor = SolicitudInvalidaException.class)
	public void restablecerConEnlace(String token, String contrasenaNueva) {
		AccesoAppService.validarNueva(contrasenaNueva);
		Instant ahora = Instant.now(reloj);
		TokenRecuperacion vigente = tokenRepository.findByTokenHash(Secretos.sha256(token))
				.filter(t -> t.estaVigente(ahora)).orElseThrow(RecuperacionContrasenaService::enlaceInvalido);
		CuentaApp cuenta = cuentaRepository.findById(vigente.getCuentaAppId()).filter(CuentaApp::isActiva)
				.orElseThrow(RecuperacionContrasenaService::enlaceInvalido);
		cambiar(cuenta, vigente, contrasenaNueva, ahora);
	}

	/**
	 * Restablece con el código de 6 dígitos (app v1). Valida la contraseña nueva
	 * antes que el código, para que una contraseña que no cumple las reglas no
	 * gaste intentos.
	 *
	 * @throws SolicitudInvalidaException
	 *             contraseña que no cumple las reglas ({@code DATOS_INVALIDOS}) o
	 *             código inválido, usado o vencido ({@code CODIGO_INVALIDO}) (400)
	 */
	@Transactional(noRollbackFor = SolicitudInvalidaException.class)
	public void restablecer(String correo, String codigo, String contrasenaNueva) {
		AccesoAppService.validarNueva(contrasenaNueva);
		Instant ahora = Instant.now(reloj);
		CuentaApp cuenta = cuentaActiva(correo).orElseThrow(RecuperacionContrasenaService::codigoInvalido);
		TokenRecuperacion vigente = tokenRepository
				.findFirstByCuentaAppIdAndUsadoFalseOrderByCreatedAtDescIdDesc(cuenta.getId())
				.filter(t -> t.estaVigente(ahora)).orElseThrow(RecuperacionContrasenaService::codigoInvalido);
		if (!passwordEncoder.matches(codigo, vigente.getCodigoHash())) {
			vigente.registrarIntentoFallido();
			throw codigoInvalido();
		}
		cambiar(cuenta, vigente, contrasenaNueva, ahora);
	}

	private void cambiar(CuentaApp cuenta, TokenRecuperacion token, String contrasenaNueva, Instant ahora) {
		token.cerrar(ahora);
		cuenta.cambiarContrasena(passwordEncoder.encode(contrasenaNueva));
		sesionRepository.findByCuentaIdAndRevocadaEnIsNull(cuenta.getId()).forEach(s -> s.revocar(ahora));
	}

	private Optional<CuentaApp> cuentaActiva(String correo) {
		if (correo == null || correo.isBlank()) {
			return Optional.empty();
		}
		return cuentaRepository.findByUsuario(CuentaApp.normalizarUsuario(correo)).filter(CuentaApp::isActiva);
	}

	private String enlace(String token) {
		return enlaceBase + (enlaceBase.contains("?") ? "&" : "?") + "token=" + token;
	}

	private static SolicitudInvalidaException codigoInvalido() {
		return new SolicitudInvalidaException(CODIGO_INVALIDO, CodigosError.CODIGO_INVALIDO);
	}

	private static SolicitudInvalidaException enlaceInvalido() {
		return new SolicitudInvalidaException(ENLACE_INVALIDO, CodigosError.CODIGO_INVALIDO);
	}
}
