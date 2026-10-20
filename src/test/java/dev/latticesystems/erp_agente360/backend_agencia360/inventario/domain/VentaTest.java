package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VentaTest {

	private static final Instant AHORA = Instant.parse("2026-10-19T16:00:00Z");
	private static final Instant DESPUES = Instant.parse("2026-10-19T17:00:00Z");

	@Test
	void facturacionEligeLaUnidadYLaUnidadPasaAVendido() {
		Vehiculo sentra = unidad("3N1AB8CV5PY123456", "489900.00");

		Venta venta = Venta.asignar(7L, sentra, 5L, AHORA);

		assertThat(venta.getProspectoId()).isEqualTo(7L);
		assertThat(venta.getVehiculo()).isSameAs(sentra);
		assertThat(venta.getPrecio()).isEqualByComparingTo("489900.00");
		assertThat(venta.getEstatus()).isEqualTo(EstatusVenta.POR_ENTREGAR);
		assertThat(venta.getAsignadaPor()).isEqualTo(5L);
		assertThat(venta.getAsignadaEn()).isEqualTo(AHORA);
		assertThat(venta.getRegistradaEn()).isNull();
		assertThat(sentra.getEstatus()).isEqualTo(EstatusVehiculo.VENDIDO);
	}

	@Test
	void noSeEligeUnaUnidadQueYaNoEstaDisponible() {
		Vehiculo sentra = unidad("3N1AB8CV5PY123456", "489900.00");
		sentra.vender();

		assertThatThrownBy(() -> Venta.asignar(7L, sentra, 5L, AHORA)).isInstanceOf(ConflictoException.class);
	}

	@Test
	void alCambiarDeUnidadLaAnteriorRegresaADisponible() {
		Vehiculo sentra = unidad("3N1AB8CV5PY123456", "489900.00");
		Vehiculo kicks = unidad("3N1CP5CV6RL123458", "529900.00");
		Venta venta = Venta.asignar(7L, sentra, 5L, AHORA);

		boolean cambio = venta.cambiarUnidad(kicks, 6L, DESPUES);

		assertThat(cambio).isTrue();
		assertThat(venta.getVehiculo()).isSameAs(kicks);
		assertThat(venta.getPrecio()).isEqualByComparingTo("529900.00");
		assertThat(venta.getAsignadaPor()).isEqualTo(6L);
		assertThat(venta.getAsignadaEn()).isEqualTo(DESPUES);
		assertThat(kicks.getEstatus()).isEqualTo(EstatusVehiculo.VENDIDO);
		assertThat(sentra.getEstatus()).isEqualTo(EstatusVehiculo.DISPONIBLE);
	}

	@Test
	void elegirLaMismaUnidadNoCambiaNada() {
		Vehiculo sentra = unidad("3N1AB8CV5PY123456", "489900.00");
		Venta venta = Venta.asignar(7L, sentra, 5L, AHORA);

		assertThat(venta.cambiarUnidad(sentra, 6L, DESPUES)).isFalse();
		assertThat(venta.getAsignadaEn()).isEqualTo(AHORA);
		assertThat(sentra.getEstatus()).isEqualTo(EstatusVehiculo.VENDIDO);
	}

	@Test
	void siLaNuevaUnidadNoEstaDisponibleSeConservaLaAnterior() {
		Vehiculo sentra = unidad("3N1AB8CV5PY123456", "489900.00");
		Vehiculo danada = Vehiculo.registrar("3N1CP5CV6RL123458", "Kicks", "Exclusive", 2026, "Azul",
				new BigDecimal("529900.00"), EstatusVehiculo.NO_DISPONIBLE);
		Venta venta = Venta.asignar(7L, sentra, 5L, AHORA);

		assertThatThrownBy(() -> venta.cambiarUnidad(danada, 6L, DESPUES)).isInstanceOf(ConflictoException.class);
		assertThat(venta.getVehiculo()).isSameAs(sentra);
		assertThat(sentra.getEstatus()).isEqualTo(EstatusVehiculo.VENDIDO);
	}

	@Test
	void unaVentaRegistradaYaNoCambiaDeUnidad() {
		Vehiculo sentra = unidad("3N1AB8CV5PY123456", "489900.00");
		Vehiculo kicks = unidad("3N1CP5CV6RL123458", "529900.00");
		Venta venta = Venta.asignar(7L, sentra, 5L, AHORA);
		venta.registrar(DESPUES);

		assertThatThrownBy(() -> venta.cambiarUnidad(kicks, 6L, DESPUES)).isInstanceOf(ConflictoException.class)
				.hasMessage("La venta ya se registró: su unidad no se cambia");
		assertThat(kicks.getEstatus()).isEqualTo(EstatusVehiculo.DISPONIBLE);
	}

	@Test
	void alFinalizarElProspectoLaVentaQuedaRegistrada() {
		Venta venta = Venta.asignar(7L, unidad("3N1AB8CV5PY123456", "489900.00"), 5L, AHORA);

		venta.registrar(DESPUES);

		assertThat(venta.getEstatus()).isEqualTo(EstatusVenta.REGISTRADA);
		assertThat(venta.getRegistradaEn()).isEqualTo(DESPUES);
	}

	@Test
	void unProspectoQueSeFinalizoSinUnidadQuedaRegistradoSinVehiculo() {
		Venta venta = Venta.registrarSinUnidad(7L, DESPUES);

		assertThat(venta.getEstatus()).isEqualTo(EstatusVenta.REGISTRADA);
		assertThat(venta.getVehiculo()).isNull();
		assertThat(venta.getPrecio()).isNull();
		assertThat(venta.getRegistradaEn()).isEqualTo(DESPUES);
	}

	private static Vehiculo unidad(String vin, String precio) {
		return Vehiculo.registrar(vin, "Sentra", "Advance", 2026, "Blanco", new BigDecimal(precio), null);
	}
}
