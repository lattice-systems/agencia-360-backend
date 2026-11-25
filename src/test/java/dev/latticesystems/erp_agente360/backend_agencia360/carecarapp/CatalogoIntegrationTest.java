package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de la app (APP-4.1) con la migración V22 y base real. */
@AutoConfigureMockMvc
@Transactional
class CatalogoIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeCliente;
	private String tokenDeGerente;

	@BeforeEach
	void prepararTokens() {
		Long cliente = registroClientesService.registrar(new NuevoCliente("Carla Ruiz", "carla@correo.com", null, null))
				.id();
		cuentasAppService.abrir(cliente, "Temporal123");
		tokenDeCliente = jwtService.generateToken(cliente, "carla@correo.com", List.of(Role.CLIENTE_CARECAR));
		Rol rol = rolRepository.findByNombre("GERENTE_MARKETING").orElseThrow();
		Usuario gerente = usuarioRepository.save(new Usuario("gerente-catalogo", "hash-de-prueba", "Gerente", rol));
		tokenDeGerente = jwtService.generateToken(gerente.getId(), gerente.getUsuario(), List.of(rol.comoRole()));
	}

	@Test
	void laAppSoloVeLosModelosActivos() throws Exception {
		// El catálogo es global y otras pruebas siembran modelos en la misma base: esta
		// prueba mira solo los de su propia marca, no el total.
		String marca = "Marca" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		agregar("""
				{"marca":"%s","modelo":"Kicks","anio":2026,"version":"Exclusive","precioDesde":489900,
				"imagenUrl":"https://example.com/kicks.png","descripcion":"SUV compacta"}""".formatted(marca))
				.andExpect(status().isCreated());
		String sentra = agregar("""
				{"marca":"%s","modelo":"Sentra","anio":2026}""".formatted(marca)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		agregar("""
				{"marca":"%s","modelo":"kicks","anio":2026,"version":"exclusive"}""".formatted(marca.toLowerCase()))
				.andExpect(status().isConflict());
		agregar("""
				{"marca":"%s","modelo":"Versa","anio":2026,"imagenUrl":"http://inseguro"}""".formatted(marca))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/catalogo/{id}/retirar", (Integer) JsonPath.read(sentra, "$.id"))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeGerente)).andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));

		String delCliente = mockMvc
				.perform(get("/api/v1/app/catalogo").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> visibles = JsonPath.read(delCliente, "$[?(@.marca == '" + marca + "')]");
		assertThat(visibles).hasSize(1);
		assertThat(visibles.get(0)).containsEntry("modelo", "Kicks").containsEntry("imagenUrl",
				"https://example.com/kicks.png");
		assertThat(((Number) visibles.get(0).get("precioDesde")).doubleValue()).isEqualTo(489900.0);

		String delPersonal = mockMvc
				.perform(get("/api/v1/catalogo").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeGerente))
				.andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> todos = JsonPath.read(delPersonal, "$[?(@.marca == '" + marca + "')]");
		assertThat(todos).hasSize(2);
	}

	@Test
	void elClienteNoAdministraElCatalogo() throws Exception {
		mockMvc.perform(post("/api/v1/catalogo").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"marca\":\"Nissan\",\"modelo\":\"X\",\"anio\":2026}")).andExpect(status().isForbidden());
	}

	private ResultActions agregar(String cuerpo) throws Exception {
		return mockMvc.perform(post("/api/v1/catalogo").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeGerente)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}
}
