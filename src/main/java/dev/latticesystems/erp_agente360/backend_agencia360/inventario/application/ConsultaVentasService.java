package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.ConsultaUnidades;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VentaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de las ventas de unidades (ERP-1.0), para el ERP y para los módulos
 * que necesitan saber qué unidad se le vendió a un prospecto.
 */
@Service
public class ConsultaVentasService implements ConsultaUnidades {

	private final VentaRepository ventaRepository;
	private final String marca;

	public ConsultaVentasService(VentaRepository ventaRepository, @Value("${app.agencia.marca}") String marca) {
		this.ventaRepository = ventaRepository;
		this.marca = marca;
	}

	@Transactional(readOnly = true)
	public List<VentaVista> listar() {
		return ventaRepository.findAllByOrderByCreatedAtDescIdDesc().stream().map(VentaVista::de).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UnidadDelProspecto> unidadDelProspecto(Long prospectoId) {
		return ventaRepository.findByProspectoId(prospectoId).map(venta -> venta.getVehiculo()).map(this::unidad);
	}

	private UnidadDelProspecto unidad(Vehiculo vehiculo) {
		return new UnidadDelProspecto(vehiculo.getId(), vehiculo.getVin(), vehiculo.getModelo(), vehiculo.getAnio(),
				vehiculo.getVersion(), marca);
	}
}
