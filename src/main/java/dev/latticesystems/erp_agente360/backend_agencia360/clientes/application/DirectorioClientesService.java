package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.Cliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.infrastructure.ClienteRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementación de {@link DirectorioClientes}. */
@Service
public class DirectorioClientesService implements DirectorioClientes {

	private final ClienteRepository clienteRepository;

	public DirectorioClientesService(ClienteRepository clienteRepository) {
		this.clienteRepository = clienteRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ClienteRegistrado> buscarPorCorreo(String correo) {
		String normalizado;
		try {
			normalizado = Cliente.normalizarCorreo(correo);
		} catch (IllegalArgumentException correoSinFormato) {
			return Optional.empty();
		}
		if (normalizado == null) {
			return Optional.empty();
		}
		return clienteRepository.findByCorreo(normalizado).map(DirectorioClientesService::registrado);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ClienteRegistrado> buscarPorId(Long id) {
		return clienteRepository.findById(id).map(DirectorioClientesService::registrado);
	}

	private static ClienteRegistrado registrado(Cliente cliente) {
		return new ClienteRegistrado(cliente.getId(), cliente.getNombre(), cliente.getCorreo());
	}
}
