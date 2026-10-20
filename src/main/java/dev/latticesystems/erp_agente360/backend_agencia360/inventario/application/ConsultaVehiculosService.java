package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de las unidades del inventario (ERP-2.0) con la visibilidad que
 * decidió el PO el 13 de octubre:
 * <ul>
 * <li>Inventario y Administrador ven todas las unidades.</li>
 * <li>Ventas y Facturación ven solo las disponibles: Facturación elige entre
 * ellas la unidad de una venta (ERP-1.0).</li>
 * <li>Los demás roles no ven unidades.</li>
 * </ul>
 * Para ellos, una unidad que no está disponible se trata como inexistente
 * (404).
 */
@Service
public class ConsultaVehiculosService {

	private final VehiculoRepository vehiculoRepository;

	public ConsultaVehiculosService(VehiculoRepository vehiculoRepository) {
		this.vehiculoRepository = vehiculoRepository;
	}

	/**
	 * @param estatus
	 *            estatus por el que se filtra; {@code null} para todas las que el
	 *            usuario puede ver
	 */
	@Transactional(readOnly = true)
	public List<VehiculoVista> listar(EstatusVehiculo estatus, UsuarioAutenticado actor) {
		List<Vehiculo> vehiculos;
		if (veTodas(actor)) {
			vehiculos = estatus == null
					? vehiculoRepository.findAllByOrderByCreatedAtDescIdDesc()
					: vehiculoRepository.findByEstatusOrderByCreatedAtDescIdDesc(estatus);
		} else if (estatus == null || estatus == EstatusVehiculo.DISPONIBLE) {
			vehiculos = vehiculoRepository.findByEstatusOrderByCreatedAtDescIdDesc(EstatusVehiculo.DISPONIBLE);
		} else {
			vehiculos = List.of();
		}
		return vehiculos.stream().map(VehiculoVista::de).toList();
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la unidad no existe o el usuario no la puede ver (404)
	 */
	@Transactional(readOnly = true)
	public VehiculoVista obtener(Long id, UsuarioAutenticado actor) {
		Optional<Vehiculo> vehiculo = veTodas(actor)
				? vehiculoRepository.findById(id)
				: vehiculoRepository.findByIdAndEstatus(id, EstatusVehiculo.DISPONIBLE);
		return vehiculo.map(VehiculoVista::de)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + id));
	}

	private static boolean veTodas(UsuarioAutenticado actor) {
		if (actor.tieneRol(Role.INVENTARIO) || actor.tieneRol(Role.ADMINISTRADOR)) {
			return true;
		}
		if (actor.tieneRol(Role.VENTAS) || actor.tieneRol(Role.FACTURACION)) {
			return false;
		}
		throw new AccessDeniedException("Tu rol no tiene acceso a las unidades");
	}
}
