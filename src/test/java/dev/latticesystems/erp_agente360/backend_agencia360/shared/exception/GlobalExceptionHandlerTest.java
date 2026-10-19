package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void errorInesperadoNoExponeElDetalleTecnico() {
		var request = new MockHttpServletRequest("GET", "/api/v1/prospectos");

		var respuesta = handler.handleUnexpected(
				new IllegalStateException("could not execute statement; SQL [insert into prospecto ...]"), request,
				new MockHttpServletResponse());

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(respuesta.getBody()).isNotNull();
		assertThat(respuesta.getBody().message()).isEqualTo(GlobalExceptionHandler.MENSAJE_ERROR_INESPERADO)
				.doesNotContain("SQL");
		assertThat(respuesta.getBody().path()).isEqualTo("/api/v1/prospectos");
	}

	@Test
	void noRespondeUnErrorSobreUnaRespuestaQueYaSeEnvio() {
		// Una conexión en tiempo real (ERP-2.1) que el navegador ya cerró.
		var request = new MockHttpServletRequest("GET", "/api/v1/vehiculos/eventos");
		var response = new MockHttpServletResponse();
		response.setCommitted(true);

		var respuesta = handler.handleUnexpected(new IOException("Broken pipe"), request, response);

		assertThat(respuesta).isNull();
	}
}
