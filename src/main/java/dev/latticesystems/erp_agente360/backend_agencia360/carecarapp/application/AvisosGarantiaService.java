package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.AvisoGarantia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Garantia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.GarantiaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aviso de garantía por vencer (V2-M10.05). Cada día revisa las coberturas
 * vigentes y, a 60 y 15 días de la fecha límite o a 1 000 km del km límite,
 * publica un {@link AvisoAlCliente} {@code garantia.por_vencer}. Lo recoge el
 * canal en vivo (SSE) y, cuando exista, el push de V2-M06.07, que solo tiene
 * que escuchar el mismo evento. Cada aviso se manda una sola vez.
 */
@Service
public class AvisosGarantiaService {

	static final String EVENTO = "garantia.por_vencer";

	private static final Logger LOG = LoggerFactory.getLogger(AvisosGarantiaService.class);

	private final GarantiaRepository garantias;
	private final VehiculosDeClientes vehiculos;
	private final KilometrajeService kilometraje;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;
	private final ZoneId zona;

	@Autowired
	public AvisosGarantiaService(GarantiaRepository garantias, VehiculosDeClientes vehiculos,
			KilometrajeService kilometraje, ApplicationEventPublisher eventos) {
		this(garantias, vehiculos, kilometraje, eventos, Clock.systemUTC(), ServicioApp.ZONA_AGENCIA);
	}

	AvisosGarantiaService(GarantiaRepository garantias, VehiculosDeClientes vehiculos, KilometrajeService kilometraje,
			ApplicationEventPublisher eventos, Clock reloj, ZoneId zona) {
		this.garantias = garantias;
		this.vehiculos = vehiculos;
		this.kilometraje = kilometraje;
		this.eventos = eventos;
		this.reloj = reloj;
		this.zona = zona;
	}

	/** Todos los días a las 9:30 (hora de México). */
	@Scheduled(cron = "${app.garantia.aviso.cron:0 30 9 * * *}", zone = "America/Mexico_City")
	public void revisarTodas() {
		int enviados = 0;
		for (Garantia garantia : garantias.vigentes(hoy())) {
			try {
				if (revisar(garantia.getId())) {
					enviados++;
				}
			} catch (RuntimeException fallo) {
				LOG.warn("No se pudo revisar la garantía {}", garantia.getId(), fallo);
			}
		}
		LOG.info("Avisos de garantía por vencer enviados: {}", enviados);
	}

	/**
	 * @return {@code true} si se avisó al cliente de esa cobertura
	 */
	@Transactional
	public boolean revisar(Long garantiaId) {
		Optional<Garantia> encontrada = garantias.findById(garantiaId);
		if (encontrada.isEmpty()) {
			return false;
		}
		Garantia garantia = encontrada.get();
		Optional<VehiculoDeCliente> vehiculo = vehiculos.buscarPorId(garantia.getVehiculoClienteId());
		if (vehiculo.isEmpty()) {
			return false;
		}
		Integer km = kilometraje.estado(vehiculo.get()).kmActual();
		LocalDate hoy = hoy();
		Optional<AvisoGarantia> aviso = PoliticaGarantia.avisoPendiente(garantia, km, hoy);
		if (aviso.isEmpty()) {
			return false;
		}
		garantia.marcarAvisado(aviso.get(), reloj.instant());
		garantias.saveAndFlush(garantia);
		CoberturaApp cobertura = PoliticaGarantia.evaluar(garantia, km, hoy);
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("vehiculoId", garantia.getVehiculoClienteId());
		datos.put("garantiaId", garantia.getId());
		datos.put("componente", garantia.getComponente());
		datos.put("venceEn", garantia.getVenceEn().toString());
		datos.put("diasRestantes", cobertura.diasRestantes());
		if (cobertura.kmRestantes() != null) {
			datos.put("kmRestantes", cobertura.kmRestantes());
		}
		datos.put("motivo", aviso.get().name().toLowerCase(Locale.ROOT));
		eventos.publishEvent(new AvisoAlCliente(vehiculo.get().propietario().id(), EVENTO, datos));
		return true;
	}

	private LocalDate hoy() {
		return LocalDate.now(reloj.withZone(zona));
	}
}
