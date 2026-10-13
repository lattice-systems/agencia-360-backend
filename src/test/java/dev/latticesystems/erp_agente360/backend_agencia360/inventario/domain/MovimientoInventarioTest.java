package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MovimientoInventarioTest {

	private static final Long RESPONSABLE = 4L;
	private static final Instant AHORA = Instant.parse("2026-10-13T16:00:00Z");

	@Test
	void laEntradaDeUnVehiculoEsDeUnaSolaUnidad() {
		Vehiculo vehiculo = Vehiculo.registrar("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco",
				new BigDecimal("389900.00"));

		MovimientoInventario movimiento = MovimientoInventario.entradaDeVehiculo(vehiculo, RESPONSABLE, AHORA);

		assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
		assertThat(movimiento.getVehiculo()).isSameAs(vehiculo);
		assertThat(movimiento.getRefaccion()).isNull();
		assertThat(movimiento.getCantidad()).isEqualTo(1);
		assertThat(movimiento.getResponsableId()).isEqualTo(RESPONSABLE);
		assertThat(movimiento.getFecha()).isEqualTo(AHORA);
	}
}
