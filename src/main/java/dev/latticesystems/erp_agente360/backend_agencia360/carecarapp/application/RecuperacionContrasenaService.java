package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CodigoRecuperacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CodigoRecuperacionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.SesionAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes.ClienteRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperación de contraseña con un código de 6 dígitos que llega por correo
 * (APP-2.3, contrato v1.1). La respuesta no revela si el correo existe.
 */
@Service
public class RecuperacionContrasenaService {

	static final String CODIGO_INVALIDO = "El código no es válido o ya venció. Pide uno nuevo.";

	private final DirectorioClientes directorioClientes;
	private final CuentaAppRepository cuentaRepository;
	private final CodigoRecuperacionRepository codigoRepository;
	private final SesionAppRepository sesionRepository;
	private final PasswordEncoder passwordEncoder;
	private final EnvioCorreos envioCorreos;
	private final Clock reloj;

	@Autowired
	public RecuperacionContrasenaService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			CodigoRecuperacionRepository codigoRepository, SesionAppRepository sesionRepository,
			PasswordEncoder passwordEncoder, EnvioCorreos envioCorreos) {
		this(directorioClientes, cuentaRepository, codigoRepository, sesionRepository, passwordEncoder, envioCorreos,
				Clock.systemUTC());
	}

	RecuperacionContrasenaService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			CodigoRecuperacionRepository codigoRepository, SesionAppRepository sesionRepository,
			PasswordEncoder passwordEncoder, EnvioCorreos envioCorreos, Clock reloj) {
		this.directorioClientes = directorioClientes;
		this.cuentaRepository = cuentaRepository;
		this.codigoRepository = codigoRepository;
		this.sesionRepository = sesionRepository;
		this.passwordEncoder = passwordEncoder;
		this.envioCorreos = envioCorreos;
		this.reloj = reloj;
	}

	/**
	 * Emite un código nuevo y cierra los anteriores. Si el correo no tiene una
	 * cuenta activa no hace nada, sin avisar.
	 */
	@Transactional
	public void solicitar(String correo) {
		Optional<ClienteRegistrado> cliente = directorioClientes.buscarPorCorreo(correo);
		Optional<CuentaApp> cuenta = cliente.flatMap(c -> cuentaRepository.findByClienteId(c.id()));
		if (cuenta.isEmpty() || !cuenta.get().isActiva()) {
			return;
		}
		Instant ahora = Instant.now(reloj);
		Long cuentaId = cuenta.get().getId();
		codigoRepository.findByCuentaIdAndUsadoEnIsNull(cuentaId).forEach(c -> c.cerrar(ahora));
		String codigo = Secretos.nuevoCodigo();
		codigoRepository.save(CodigoRecuperacion.emitir(cuentaId, passwordEncoder.encode(codigo), ahora));
		envioCorreos.enviarCodigoRecuperacion(cliente.get().correo(), cliente.get().nombre(), codigo);
	}

	/**
	 * Valida la contraseña nueva antes que el código, para que una contraseña que
	 * no cumple las reglas no gaste intentos. Al restablecer se cierran las
	 * sesiones abiertas.
	 *
	 * @throws SolicitudInvalidaException
	 *             contraseña que no cumple las reglas ({@code DATOS_INVALIDOS}) o
	 *             código inválido, usado o vencido ({@code CODIGO_INVALIDO}) (400)
	 */
	@Transactional(noRollbackFor = SolicitudInvalidaException.class)
	public void restablecer(String correo, String codigo, String contrasenaNueva) {
		AccesoAppService.validarNueva(contrasenaNueva);
		Instant ahora = Instant.now(reloj);
		CuentaApp cuenta = directorioClientes.buscarPorCorreo(correo)
				.flatMap(c -> cuentaRepository.findByClienteId(c.id())).filter(CuentaApp::isActiva)
				.orElseThrow(RecuperacionContrasenaService::codigoInvalido);
		CodigoRecuperacion vigente = codigoRepository
				.findFirstByCuentaIdAndUsadoEnIsNullOrderByCreatedAtDescIdDesc(cuenta.getId())
				.filter(c -> c.estaVigente(ahora)).orElseThrow(RecuperacionContrasenaService::codigoInvalido);
		if (!passwordEncoder.matches(codigo, vigente.getCodigoHash())) {
			vigente.registrarIntentoFallido();
			throw codigoInvalido();
		}
		vigente.cerrar(ahora);
		cuenta.cambiarContrasena(passwordEncoder.encode(contrasenaNueva));
		sesionRepository.findByCuentaIdAndRevocadaEnIsNull(cuenta.getId()).forEach(s -> s.revocar(ahora));
	}

	private static SolicitudInvalidaException codigoInvalido() {
		return new SolicitudInvalidaException(CODIGO_INVALIDO, CodigosError.CODIGO_INVALIDO);
	}
}
