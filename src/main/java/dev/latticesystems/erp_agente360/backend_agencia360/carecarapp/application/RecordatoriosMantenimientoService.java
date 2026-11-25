package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.RecordatorioMantenimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.LecturaKmRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.RecordatorioMantenimientoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recordatorio del próximo servicio (V2-M05.05). Cada día revisa los vehículos
 * con lecturas de kilometraje y, si el próximo servicio está a
 * {@code app.mantenimiento.recordatorio.km} km o
 * {@code app.mantenimiento.recordatorio.dias} días, publica un
 * {@link AvisoAlCliente} {@code mantenimiento.recordatorio}. Lo recoge el canal
 * en vivo (SSE) y, cuando exista, el push de V2-M06.07, que solo tiene que
 * escuchar el mismo evento. Un servicio se avisa una sola vez por vehículo.
 */
@Service
public class RecordatoriosMantenimientoService {

	static final String EVENTO = "mantenimiento.recordatorio";

	private static final Logger LOG = LoggerFactory.getLogger(RecordatoriosMantenimientoService.class);

	private final LecturaKmRepository lecturas;
	private final VehiculosDeClientes vehiculos;
	private final MantenimientoAppService mantenimiento;
	private final RecordatorioMantenimientoRepository recordatorios;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;
	private final PoliticaRecordatorioMantenimiento politica;

	@Autowired
	public RecordatoriosMantenimientoService(LecturaKmRepository lecturas, VehiculosDeClientes vehiculos,
			MantenimientoAppService mantenimiento, RecordatorioMantenimientoRepository recordatorios,
			ApplicationEventPublisher eventos, @Value("${app.mantenimiento.recordatorio.km:1000}") int kmAntes,
			@Value("${app.mantenimiento.recordatorio.dias:14}") int diasAntes) {
		this(lecturas, vehiculos, mantenimiento, recordatorios, eventos, Clock.systemUTC(),
				new PoliticaRecordatorioMantenimiento(kmAntes, diasAntes));
	}

	RecordatoriosMantenimientoService(LecturaKmRepository lecturas, VehiculosDeClientes vehiculos,
			MantenimientoAppService mantenimiento, RecordatorioMantenimientoRepository recordatorios,
			ApplicationEventPublisher eventos, Clock reloj, PoliticaRecordatorioMantenimiento politica) {
		this.lecturas = lecturas;
		this.vehiculos = vehiculos;
		this.mantenimiento = mantenimiento;
		this.recordatorios = recordatorios;
		this.eventos = eventos;
		this.reloj = reloj;
		this.politica = politica;
	}

	/** Todos los días a las 9:00 (hora de México). */
	@Scheduled(cron = "${app.mantenimiento.recordatorio.cron:0 0 9 * * *}", zone = "America/Mexico_City")
	public void revisarTodos() {
		int enviados = 0;
		for (Long vehiculoId : lecturas.vehiculosConLecturas()) {
			try {
				if (revisar(vehiculoId)) {
					enviados++;
				}
			} catch (RuntimeException fallo) {
				// Un vehículo con datos raros no debe frenar a los demás.
				LOG.warn("No se pudo revisar el recordatorio del vehículo {}", vehiculoId, fallo);
			}
		}
		LOG.info("Recordatorios de mantenimiento enviados: {}", enviados);
	}

	/**
	 * @return {@code true} si se avisó al cliente de ese vehículo
	 */
	@Transactional
	public boolean revisar(Long vehiculoId) {
		Optional<VehiculoDeCliente> vehiculo = vehiculos.buscarPorId(vehiculoId);
		if (vehiculo.isEmpty()) {
			return false;
		}
		Long clienteId = vehiculo.get().propietario().id();
		ProximoServicioApp proximo = mantenimiento.plan(clienteId, vehiculoId).proximo();
		Optional<String> motivo = politica.motivo(proximo);
		if (motivo.isEmpty()
				|| recordatorios.existsByVehiculoClienteIdAndPlanMantenimientoId(vehiculoId, proximo.servicioId())) {
			return false;
		}
		recordatorios.saveAndFlush(
				new RecordatorioMantenimiento(vehiculoId, proximo.servicioId(), motivo.get(), reloj.instant()));
		String razon = motivo.get();
		Map<String, Object> datos = Map.of("vehiculoId", vehiculoId, "servicioId", proximo.servicioId(), "nombre",
				proximo.nombre(), "faltanKm", proximo.faltanKm(), "faltanDias", proximo.faltanDias(), "motivo", razon);
		eventos.publishEvent(new AvisoAlCliente(clienteId, EVENTO, datos));
		return true;
	}
}
