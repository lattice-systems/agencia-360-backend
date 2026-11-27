package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.PushApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.ResultadoApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ActividadEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ActividadEnVivoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstadoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.MotivoCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.OrdenEnVivo;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mantiene la Live Activity del taller en la pantalla bloqueada (V2-M25.05):
 * cuando la orden cambia de etapa, recibe un trabajo adicional o se responde
 * uno, manda por APNs el estado nuevo a cada actividad registrada, y al
 * entregar el auto la termina ({@code event: end}). Escucha el mismo
 * {@link OrdenServicioCambiada} que el SSE de la app, después de confirmar la
 * transacción de Servicio, y nunca la rompe; sin credenciales de APNs o sin el
 * identificador de la app no hace nada.
 *
 * <p>
 * El estado viaja como {@code content-state} y la app lo decodifica con un
 * {@code Codable}: {@code etapa}, {@code estatus}, {@code avance},
 * {@code adicionalesPendientes} y {@code entregaEstimada} (segundos desde 1970,
 * omitida si no hay). Las fechas van como número a propósito: ActivityKit
 * decodifica con un {@code JSONDecoder} por omisión y el formato de fecha por
 * omisión de Swift no es el ISO.
 */
@Component
public class ActualizadorDeActividadesEnVivo {

	private static final Set<MotivoCambio> MOTIVOS_QUE_MUEVEN_LA_PANTALLA = Set.of(MotivoCambio.APERTURA,
			MotivoCambio.ETAPA, MotivoCambio.ADICIONAL, MotivoCambio.RESPUESTA);
	private static final Duration PERMANENCIA_AL_ENTREGAR = Duration.ofMinutes(15);
	private static final Logger LOG = LoggerFactory.getLogger(ActualizadorDeActividadesEnVivo.class);
	private static final Map<EtapaTaller, String> ETIQUETAS = etiquetas();

	private final ConfiguracionApns configuracion;
	private final EnvioApns apns;
	private final ActividadEnVivoRepository actividades;
	private final TallerEnVivo taller;
	private final TransactionTemplate transaccionNueva;
	private final Clock reloj;
	private final ObjectMapper json = new ObjectMapper();

	@Autowired
	ActualizadorDeActividadesEnVivo(ConfiguracionApns configuracion, EnvioApns apns,
			ActividadEnVivoRepository actividades, TallerEnVivo taller, PlatformTransactionManager transacciones) {
		this(configuracion, apns, actividades, taller, transacciones, Clock.systemUTC());
	}

	ActualizadorDeActividadesEnVivo(ConfiguracionApns configuracion, EnvioApns apns,
			ActividadEnVivoRepository actividades, TallerEnVivo taller, PlatformTransactionManager transacciones,
			Clock reloj) {
		this.configuracion = configuracion;
		this.apns = apns;
		this.actividades = actividades;
		this.taller = taller;
		this.reloj = reloj;
		this.transaccionNueva = new TransactionTemplate(transacciones);
		this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@TransactionalEventListener(fallbackExecution = true)
	public void alCambiarLaOrden(OrdenServicioCambiada cambio) {
		if (!configuracion.actividadesConfiguradas() || !apns.disponible() || cambio.motivo() == null
				|| !MOTIVOS_QUE_MUEVEN_LA_PANTALLA.contains(cambio.motivo())) {
			return;
		}
		try {
			List<ActividadEnVivo> registradas = actividades.findByOrdenServicioIdAndActivaTrue(cambio.ordenId());
			if (registradas.isEmpty()) {
				return;
			}
			Optional<OrdenEnVivo> orden = taller.orden(cambio.ordenId());
			if (orden.isEmpty() || orden.get().etapa() == null) {
				return;
			}
			boolean entregada = orden.get().estatus() == EstatusOrden.ENTREGADO;
			String carga = carga(orden.get(), entregada, cambio.motivo() == MotivoCambio.ETAPA);
			for (ActividadEnVivo actividad : registradas) {
				ResultadoApns resultado = apns.enviar(PushApns.deUnaActividad(actividad.getPushToken(),
						configuracion.bundleId(), configuracion.sandbox(), carga));
				if (entregada || resultado.tokenMuerto()) {
					transaccionNueva.executeWithoutResult(estado -> {
						actividad.terminar();
						actividades.save(actividad);
					});
				}
			}
		} catch (RuntimeException | JsonProcessingException fallo) {
			LOG.warn("No se pudo actualizar la Live Activity de la orden {}", cambio.ordenId(), fallo);
		}
	}

	private String carga(OrdenEnVivo orden, boolean terminar, boolean alertar) throws JsonProcessingException {
		long ahora = reloj.instant().getEpochSecond();
		Map<String, Object> estado = new LinkedHashMap<>();
		estado.put("etapa", orden.etapa().name());
		estado.put("estatus", orden.estatus().name());
		estado.put("avance", orden.avance());
		estado.put("adicionalesPendientes", orden.adicionales().stream()
				.filter(adicional -> adicional.estado() == EstadoAdicional.PENDIENTE).count());
		if (orden.entregaEstimada() != null) {
			estado.put("entregaEstimada", orden.entregaEstimada().getEpochSecond());
		}
		Map<String, Object> aps = new LinkedHashMap<>();
		aps.put("timestamp", ahora);
		aps.put("event", terminar ? "end" : "update");
		aps.put("content-state", estado);
		if (terminar) {
			aps.put("dismissal-date", ahora + PERMANENCIA_AL_ENTREGAR.toSeconds());
		}
		if (alertar && !terminar) {
			aps.put("alert", Map.of("title", "Tu auto en CareCar", "body", ETIQUETAS.get(orden.etapa())));
		}
		return json.writeValueAsString(Map.of("aps", aps));
	}

	private static Map<EtapaTaller, String> etiquetas() {
		Map<EtapaTaller, String> etiquetas = new EnumMap<>(EtapaTaller.class);
		etiquetas.put(EtapaTaller.RECIBIDO, "Recibido en el taller");
		etiquetas.put(EtapaTaller.DIAGNOSTICO, "En diagnóstico");
		etiquetas.put(EtapaTaller.ESPERANDO_APROBACION, "Esperando tu aprobación");
		etiquetas.put(EtapaTaller.EN_REPARACION, "En reparación");
		etiquetas.put(EtapaTaller.CONTROL_DE_CALIDAD, "En control de calidad");
		etiquetas.put(EtapaTaller.LAVADO, "En lavado");
		etiquetas.put(EtapaTaller.LISTO_PARA_ENTREGA, "Listo para entrega");
		etiquetas.put(EtapaTaller.ENTREGADO, "Entregado");
		return etiquetas;
	}
}
