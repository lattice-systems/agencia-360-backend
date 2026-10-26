package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.VentaDeUnidades;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Venta;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vende una unidad al emitir la factura (ERP-1.3). Ya no existe el apartado ni
 * la elección previa: la unidad pasa de Disponible a Vendido en la transacción
 * de la emisión, con la fila bloqueada para que dos facturas no se la lleven.
 */
@Service
public class VentaUnidadService implements VentaDeUnidades {

	private final VehiculoRepository vehiculoRepository;
	private final VentaRepository ventaRepository;
	private final ApplicationEventPublisher eventos;
	private final String marca;

	public VentaUnidadService(VehiculoRepository vehiculoRepository, VentaRepository ventaRepository,
			ApplicationEventPublisher eventos, @Value("${app.agencia.marca}") String marca) {
		this.vehiculoRepository = vehiculoRepository;
		this.ventaRepository = ventaRepository;
		this.eventos = eventos;
		this.marca = marca;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public UnidadVendida vender(Long prospectoId, Long vehiculoId, Long usuarioId, Instant ahora) {
		// La fila de la unidad queda bloqueada: dos facturas no la venden a la vez.
		Vehiculo unidad = vehiculoRepository.buscarParaActualizar(vehiculoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		if (ventaRepository.findByProspectoId(prospectoId).isPresent()) {
			throw new ConflictoException("El prospecto ya tiene una unidad vendida");
		}
		Venta venta = ventaRepository.saveAndFlush(Venta.registrar(prospectoId, unidad, usuarioId, ahora));
		eventos.publishEvent(new VehiculoCambiado(unidad.getId()));
		return new UnidadVendida(venta.getId(), unidad.getId(), unidad.getVin(), marca, unidad.getModelo(),
				unidad.getVersion(), unidad.getAnio(), unidad.getColor(), unidad.getPrecio());
	}
}
