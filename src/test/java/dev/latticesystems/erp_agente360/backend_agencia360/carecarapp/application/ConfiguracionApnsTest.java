package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfiguracionApnsTest {

	@Test
	void sinNadaNoEstaConfigurado() {
		ConfiguracionApns vacia = new ConfiguracionApns("", "", "", "", false);

		assertThat(vacia.configurado()).isFalse();
		assertThat(vacia.actividadesConfiguradas()).isFalse();
	}

	@Test
	void conLlaveEquipoYIdentificadorEstaConfiguradoParaPases() {
		ConfiguracionApns sinBundle = new ConfiguracionApns("KEY1234567", "ABCDE12345", "cDhi", "", false);

		assertThat(sinBundle.configurado()).isTrue();
		assertThat(sinBundle.actividadesConfiguradas()).isFalse();
	}

	@Test
	void lasActividadesEnVivoTambienPidenElIdentificadorDeLaApp() {
		ConfiguracionApns completa = new ConfiguracionApns("KEY1234567", "ABCDE12345", "cDhi", "mx.carecar.app", true);

		assertThat(completa.actividadesConfiguradas()).isTrue();
		assertThat(completa.sandbox()).isTrue();
	}

	@Test
	void faltandoUnaDeLasTresCredencialesNoSeConfigura() {
		assertThat(new ConfiguracionApns("", "ABCDE12345", "cDhi", "mx.carecar.app", false).configurado()).isFalse();
		assertThat(new ConfiguracionApns("KEY1234567", " ", "cDhi", "mx.carecar.app", false).configurado()).isFalse();
		assertThat(new ConfiguracionApns("KEY1234567", "ABCDE12345", null, "mx.carecar.app", false).configurado())
				.isFalse();
	}
}
