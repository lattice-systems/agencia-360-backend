package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba el acceso de los clientes a la app de punta a punta (APP-2.1, APP-2.2
 * y APP-2.3, contrato v1.1): migración, seguridad, endpoints y base de datos
 * real (Testcontainers). Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class AccesoAppIntegrationTest extends AbstractIntegrationTest {

	private static final String AUTH = "/api/v1/app/auth";
	private static final String CORREO = "ana.lopez@correo.com";
	private static final String TEMPORAL = "Temporal123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Long clienteId;

	@BeforeEach
	void abrirCuenta() {
		clienteId = registroClientesService.registrar(new NuevoCliente("Ana López", CORREO, null, null)).id();
		cuentasAppService.abrir(clienteId, TEMPORAL);
	}

	@Test
	void iniciaSesionConElCorreoSinDistinguirMayusculas() throws Exception {
		login(" Ana.Lopez@Correo.com ", TEMPORAL).andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.debeCambiarContrasena").value(true));
	}

	@Test
	void respondeCredencialesInvalidasSinRevelarSiElCorreoExiste() throws Exception {
		login(CORREO, "Incorrecta1").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
				.andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
		login("nadie@correo.com", TEMPORAL).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
	}

	@Test
	void bloqueaElLoginTrasCincoIntentosFallidos() throws Exception {
		for (int i = 0; i < 5; i++) {
			login(CORREO, "Incorrecta1").andExpect(status().isUnauthorized());
		}

		login(CORREO, TEMPORAL).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"))
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	@Test
	void unaCuentaDesactivadaNoEntraYSuTokenDejaDeServir() throws Exception {
		String token = accessTokenDe(login(CORREO, TEMPORAL));

		cuentasAppService.desactivar(clienteId);

		login(CORREO, TEMPORAL).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
		mockMvc.perform(get("/api/v1/app/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
	}

	@Test
	void elPerfilEsDelClienteQueInicioSesion() throws Exception {
		String token = accessTokenDe(login(CORREO, TEMPORAL));

		mockMvc.perform(get("/api/v1/app/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(clienteId))
				.andExpect(jsonPath("$.nombre").value("Ana López")).andExpect(jsonPath("$.correo").value(CORREO));
	}

	@Test
	void elPersonalNoUsaLasRutasDeLaAppYElClienteNoUsaLasDelPersonal() throws Exception {
		mockMvc.perform(get("/api/v1/app/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDePersonal()))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/app/perfil")).andExpect(status().isUnauthorized());

		String tokenCliente = accessTokenDe(login(CORREO, TEMPORAL));
		mockMvc.perform(get("/api/v1/clientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
				.andExpect(status().isForbidden());
	}

	@Test
	void elRefreshTokenSeUsaUnaSolaVez() throws Exception {
		String refresh = refreshTokenDe(login(CORREO, TEMPORAL));

		String nuevo = refreshTokenDe(
				refrescar(refresh).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()));

		refrescar(refresh).andExpect(status().isUnauthorized());
		// Reutilizar uno revocado cierra todas las sesiones de la cuenta.
		refrescar(nuevo).andExpect(status().isUnauthorized());
	}

	@Test
	void cerrarSesionRevocaElRefreshToken() throws Exception {
		String refresh = refreshTokenDe(login(CORREO, TEMPORAL));

		mockMvc.perform(post(AUTH + "/cerrar-sesion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isNoContent());

		refrescar(refresh).andExpect(status().isUnauthorized());
	}

	@Test
	void cambiaLaContrasenaTemporalEnElPrimerIngreso() throws Exception {
		String token = accessTokenDe(login(CORREO, TEMPORAL));

		cambiarContrasena(token, TEMPORAL, "corta1").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		cambiarContrasena(token, TEMPORAL, TEMPORAL).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La contraseña nueva debe ser distinta de la actual"));
		cambiarContrasena(token, "Incorrecta1", "NuevaClave9").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));
		cambiarContrasena(token, TEMPORAL, "NuevaClave9").andExpect(status().isNoContent());

		login(CORREO, "NuevaClave9").andExpect(status().isOk())
				.andExpect(jsonPath("$.debeCambiarContrasena").value(false));
	}

	@Test
	void recuperaLaContrasenaConElCodigoQueLlegaPorCorreo() throws Exception {
		recuperar(CORREO).andExpect(status().isAccepted());
		ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
		verify(envioCorreos).enviarCodigoRecuperacion(eq(CORREO), eq("Ana López"), codigo.capture());

		String incorrecto = codigo.getValue().equals("000000") ? "111111" : "000000";
		restablecer(incorrecto, "NuevaClave9").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
		restablecer(codigo.getValue(), "corta").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		restablecer(codigo.getValue(), "NuevaClave9").andExpect(status().isNoContent());

		login(CORREO, "NuevaClave9").andExpect(status().isOk());
		restablecer(codigo.getValue(), "OtraClave9").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
	}

	@Test
	void pedirUnCodigoParaUnCorreoInexistenteRespondeIgualSinEnviarCorreo() throws Exception {
		recuperar("nadie@correo.com").andExpect(status().isAccepted());

		verify(envioCorreos, never()).enviarCodigoRecuperacion(anyString(), anyString(), anyString());
	}

	private ResultActions login(String correo, String contrasena) throws Exception {
		return mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
	}

	private ResultActions refrescar(String refreshToken) throws Exception {
		return mockMvc.perform(post(AUTH + "/refrescar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private ResultActions cambiarContrasena(String token, String actual, String nueva) throws Exception {
		return mockMvc.perform(post(AUTH + "/cambiar-contrasena").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contrasenaActual\":\"%s\",\"contrasenaNueva\":\"%s\"}".formatted(actual, nueva)));
	}

	private ResultActions recuperar(String correo) throws Exception {
		return mockMvc.perform(post(AUTH + "/recuperar-contrasena").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\"}"));
	}

	private ResultActions restablecer(String codigo, String nueva) throws Exception {
		return mockMvc.perform(post(AUTH + "/restablecer-contrasena").contentType(MediaType.APPLICATION_JSON).content(
				"{\"correo\":\"%s\",\"codigo\":\"%s\",\"contrasenaNueva\":\"%s\"}".formatted(CORREO, codigo, nueva)));
	}

	private String tokenDePersonal() {
		Rol rol = rolRepository.findByNombre("GERENTE_MARKETING").orElseThrow();
		Usuario gerente = usuarioRepository.save(new Usuario("gerente-prueba", "hash-de-prueba", "Gerente", rol));
		return jwtService.generateToken(gerente.getId(), gerente.getUsuario(), List.of(rol.comoRole()));
	}

	private static String accessTokenDe(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	private static String refreshTokenDe(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.refreshToken");
	}
}
