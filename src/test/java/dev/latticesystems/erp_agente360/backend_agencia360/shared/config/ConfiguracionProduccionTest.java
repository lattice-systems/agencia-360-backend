package dev.latticesystems.erp_agente360.backend_agencia360.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Guarda la configuración de producción que no se puede ejercitar con MockMvc.
 */
class ConfiguracionProduccionTest {

	@Test
	void produccionRespetaLosEncabezadosDelProxyParaArmarUrlsHttps() throws IOException {
		Properties propiedades = new Properties();
		try (InputStream entrada = getClass().getResourceAsStream("/application-prod.properties")) {
			assertThat(entrada).isNotNull();
			propiedades.load(entrada);
		}

		// Sin esto, las URL firmadas de manuales y documentos salen con http:// detrás
		// del
		// balanceador de Azure y la app de producción las rechaza.
		assertThat(propiedades.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
	}
}
