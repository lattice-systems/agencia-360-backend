package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.Cliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.infrastructure.ClienteRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de clientes (CRM-1.5). Lo usan la creación automática al finalizar
 * un prospecto y el alta manual del Gerente de marketing (CRM-1.3).
 */
@Service
public class RegistroClientesService {

	private final ClienteRepository clienteRepository;

	public RegistroClientesService(ClienteRepository clienteRepository) {
		this.clienteRepository = clienteRepository;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta el nombre o un dato no es válido (400)
	 * @throws ConflictoException
	 *             si ya hay un cliente con ese correo o de ese prospecto (409)
	 */
	@Transactional
	public ClienteDetalle registrar(NuevoCliente nuevo) {
		Cliente cliente = Cliente.registrar(nuevo.nombre(), nuevo.correo(), nuevo.telefono(), nuevo.prospectoId());
		if (cliente.getCorreo() != null && clienteRepository.findByCorreo(cliente.getCorreo()).isPresent()) {
			throw new ConflictoException("Ya existe un cliente con el correo " + cliente.getCorreo());
		}
		if (cliente.getProspectoId() != null
				&& clienteRepository.findByProspectoId(cliente.getProspectoId()).isPresent()) {
			throw new ConflictoException("El prospecto " + cliente.getProspectoId() + " ya tiene un cliente");
		}
		return ClienteDetalle.de(clienteRepository.save(cliente));
	}
}
