package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

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
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista de trabajo de Facturación (ERP-1.0): los prospectos que ya pasaron Pago
 * y esperan su unidad y su factura.
 */
@AutoConfigureMockMvc
@Transactional
class ProspectosPorFacturarIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/prospectos/por-facturar";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	void facturacionVeSoloLosProspectosEnFacturacionConSuVendedor() throws Exception {
		Usuario vendedor = registrar("vendedor.facturar", Role.VENTAS);
		long luis = prospecto(vendedor, "Luis Herrera", "4775550104", "FACTURACION");
		prospecto(vendedor, "Ana López", "4775550101", "PAGO");
		prospecto(vendedor, "Valeria Díaz", "4775550105", "ENTREGA");

		mockMvc.perform(conToken(get(URL), token(registrar("facturacion.facturar", Role.FACTURACION))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(luis)).andExpect(jsonPath("$[0].nombre").value("Luis Herrera"))
				.andExpect(jsonPath("$[0].telefono").value("4775550104"))
				.andExpect(jsonPath("$[0].vendedorId").value(vendedor.getId()))
				.andExpect(jsonPath("$[0].vendedor").value("Persona vendedor.facturar"))
				.andExpect(jsonPath("$[0].enFacturacionDesde").isNotEmpty());
	}

	@Test
	void soloFacturacionVeLaLista() throws Exception {
		mockMvc.perform(conToken(get(URL), token(registrar("vendedor.facturar", Role.VENTAS))))
				.andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(URL), token(registrar("inventario.facturar", Role.INVENTARIO))))
				.andExpect(status().isForbidden());
	}

	private long prospecto(Usuario vendedor, String nombre, String telefono, String etapa) throws Exception {
		String respuesta = mockMvc
				.perform(conToken(post("/api/v1/prospectos"), token(vendedor)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","telefono":"%s","correo":null}
								""".formatted(nombre, telefono)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
		entityManager.flush();
		jdbcTemplate.update("update prospecto set etapa = ? where id = ?", etapa, id);
		entityManager.clear();
		return id;
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
