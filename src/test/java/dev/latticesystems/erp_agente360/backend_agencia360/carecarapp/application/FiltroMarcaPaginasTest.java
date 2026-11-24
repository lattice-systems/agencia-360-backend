package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiltroMarcaPaginasTest {

	@Test
	void sacaLasPaginasDeLaUltimaLineaYNuncaLasMuestra() {
		List<String> mostrado = new ArrayList<>();
		FiltroMarcaPaginas filtro = new FiltroMarcaPaginas(mostrado::add);

		for (String trozo : List.of("Cada 10 000 ", "km o un año.\nRevisa el ", "nivel.\nPAGI", "NAS: 2, 7")) {
			filtro.agregar(trozo);
		}
		FiltroMarcaPaginas.Resultado fin = filtro.terminar();

		assertThat(String.join("", mostrado)).isEqualTo("Cada 10 000 km o un año.\nRevisa el nivel.");
		assertThat(fin.texto()).isEqualTo("Cada 10 000 km o un año.\nRevisa el nivel.");
		assertThat(fin.paginas()).containsExactly(2, 7);
		assertThat(fin.encontrado()).isTrue();
	}

	@Test
	void paginasNingunaEsNoEncontrado() {
		FiltroMarcaPaginas filtro = new FiltroMarcaPaginas(t -> {
		});
		filtro.agregar("No lo encontré en tu manual.\nPAGINAS: ninguna");
		FiltroMarcaPaginas.Resultado fin = filtro.terminar();
		assertThat(fin.texto()).isEqualTo("No lo encontré en tu manual.");
		assertThat(fin.encontrado()).isFalse();
		assertThat(fin.paginas()).isEmpty();
	}

	@Test
	void sinMarcaSeMuestraTodoYNoCita() {
		List<String> mostrado = new ArrayList<>();
		FiltroMarcaPaginas filtro = new FiltroMarcaPaginas(mostrado::add);
		filtro.agregar("Respuesta sin marca");
		FiltroMarcaPaginas.Resultado fin = filtro.terminar();
		assertThat(String.join("", mostrado)).isEqualTo("Respuesta sin marca");
		assertThat(fin.encontrado()).isTrue();
		assertThat(fin.paginas()).isEmpty();
	}

	@Test
	void loMostradoHastaAhoraSirveParaElRegistroParcial() {
		FiltroMarcaPaginas filtro = new FiltroMarcaPaginas(t -> {
		});
		filtro.agregar("Primera línea.\nSegunda a medi");
		assertThat(filtro.mostrado()).isEqualTo("Primera línea.");
	}
}
