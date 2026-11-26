package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.ModeloCompatible;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CategoriaTienda;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefaccionTiendaTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T12:00:00Z");

	private final Refaccion refaccion = Refaccion.registrar("TAP-1", "Tapetes", BigDecimal.ONE, BigDecimal.TEN, 0);

	private static PublicacionTienda publicacion(CategoriaTienda categoria, String foto, boolean universal,
			ModeloCompatible... modelos) {
		return new PublicacionTienda(true, categoria, null, foto, universal, List.of(modelos));
	}

	@Test
	void paraPublicarExigeCategoriaCompatibilidadYFotoSegura() {
		assertThatThrownBy(() -> refaccion.publicar(publicacion(null, null, true)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> refaccion.publicar(publicacion(CategoriaTienda.ACCESORIO, null, false)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> refaccion.publicar(publicacion(CategoriaTienda.ACCESORIO, "http://foto.test/a.jpg", true)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> refaccion.publicar(
				publicacion(CategoriaTienda.ACCESORIO, null, false, new ModeloCompatible("March", 2022, 2020))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(refaccion.isPublicadaEnApp()).isFalse();
	}

	@Test
	void noApartaMasDeLoQueHayNiCeroPiezas() {
		assertThatThrownBy(() -> refaccion.apartar(1, 1L, AHORA)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> refaccion.apartar(0, 1L, AHORA)).isInstanceOf(IllegalArgumentException.class);

		MovimientoInventario entrada = refaccion.liberar(2, 1L, AHORA);
		MovimientoInventario salida = refaccion.apartar(2, 1L, AHORA);

		assertThat(entrada.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
		assertThat(salida.getTipo()).isEqualTo(TipoMovimiento.SALIDA);
		assertThat(salida.getPedidoAppId()).isEqualTo(1L);
		assertThat(salida.getResponsableId()).isNull();
		assertThat(refaccion.getExistencia()).isZero();
	}
}
