package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DocumentoVehiculoTest {

	private static final LocalDate HOY = LocalDate.of(2026, 11, 27);

	private static DocumentoVehiculo conVencimiento(LocalDate vence) {
		return new DocumentoVehiculo(1L, TipoDocumento.VERIFICACION, null, "VER-123", null, vence,
				OrigenDocumento.CLIENTE, null, null);
	}

	@Test
	void sinFechaNoTieneEstadoDeVencimiento() {
		DocumentoVehiculo factura = conVencimiento(null);

		assertThat(factura.estadoEn(HOY)).isEqualTo(EstadoDocumento.SIN_FECHA);
		assertThat(factura.diasRestantesEn(HOY)).isNull();
	}

	@Test
	void vencidoSiLaFechaYaPaso() {
		DocumentoVehiculo ayer = conVencimiento(HOY.minusDays(1));

		assertThat(ayer.estadoEn(HOY)).isEqualTo(EstadoDocumento.VENCIDO);
		assertThat(ayer.diasRestantesEn(HOY)).isEqualTo(-1);
	}

	@Test
	void venceHoyTodaviaCuentaComoPorVencer() {
		DocumentoVehiculo hoy = conVencimiento(HOY);

		assertThat(hoy.estadoEn(HOY)).isEqualTo(EstadoDocumento.POR_VENCER);
		assertThat(hoy.diasRestantesEn(HOY)).isZero();
	}

	@Test
	void treintaDiasEsElUltimoDiaDePorVencer() {
		assertThat(conVencimiento(HOY.plusDays(30)).estadoEn(HOY)).isEqualTo(EstadoDocumento.POR_VENCER);
		assertThat(conVencimiento(HOY.plusDays(31)).estadoEn(HOY)).isEqualTo(EstadoDocumento.VIGENTE);
	}

	@Test
	void otroExigeUnTitulo() {
		assertThatThrownBy(() -> new DocumentoVehiculo(1L, TipoDocumento.OTRO, " ", null, null, null,
				OrigenDocumento.CLIENTE, null, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void elVencimientoNoPuedeSerAnteriorALaEmision() {
		assertThatThrownBy(() -> new DocumentoVehiculo(1L, TipoDocumento.POLIZA, null, null, HOY, HOY.minusDays(1),
				OrigenDocumento.CLIENTE, null, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void guardaLaAseguradoraSoloDeLaPoliza() {
		DocumentoVehiculo poliza = new DocumentoVehiculo(1L, TipoDocumento.POLIZA, null, "POL-1", null,
				HOY.plusDays(100), OrigenDocumento.CLIENTE, " Seguros Ejemplo ", " +52 800 000 0000 ");
		DocumentoVehiculo verificacion = new DocumentoVehiculo(1L, TipoDocumento.VERIFICACION, null, null, null, null,
				OrigenDocumento.CLIENTE, "Seguros Ejemplo", "+52 800 000 0000");

		assertThat(poliza.getAseguradora()).isEqualTo("Seguros Ejemplo");
		assertThat(poliza.getTelefonoAseguradora()).isEqualTo("+52 800 000 0000");
		assertThat(verificacion.getAseguradora()).isNull();
		assertThat(verificacion.getTelefonoAseguradora()).isNull();
	}
}
