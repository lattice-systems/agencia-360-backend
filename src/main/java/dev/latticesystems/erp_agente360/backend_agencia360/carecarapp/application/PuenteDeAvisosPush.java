package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.EntregaDeAvisos;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lleva al módulo {@code notificaciones} lo que le pasa al cliente (V2-M06.07):
 * cada {@link AvisoAlCliente} y cada {@link OrdenServicioCambiada} se vuelven
 * un push y una fila de la bandeja.
 *
 * <p>
 * La dirección es {@code carecarapp → notificaciones}, no al revés: el módulo
 * de avisos no conoce los eventos de este, así que no hay ciclo y un módulo
 * nuevo que quiera avisar solo llama a {@link EntregaDeAvisos}. Corre después
 * de confirmar la transacción que causó el aviso y nunca la rompe:
 * {@code avisar} no lanza.
 */
@Component
class PuenteDeAvisosPush {

	static final String EVENTO_ORDEN = "orden";

	private final EntregaDeAvisos avisos;

	PuenteDeAvisosPush(EntregaDeAvisos avisos) {
		this.avisos = avisos;
	}

	@TransactionalEventListener(fallbackExecution = true)
	void alAvisarAlCliente(AvisoAlCliente aviso) {
		avisos.avisar(aviso.clienteId(), aviso.evento(), aviso.datos());
	}

	/**
	 * La orden no viaja como {@link AvisoAlCliente} (el canal en vivo la manda con
	 * su propio nombre), así que aquí se arma con el mismo nombre de evento.
	 */
	@TransactionalEventListener(fallbackExecution = true)
	void alCambiarOrden(OrdenServicioCambiada cambio) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("ordenId", cambio.ordenId());
		datos.put("vehiculoId", cambio.vehiculoId());
		datos.put("etapa", cambio.etapa() == null ? null : cambio.etapa().name());
		datos.put("motivo", cambio.motivo() == null ? null : cambio.motivo().name());
		avisos.avisar(cambio.clienteId(), EVENTO_ORDEN, datos);
	}
}
