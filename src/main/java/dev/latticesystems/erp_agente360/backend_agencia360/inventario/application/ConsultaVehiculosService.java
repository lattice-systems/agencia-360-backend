package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de las unidades del inventario (ERP-2.0). */
@Service
public class ConsultaVehiculosService {

	private final VehiculoRepository vehiculoRepository;

	public ConsultaVehiculosService(VehiculoRepository vehiculoRepository) {
		this.vehiculoRepository = vehiculoRepository;
	}

	/**
	 * @param estatus
	 *            estatus por el que se filtra; {@code null} para todas
	 */
	@Transactional(readOnly = true)
	public List<VehiculoVista> listar(EstatusVehiculo estatus) {
		List<Vehiculo> vehiculos = estatus == null
				? vehiculoRepository.findAllByOrderByCreatedAtDescIdDesc()
				: vehiculoRepository.findByEstatusOrderByCreatedAtDescIdDesc(estatus);
		return vehiculos.stream().map(VehiculoVista::de).toList();
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la unidad no existe (404)
	 */
	@Transactional(readOnly = true)
	public VehiculoVista obtener(Long id) {
		return vehiculoRepository.findById(id).map(VehiculoVista::de)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + id));
	}
}
