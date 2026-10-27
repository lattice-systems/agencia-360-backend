package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.FacturaEmitida;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Autorización de entrega (ERP-1.2): la factura emitida es la autorización, así
 * que al recibir {@link FacturaEmitida} el prospecto pasa de Facturación a
 * Entrega y vuelve al vendedor para que lo finalice. No hay pantalla aparte
 * para autorizar.
 *
 * <p>
 * Corre después del commit de la emisión, en su propia transacción. Si el
 * evento llega dos veces (Modulith reintenta las publicaciones pendientes), la
 * segunda no hace nada.
 */
@Service
public class EntregaAlFacturarService {

	private static final Logger LOG = LoggerFactory.getLogger(EntregaAlFacturarService.class);

	private final ProspectoRepository prospectoRepository;

	public EntregaAlFacturarService(ProspectoRepository prospectoRepository) {
		this.prospectoRepository = prospectoRepository;
	}

	@ApplicationModuleListener
	public void alEmitirFactura(FacturaEmitida factura) {
		Optional<Prospecto> encontrado = prospectoRepository.findById(factura.prospectoId());
		if (encontrado.isEmpty()) {
			LOG.warn("Se emitió la factura {} de un prospecto que no existe: {}", factura.folio(),
					factura.prospectoId());
			return;
		}
		Prospecto prospecto = encontrado.get();
		if (prospecto.autorizarEntregaPorFactura(factura.emitidaEn(), factura.emitidaPorId())) {
			return;
		}
		if (prospecto.getEtapa() != Etapa.ENTREGA && prospecto.getEtapa() != Etapa.FINALIZADO) {
			// Por ejemplo, el Administrador aprobó su cierre mientras se facturaba.
			LOG.warn("Se emitió la factura {} del prospecto {}, que está en {}; no pasa a Entrega", factura.folio(),
					factura.prospectoId(), prospecto.getEtapa());
		}
	}
}
