package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoDeEstrenoCreado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoDeReferidoCreado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ReferidoRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.SolicitudEstrenoCreada;
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
 * Prospectos que nacen de la app: al llegar una solicitud de estreno
 * (V2-M16.01) o un amigo referido (V2-M12.04) crea el prospecto con origen APP
 * y lo asigna al vendedor activo con menos prospectos abiertos (empate: el de
 * menor id). También responde el estado de un prospecto para la app
 * (V2-M16.04).
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
		Optional<Long> vendedor = vendedorConMenosCarga();
		if (vendedor.isEmpty()) {
			// Sin vendedores activos la solicitud sigue RECIBIDA; Modulith reintenta
			// las publicaciones pendientes al reiniciar.
			LOG.warn("Solicitud de estreno {} sin vendedor activo", solicitud.solicitudId());
			throw new IllegalStateException("No hay vendedores activos");
		}
		Prospecto prospecto = prospectos.save(Prospecto.registrarDesdeApp(solicitud.nombre(), solicitud.telefono(),
				solicitud.correo(), vendedor.get(), Instant.now(reloj)));
		eventos.publishEvent(new ProspectoDeEstrenoCreado(solicitud.solicitudId(), prospecto.getId()));
	}

	@ApplicationModuleListener
	void alRegistrarseReferido(ReferidoRegistrado referido) {
		Long vendedor = vendedorConMenosCarga().orElseThrow(() -> {
			// Igual que con estreno: Modulith reintenta la publicación pendiente al
			// reiniciar.
			LOG.warn("Referido {} sin vendedor activo", referido.referidoId());
			return new IllegalStateException("No hay vendedores activos");
		});
		Prospecto prospecto = prospectos.save(Prospecto.registrarDesdeApp(referido.nombre(), referido.telefono(),
				referido.correo(), vendedor, Instant.now(reloj)));
		eventos.publishEvent(new ProspectoDeReferidoCreado(referido.referidoId(), prospecto.getId()));
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
