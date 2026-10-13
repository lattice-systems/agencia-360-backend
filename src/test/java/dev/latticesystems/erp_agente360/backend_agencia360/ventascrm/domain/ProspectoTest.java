package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProspectoTest {

	private static final Long VENDEDOR = 7L;
	private static final Instant AHORA = Instant.parse("2026-10-12T18:00:00Z");
	private static final Instant DESPUES = Instant.parse("2026-10-13T10:30:00Z");

	@Test
	void seRegistraEnPrimerContactoConElVendedorQueLoCaptura() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		assertThat(prospecto.getNombre()).isEqualTo("Ana López");
		assertThat(prospecto.getTelefono()).isEqualTo("4771234567");
		assertThat(prospecto.getCorreo()).isNull();
		assertThat(prospecto.getEtapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
		assertThat(prospecto.getVendedorId()).isEqualTo(VENDEDOR);
	}

	@Test
	void elRegistroEscribeElPrimerMovimientoDelHistorial() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		assertThat(prospecto.getHistorial()).singleElement().satisfies(movimiento -> {
			assertThat(movimiento.getEtapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
			assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.CRM);
			assertThat(movimiento.getFechaHora()).isEqualTo(AHORA);
		});
	}

	@Test
	void aceptaSoloCorreoSinTelefono() {
		Prospecto prospecto = Prospecto.registrar("Luis Pérez", null, "luis@ejemplo.com", VENDEDOR, AHORA);

		assertThat(prospecto.getCorreo()).isEqualTo("luis@ejemplo.com");
		assertThat(prospecto.getTelefono()).isNull();
	}

	@Test
	void quitaEspaciosConvierteVaciosEnNuloYGuardaElCorreoEnMinusculas() {
		Prospecto prospecto = Prospecto.registrar("  Ana López ", "   ", " Ana@Ejemplo.COM ", VENDEDOR, AHORA);

		assertThat(prospecto.getNombre()).isEqualTo("Ana López");
		assertThat(prospecto.getTelefono()).isNull();
		assertThat(prospecto.getCorreo()).isEqualTo("ana@ejemplo.com");
	}

	@Test
	void rechazaUnProspectoSinNombre() {
		assertThatThrownBy(() -> Prospecto.registrar("  ", "4771234567", null, VENDEDOR, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El nombre es obligatorio");
	}

	@Test
	void rechazaUnProspectoSinTelefonoNiCorreo() {
		assertThatThrownBy(() -> Prospecto.registrar("Ana López", " ", null, VENDEDOR, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Captura al menos un teléfono o un correo");
	}

	@Test
	void cambiarDeEtapaRegistraElMovimientoConSuFechaYOrigen() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		boolean cambio = prospecto.cambiarEtapa(Etapa.VISITA, null, OrigenCambio.CRM, DESPUES);

		assertThat(cambio).isTrue();
		assertThat(prospecto.getEtapa()).isEqualTo(Etapa.VISITA);
		assertThat(prospecto.getHistorial()).hasSize(2).last().satisfies(movimiento -> {
			assertThat(movimiento.getEtapa()).isEqualTo(Etapa.VISITA);
			assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.CRM);
			assertThat(movimiento.getFechaHora()).isEqualTo(DESPUES);
		});
	}

	@Test
	void aceptaCambiosQueVienenDelErp() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		prospecto.cambiarEtapa(Etapa.FACTURACION, null, OrigenCambio.ERP, DESPUES);

		assertThat(prospecto.getHistorial()).last().extracting(HistorialEtapa::getOrigen).isEqualTo(OrigenCambio.ERP);
	}

	@Test
	void pedirLaMismaEtapaNoEscribeHistorial() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		boolean cambio = prospecto.cambiarEtapa(Etapa.PRIMER_CONTACTO, null, OrigenCambio.CRM, DESPUES);

		assertThat(cambio).isFalse();
		assertThat(prospecto.getHistorial()).hasSize(1);
	}

	@Test
	void normalizaUnTelefonoDeDiezDigitosEscritoConSeparadores() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "(477) 123-45 67", null, VENDEDOR, AHORA);

		assertThat(prospecto.getTelefono()).isEqualTo("4771234567");
	}

	@Test
	void rechazaUnTelefonoQueNoTieneDiezDigitos() {
		assertThatThrownBy(() -> Prospecto.registrar("Ana López", "47712345", null, VENDEDOR, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El teléfono debe tener 10 dígitos");
	}

	@Test
	void rechazaUnTelefonoConLadaInternacional() {
		assertThatThrownBy(() -> Prospecto.registrar("Ana López", "+52 477 123 4567", null, VENDEDOR, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rechazaUnTelefonoConLetras() {
		assertThatThrownBy(() -> Prospecto.registrar("Ana López", "477ABC4567", null, VENDEDOR, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void noSePuedeCerrarSinMotivo() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		assertThatThrownBy(() -> prospecto.cambiarEtapa(Etapa.CERRADO, "  ", OrigenCambio.CRM, DESPUES))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El motivo de cierre es obligatorio");
		assertThat(prospecto.getEtapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
		assertThat(prospecto.getHistorial()).hasSize(1);
	}

	@Test
	void alCerrarseGuardaElMotivo() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		prospecto.cambiarEtapa(Etapa.CERRADO, "  Compró en otra agencia ", OrigenCambio.CRM, DESPUES);

		assertThat(prospecto.getMotivoCierre()).isEqualTo("Compró en otra agencia");
	}

	@Test
	void ignoraElMotivoCuandoNoSeCierra() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		prospecto.cambiarEtapa(Etapa.VISITA, "No aplica", OrigenCambio.CRM, DESPUES);

		assertThat(prospecto.getMotivoCierre()).isNull();
	}

	@Test
	void alCerrarseGuardaLaUltimaEtapaAlcanzada() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);
		prospecto.cambiarEtapa(Etapa.COTIZACION, null, OrigenCambio.CRM, AHORA);

		prospecto.cambiarEtapa(Etapa.CERRADO, "No le alcanzó el crédito", OrigenCambio.CRM, DESPUES);

		assertThat(prospecto.getEtapa()).isEqualTo(Etapa.CERRADO);
		assertThat(prospecto.getUltimaEtapa()).isEqualTo(Etapa.COTIZACION);
	}

	@Test
	void alReabrirseOlvidaLaUltimaEtapa() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);
		prospecto.cambiarEtapa(Etapa.CERRADO, "No le alcanzó el crédito", OrigenCambio.CRM, AHORA);

		prospecto.cambiarEtapa(Etapa.VISITA, null, OrigenCambio.CRM, DESPUES);

		assertThat(prospecto.getUltimaEtapa()).isNull();
		assertThat(prospecto.getMotivoCierre()).isNull();
	}

	@Test
	void convierteElTextoDeUnaPeticionEnEtapa() {
		assertThat(Etapa.desde("VISITA")).isEqualTo(Etapa.VISITA);
		assertThatThrownBy(() -> Etapa.desde("GANADO")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Etapa no válida: GANADO");
	}
}
