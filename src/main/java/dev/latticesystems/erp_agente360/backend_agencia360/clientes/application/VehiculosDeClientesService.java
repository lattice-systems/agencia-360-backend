package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.Cliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.VehiculoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.infrastructure.VehiculoClienteRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementación de {@link VehiculosDeClientes}. */
@Service
public class VehiculosDeClientesService implements VehiculosDeClientes {

	static final int LARGO_MINIMO_BUSQUEDA = 5;
	static final int MAXIMO_RESULTADOS = 20;
	/**
	 * Un VIN solo tiene letras y números; cualquier otro carácter no encuentra
	 * nada.
	 */
	private static final Pattern TEXTO_DE_VIN = Pattern.compile("[A-Z0-9]+");

	private final VehiculoClienteRepository vehiculoRepository;

	public VehiculosDeClientesService(VehiculoClienteRepository vehiculoRepository) {
		this.vehiculoRepository = vehiculoRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<VehiculoDeCliente> buscarPorVin(String vin) {
		String texto = vin == null ? "" : vin.strip().toUpperCase(Locale.ROOT);
		if (texto.length() < LARGO_MINIMO_BUSQUEDA || !TEXTO_DE_VIN.matcher(texto).matches()) {
			return List.of();
		}
		return vehiculoRepository.buscarPorVin(texto, PageRequest.of(0, MAXIMO_RESULTADOS)).stream()
				.map(VehiculosDeClientesService::vista).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<VehiculoDeCliente> buscarPorId(Long vehiculoId) {
		return vehiculoRepository.obtener(vehiculoId).map(VehiculosDeClientesService::vista);
	}

	@Override
	@Transactional(readOnly = true)
	public List<VehiculoDeCliente> deCliente(Long clienteId) {
		return vehiculoRepository.deCliente(clienteId).stream().map(VehiculosDeClientesService::vista).toList();
	}

	@Override
	@Transactional
	public VehiculoDeCliente corregir(Long vehiculoId, String placas, Integer kilometraje) {
		VehiculoCliente vehiculo = vehiculoRepository.obtener(vehiculoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		vehiculo.corregir(placas, kilometraje);
		vehiculoRepository.flush();
		return vista(vehiculo);
	}

	private static VehiculoDeCliente vista(VehiculoCliente vehiculo) {
		Cliente cliente = vehiculo.getCliente();
		return new VehiculoDeCliente(vehiculo.getId(), vehiculo.getVin(), vehiculo.getMarca(), vehiculo.getModelo(),
				vehiculo.getAnio(), vehiculo.getVersion(), vehiculo.getPlacas(), vehiculo.getKilometraje(),
				new Propietario(cliente.getId(), cliente.getNombre(), cliente.getCorreo(), cliente.getTelefono()));
	}
}
