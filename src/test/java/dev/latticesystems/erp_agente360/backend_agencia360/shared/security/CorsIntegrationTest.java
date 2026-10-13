package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El navegador manda una petición OPTIONS (preflight) sin token antes de cada
 * petición con el header Authorization. Debe responderla la configuración CORS
 * y no la seguridad, o el ERP no puede consultar nada desde otro origen.
 */
@AutoConfigureMockMvc
class CorsIntegrationTest extends AbstractIntegrationTest {

	private static final String ORIGEN_PERMITIDO = "http://localhost:5173";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void respondeElPreflightDeUnaRutaProtegidaParaUnOrigenPermitido() throws Exception {
		mockMvc.perform(options("/api/v1/refacciones").header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")).andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO));
	}

	@Test
	void rechazaElPreflightDeUnOrigenNoPermitido() throws Exception {
		mockMvc.perform(options("/api/v1/refacciones").header(HttpHeaders.ORIGIN, "https://sitio-ajeno.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}
}
