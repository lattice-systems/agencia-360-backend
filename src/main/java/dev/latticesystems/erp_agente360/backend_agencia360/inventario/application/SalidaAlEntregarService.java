package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

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
 * Cuando el vendedor pasa el prospecto a Finalizado, la unidad sale del
 * inventario a cargo del vendedor, con la fecha de entrega. La venta ya no se
 * registra aquí: desde ERP-1.3 es venta al emitir la factura.
 *
 * <p>
 * Corre después del commit del cambio de etapa, en su propia transacción. Si el
 * evento llega dos veces (Modulith reintenta las publicaciones pendientes), la
 * segunda no hace nada. La unidad se queda en Vendido.
 */
@Service
public class SalidaAlEntregarService {

	private static final Logger LOG = LoggerFactory.getLogger(SalidaAlEntregarService.class);

	private final VentaRepository ventaRepository;
	private final MovimientoInventarioRepository movimientoRepository;
	private final ApplicationEventPublisher eventos;

	public SalidaAlEntregarService(VentaRepository ventaRepository, MovimientoInventarioRepository movimientoRepository,
			ApplicationEventPublisher eventos) {
		this.ventaRepository = ventaRepository;
		this.movimientoRepository = movimientoRepository;
		this.eventos = eventos;
	}

	@ApplicationModuleListener
	public void alFinalizar(ProspectoFinalizado finalizado) {
		Optional<Venta> existente = ventaRepository.findByProspectoId(finalizado.prospectoId());
		if (existente.isEmpty()) {
			LOG.warn("El prospecto {} se finalizó sin factura; no sale ninguna unidad del inventario",
					finalizado.prospectoId());
			return;
		}
		Venta venta = existente.get();
		if (!venta.entregar(finalizado.fechaEntrega()) || venta.getVehiculo() == null) {
			return;
		}
		movimientoRepository.save(MovimientoInventario.salidaDeVehiculo(venta.getVehiculo(), finalizado.vendedorId(),
				finalizado.fechaEntrega()));
		eventos.publishEvent(new VehiculoCambiado(venta.getVehiculo().getId()));
	}
}
