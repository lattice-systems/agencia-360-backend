package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VentaTest {

	private static final Instant EMISION = Instant.parse("2026-10-24T16:00:00Z");
	private static final Instant ENTREGA = Instant.parse("2026-10-25T17:00:00Z");

	@Test
	void alEmitirLaFacturaLaUnidadPasaAVendidoYEsVenta() {
		Vehiculo sentra = unidad("3N1AB8CV5PY123456", "489900.00");

		Venta venta = Venta.registrar(7L, sentra, 5L, EMISION);

		assertThat(venta.getProspectoId()).isEqualTo(7L);
		assertThat(venta.getVehiculo()).isSameAs(sentra);
		assertThat(venta.getPrecio()).isEqualByComparingTo("489900.00");
		assertThat(venta.getEstatus()).isEqualTo(EstatusVenta.REGISTRADA);
		assertThat(venta.getAsignadaPor()).isEqualTo(5L);
		assertThat(venta.getAsignadaEn()).isEqualTo(EMISION);
		assertThat(venta.getRegistradaEn()).isEqualTo(EMISION);
		assertThat(venta.getEntregadaEn()).isNull();
		assertThat(sentra.getEstatus()).isEqualTo(EstatusVehiculo.VENDIDO);
	}

	@Test
	void noSeVendeUnaUnidadQueYaNoEstaDisponible() {
		Vehiculo sentra = unidad("3N1AB8CV5PY123456", "489900.00");
		sentra.vender();
		Vehiculo danada = Vehiculo.registrar("3N1CP5CV6RL123458", "Kicks", "Exclusive", 2026, "Azul",
				new BigDecimal("529900.00"), EstatusVehiculo.NO_DISPONIBLE);

		assertThatThrownBy(() -> Venta.registrar(7L, sentra, 5L, EMISION)).isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> Venta.registrar(7L, danada, 5L, EMISION)).isInstanceOf(ConflictoException.class);
		assertThat(danada.getEstatus()).isEqualTo(EstatusVehiculo.NO_DISPONIBLE);
	}

	@Test
	void alFinalizarElProspectoLaVentaQuedaEntregadaUnaSolaVez() {
		Venta venta = Venta.registrar(7L, unidad("3N1AB8CV5PY123456", "489900.00"), 5L, EMISION);

		assertThat(venta.entregar(ENTREGA)).isTrue();
		assertThat(venta.entregar(ENTREGA.plusSeconds(60))).isFalse();

		assertThat(venta.getEstatus()).isEqualTo(EstatusVenta.ENTREGADA);
		assertThat(venta.getEntregadaEn()).isEqualTo(ENTREGA);
		assertThat(venta.getVehiculo().getEstatus()).as("sigue Vendido; Entregado está pendiente con el PO")
				.isEqualTo(EstatusVehiculo.VENDIDO);
	}

	private static Vehiculo unidad(String vin, String precio) {
		return Vehiculo.registrar(vin, "Sentra", "Advance", 2026, "Blanco", new BigDecimal(precio), null);
	}
}
