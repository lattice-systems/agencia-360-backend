package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.OrdenEnCurso;
import java.time.LocalDateTime;

/**
 * Orden abierta del cliente para la tarjeta de Inicio (V2-M04.01).
 *
 * @param vehiculo
 *            marca, modelo y año, por ejemplo «Nissan March 2026»
 * @param entregaEstimada
 *            en hora de la agencia; {@code null} si aún no se estima
 * @param avance
 *            de 0 a 100 según la etapa
 */
public record OrdenEnCursoApp(Long id, Long vehiculoId, String vehiculo, EtapaTaller etapa, EstatusServicio estatus,
		@JsonFormat(pattern = ServicioApp.FORMATO_FECHA_HORA) LocalDateTime entregaEstimada, String asesor, int avance,
		int adicionalesPendientes) {

	static OrdenEnCursoApp de(OrdenEnCurso orden, String vehiculo) {
		return new OrdenEnCursoApp(orden.id(), orden.vehiculoId(), vehiculo, orden.etapa(),
				EstatusServicio.valueOf(orden.estatus().name()), ServicioApp.enLaAgencia(orden.entregaEstimada()),
				orden.asesor(), orden.avance(), orden.adicionalesPendientes());
	}
}
