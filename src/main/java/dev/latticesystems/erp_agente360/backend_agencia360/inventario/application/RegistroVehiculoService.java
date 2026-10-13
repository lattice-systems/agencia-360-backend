package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de unidades en el inventario (ERP-2.0). Cada alta deja su movimiento de
 * entrada a cargo de quien la registra, para que el inventario se pueda
 * reconstruir a partir de sus movimientos.
 */
@Service
public class RegistroVehiculoService {

	private final VehiculoRepository vehiculoRepository;
	private final MovimientoInventarioRepository movimientoRepository;
	private final Clock reloj;

	@Autowired
	public RegistroVehiculoService(VehiculoRepository vehiculoRepository,
			MovimientoInventarioRepository movimientoRepository) {
		this(vehiculoRepository, movimientoRepository, Clock.systemUTC());
	}

	RegistroVehiculoService(VehiculoRepository vehiculoRepository, MovimientoInventarioRepository movimientoRepository,
			Clock reloj) {
		this.vehiculoRepository = vehiculoRepository;
		this.movimientoRepository = movimientoRepository;
		this.reloj = reloj;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si los datos no son válidos (400)
	 * @throws ConflictoException
	 *             si ya existe una unidad con ese VIN (409)
	 */
	@Transactional
	public VehiculoVista registrar(NuevoVehiculo nuevo, UsuarioAutenticado responsable) {
		Vehiculo vehiculo = Vehiculo.registrar(nuevo.vin(), nuevo.modelo(), nuevo.version(), nuevo.anio(),
				nuevo.color(), nuevo.estatus());
		if (vehiculoRepository.existsByVin(vehiculo.getVin())) {
			throw new ConflictoException("Ya existe un vehículo con el VIN " + vehiculo.getVin());
		}
		Vehiculo guardado = vehiculoRepository.save(vehiculo);
		movimientoRepository
				.save(MovimientoInventario.entradaDeVehiculo(guardado, responsable.id(), Instant.now(reloj)));
		return VehiculoVista.de(guardado);
	}
}
