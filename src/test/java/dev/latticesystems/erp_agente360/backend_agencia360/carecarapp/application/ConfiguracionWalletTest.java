package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfiguracionWalletTest {

	private static ConfiguracionWallet conAppleCompleto(String urlServicioWeb) {
		return new ConfiguracionWallet("pass.mx.carecar", "ABCDE12345", "p12", "clave", "wwdr", "", "", urlServicioWeb);
	}

	@Test
	void sinUrlPublicaElPaseDeAppleNoSeActualizaSolo() {
		assertThat(conAppleCompleto("").appleConfigurado()).isTrue();
		assertThat(conAppleCompleto("").appleActualizable()).isFalse();
	}

	@Test
	void conUrlHttpsYCredencialesElPaseSeActualizaSolo() {
		assertThat(conAppleCompleto("https://api.carecar.mx").appleActualizable()).isTrue();
	}

	@Test
	void appleExigeHttpsParaElServicioWeb() {
		assertThat(conAppleCompleto("http://192.168.1.10:8080").appleActualizable()).isFalse();
	}

	@Test
	void sinLasCredencialesDeApplePorMuchaUrlQueHayaNoSeActualiza() {
		ConfiguracionWallet sinP12 = new ConfiguracionWallet("pass.mx.carecar", "ABCDE12345", "", "", "", "", "",
				"https://api.carecar.mx");

		assertThat(sinP12.appleActualizable()).isFalse();
	}

	@Test
	void laUrlSeNormalizaSinDiagonalFinal() {
		assertThat(conAppleCompleto("https://api.carecar.mx//").appleWebServiceUrl())
				.isEqualTo("https://api.carecar.mx");
	}

	@Test
	void elConstructorViejoDeSieteArgumentosSigueSinServicioWeb() {
		ConfiguracionWallet vieja = new ConfiguracionWallet("pass.mx.carecar", "ABCDE12345", "p12", "clave", "wwdr", "",
				"");

		assertThat(vieja.appleActualizable()).isFalse();
		assertThat(vieja.appleWebServiceUrl()).isEmpty();
	}
}
