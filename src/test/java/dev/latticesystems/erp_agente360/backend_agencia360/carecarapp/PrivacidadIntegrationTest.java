package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * M22.01 de punta a punta: aviso de privacidad versionado y su aceptación por
 * cuenta.
 */
@AutoConfigureMockMvc
class PrivacidadIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private long clienteId;
	private String token;
	private String tokenGerente;

	@BeforeEach
	void cliente() {
		String unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String correo = "aviso-" + unico + "@correo.com";
		clienteId = registroClientesService.registrar(new NuevoCliente("Ana Aviso", correo, null, null)).id();
		cuentasAppService.abrir(clienteId, "Temporal123");
		token = jwtService.generateToken(clienteId, correo, List.of(Role.CLIENTE_CARECAR));
		tokenGerente = jwtService.generateToken(1L, "gerente", List.of(Role.GERENTE_MARKETING));
	}

	@Test
	void laVersionUnoEsElAvisoQueLaAppTraiaYUnaCuentaNuevaNoLaHaAceptado() throws Exception {
		String texto = jdbc.queryForObject("select texto from aviso_privacidad where version = 1", String.class);
		assertThat(texto).contains("Agencia360").contains("Derechos ARCO");

		avisoVigente().andExpect(status().isOk()).andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.version").isNumber()).andExpect(jsonPath("$.texto").isNotEmpty())
				.andExpect(jsonPath("$.vigenteDesde").isNotEmpty()).andExpect(jsonPath("$.aceptado").value(false))
				.andExpect(jsonPath("$.aceptadoEn").doesNotExist());
	}

	@Test
	void laVersionDosNombraALosProveedoresExternosQueRecibenDatos() {
		String texto = jdbc.queryForObject("select texto from aviso_privacidad where version = 2", String.class);

		assertThat(texto).contains("Microsoft Azure").contains("Neon").contains("OpenStreetMap").contains("Gemini")
				.contains("Derechos ARCO");
		assertThat(texto)
				.doesNotContain("Transferencias: solo compartimos tus datos con autoridades cuando la ley lo exige");
	}

	@Test
	void unaCuentaNuevaVeComoVigenteAlMenosLaVersionDos() throws Exception {
		avisoVigente().andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
				.andExpect(jsonPath("$.aceptado").value(false));
	}

	@Test
	void aceptarRegistraLaVersionUnaSolaVezAunqueSeRepita() throws Exception {
		long aviso = idDelVigente();

		String primera = aceptar(aviso, token).andExpect(status().isOk()).andExpect(jsonPath("$.avisoId").value(aviso))
				.andExpect(jsonPath("$.version").isNumber()).andExpect(jsonPath("$.aceptadoEn").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String segunda = aceptar(aviso, token).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();

		assertThat(JsonPath.<String>read(segunda, "$.aceptadoEn")).isEqualTo(JsonPath.read(primera, "$.aceptadoEn"));
		Integer filas = jdbc.queryForObject("""
				select count(*) from aceptacion_aviso a join cuenta_app c on c.id = a.cuenta_app_id
				where c.cliente_id = ? and a.aviso_privacidad_id = ?
				""", Integer.class, clienteId, aviso);
		assertThat(filas).isEqualTo(1);
		avisoVigente().andExpect(jsonPath("$.aceptado").value(true)).andExpect(jsonPath("$.aceptadoEn").isNotEmpty());
	}

	@Test
	void unaVersionNuevaVuelveAPedirLaAceptacionYLaVieja_YaNoSirve() throws Exception {
		long vieja = idDelVigente();
		aceptar(vieja, token).andExpect(status().isOk());

		String cuerpo = publicar("{\"texto\":\"Aviso nuevo: ahora también usamos tu ubicación al pedir ayuda.\"}",
				tokenGerente).andExpect(status().isCreated()).andExpect(jsonPath("$.aceptado").value(false))
				.andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("ubicación"))).andReturn()
				.getResponse().getContentAsString();
		long nueva = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
		int versionNueva = JsonPath.read(cuerpo, "$.version");

		avisoVigente().andExpect(jsonPath("$.id").value(nueva)).andExpect(jsonPath("$.version").value(versionNueva))
				.andExpect(jsonPath("$.aceptado").value(false));
		aceptar(vieja, token).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("AVISO_NO_VIGENTE"));
		aceptar(nueva, token).andExpect(status().isOk());
		avisoVigente().andExpect(jsonPath("$.aceptado").value(true));

		// El aviso anterior sigue guardado con su aceptación: nada se edita.
		Integer aceptacionesDeLaVieja = jdbc.queryForObject("""
				select count(*) from aceptacion_aviso a join cuenta_app c on c.id = a.cuenta_app_id
				where c.cliente_id = ? and a.aviso_privacidad_id = ?
				""", Integer.class, clienteId, vieja);
		assertThat(aceptacionesDeLaVieja).isEqualTo(1);
	}

	@Test
	void unAvisoQueNoExisteDa404() throws Exception {
		aceptar(Long.MAX_VALUE, token).andExpect(status().isNotFound());
	}

	@Test
	void sinAvisoIdLaAceptacionEsUnaSolicitudInvalida() throws Exception {
		mockMvc.perform(post("/api/v1/app/privacidad/aceptaciones").contentType(MediaType.APPLICATION_JSON)
				.content("{}").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isBadRequest());
	}

	@Test
	void sinSesionDa401() throws Exception {
		mockMvc.perform(get("/api/v1/app/privacidad/aviso")).andExpect(status().isUnauthorized());
	}

	@Test
	void publicarSoloLoHaceElGerenteDeMarketingYConUnTextoValido() throws Exception {
		publicar("{\"texto\":\"Texto\"}", token).andExpect(status().isForbidden());
		publicar("{\"texto\":\"   \"}", tokenGerente).andExpect(status().isBadRequest());
		publicar("{}", tokenGerente).andExpect(status().isBadRequest());
		publicar("{\"texto\":\"" + "a".repeat(20_001) + "\"}", tokenGerente).andExpect(status().isBadRequest());
		publicar("{\"texto\":\"Texto\",\"vigenteDesde\":\"2000-01-01T00:00:00Z\"}", tokenGerente)
				.andExpect(status().isBadRequest());
	}

	@Test
	void unAvisoPublicadoNoSePuedeEditarNiBorrar() {
		long aviso = idDeLaVersionUno();
		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> jdbc.update("update aviso_privacidad set texto = 'otro' where id = ?", aviso))
				.hasMessageContaining("inmutable");
		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> jdbc.update("delete from aviso_privacidad where id = ?", aviso))
				.hasMessageContaining("inmutable");
	}

	private long idDeLaVersionUno() {
		return jdbc.queryForObject("select id from aviso_privacidad where version = 1", Long.class);
	}

	private long idDelVigente() throws Exception {
		String cuerpo = avisoVigente().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
	}

	private ResultActions avisoVigente() throws Exception {
		return mockMvc
				.perform(get("/api/v1/app/privacidad/aviso").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions aceptar(long avisoId, String bearer) throws Exception {
		return mockMvc.perform(post("/api/v1/app/privacidad/aceptaciones").contentType(MediaType.APPLICATION_JSON)
				.content("{\"avisoId\":" + avisoId + "}").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer));
	}

	private ResultActions publicar(String json, String bearer) throws Exception {
		return mockMvc.perform(post("/api/v1/avisos-privacidad").contentType(MediaType.APPLICATION_JSON).content(json)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer));
	}
}
