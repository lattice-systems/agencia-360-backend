package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VehiculoTest {

	private static final String VIN = "3VWFE21C04M000001";
	private static final BigDecimal PRECIO = new BigDecimal("389900.00");

	@Test
	void seRegistraDisponibleConSusDatos() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO);

		assertThat(vehiculo.getVin()).isEqualTo(VIN);
		assertThat(vehiculo.getModelo()).isEqualTo("Jetta");
		assertThat(vehiculo.getVersion()).isEqualTo("Comfortline");
		assertThat(vehiculo.getAnio()).isEqualTo(2026);
		assertThat(vehiculo.getColor()).isEqualTo("Blanco");
		assertThat(vehiculo.getPrecio()).isEqualByComparingTo(PRECIO);
		assertThat(vehiculo.getEstatus()).isEqualTo(EstatusVehiculo.DISPONIBLE);
	}

	@Test
	void quitaEspaciosYGuardaElVinEnMayusculas() {
		Vehiculo vehiculo = Vehiculo.registrar(" 3vwfe21c04m000001 ", "  Jetta ", " Comfortline", 2026, "Blanco ",
				PRECIO);

		assertThat(vehiculo.getVin()).isEqualTo(VIN);
		assertThat(vehiculo.getModelo()).isEqualTo("Jetta");
		assertThat(vehiculo.getVersion()).isEqualTo("Comfortline");
		assertThat(vehiculo.getColor()).isEqualTo("Blanco");
	}

	@Test
	void rechazaUnVinQueNoTiene17Caracteres() {
		assertThatThrownBy(() -> Vehiculo.registrar("3VWFE21C04M00001", "Jetta", "Comfortline", 2026, "Blanco", PRECIO))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El VIN debe tener 17 caracteres, sin las letras I, O ni Q");
	}

	@Test
	void rechazaUnVinConLasLetrasIOQ() {
		assertThatThrownBy(
				() -> Vehiculo.registrar("3VWFE21C04M00000O", "Jetta", "Comfortline", 2026, "Blanco", PRECIO))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El VIN debe tener 17 caracteres, sin las letras I, O ni Q");
	}

	@Test
	void rechazaCamposDeTextoVacios() {
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, " ", "Comfortline", 2026, "Blanco", PRECIO))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El modelo es obligatorio");
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", null, 2026, "Blanco", PRECIO))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("La versión es obligatoria");
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "", PRECIO))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El color es obligatorio");
	}

	@Test
	void rechazaUnAnioInvalido() {
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", "Comfortline", 1899, "Blanco", PRECIO))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El año no es válido");
	}

	@Test
	void rechazaUnPrecioQueNoEsMayorACero() {
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", BigDecimal.ZERO))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El precio debe ser mayor a cero");
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El precio debe ser mayor a cero");
	}
}
