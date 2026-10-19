package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.SesionAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes.ClienteRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuentas de la app de los clientes (CRM-1.3): alta con contraseña temporal que
 * llega por correo, desactivación y reactivación. Las usa el Gerente de
 * marketing y la creación automática al finalizar un prospecto.
 */
@Service
public class CuentasAppService {

	private final DirectorioClientes directorioClientes;
	private final CuentaAppRepository cuentaRepository;
	private final SesionAppRepository sesionRepository;
	private final PasswordEncoder passwordEncoder;
	private final EnvioCorreos envioCorreos;

	public CuentasAppService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			SesionAppRepository sesionRepository, PasswordEncoder passwordEncoder, EnvioCorreos envioCorreos) {
		this.directorioClientes = directorioClientes;
		this.cuentaRepository = cuentaRepository;
		this.sesionRepository = sesionRepository;
		this.passwordEncoder = passwordEncoder;
		this.envioCorreos = envioCorreos;
	}

	/**
	 * Abre la cuenta con una contraseña temporal generada y se la manda al cliente
	 * por correo.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el cliente no existe (404)
	 * @throws ConflictoException
	 *             si el cliente no tiene correo o ya tiene cuenta (409)
	 */
	@Transactional
	public EstadoCuentaApp abrir(Long clienteId) {
		String contrasenaTemporal = Secretos.nuevaContrasenaTemporal();
		ClienteRegistrado cliente = abrir(clienteId, contrasenaTemporal);
		envioCorreos.enviarCuentaNueva(cliente.correo(), cliente.nombre(), contrasenaTemporal);
		return estado(clienteId);
	}

	/**
	 * Abre la cuenta con la contraseña temporal indicada, sin mandar correo.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el cliente no existe (404)
	 * @throws ConflictoException
	 *             si el cliente no tiene correo o ya tiene cuenta (409)
	 */
	@Transactional
	public ClienteRegistrado abrir(Long clienteId, String contrasenaTemporal) {
		ClienteRegistrado cliente = directorioClientes.buscarPorId(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId));
		if (cliente.correo() == null) {
			throw new ConflictoException("El cliente no tiene correo para entrar a la app");
		}
		if (cuentaRepository.existsByClienteId(clienteId)) {
			throw new ConflictoException("El cliente ya tiene cuenta en la app");
		}
		AccesoAppService.validarNueva(contrasenaTemporal);
		cuentaRepository.save(CuentaApp.abrir(clienteId, passwordEncoder.encode(contrasenaTemporal)));
		return cliente;
	}

	@Transactional(readOnly = true)
	public EstadoCuentaApp estado(Long clienteId) {
		return cuentaRepository.findByClienteId(clienteId).map(c -> new EstadoCuentaApp(clienteId,
				c.isActiva() ? EstadoCuentaApp.ACTIVA : EstadoCuentaApp.DESACTIVADA, c.isDebeCambiarContrasena()))
				.orElseGet(() -> new EstadoCuentaApp(clienteId, EstadoCuentaApp.SIN_CUENTA, false));
	}

	/**
	 * Desactiva la cuenta y cierra sus sesiones; el token vigente deja de servir en
	 * la siguiente petición.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el cliente no tiene cuenta (404)
	 */
	@Transactional
	public EstadoCuentaApp desactivar(Long clienteId) {
		CuentaApp cuenta = cuentaDe(clienteId);
		cuenta.desactivar();
		Instant ahora = Instant.now();
		sesionRepository.findByCuentaIdAndRevocadaEnIsNull(cuenta.getId()).forEach(s -> s.revocar(ahora));
		return estado(clienteId);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el cliente no tiene cuenta (404)
	 */
	@Transactional
	public EstadoCuentaApp activar(Long clienteId) {
		cuentaDe(clienteId).activar();
		return estado(clienteId);
	}

	private CuentaApp cuentaDe(Long clienteId) {
		return cuentaRepository.findByClienteId(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("El cliente no tiene cuenta en la app"));
	}
}
