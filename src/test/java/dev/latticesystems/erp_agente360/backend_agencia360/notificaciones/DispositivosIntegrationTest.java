package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * V2-M06.02: la app registra el token de push tras iniciar sesión y lo da de
 * baja al cerrarla, con la migración V60 y base real.
 */
@AutoConfigureMockMvc
@Transactional
class DispositivosIntegrationTest extends AbstractIntegrationTest {

	private static final String INSTALACION = "5b0c1d52-3f4e-4a1b-9c6d-7e8f90a1b2c3";

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

	private Long ana;
	private String tokenAna;

	@BeforeEach
	void preparar() {
		String sufijo = UUID.randomUUID().toString().substring(0, 8);
		ana = cliente("Ana Push", "ana.push." + sufijo + "@correo.com");
		tokenAna = jwtService.generateToken(ana, "ana.push." + sufijo + "@correo.com", List.of(Role.CLIENTE_CARECAR));
	}

	private Long cliente(String nombre, String correo) {
		Long id = registroClientesService.registrar(new NuevoCliente(nombre, correo, null, null)).id();
		cuentasAppService.abrir(id, "Temporal123");
		return id;
	}

	private String tokenDe(Long clienteId) {
		return jwtService.generateToken(clienteId, "otro@correo.com", List.of(Role.CLIENTE_CARECAR));
	}

	private ResultActions registrar(String instalacion, String bearer, String cuerpo) throws Exception {
		MockHttpServletRequestBuilder peticion = put("/api/v1/app/dispositivos/" + instalacion)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		if (bearer != null) {
			peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
		}
		return mockMvc.perform(peticion);
	}

	private static String cuerpo(String token, String plataforma) {
		return "{\"tokenFcm\":\"" + token + "\",\"plataforma\":\"" + plataforma + "\"}";
	}

	private List<Map<String, Object>> filas(String instalacion) {
		return jdbc.queryForList("select * from dispositivo_push where instalacion_id = ?", instalacion);
	}

	@Test
	void registraElTokenDeLaInstalacionALaCuentaDelCliente() throws Exception {
		registrar(INSTALACION, tokenAna, cuerpo("token-uno", "android")).andExpect(status().isNoContent());

		List<Map<String, Object>> filas = filas(INSTALACION);
		assertThat(filas).hasSize(1);
		assertThat(filas.get(0)).containsEntry("token_fcm", "token-uno").containsEntry("plataforma", "android")
				.containsEntry("idioma", "es").containsEntry("activo", true);
		Long cuenta = jdbc.queryForObject("select id from cuenta_app where cliente_id = ?", Long.class, ana);
		assertThat(filas.get(0).get("cuenta_app_id")).isEqualTo(cuenta);
	}

	@Test
	void volverARegistrarActualizaSinDuplicar() throws Exception {
		registrar(INSTALACION, tokenAna, cuerpo("token-uno", "android")).andExpect(status().isNoContent());
		jdbc.update("update dispositivo_push set activo = false where instalacion_id = ?", INSTALACION);

		registrar(INSTALACION, tokenAna, "{\"tokenFcm\":\"token-dos\",\"plataforma\":\"IOS\",\"idioma\":\"en\"}")
				.andExpect(status().isNoContent());

		List<Map<String, Object>> filas = filas(INSTALACION);
		assertThat(filas).hasSize(1);
		assertThat(filas.get(0)).containsEntry("token_fcm", "token-dos").containsEntry("plataforma", "ios")
				.containsEntry("idioma", "en").containsEntry("activo", true);
	}

	@Test
	void unaInstalacionPasaALaCuentaDeQuienEntraDespuesEnElMismoTelefono() throws Exception {
		Long bruno = cliente("Bruno Push", "bruno.push." + UUID.randomUUID() + "@correo.com");
		registrar(INSTALACION, tokenAna, cuerpo("token-uno", "android")).andExpect(status().isNoContent());

		registrar(INSTALACION, tokenDe(bruno), cuerpo("token-uno", "android")).andExpect(status().isNoContent());

		Long cuentaBruno = jdbc.queryForObject("select id from cuenta_app where cliente_id = ?", Long.class, bruno);
		assertThat(filas(INSTALACION)).hasSize(1);
		assertThat(filas(INSTALACION).get(0).get("cuenta_app_id")).isEqualTo(cuentaBruno);
	}

	@Test
	void elMismoTokenEnOtraInstalacionDescartaLaFilaVieja() throws Exception {
		String vieja = "11111111-aaaa-bbbb-cccc-111111111111";
		registrar(vieja, tokenAna, cuerpo("token-reinstalado", "android")).andExpect(status().isNoContent());

		registrar(INSTALACION, tokenAna, cuerpo("token-reinstalado", "android")).andExpect(status().isNoContent());

		assertThat(filas(vieja)).isEmpty();
		assertThat(filas(INSTALACION)).hasSize(1);
	}

	@Test
	void darDeBajaBorraElTokenDeEsaInstalacion() throws Exception {
		registrar(INSTALACION, tokenAna, cuerpo("token-uno", "android")).andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/app/dispositivos/" + INSTALACION).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenAna)).andExpect(status().isNoContent());

		assertThat(filas(INSTALACION)).isEmpty();
	}

	@Test
	void darDeBajaDosVecesOUnaInstalacionQueNoExisteEsIgualUn204() throws Exception {
		mockMvc.perform(delete("/api/v1/app/dispositivos/" + INSTALACION).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenAna)).andExpect(status().isNoContent());
	}

	@Test
	void unClienteNoPuedeDarDeBajaLaInstalacionDeOtro() throws Exception {
		Long bruno = cliente("Bruno Push", "bruno.push." + UUID.randomUUID() + "@correo.com");
		registrar(INSTALACION, tokenAna, cuerpo("token-uno", "android")).andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/app/dispositivos/" + INSTALACION).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDe(bruno))).andExpect(status().isNoContent());

		assertThat(filas(INSTALACION)).hasSize(1);
	}

	@Test
	void rechazaUnTokenVacioUnaPlataformaDesconocidaOUnaInstalacionCorta() throws Exception {
		registrar(INSTALACION, tokenAna, cuerpo("", "android")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		registrar(INSTALACION, tokenAna, cuerpo("token-uno", "windows")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		registrar("corta", tokenAna, cuerpo("token-uno", "ios")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		assertThat(filas(INSTALACION)).isEmpty();
	}

	@Test
	void sinTokenDeAccesoResponde401() throws Exception {
		registrar(INSTALACION, null, cuerpo("token-uno", "android")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/app/dispositivos/" + INSTALACION)).andExpect(status().isUnauthorized());
	}

	@Test
	void unUsuarioDelPersonalNoPuedeRegistrarDispositivos() throws Exception {
		String personal = jwtService.generateToken(1L, "gerente", List.of(Role.ADMINISTRADOR));

		registrar(INSTALACION, personal, cuerpo("token-uno", "android")).andExpect(status().isForbidden());
	}
}
