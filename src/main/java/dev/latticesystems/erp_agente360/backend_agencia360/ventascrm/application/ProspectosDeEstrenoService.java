package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoDeEstrenoCreado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoDeValuacionCreado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.SolicitudEstrenoCreada;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ValuacionSolicitada;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaProspectos;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prospectos que nacen de la app (V2-M16.01 y V2-M26.01): al llegar una
 * solicitud de estreno o de valuación crea el prospecto con origen APP y lo asigna al vendedor activo con
 * menos prospectos abiertos (empate: el de menor id). También responde el
 * estado de un prospecto para la app (V2-M16.04).
 */
@Service
class ProspectosDeEstrenoService implements ConsultaProspectos {

	private static final Logger LOG = LoggerFactory.getLogger(ProspectosDeEstrenoService.class);
	private static final Set<Etapa> TERMINADAS = Set.of(Etapa.FINALIZADO, Etapa.CERRADO);

	private final ProspectoRepository prospectos;
	private final ConsultaVendedores vendedores;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj = Clock.systemUTC();

	ProspectosDeEstrenoService(ProspectoRepository prospectos, ConsultaVendedores vendedores,
			ApplicationEventPublisher eventos) {
		this.prospectos = prospectos;
		this.vendedores = vendedores;
		this.eventos = eventos;
	}

	@ApplicationModuleListener
	void alCrearseSolicitud(SolicitudEstrenoCreada solicitud) {
		Prospecto prospecto = crearDesdeApp(solicitud.nombre(), solicitud.telefono(), solicitud.correo(),
				"Solicitud de estreno " + solicitud.solicitudId());
		eventos.publishEvent(new ProspectoDeEstrenoCreado(solicitud.solicitudId(), prospecto.getId()));
	}

	/** V2-M26.01: quien pide una valuación y no tiene un prospecto abierto. */
	@ApplicationModuleListener
	void alPedirseValuacion(ValuacionSolicitada valuacion) {
		Prospecto prospecto = crearDesdeApp(valuacion.nombre(), valuacion.telefono(), valuacion.correo(),
				"Valuación " + valuacion.valuacionId());
		eventos.publishEvent(new ProspectoDeValuacionCreado(valuacion.valuacionId(), prospecto.getId()));
	}

	private Prospecto crearDesdeApp(String nombre, String telefono, String correo, String origen) {
		Optional<Long> vendedor = vendedorConMenosCarga();
		if (vendedor.isEmpty()) {
			// Sin vendedores activos queda sin prospecto; Modulith reintenta las
			// publicaciones pendientes al reiniciar.
			LOG.warn("{} sin vendedor activo", origen);
			throw new IllegalStateException("No hay vendedores activos");
		}
		return prospectos.save(
				Prospecto.registrarDesdeApp(nombre, telefono, correo, vendedor.get(), Instant.now(reloj)));
	}

	private Optional<Long> vendedorConMenosCarga() {
		List<Long> activos = vendedores.vendedoresActivos();
		return activos.stream()
				.min(Comparator.<Long>comparingLong(id -> prospectos.countByVendedorIdAndEtapaNotIn(id, TERMINADAS))
						.thenComparing(Comparator.naturalOrder()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EstadoProspecto> estado(Long prospectoId) {
		return prospectos.findById(prospectoId).map(
				p -> new EstadoProspecto(p.getEtapa().name(), TERMINADAS.contains(p.getEtapa()), p.getVendedorId()));
	}
}
