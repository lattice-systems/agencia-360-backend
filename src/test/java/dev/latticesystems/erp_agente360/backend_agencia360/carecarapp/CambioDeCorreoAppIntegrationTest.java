package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * V2-M01.07 · cambiar el correo, con verificación.
 *
 * <p>
 * Solo el correo: verificar el teléfono exige mandar un código <b>al
 * teléfono</b> y no hay SMS en ningún lado.
 *
 * <p>
 * Lo que más se prueba aquí son las dos cosas que harían daño de verdad: que
 * <b>el correo no cambie hasta confirmar</b> —si cambiara antes, el cliente se
 * quedaría con uno que no ha probado que es suyo y, como el usuario de la
 * cuenta lo sigue, sin poder entrar— y que <b>pedir el cambio exija la
 * contraseña</b>, porque sin eso quien tenga el celular desbloqueado se queda
 * con la cuenta.
 */
@AutoConfigureMockMvc
@Transactional
class CambioDeCorreoAppIntegrationTest extends AbstractIntegrationTest {

	private static final String PERFIL = "/api/v1/app/perfil";
	private static final String CORREO = "ana.lopez@correo.com";
	private static final String CORREO_NUEVO = "ana.nueva@correo.com";
	private static final String CONTRASENA = "Temporal123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Long clienteId;
	private String token;

	@BeforeEach
	void abrirCuenta() throws Exception {
		clienteId = registroClientesService.registrar(new NuevoCliente("Ana López", CORREO, "4771234567", null)).id();
		cuentasAppService.abrir(clienteId, CONTRASENA);
		token = accessTokenDe(login(CORREO, CONTRASENA));
	}

	@Test
	void pedirElCambioMandaElCodigoYNoCambiaNadaTodavia() throws Exception {
		pedirCambio(CORREO_NUEVO, CONTRASENA).andExpect(status().isAccepted()).andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.expira").isNotEmpty());

		// Lo que de verdad importa: el correo sigue siendo el de antes.
		assertThat(correoDelCliente()).isEqualTo(CORREO);
		assertThat(usuarioDeLaCuenta()).isEqualTo(CORREO);
		// Y el código fue al correo NUEVO, que es a donde sirve mandarlo.
		verify(envioCorreos).enviarCodigoDeContacto(eq(CORREO_NUEVO), eq("Ana López"), anyString());
	}

	@Test
	void laContrasenaEquivocadaNoEmpiezaNada() throws Exception {
		// 400 y no 401 a proposito: la app lee un 401 como sesion vencida y
		// cerraria la sesion sin explicar nada.
		pedirCambio(CORREO_NUEVO, "Incorrecta1").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

		verify(envioCorreos, never()).enviarCodigoDeContacto(anyString(), anyString(), anyString());
		assertThat(correoDelCliente()).isEqualTo(CORREO);
	}

	@Test
	void unCorreoSinFormatoEsDatosInvalidosYNoSeConfundeConLaContrasena() throws Exception {
		// Los dos son 400 y solo los separa `codigo`. Si compartieran codigo, la
		// pantalla culparia al correo cuando fallo la contrasena, o al reves.
		pedirCambio("no-es-correo", CONTRASENA).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void unCorreoDeOtroClienteResponde409() throws Exception {
		Long otro = registroClientesService
				.registrar(new NuevoCliente("Luis Pérez", "luis.perez@correo.com", null, null)).id();
		cuentasAppService.abrir(otro, CONTRASENA);

		pedirCambio("luis.perez@correo.com", CONTRASENA).andExpect(status().isConflict());

		assertThat(correoDelCliente()).isEqualTo(CORREO);
	}

	@Test
	void confirmarCambiaElCorreoYTambienElUsuarioDeLaCuenta() throws Exception {
		// El segundo criterio de la historia, tal cual: al cambiar el correo
		// cambia tambien `cuenta_app.usuario` (V2-T05.03).
		long id = idDeLaVerificacion(pedirCambio(CORREO_NUEVO, CONTRASENA));
		String codigo = codigoEnviado();

		confirmar(id, codigo).andExpect(status().isOk()).andExpect(jsonPath("$.correo").value(CORREO_NUEVO));

		assertThat(correoDelCliente()).isEqualTo(CORREO_NUEVO);
		assertThat(usuarioDeLaCuenta()).isEqualTo(CORREO_NUEVO);
	}

	@Test
	void despuesDeConfirmarSeEntraConElCorreoNuevoYNoConElViejo() throws Exception {
		// De punta a punta: es lo que hace que cambiar el correo sea delicado.
		long id = idDeLaVerificacion(pedirCambio(CORREO_NUEVO, CONTRASENA));
		confirmar(id, codigoEnviado()).andExpect(status().isOk());

		login(CORREO_NUEVO, CONTRASENA).andExpect(status().isOk());
		login(CORREO, CONTRASENA).andExpect(status().isUnauthorized());
	}

	@Test
	void unCodigoEquivocadoNoCambiaNada() throws Exception {
		long id = idDeLaVerificacion(pedirCambio(CORREO_NUEVO, CONTRASENA));

		confirmar(id, "000000").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));

		assertThat(correoDelCliente()).isEqualTo(CORREO);
	}

	@Test
	void elCodigoSirveUnaSolaVez() throws Exception {
		long id = idDeLaVerificacion(pedirCambio(CORREO_NUEVO, CONTRASENA));
		String codigo = codigoEnviado();
		confirmar(id, codigo).andExpect(status().isOk());

		confirmar(id, codigo).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
	}

	@Test
	void unaVerificacionDeOtroClienteResponde404() throws Exception {
		// 404 y no 403: un 403 diria «existe, pero no es tuya».
		long id = idDeLaVerificacion(pedirCambio(CORREO_NUEVO, CONTRASENA));

		Long otro = registroClientesService
				.registrar(new NuevoCliente("Luis Pérez", "luis.perez@correo.com", null, null)).id();
		cuentasAppService.abrir(otro, CONTRASENA);
		String tokenAjeno = accessTokenDe(login("luis.perez@correo.com", CONTRASENA));

		mockMvc.perform(post(PERFIL + "/verificaciones/" + id + "/confirmar")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAjeno).contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + codigoEnviado() + "\"}")).andExpect(status().isNotFound());

		assertThat(correoDelCliente()).isEqualTo(CORREO);
	}

	@Test
	void lasDosRutasExigenSesionDeCliente() throws Exception {
		mockMvc.perform(patch(PERFIL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"x@y.com\",\"contrasenaActual\":\"z\"}")).andExpect(status().isUnauthorized());
		mockMvc.perform(post(PERFIL + "/verificaciones/1/confirmar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"123456\"}")).andExpect(status().isUnauthorized());
	}

	private ResultActions pedirCambio(String correo, String contrasena) throws Exception {
		return mockMvc.perform(patch(PERFIL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasenaActual\":\"%s\"}".formatted(correo, contrasena)));
	}

	private ResultActions confirmar(long id, String codigo) throws Exception {
		return mockMvc.perform(post(PERFIL + "/verificaciones/" + id + "/confirmar")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + codigo + "\"}"));
	}

	private ResultActions login(String correo, String contrasena) throws Exception {
		return mockMvc.perform(post("/api/v1/app/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
	}

	/** El código que se mandó por correo; es el único lugar donde existe. */
	private String codigoEnviado() {
		ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
		verify(envioCorreos).enviarCodigoDeContacto(anyString(), anyString(), codigo.capture());
		return codigo.getValue();
	}

	private static long idDeLaVerificacion(ResultActions resultado) throws Exception {
		return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private static String accessTokenDe(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	private String correoDelCliente() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("select correo from cliente where id = ?", String.class, clienteId);
	}

	private String usuarioDeLaCuenta() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("select usuario from cuenta_app where cliente_id = ?", String.class,
				clienteId);
	}
}
