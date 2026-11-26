package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * La orden en el taller para otros módulos: la tarjeta de Inicio, la línea de
 * tiempo, la inspección multipunto y los trabajos adicionales de la app CareCar
 * (V2-M04.01, V2-M04.02, V2-M04.04 y V2-M04.05). Es interfaz pública del
 * módulo; la implementación vive en {@code servicio.application}. No revisa de
 * quién es el vehículo: eso lo hace quien la usa.
 */
public interface TallerEnVivo {

	/**
	 * Órdenes con línea de tiempo que aún no se entregan, de los vehículos dados,
	 * de la más reciente a la más antigua. Las órdenes registradas en el expediente
	 * ya hechas no tienen línea de tiempo y no aparecen.
	 */
	List<OrdenEnCurso> enCurso(Collection<Long> vehiculoIds);

	/** La orden con sus etapas, su inspección y sus trabajos adicionales. */
	Optional<OrdenEnVivo> orden(Long ordenId);

	/**
	 * Aprueba o rechaza un trabajo adicional con la firma del cliente.
	 *
	 * @param firmaPng
	 *            la firma en PNG, de 1 MB como máximo
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException
	 *             si la orden o el trabajo no existen (404)
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException
	 *             si el trabajo ya se respondió ({@code ADICIONAL_YA_RESPONDIDO}) o
	 *             la orden ya está terminada ({@code ORDEN_CERRADA}) (409)
	 * @throws IllegalArgumentException
	 *             si la firma no es un PNG válido (400)
	 */
	OrdenEnVivo responder(Long ordenId, Long adicionalId, boolean aprobado, byte[] firmaPng, Long clienteId);

	/**
	 * Una orden abierta del cliente, para la tarjeta de Inicio.
	 *
	 * @param asesor
	 *            nombre de quien la atiende; {@code null} si no se sabe
	 * @param avance
	 *            de 0 a 100 según la etapa
	 * @param totalEstimado
	 *            total de la orden más los adicionales aprobados que aún no pasan a
	 *            ella, con IVA incluido
	 */
	record OrdenEnCurso(Long id, Long vehiculoId, EtapaTaller etapa, EstatusOrden estatus, Instant entregaEstimada,
			String asesor, int avance, int adicionalesPendientes, BigDecimal totalEstimado) {
	}

	/**
	 * La orden completa. {@code etapas} trae solo las que ya empezaron, en orden;
	 * la última es la actual. {@code totalEstimado} es el total de la orden más los
	 * trabajos adicionales aprobados que aún no pasan a ella, con IVA incluido: al
	 * quedar lista la orden, los aprobados ya están en su total.
	 */
	record OrdenEnVivo(Long id, Long vehiculoId, EstatusOrden estatus, EtapaTaller etapa, Instant entregaEstimada,
			String asesor, int avance, List<PasoDeEtapa> etapas, List<PuntoRevisado> inspeccion,
			List<Evidencia> evidencias, List<Adicional> adicionales, BigDecimal totalOrden,
			BigDecimal totalAdicionalesAprobados, BigDecimal totalAdicionalesPendientes, BigDecimal totalEstimado,
			List<PiezasPorInstalar.PedidoPorInstalar> pedidosApp) {
	}

	/** Una etapa que ya empezó; {@code fin} es nulo mientras siga en curso. */
	record PasoDeEtapa(EtapaTaller etapa, Instant inicio, Instant fin, String nota) {
	}

	/** Un punto revisado con su semáforo y sus fotos o videos. */
	record PuntoRevisado(Long id, String punto, ResultadoInspeccion resultado, String comentario,
			List<Evidencia> evidencias) {
	}

	/** Una foto o un video con su URL temporal firmada. */
	record Evidencia(Long id, TipoEvidencia tipo, String url) {
	}

	/** Un trabajo adicional cotizado; los montos traen el IVA incluido. */
	record Adicional(Long id, String concepto, BigDecimal manoDeObra, BigDecimal refacciones, BigDecimal total,
			EstadoAdicional estado, Instant respondidoEn) {
	}
}
