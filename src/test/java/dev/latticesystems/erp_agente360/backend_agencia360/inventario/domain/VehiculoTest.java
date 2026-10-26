package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VehiculoTest {

	private static final BigDecimal PRECIO = new BigDecimal("389900.00");

	private static final String VIN = "3VWFE21C04M000001";

	@Test
	void seRegistraDisponibleConSusDatos() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);

		assertThat(vehiculo.getVin()).isEqualTo(VIN);
		assertThat(vehiculo.getModelo()).isEqualTo("Jetta");
		assertThat(vehiculo.getVersion()).isEqualTo("Comfortline");
		assertThat(vehiculo.getAnio()).isEqualTo(2026);
		assertThat(vehiculo.getColor()).isEqualTo("Blanco");
		assertThat(vehiculo.getPrecio()).isEqualByComparingTo(PRECIO);
		assertThat(vehiculo.getEstatus()).isEqualTo(EstatusVehiculo.DISPONIBLE);
	}

	@Test
	void puedeEntrarComoNoDisponible() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO,
				EstatusVehiculo.NO_DISPONIBLE);

		assertThat(vehiculo.getEstatus()).isEqualTo(EstatusVehiculo.NO_DISPONIBLE);
	}

	@Test
	void noPuedeEntrarConUnEstatusDeVenta() {
		for (EstatusVehiculo estatus : new EstatusVehiculo[]{EstatusVehiculo.VENDIDO, EstatusVehiculo.ENTREGADO}) {
			assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, estatus))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Una unidad solo puede entrar como Disponible o No disponible");
		}
	}

	@Test
	void quitaEspaciosYGuardaElVinEnMayusculas() {
		Vehiculo vehiculo = Vehiculo.registrar(" 3vwfe21c04m000001 ", "  Jetta ", " Comfortline", 2026, "Blanco ",
				PRECIO, null);

		assertThat(vehiculo.getVin()).isEqualTo(VIN);
		assertThat(vehiculo.getModelo()).isEqualTo("Jetta");
		assertThat(vehiculo.getVersion()).isEqualTo("Comfortline");
		assertThat(vehiculo.getColor()).isEqualTo("Blanco");
	}

	@Test
	void rechazaUnVinQueNoTiene17Caracteres() {
		assertThatThrownBy(
				() -> Vehiculo.registrar("3VWFE21C04M00001", "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El VIN debe tener 17 caracteres, sin las letras I, O ni Q");
	}

	@Test
	void rechazaUnVinConLasLetrasIOQ() {
		assertThatThrownBy(
				() -> Vehiculo.registrar("3VWFE21C04M00000O", "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El VIN debe tener 17 caracteres, sin las letras I, O ni Q");
	}

	@Test
	void rechazaCamposDeTextoVacios() {
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, " ", "Comfortline", 2026, "Blanco", PRECIO, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El modelo es obligatorio");
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", null, 2026, "Blanco", PRECIO, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("La versión es obligatoria");
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "", PRECIO, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El color es obligatorio");
	}

	@Test
	void rechazaUnAnioInvalido() {
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", "Comfortline", 1899, "Blanco", PRECIO, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El año no es válido");
	}

	@Test
	void seEditanSusDatosConLasMismasReglasDelAlta() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);

		vehiculo.editar(" 3vwfe21c04m000009 ", "Jetta GLI", "GLI", 2025, " Rojo ", PRECIO,
				EstatusVehiculo.NO_DISPONIBLE);

		assertThat(vehiculo.getVin()).isEqualTo("3VWFE21C04M000009");
		assertThat(vehiculo.getModelo()).isEqualTo("Jetta GLI");
		assertThat(vehiculo.getVersion()).isEqualTo("GLI");
		assertThat(vehiculo.getAnio()).isEqualTo(2025);
		assertThat(vehiculo.getColor()).isEqualTo("Rojo");
		assertThat(vehiculo.getEstatus()).isEqualTo(EstatusVehiculo.NO_DISPONIBLE);
	}

	@Test
	void sinEstatusAlEditarConservaElActual() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO,
				EstatusVehiculo.NO_DISPONIBLE);

		vehiculo.editar(VIN, "Jetta", "Comfortline", 2026, "Gris", PRECIO, null);

		assertThat(vehiculo.getEstatus()).isEqualTo(EstatusVehiculo.NO_DISPONIBLE);
	}

	@Test
	void inventarioNoPoneEstatusDeVentaAlEditar() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);

		assertThatThrownBy(
				() -> vehiculo.editar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, EstatusVehiculo.VENDIDO))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Inventario solo cambia el estatus entre Disponible y No disponible");
		assertThat(vehiculo.getEstatus()).isEqualTo(EstatusVehiculo.DISPONIBLE);
	}

	@Test
	void unaEdicionInvalidaNoCambiaNada() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);

		assertThatThrownBy(() -> vehiculo.editar(VIN, "Otro", "Otra", 2026, " ", PRECIO, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El color es obligatorio");
		assertThat(vehiculo.getModelo()).isEqualTo("Jetta");
		assertThat(vehiculo.getColor()).isEqualTo("Blanco");
	}

	@Test
	void rechazaUnPrecioQueNoEsMayorACero() {
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", BigDecimal.ZERO, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El precio debe ser mayor a cero");
		assertThatThrownBy(() -> Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", null, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El precio debe ser mayor a cero");
	}

	@Test
	void alEditarSeCorrigeElPrecio() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);

		vehiculo.editar(VIN, "Jetta", "Comfortline", 2026, "Blanco", new BigDecimal("399900.00"), null);

		assertThat(vehiculo.getPrecio()).isEqualByComparingTo("399900.00");
	}

	@Test
	void rechazaUnVinVacioONulo() {
		assertThatThrownBy(() -> Vehiculo.registrar(null, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El VIN debe tener 17 caracteres, sin las letras I, O ni Q");
		assertThatThrownBy(() -> Vehiculo.registrar("   ", "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El VIN debe tener 17 caracteres, sin las letras I, O ni Q");
	}

	@Test
	void inventarioNoSacaDeVendidoAUnaUnidad() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);
		vehiculo.vender();

		assertThatThrownBy(
				() -> vehiculo.editar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, EstatusVehiculo.DISPONIBLE))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Inventario solo cambia el estatus entre Disponible y No disponible");
		assertThat(vehiculo.getEstatus()).isEqualTo(EstatusVehiculo.VENDIDO);
	}

	@Test
	void unaUnidadVendidaSeCorrigeSinTocarSuEstatus() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);
		vehiculo.vender();

		vehiculo.editar(VIN, "Jetta", "Comfortline", 2026, "Rojo", PRECIO, EstatusVehiculo.VENDIDO);

		assertThat(vehiculo.getColor()).isEqualTo("Rojo");
		assertThat(vehiculo.getEstatus()).isEqualTo(EstatusVehiculo.VENDIDO);
	}

	@Test
	void seVendeUnaUnidadDisponible() {
		Vehiculo vehiculo = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);

		vehiculo.vender();

		assertThat(vehiculo.getEstatus()).isEqualTo(EstatusVehiculo.VENDIDO);
	}

	@Test
	void noSeVendeUnaUnidadQueNoEstaDisponible() {
		Vehiculo vendida = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);
		vendida.vender();
		Vehiculo danada = Vehiculo.registrar(VIN, "Jetta", "Comfortline", 2026, "Blanco", PRECIO,
				EstatusVehiculo.NO_DISPONIBLE);

		assertThatThrownBy(vendida::vender).isInstanceOf(ConflictoException.class)
				.hasMessage("La unidad 3VWFE21C04M000001 ya no está disponible");
		assertThatThrownBy(danada::vender).isInstanceOf(ConflictoException.class);
		assertThat(danada.getEstatus()).isEqualTo(EstatusVehiculo.NO_DISPONIBLE);
	}
}
