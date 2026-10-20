package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVenta;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Venta;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoFinalizado;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Registra la venta cuando el vendedor pasa el prospecto a Finalizado (ERP-1.0:
 * es venta cuando el vendedor llevó el proceso hasta Finalizado). La unidad que
 * eligió Facturación sale del inventario a cargo del vendedor, con la fecha de
 * entrega.
 *
 * <p>
 * Corre después del commit del cambio de etapa, en su propia transacción. Si el
 * evento llega dos veces (Modulith reintenta las publicaciones pendientes), la
 * segunda no hace nada.
 */
@Service
public class RegistroVentaService {

	private static final Logger LOG = LoggerFactory.getLogger(RegistroVentaService.class);

	private final VentaRepository ventaRepository;
	private final MovimientoInventarioRepository movimientoRepository;
	private final ApplicationEventPublisher eventos;

	public RegistroVentaService(VentaRepository ventaRepository, MovimientoInventarioRepository movimientoRepository,
			ApplicationEventPublisher eventos) {
		this.ventaRepository = ventaRepository;
		this.movimientoRepository = movimientoRepository;
		this.eventos = eventos;
	}

	@ApplicationModuleListener
	public void alFinalizar(ProspectoFinalizado finalizado) {
		Optional<Venta> existente = ventaRepository.findByProspectoId(finalizado.prospectoId());
		if (existente.isEmpty()) {
			// Facturación no eligió la unidad: la venta se registra igual para no
			// perderla.
			LOG.warn("El prospecto {} se finalizó sin unidad elegida por Facturación", finalizado.prospectoId());
			ventaRepository.save(Venta.registrarSinUnidad(finalizado.prospectoId(), finalizado.fechaEntrega()));
			return;
		}
		Venta venta = existente.get();
		if (venta.getEstatus() == EstatusVenta.REGISTRADA) {
			return;
		}
		venta.registrar(finalizado.fechaEntrega());
		movimientoRepository.save(MovimientoInventario.salidaDeVehiculo(venta.getVehiculo(), finalizado.vendedorId(),
				finalizado.fechaEntrega()));
		eventos.publishEvent(new VehiculoCambiado(venta.getVehiculo().getId()));
	}
}
