package dev.latticesystems.erp_agente360.backend_agencia360.acceso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba el módulo de acceso de punta a punta: migración, seguridad, endpoints
 * y base de datos real (Testcontainers). Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class AccesoIntegrationTest extends AbstractIntegrationTest {

	private static final String CONTRASENA = "Secreta123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	@Test
	void laMigracionCargaLosNueveRolesDeLaMatriz() {
		assertThat(rolRepository.findAll()).extracting(Rol::getNombre).containsExactlyInAnyOrder("ADMINISTRADOR",
				"GERENTE_MARKETING", "VENTAS", "FACTURACION", "INVENTARIO", "SERVICIO", "REFACCIONES", "CAJA",
				"CONTABILIDAD");
	}

	@Test
	void iniciaSesionEnElErpConCredencialesCorrectas() throws Exception {
		registrar("caja1", "CAJA");

		mockMvc.perform(login("caja1", CONTRASENA, "ERP")).andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.rol").value("CAJA")).andExpect(jsonPath("$.aplicacion").value("ERP"));
	}

	@Test
	void elTokenLlevaElIdDelUsuarioEnLaBase() throws Exception {
		Usuario vendedor = registrar("vendedor1", "VENTAS");

		var actor = jwtService.extraerUsuario(jwtService.parseClaims(token("vendedor1", "CRM")));

		assertThat(actor.id()).isEqualTo(vendedor.getId());
		assertThat(actor.tieneRol(Role.VENTAS)).isTrue();
	}

	@Test
	void respondeNoAutorizadoConContrasenaIncorrecta() throws Exception {
		registrar("caja1", "CAJA");

		mockMvc.perform(login("caja1", "incorrecta", "ERP")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"));
	}

	@Test
	void unVendedorNoEntraAlErpPeroSiAlCrm() throws Exception {
		registrar("vendedor1", "VENTAS");

		mockMvc.perform(login("vendedor1", CONTRASENA, "ERP")).andExpect(status().isForbidden());
		mockMvc.perform(login("vendedor1", CONTRASENA, "CRM")).andExpect(status().isOk());
	}

	@Test
	void rechazaLoginSinAplicacion() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuario\":\"caja1\",\"contrasena\":\"x\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void lasRutasProtegidasExigenToken() throws Exception {
		mockMvc.perform(get("/api/v1/usuarios")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void unRolSinPermisosDeAdministracionNoVeUsuarios() throws Exception {
		registrar("caja1", "CAJA");
		String token = token("caja1", "ERP");

		mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void elAdministradorCreaUnUsuarioQueDespuesIniciaSesion() throws Exception {
		registrar("admin1", "ADMINISTRADOR");
		String token = token("admin1", "ERP");

		mockMvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"usuario":"inventario1","contrasena":"Otra12345",
						 "nombre":"Encargado de inventario","rol":"INVENTARIO"}
						""")).andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("INVENTARIO"))
				.andExpect(jsonPath("$.contrasena").doesNotExist());

		mockMvc.perform(login("inventario1", "Otra12345", "ERP")).andExpect(status().isOk());
	}

	@Test
	void elAdministradorNoPuedeCrearVendedores() throws Exception {
		registrar("admin1", "ADMINISTRADOR");
		String token = token("admin1", "ERP");

		mockMvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"usuario":"vendedor9","contrasena":"Otra12345","nombre":"Vendedor","rol":"VENTAS"}
						""")).andExpect(status().isForbidden());
	}

	@Test
	void elGerenteDeMarketingSoloVeUsuariosDelCrm() throws Exception {
		registrar("gerente1", "GERENTE_MARKETING");
		registrar("vendedor1", "VENTAS");
		registrar("caja1", "CAJA");
		String token = token("gerente1", "CRM");

		mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$[?(@.usuario == 'vendedor1')]").exists())
				.andExpect(jsonPath("$[?(@.usuario == 'caja1')]").doesNotExist());
	}

	@Test
	void rechazaUsuarioRepetido() throws Exception {
		registrar("admin1", "ADMINISTRADOR");
		registrar("caja1", "CAJA");
		String token = token("admin1", "ERP");

		mockMvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"usuario":"caja1","contrasena":"Otra12345","nombre":"Otro cajero","rol":"CAJA"}
						""")).andExpect(status().isConflict());
	}

	private Usuario registrar(String usuario, String rol) {
		Rol encontrado = rolRepository.findByNombre(rol).orElseThrow();
		return usuarioRepository
				.save(new Usuario(usuario, passwordEncoder.encode(CONTRASENA), "Persona " + usuario, encontrado));
	}

	private String token(String usuario, String aplicacion) throws Exception {
		String respuesta = mockMvc.perform(login(usuario, CONTRASENA, aplicacion)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.accessToken");
	}

	private static RequestBuilder login(String usuario, String contrasena, String aplicacion) {
		String cuerpo = """
				{"usuario":"%s","contrasena":"%s","aplicacion":"%s"}
				""".formatted(usuario, contrasena, aplicacion);
		return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}
}
