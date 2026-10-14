package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.EstadoSolicitud;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.SolicitudCierre;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.SolicitudCierreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class SolicitudesCierreServiceTest {

	private static final Instant AHORA = Instant.parse("2026-10-13T10:00:00Z");
	private static final String MOTIVO = "El cliente canceló la compra";
	private static final UsuarioAutenticado VENDEDOR = new UsuarioAutenticado(7L, "vendedor1", Set.of(Role.VENTAS));
	private static final UsuarioAutenticado ADMINISTRADOR = new UsuarioAutenticado(1L, "admin",
			Set.of(Role.ADMINISTRADOR));
	private static final UsuarioAutenticado GERENTE = new UsuarioAutenticado(2L, "gerente",
			Set.of(Role.GERENTE_MARKETING));

	@Mock
	private SolicitudCierreRepository solicitudCierreRepository;

	@Mock
	private ProspectoRepository prospectoRepository;

	@Mock
	private DirectorioUsuarios directorioUsuarios;

	private SolicitudesCierreService service;

	@BeforeEach
	void setUp() {
		service = new SolicitudesCierreService(solicitudCierreRepository, prospectoRepository, directorioUsuarios,
				Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	private static Prospecto enPago() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, 7L, AHORA);
		prospecto.moverPorVendedor(Etapa.PAGO, null, 7L, AHORA);
		return prospecto;
	}

	@Test
	void elVendedorSolicitaElCierreDeSuProspecto() {
		Prospecto ana = enPago();
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.of(ana));
		when(solicitudCierreRepository.save(any(SolicitudCierre.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(directorioUsuarios.nombresDe(anyCollection())).thenReturn(Map.of(7L, "Sergio Luna"));

		SolicitudCierreVista solicitud = service.solicitar(1L, MOTIVO, VENDEDOR);

		assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
		assertThat(solicitud.prospectoNombre()).isEqualTo("Ana López");
		assertThat(solicitud.solicitanteNombre()).isEqualTo("Sergio Luna");
		assertThat(ana.isCierrePendiente()).isTrue();
	}

	@Test
	void unVendedorNoSolicitaElCierreDeUnProspectoAjeno() {
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.solicitar(1L, MOTIVO, VENDEDOR))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void soloElVendedorSolicita() {
		assertThatThrownBy(() -> service.solicitar(1L, MOTIVO, GERENTE)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(prospectoRepository);
	}

	@Test
	void elAdministradorApruebaYElProspectoSeCierra() {
		Prospecto ana = enPago();
		SolicitudCierre solicitud = SolicitudCierre.solicitar(ana, MOTIVO, 7L);
		when(solicitudCierreRepository.findById(5L)).thenReturn(Optional.of(solicitud));

		SolicitudCierreVista resuelta = service.aprobar(5L, ADMINISTRADOR);

		assertThat(resuelta.estado()).isEqualTo(EstadoSolicitud.APROBADA);
		assertThat(resuelta.etapa()).isEqualTo(Etapa.CERRADO);
		assertThat(ana.getMotivoCierre()).isEqualTo(MOTIVO);
	}

	@Test
	void elAdministradorRechazaYElProspectoSigueEnSuEtapa() {
		Prospecto ana = enPago();
		when(solicitudCierreRepository.findById(5L))
				.thenReturn(Optional.of(SolicitudCierre.solicitar(ana, MOTIVO, 7L)));

		SolicitudCierreVista resuelta = service.rechazar(5L, ADMINISTRADOR);

		assertThat(resuelta.estado()).isEqualTo(EstadoSolicitud.RECHAZADA);
		assertThat(ana.getEtapa()).isEqualTo(Etapa.PAGO);
	}

	@Test
	void soloElAdministradorResuelve() {
		assertThatThrownBy(() -> service.aprobar(5L, GERENTE)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.pendientes(VENDEDOR)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(solicitudCierreRepository);
	}

	@Test
	void elAdministradorVeLasPendientesConElNombreDelVendedor() {
		when(solicitudCierreRepository.buscarPorEstado(EstadoSolicitud.PENDIENTE))
				.thenReturn(List.of(SolicitudCierre.solicitar(enPago(), MOTIVO, 7L)));
		when(directorioUsuarios.nombresDe(anyCollection())).thenReturn(Map.of(7L, "Sergio Luna"));

		List<SolicitudCierreVista> pendientes = service.pendientes(ADMINISTRADOR);

		assertThat(pendientes).singleElement().satisfies(solicitud -> {
			assertThat(solicitud.motivo()).isEqualTo(MOTIVO);
			assertThat(solicitud.solicitanteNombre()).isEqualTo("Sergio Luna");
			assertThat(solicitud.etapa()).isEqualTo(Etapa.PAGO);
		});
	}
}
