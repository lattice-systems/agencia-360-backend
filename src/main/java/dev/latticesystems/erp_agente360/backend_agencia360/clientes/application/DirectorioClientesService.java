package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.CorreoDeClienteCambiado;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.Cliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.infrastructure.ClienteRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementación de {@link DirectorioClientes}. */
@Service
public class DirectorioClientesService implements DirectorioClientes {

	private final ClienteRepository clienteRepository;
	private final ApplicationEventPublisher eventos;

	public DirectorioClientesService(ClienteRepository clienteRepository, ApplicationEventPublisher eventos) {
		this.clienteRepository = clienteRepository;
		this.eventos = eventos;
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

	@Override
	@Transactional
	public ClienteRegistrado cambiarCorreo(Long clienteId, String correo) {
		Cliente cliente = clienteRepository.findById(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId));
		String nuevo = Cliente.normalizarCorreo(correo);
		if (nuevo == null) {
			throw new IllegalArgumentException("El correo es obligatorio");
		}
		if (nuevo.equals(cliente.getCorreo())) {
			return registrado(cliente);
		}
		clienteRepository.findByCorreo(nuevo).filter(otro -> !otro.getId().equals(clienteId)).ifPresent(otro -> {
			throw new ConflictoException("Ese correo ya es de otro cliente");
		});
		cliente.cambiarCorreo(nuevo);
		eventos.publishEvent(new CorreoDeClienteCambiado(clienteId, nuevo));
		return registrado(cliente);
	}

	@Override
	@Transactional
	public void anonimizar(Long clienteId) {
		clienteRepository.findById(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId)).anonimizar();
	}

	private static ClienteRegistrado registrado(Cliente cliente) {
		return new ClienteRegistrado(cliente.getId(), cliente.getNombre(), cliente.getCorreo(), cliente.getTelefono(),
				cliente.getCreatedAt());
	}
}
