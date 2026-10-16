package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.infrastructure.ClienteRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de clientes para el Gerente de marketing (CRM-1.5). */
@Service
public class ConsultaClientesService {

	private final ClienteRepository clienteRepository;

	public ConsultaClientesService(ClienteRepository clienteRepository) {
		this.clienteRepository = clienteRepository;
	}

	/**
	 * @param buscar
	 *            texto a buscar en el nombre o el correo; vacío regresa todos
	 */
	@Transactional(readOnly = true)
	public List<ClienteVista> listar(String buscar) {
		var clientes = buscar == null || buscar.isBlank()
				? clienteRepository.listarConVehiculos()
				: clienteRepository.buscarConVehiculos(buscar.strip());
		return clientes.stream().map(ClienteVista::de).toList();
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si no existe (404)
	 */
	@Transactional(readOnly = true)
	public ClienteDetalle obtener(Long id) {
		return clienteRepository.obtenerConVehiculos(id).map(ClienteDetalle::de)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + id));
	}
}
