package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta y desactivación de cuentas de la app. La usa CRM-1.3 (cuenta automática
 * al finalizar y alta manual del Gerente de marketing).
 */
@Service
public class CuentasAppService {

	private final DirectorioClientes directorioClientes;
	private final CuentaAppRepository cuentaRepository;
	private final PasswordEncoder passwordEncoder;

	public CuentasAppService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			PasswordEncoder passwordEncoder) {
		this.directorioClientes = directorioClientes;
		this.cuentaRepository = cuentaRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Abre la cuenta con una contraseña temporal que el cliente cambia en su primer
	 * ingreso.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el cliente no existe (404)
	 * @throws ConflictoException
	 *             si el cliente no tiene correo o ya tiene cuenta (409)
	 */
	@Transactional
	public void abrir(Long clienteId, String contrasenaTemporal) {
		var cliente = directorioClientes.buscarPorId(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId));
		if (cliente.correo() == null) {
			throw new ConflictoException("El cliente " + clienteId + " no tiene correo para entrar a la app");
		}
		if (cuentaRepository.existsByClienteId(clienteId)) {
			throw new ConflictoException("El cliente " + clienteId + " ya tiene cuenta en la app");
		}
		AccesoAppService.validarNueva(contrasenaTemporal);
		cuentaRepository.save(CuentaApp.abrir(clienteId, passwordEncoder.encode(contrasenaTemporal)));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el cliente no tiene cuenta (404)
	 */
	@Transactional
	public void desactivar(Long clienteId) {
		cuentaRepository.findByClienteId(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("El cliente " + clienteId + " no tiene cuenta"))
				.desactivar();
	}
}
