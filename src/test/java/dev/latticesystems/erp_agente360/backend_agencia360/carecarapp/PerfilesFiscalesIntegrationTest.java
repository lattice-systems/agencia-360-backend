package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AnonimizadorDeCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ExportadorDeDatos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Datos fiscales de punta a punta (V2-M08.05): alta, edición, predeterminado,
 * borrado, errores por campo, catálogos y los derechos ARCO. Cada prueba usa
 * clientes con correo propio y no asume totales.
 */
@AutoConfigureMockMvc
class PerfilesFiscalesIntegrationTest extends AbstractIntegrationTest {

	private static final String RUTA = "/api/v1/app/perfiles-fiscales";

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

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ExportadorDeDatos exportador;

	@Autowired
	private AnonimizadorDeCuenta anonimizador;

	@Autowired
	private ObjectMapper json;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Long cliente;
	private String token;
	private String tokenDeOtro;
	private String tokenDeCaja;

	@BeforeEach
	void clientes() {
		cliente = registrar("Dolores Gómez");
		token = tokenDe(cliente);
		tokenDeOtro = tokenDe(registrar("Otro Cliente"));
		String unico = UUID.randomUUID().toString().substring(0, 8);
		Rol caja = rolRepository.findByNombre("CAJA").orElseThrow();
		Usuario cajero = usuarioRepository.save(new Usuario("caja-" + unico, "hash", "Carla Caja", caja));
		tokenDeCaja = jwtService.generateToken(cajero.getId(), cajero.getUsuario(), List.of(Role.CAJA));
	}

	@Test
	void losCatalogosTraenRegimenesYUsosConSusReglas() throws Exception {
		con(token, get("/api/v1/app/catalogos-fiscales")).andExpect(status().isOk())
				.andExpect(jsonPath("$.regimenes[?(@.clave == '601')].moral").value(true))
				.andExpect(jsonPath("$.regimenes[?(@.clave == '601')].fisica").value(false))
				.andExpect(jsonPath("$.usosCfdi[?(@.clave == 'G03')].regimenes[0]").value("601"))
				.andExpect(jsonPath("$.usosCfdi[?(@.clave == 'CP01')]").isEmpty());
	}

	@Test
	void elPrimeroQuedaPredeterminadoYSeGuardaEnMayusculas() throws Exception {
		crear(cuerpo(" gode561231gr8 ", "Dolores   Gómez", "612", "G03", null)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.rfc").value("GODE561231GR8"))
				.andExpect(jsonPath("$.tipoPersona").value("FISICA"))
				.andExpect(jsonPath("$.razonSocial").value("DOLORES GÓMEZ"))
				.andExpect(jsonPath("$.predeterminado").value(true));

		con(token, get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].codigoPostal").value("76000"));
	}

	@Test
	void otroPuedeVolverseElPredeterminadoYSoloHayUno() throws Exception {
		Long fisica = id(crear(cuerpo("GODE561231GR8", "Dolores Gómez", "612", "G03", null)));
		Long moral = id(crear(cuerpo("EKU9003173C9", "Escuela Kemper Urgate", "601", "G03", true)));

		con(token, get(RUTA)).andExpect(jsonPath("$[0].id").value(moral))
				.andExpect(jsonPath("$[0].predeterminado").value(true)).andExpect(jsonPath("$[1].id").value(fisica))
				.andExpect(jsonPath("$[1].predeterminado").value(false));

		editar(fisica, cuerpo("GODE561231GR8", "Dolores Gómez", "612", "I03", true)).andExpect(status().isOk())
				.andExpect(jsonPath("$.usoCfdi").value("I03")).andExpect(jsonPath("$.predeterminado").value(true));
		assertThat(jdbc.queryForObject("select count(*) from perfil_fiscal where cliente_id = ? and predeterminado",
				Integer.class, cliente)).isEqualTo(1);
	}

	@Test
	void alBorrarElPredeterminadoLoTomaElQueQueda() throws Exception {
		Long primero = id(crear(cuerpo("GODE561231GR8", "Dolores Gómez", "612", "G03", null)));
		Long segundo = id(crear(cuerpo("EKU9003173C9", "Escuela Kemper Urgate", "601", "G03", null)));

		con(token, delete(RUTA + "/{id}", primero)).andExpect(status().isNoContent());

		con(token, get(RUTA + "/{id}", segundo)).andExpect(status().isOk())
				.andExpect(jsonPath("$.predeterminado").value(true));
		con(token, get(RUTA + "/{id}", primero)).andExpect(status().isNotFound());
	}

	@Test
	void losErroresDicenQueCorregirEnCadaCampo() throws Exception {
		crear(cuerpo("GODE561231GR9", " ", "601", "D01", null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_FISCALES_INVALIDOS"))
				.andExpect(jsonPath("$.campos.rfc").value(org.hamcrest.Matchers.containsString("dígito verificador")))
				.andExpect(jsonPath("$.campos.razonSocial").exists())
				.andExpect(jsonPath("$.campos.codigoPostal").doesNotExist());

		crear(cuerpo("EKU9003173C9", "Escuela Kemper Urgate", "601", "D01", null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.usoCfdi").value("Ese uso de CFDI no aplica a una persona moral"))
				.andExpect(jsonPath("$.campos.rfc").doesNotExist());
		assertThat(
				jdbc.queryForObject("select count(*) from perfil_fiscal where cliente_id = ?", Integer.class, cliente))
				.isZero();
	}

	@Test
	void otroErrorNoTraeCampos() throws Exception {
		con(token, get(RUTA + "/{id}", 999_999_999L)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.campos").doesNotExist());
	}

	@Test
	void soloSeVenYCambianLosPropios() throws Exception {
		Long propio = id(crear(cuerpo("GODE561231GR8", "Dolores Gómez", "612", "G03", null)));

		con(tokenDeOtro, get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		con(tokenDeOtro, get(RUTA + "/{id}", propio)).andExpect(status().isNotFound());
		con(tokenDeOtro,
				put(RUTA + "/{id}", propio).contentType(MediaType.APPLICATION_JSON)
						.content(cuerpo("EKU9003173C9", "Escuela", "601", "G03", true)))
				.andExpect(status().isNotFound());
		con(tokenDeOtro, delete(RUTA + "/{id}", propio)).andExpect(status().isNotFound());
		con(token, get(RUTA + "/{id}", propio)).andExpect(jsonPath("$.rfc").value("GODE561231GR8"));
	}

	@Test
	void soloLosClientesDeLaApp() throws Exception {
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
		con(tokenDeCaja, get(RUTA)).andExpect(status().isForbidden());
		con(tokenDeCaja, get("/api/v1/app/catalogos-fiscales")).andExpect(status().isForbidden());
	}

	@Test
	void laCopiaDeMisDatosLosTraeYCancelarLaCuentaLosBorra() throws Exception {
		crear(cuerpo("GODE561231GR8", "Dolores Gómez", "612", "G03", null)).andExpect(status().isCreated());

		JsonNode copia = json.readTree(exportador.exportar(cliente, Instant.now()));
		assertThat(copia.at("/perfilesFiscales/0/rfc").asText()).isEqualTo("GODE561231GR8");
		assertThat(copia.at("/alcance/incluye").toString()).contains("datos fiscales");

		anonimizador.anonimizar(cliente);
		assertThat(
				jdbc.queryForObject("select count(*) from perfil_fiscal where cliente_id = ?", Integer.class, cliente))
				.isZero();
	}

	private Long registrar(String nombre) {
		String correo = "fiscal-" + UUID.randomUUID().toString().substring(0, 8) + "@correo.com";
		Long id = registroClientesService.registrar(new NuevoCliente(nombre, correo, null, null)).id();
		cuentasAppService.abrir(id, "Temporal123");
		return id;
	}

	private String tokenDe(Long clienteId) {
		String correo = jdbc.queryForObject("select correo from cliente where id = ?", String.class, clienteId);
		return jwtService.generateToken(clienteId, correo, List.of(Role.CLIENTE_CARECAR));
	}

	private static String cuerpo(String rfc, String razonSocial, String regimen, String uso, Boolean predeterminado) {
		return """
				{"rfc":"%s","razonSocial":"%s","regimenFiscal":"%s","codigoPostal":"76000","usoCfdi":"%s",\
				"correo":null,"predeterminado":%s}""".formatted(rfc, razonSocial, regimen, uso, predeterminado);
	}

	private ResultActions crear(String cuerpo) throws Exception {
		return con(token, post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions editar(Long id, String cuerpo) throws Exception {
		return con(token, put(RUTA + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private static Long id(ResultActions respuesta) throws Exception {
		Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}

	private ResultActions con(String jwt, MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt));
	}
}
