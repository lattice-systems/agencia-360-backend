package dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClienteTest {

	private static final String VIN = "3VWFE21C04M000001";

	@Test
	void guardaElCorreoEnMinusculasYSinEspacios() {
		Cliente cliente = Cliente.registrar("  Ana López ", " Ana.Lopez@Correo.COM ", " 4775550101 ", 5L);

		assertThat(cliente.getNombre()).isEqualTo("Ana López");
		assertThat(cliente.getCorreo()).isEqualTo("ana.lopez@correo.com");
		assertThat(cliente.getTelefono()).isEqualTo("4775550101");
		assertThat(cliente.getProspectoId()).isEqualTo(5L);
	}

	@Test
	void puedeNoTenerCorreoSiRechazoLaApp() {
		Cliente cliente = Cliente.registrar("Ana López", "  ", null, null);

		assertThat(cliente.getCorreo()).isNull();
		assertThat(cliente.getTelefono()).isNull();
	}

	@Test
	void exigeElNombre() {
		assertThatThrownBy(() -> Cliente.registrar(" ", "ana@correo.com", null, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El nombre es obligatorio");
	}

	@Test
	void rechazaUnCorreoSinFormato() {
		assertThatThrownBy(() -> Cliente.registrar("Ana López", "ana-correo.com", null, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El correo no es válido");
	}

	@Test
	void puedeTenerVariosVehiculos() {
		Cliente cliente = Cliente.registrar("Ana López", "ana@correo.com", null, null);

		cliente.asignarVehiculo(10L, VIN, "Jetta", 2026);
		cliente.asignarVehiculo(null, "3VWFE21C04M000002", "Tiguan", 2025);

		assertThat(cliente.getVehiculos()).extracting(VehiculoCliente::getModelo).containsExactly("Jetta", "Tiguan");
	}

	@Test
	void noDuplicaUnVehiculoConElMismoVin() {
		Cliente cliente = Cliente.registrar("Ana López", "ana@correo.com", null, null);

		assertThat(cliente.asignarVehiculo(10L, VIN, "Jetta", 2026)).isTrue();
		assertThat(cliente.asignarVehiculo(10L, VIN.toLowerCase(), "Jetta", 2026)).isFalse();
		assertThat(cliente.getVehiculos()).hasSize(1);
	}

	@Test
	void validaLosDatosDelVehiculo() {
		Cliente cliente = Cliente.registrar("Ana López", "ana@correo.com", null, null);

		assertThatThrownBy(() -> cliente.asignarVehiculo(null, "CORTO", "Jetta", 2026))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El VIN debe tener 17 caracteres");
		assertThatThrownBy(() -> cliente.asignarVehiculo(null, VIN, " ", 2026))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> cliente.asignarVehiculo(null, VIN, "Jetta", 1899))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El año debe ser 1900 o posterior");
	}

	@Test
	void guardaLaMarcaYLaVersionSiVienen() {
		Cliente cliente = Cliente.registrar("Ana López", "ana@correo.com", null, null);

		cliente.asignarVehiculo(10L, VIN, " Nissan ", "Sentra", 2026, " Advance ");
		cliente.asignarVehiculo(null, "3VWFE21C04M000002", null, "March", 2025, "  ");

		assertThat(cliente.getVehiculos()).extracting(VehiculoCliente::getMarca).containsExactly("Nissan", null);
		assertThat(cliente.getVehiculos()).extracting(VehiculoCliente::getVersion).containsExactly("Advance", null);
	}

	@Test
	void servicioCorrigeLasPlacasYElKilometraje() {
		Cliente cliente = Cliente.registrar("Ana López", "ana@correo.com", null, null);
		cliente.asignarVehiculo(10L, VIN, "Nissan", "Sentra", 2026, "Advance");
		VehiculoCliente vehiculo = cliente.getVehiculos().getFirst();

		vehiculo.corregir(" abc-123-d ", 15_000);
		assertThat(vehiculo.getPlacas()).isEqualTo("ABC-123-D");
		assertThat(vehiculo.getKilometraje()).isEqualTo(15_000);

		vehiculo.corregir(" ", null);
		assertThat(vehiculo.getPlacas()).isNull();
		assertThat(vehiculo.getKilometraje()).isNull();
	}

	@Test
	void validaLaCorreccionDelVehiculo() {
		Cliente cliente = Cliente.registrar("Ana López", "ana@correo.com", null, null);
		cliente.asignarVehiculo(10L, VIN, "Sentra", 2026);
		VehiculoCliente vehiculo = cliente.getVehiculos().getFirst();

		assertThatThrownBy(() -> vehiculo.corregir("ABC", -1)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El kilometraje no puede ser negativo");
		assertThatThrownBy(() -> vehiculo.corregir("ABCDEFGHIJKLMNOP", 10)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Las placas admiten hasta 15 caracteres");
	}
}
