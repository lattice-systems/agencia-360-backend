package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrección de los datos de una unidad (decisión del PO del 13 de octubre: se
 * edita, no se elimina).
 */
@Service
public class EdicionVehiculoService {

	private final VehiculoRepository vehiculoRepository;

	public EdicionVehiculoService(VehiculoRepository vehiculoRepository) {
		this.vehiculoRepository = vehiculoRepository;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la unidad no existe (404)
	 * @throws IllegalArgumentException
	 *             si un dato no es válido o el cambio de estatus no le toca a
	 *             Inventario (400)
	 * @throws ConflictoException
	 *             si otra unidad ya tiene ese VIN (409)
	 */
	@Transactional
	public VehiculoVista editar(Long id, VehiculoEditado datos) {
		Vehiculo vehiculo = vehiculoRepository.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + id));
		// Se revisa antes de modificar la entidad: la consulta forzaría a guardar el
		// cambio y el choque llegaría como error de la base en lugar de este mensaje.
		String vin = Vehiculo.normalizarVin(datos.vin());
		if (vehiculoRepository.existsByVinAndIdNot(vin, id)) {
			throw new ConflictoException("Ya existe un vehículo con el VIN " + vin);
		}
		vehiculo.editar(vin, datos.modelo(), datos.version(), datos.anio(), datos.color(), datos.estatus());
		return VehiculoVista.de(vehiculo);
	}
}
