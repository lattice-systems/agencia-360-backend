package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DatosFiscales;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PerfilFiscal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PerfilFiscalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CamposInvalidosException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reglas del predeterminado y de propiedad de los datos fiscales (V2-M08.05).
 */
class PerfilesFiscalesAppServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T18:00:00Z");
	private static final long CLIENTE = 7L;

	private final PerfilFiscalRepository perfiles = mock(PerfilFiscalRepository.class);
	private final List<PerfilFiscal> guardados = new ArrayList<>();
	private PerfilesFiscalesAppService servicio;

	@BeforeEach
	void preparar() {
		servicio = new PerfilesFiscalesAppService(perfiles, Clock.fixed(AHORA, ZoneOffset.UTC));
		when(perfiles.findByClienteIdOrderByPredeterminadoDescIdAsc(CLIENTE)).thenAnswer(invocacion -> guardados
				.stream().sorted((a, b) -> Boolean.compare(b.isPredeterminado(), a.isPredeterminado())).toList());
		when(perfiles.save(any(PerfilFiscal.class))).thenAnswer(invocacion -> {
			PerfilFiscal perfil = invocacion.getArgument(0);
			if (perfil.getId() == null) {
				ReflectionTestUtils.setField(perfil, "id", 100L + guardados.size());
				guardados.add(perfil);
			}
			return perfil;
		});
	}

	@Test
	void elPrimeroEsElPredeterminadoAunqueNoLoPida() {
		PerfilFiscalApp primero = servicio.crear(CLIENTE, entrada("GODE561231GR8", null));

		assertThat(primero.predeterminado()).isTrue();
		assertThat(primero.razonSocial()).isEqualTo("DOLORES GÓMEZ");
		assertThat(primero.creadoEn()).isEqualTo(AHORA);
	}

	@Test
	void elSegundoNoLoEsSalvoQueLoPida() {
		servicio.crear(CLIENTE, entrada("GODE561231GR8", null));
		PerfilFiscalApp segundo = servicio.crear(CLIENTE, entrada("EKU9003173C9", false));
		PerfilFiscalApp tercero = servicio.crear(CLIENTE, entrada("MUÑO900516NL7", true));

		assertThat(segundo.predeterminado()).isFalse();
		assertThat(tercero.predeterminado()).isTrue();
		assertThat(guardados).filteredOn(PerfilFiscal::isPredeterminado).extracting(PerfilFiscal::getRfc)
				.containsExactly("MUÑO900516NL7");
	}

	@Test
	void quitaElPredeterminadoAnteriorAntesDeGuardarElNuevo() {
		PerfilFiscal anterior = existente("GODE561231GR8", true);
		servicio.crear(CLIENTE, entrada("EKU9003173C9", true));

		InOrder orden = inOrder(perfiles);
		orden.verify(perfiles).bloquearDelCliente(PerfilesFiscalesAppService.PREFIJO_BLOQUEO | CLIENTE);
		orden.verify(perfiles).saveAndFlush(anterior);
		orden.verify(perfiles).save(any(PerfilFiscal.class));
		assertThat(anterior.isPredeterminado()).isFalse();
	}

	@Test
	void editarCorrigeYPuedeVolverloPredeterminado() {
		PerfilFiscal predeterminado = existente("GODE561231GR8", true);
		PerfilFiscal otro = existente("EKU9003173C9", false);
		when(perfiles.findByIdAndClienteId(otro.getId(), CLIENTE)).thenReturn(Optional.of(otro));

		PerfilFiscalApp editado = servicio.editar(CLIENTE, otro.getId(),
				new EntradaPerfilFiscal("EKU9003173C9", "Escuela Kemper", "601", "42501", "I03", null, true));

		assertThat(editado.usoCfdi()).isEqualTo("I03");
		assertThat(editado.predeterminado()).isTrue();
		assertThat(predeterminado.isPredeterminado()).isFalse();
	}

	@Test
	void editarConPredeterminadoEnFalsoNoDejaSinPredeterminado() {
		PerfilFiscal unico = existente("GODE561231GR8", true);
		when(perfiles.findByIdAndClienteId(unico.getId(), CLIENTE)).thenReturn(Optional.of(unico));

		servicio.editar(CLIENTE, unico.getId(), entrada("GODE561231GR8", false));

		assertThat(unico.isPredeterminado()).isTrue();
	}

	@Test
	void alBorrarElPredeterminadoLoTomaElMasViejoQueQueda() {
		PerfilFiscal borrado = existente("GODE561231GR8", true);
		PerfilFiscal viejo = existente("EKU9003173C9", false);
		existente("MUÑO900516NL7", false);
		when(perfiles.findByIdAndClienteId(borrado.getId(), CLIENTE)).thenReturn(Optional.of(borrado));
		doAnswer(invocacion -> guardados.remove(borrado)).when(perfiles).delete(borrado);

		servicio.borrar(CLIENTE, borrado.getId());

		assertThat(viejo.isPredeterminado()).isTrue();
		assertThat(guardados).filteredOn(PerfilFiscal::isPredeterminado).hasSize(1);
	}

	@Test
	void unPerfilAjenoNoExiste() {
		when(perfiles.findByIdAndClienteId(55L, CLIENTE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.uno(CLIENTE, 55L)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.borrar(CLIENTE, 55L)).isInstanceOf(RecursoNoEncontradoException.class);
		verify(perfiles, never()).delete(any(PerfilFiscal.class));
	}

	@Test
	void datosQueNoSirvenNoBloqueanNiGuardan() {
		assertThatThrownBy(() -> servicio.crear(CLIENTE, entrada("GODE561231GR9", null)))
				.isInstanceOf(CamposInvalidosException.class);

		verify(perfiles, never()).bloquearDelCliente(anyLong());
		verify(perfiles, never()).save(any(PerfilFiscal.class));
	}

	private static EntradaPerfilFiscal entrada(String rfc, Boolean predeterminado) {
		boolean moral = rfc.length() == 12;
		return new EntradaPerfilFiscal(rfc, moral ? "Escuela Kemper Urgate" : "Dolores Gómez", moral ? "601" : "612",
				"76000", "G03", null, predeterminado);
	}

	private PerfilFiscal existente(String rfc, boolean predeterminado) {
		EntradaPerfilFiscal datos = entrada(rfc, null);
		PerfilFiscal perfil = new PerfilFiscal(CLIENTE, DatosFiscales.validar(datos.rfc(), datos.razonSocial(),
				datos.regimenFiscal(), datos.codigoPostal(), datos.usoCfdi(), null), AHORA);
		perfil.marcarPredeterminado(predeterminado);
		ReflectionTestUtils.setField(perfil, "id", 100L + guardados.size());
		guardados.add(perfil);
		return perfil;
	}
}
