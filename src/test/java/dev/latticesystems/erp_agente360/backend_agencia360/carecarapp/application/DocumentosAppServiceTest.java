package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DocumentoVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.OrigenDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.DocumentoVehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.Propietario;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentosAppServiceTest {

	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

	private final VehiculosDeClientes vehiculos = mock(VehiculosDeClientes.class);
	private final DocumentoVehiculoRepository documentos = mock(DocumentoVehiculoRepository.class);

	private DocumentosAppService servicioA(String instante) {
		return new DocumentosAppService(vehiculos, documentos, Clock.fixed(Instant.parse(instante), ZONA));
	}

	private void vehiculoDe(Long vehiculoId, Long clienteId) {
		when(vehiculos.buscarPorId(vehiculoId)).thenReturn(
				Optional.of(new VehiculoDeCliente(vehiculoId, "3N1AB8CV5PY123456", "Nissan", "Frontier", 2026, "PRO-4X",
						"GTO-501-S", 3400, "Azul", new Propietario(clienteId, "Diego", "diego@correo.com", null))));
	}

	@Test
	void calculaElEstadoConLaFechaDeLaAgenciaNoLaDelServidor() {
		vehiculoDe(7L, 1L);
		when(documentos.delVehiculo(7L)).thenReturn(List.of(new DocumentoVehiculo(7L, TipoDocumento.VERIFICACION, null,
				null, null, LocalDate.of(2026, 11, 27), OrigenDocumento.CLIENTE, null, null)));

		// 03:00 UTC del 28 todavía es 27 de noviembre 21:00 en la Ciudad de México:
		// vence hoy.
		List<DocumentoApp> lista = servicioA("2026-11-28T03:00:00Z").listar(1L, 7L);

		assertThat(lista).singleElement().satisfies(documento -> {
			assertThat(documento.estado()).isEqualTo(EstadoDocumento.POR_VENCER);
			assertThat(documento.diasRestantes()).isZero();
			assertThat(documento.tieneArchivo()).isFalse();
		});
	}

	@Test
	void devuelveListaVaciaSiNoHayDocumentos() {
		vehiculoDe(7L, 1L);
		when(documentos.delVehiculo(7L)).thenReturn(List.of());

		assertThat(servicioA("2026-11-27T18:00:00Z").listar(1L, 7L)).isEmpty();
	}

	@Test
	void unVehiculoAjenoEsComoSiNoExistiera() {
		vehiculoDe(7L, 2L);

		assertThatThrownBy(() -> servicioA("2026-11-27T18:00:00Z").listar(1L, 7L))
				.isInstanceOf(RecursoNoEncontradoException.class);
		verifyNoInteractions(documentos);
	}

	@Test
	void unVehiculoQueNoExisteResponde404() {
		when(vehiculos.buscarPorId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicioA("2026-11-27T18:00:00Z").listar(1L, 99L))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void laPolizaTrajeSuAseguradoraYTelefono() {
		vehiculoDe(7L, 1L);
		when(documentos.delVehiculo(7L))
				.thenReturn(List.of(new DocumentoVehiculo(7L, TipoDocumento.POLIZA, null, "POL-1", null,
						LocalDate.of(2027, 5, 1), OrigenDocumento.CLIENTE, "Seguros Ejemplo", "+52 800 000 1234")));

		DocumentoApp poliza = servicioA("2026-11-27T18:00:00Z").listar(1L, 7L).get(0);

		assertThat(poliza.aseguradora()).isEqualTo("Seguros Ejemplo");
		assertThat(poliza.telefonoAseguradora()).isEqualTo("+52 800 000 1234");
		assertThat(poliza.estado()).isEqualTo(EstadoDocumento.VIGENTE);
	}
}
