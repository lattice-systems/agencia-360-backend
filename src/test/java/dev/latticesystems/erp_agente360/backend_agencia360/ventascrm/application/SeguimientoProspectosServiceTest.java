package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.OrigenCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
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
class SeguimientoProspectosServiceTest {

	private static final Instant REGISTRO = Instant.parse("2026-10-12T18:00:00Z");
	private static final Instant AHORA = Instant.parse("2026-10-13T10:30:00Z");

	private static final UsuarioAutenticado VENDEDOR = new UsuarioAutenticado(7L, "vendedor1", Set.of(Role.VENTAS));
	private static final UsuarioAutenticado GERENTE = new UsuarioAutenticado(2L, "gerente1",
			Set.of(Role.GERENTE_MARKETING));
	private static final UsuarioAutenticado CONTABILIDAD = new UsuarioAutenticado(3L, "conta1",
			Set.of(Role.CONTABILIDAD));

	private static final UsuarioAutenticado CONTADOR = new UsuarioAutenticado(30L, "contabilidad1",
			Set.of(Role.CONTABILIDAD));
	private static final UsuarioAutenticado FACTURISTA = new UsuarioAutenticado(40L, "facturacion1",
			Set.of(Role.FACTURACION));

	@Mock
	private ProspectoRepository prospectoRepository;

	@Mock
	private DirectorioUsuarios directorioUsuarios;

	private SeguimientoProspectosService service;

	@BeforeEach
	void setUp() {
		service = new SeguimientoProspectosService(prospectoRepository, directorioUsuarios,
				Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	void unVendedorSoloRecibeSusProspectos() {
		when(prospectoRepository.findByVendedorIdOrderByCreatedAtDescIdDesc(7L))
				.thenReturn(List.of(prospecto("Ana López", 7L)));

		List<ProspectoVista> lista = service.listar(null, VENDEDOR);

		assertThat(lista).extracting(ProspectoVista::nombre).containsExactly("Ana López");
	}

	@Test
	void unVendedorFiltraSusProspectosPorEtapa() {
		when(prospectoRepository.findByVendedorIdAndEtapaOrderByCreatedAtDescIdDesc(7L, Etapa.VISITA))
				.thenReturn(List.of());

		assertThat(service.listar(Etapa.VISITA, VENDEDOR)).isEmpty();
	}

	@Test
	void elGerenteDeMarketingRecibeTodos() {
		when(prospectoRepository.findAllByOrderByCreatedAtDescIdDesc())
				.thenReturn(List.of(prospecto("Ana López", 7L), prospecto("Luis Pérez", 8L)));

		assertThat(service.listar(null, GERENTE)).hasSize(2);
	}

	@Test
	void elGerenteDeMarketingFiltraTodosPorEtapa() {
		when(prospectoRepository.findByEtapaOrderByCreatedAtDescIdDesc(Etapa.PRIMER_CONTACTO))
				.thenReturn(List.of(prospecto("Ana López", 7L)));

		assertThat(service.listar(Etapa.PRIMER_CONTACTO, GERENTE)).hasSize(1);
	}

	@Test
	void unRolSinFuncionEnElCrmNoVeProspectos() {
		assertThatThrownBy(() -> service.listar(null, CONTABILIDAD)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(prospectoRepository);
	}

	@Test
	void unVendedorCambiaLaEtapaDeSuProspecto() {
		Prospecto ana = prospecto("Ana López", 7L);
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.of(ana));

		ProspectoVista actualizado = service.cambiarEtapa(1L, Etapa.VISITA, null, VENDEDOR);

		assertThat(actualizado.etapa()).isEqualTo(Etapa.VISITA);
		assertThat(ana.getHistorial()).last().satisfies(movimiento -> {
			assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.CRM);
			assertThat(movimiento.getFechaHora()).isEqualTo(AHORA);
		});
	}

	@Test
	void unProspectoAjenoSeTrataComoInexistente() {
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.cambiarEtapa(1L, Etapa.VISITA, null, VENDEDOR))
				.isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe el prospecto 1");
	}

	@Test
	void unVendedorCierraSuProspectoConMotivo() {
		Prospecto ana = prospecto("Ana López", 7L);
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.of(ana));

		ProspectoVista cerrado = service.cambiarEtapa(1L, Etapa.CERRADO, "Compró en otra agencia", VENDEDOR);

		assertThat(cerrado.etapa()).isEqualTo(Etapa.CERRADO);
		assertThat(cerrado.motivoCierre()).isEqualTo("Compró en otra agencia");
		assertThat(cerrado.ultimaEtapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
	}

	@Test
	void elGerenteDeMarketingNoCambiaEtapasAunqueVeaTodo() {
		assertThatThrownBy(() -> service.cambiarEtapa(1L, Etapa.VISITA, null, GERENTE))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(prospectoRepository);
	}

	@Test
	void unVendedorNoPuedeMoverUnProspectoEnPago() {
		Prospecto ana = prospecto("Ana López", 7L);
		ana.moverPorVendedor(Etapa.PAGO, null, 7L, REGISTRO);
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.of(ana));

		assertThatThrownBy(() -> service.cambiarEtapa(1L, Etapa.FACTURACION, null, VENDEDOR))
				.isInstanceOf(ConflictoException.class);
	}

	@Test
	void contabilidadPasaDePagoAFacturacion() {
		Prospecto ana = prospecto("Ana López", 7L);
		ana.moverPorVendedor(Etapa.PAGO, null, 7L, REGISTRO);
		when(prospectoRepository.findById(1L)).thenReturn(Optional.of(ana));

		ProspectoVista actualizado = service.cambiarEtapa(1L, Etapa.FACTURACION, null, CONTADOR);

		assertThat(actualizado.etapa()).isEqualTo(Etapa.FACTURACION);
		assertThat(actualizado.ultimoCambio()).isEqualTo(AHORA);
	}

	@Test
	void contabilidadSoloPuedePasarAFacturacion() {
		assertThatThrownBy(() -> service.cambiarEtapa(1L, Etapa.ENTREGA, null, CONTADOR))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(prospectoRepository);
	}

	@Test
	void facturacionPasaDeFacturacionAEntrega() {
		Prospecto ana = prospecto("Ana López", 7L);
		ana.moverPorVendedor(Etapa.PAGO, null, 7L, REGISTRO);
		ana.validarPago(30L, REGISTRO);
		when(prospectoRepository.findById(1L)).thenReturn(Optional.of(ana));

		assertThat(service.cambiarEtapa(1L, Etapa.ENTREGA, null, FACTURISTA).etapa()).isEqualTo(Etapa.ENTREGA);
	}

	@Test
	void elHistorialMuestraQuienHizoCadaCambio() {
		Prospecto ana = prospecto("Ana López", 7L);
		ana.moverPorVendedor(Etapa.VISITA, null, 7L, AHORA);
		when(prospectoRepository.findById(1L)).thenReturn(Optional.of(ana));
		when(directorioUsuarios.nombresDe(List.of(7L, 7L))).thenReturn(Map.of(7L, "Sergio Luna"));

		List<MovimientoVista> historial = service.historial(1L, GERENTE);

		assertThat(historial).extracting(MovimientoVista::etapa).containsExactly(Etapa.PRIMER_CONTACTO, Etapa.VISITA);
		assertThat(historial).extracting(MovimientoVista::usuarioNombre).containsOnly("Sergio Luna");
	}

	@Test
	void unVendedorNoVeElHistorialDeUnProspectoAjeno() {
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.historial(1L, VENDEDOR)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	private static Prospecto prospecto(String nombre, Long vendedorId) {
		return Prospecto.registrar(nombre, "47700000%02d".formatted(vendedorId), null, vendedorId, REGISTRO);
	}
}
