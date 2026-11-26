package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Libera los apartados vencidos (V2-M14.02). Cada pedido va en su propia
 * transacción para que uno que falle no detenga a los demás.
 */
@Component
public class VencimientoDeApartados {

	private static final Logger BITACORA = LoggerFactory.getLogger(VencimientoDeApartados.class);

	private static final String INICIO = "${app.tienda.apartado.vencer-inicio:PT1M}";
	private static final String CADA = "${app.tienda.apartado.vencer-cada:PT5M}";

	private final TiendaAppService tienda;

	public VencimientoDeApartados(TiendaAppService tienda) {
		this.tienda = tienda;
	}

	@Scheduled(initialDelayString = INICIO, fixedDelayString = CADA)
	public void liberarVencidos() {
		for (Long pedidoId : tienda.idsVencidos()) {
			try {
				tienda.vencer(pedidoId);
			} catch (RuntimeException ex) {
				BITACORA.warn("No se pudo liberar el apartado {}", pedidoId, ex);
			}
		}
	}
}
