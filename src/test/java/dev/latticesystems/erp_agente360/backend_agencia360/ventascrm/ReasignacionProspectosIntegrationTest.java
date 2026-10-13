package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba la reasignación de prospectos de punta a punta (CRM-0.1): seguridad,
 * validación del vendedor contra el módulo de acceso y visibilidad después de
 * reasignar. Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class ReasignacionProspectosIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/prospectos";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Test
	void elGerenteReasignaYElProspectoPasaAlOtroVendedor() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		long id = crearProspecto(vendedor1, "Ana López", "4771234567");

		mockMvc.perform(reasignacion(token(gerente), id, vendedor2.getId())).andExpect(status().isOk())
				.andExpect(jsonPath("$.vendedorId").value(vendedor2.getId()))
				.andExpect(jsonPath("$.etapa").value("PRIMER_CONTACTO"));

		mockMvc.perform(conToken(get(URL), token(vendedor1))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(conToken(get(URL), token(vendedor2))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("Ana López"));
	}

	@Test
	void unVendedorNoPuedeReasignar() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		long id = crearProspecto(vendedor1, "Ana López", "4771234567");

		mockMvc.perform(reasignacion(token(vendedor1), id, vendedor2.getId())).andExpect(status().isForbidden());
	}

	@Test
	void respondeNoEncontradoSiElProspectoNoExiste() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);

		mockMvc.perform(reasignacion(token(gerente), 999_999L, vendedor.getId())).andExpect(status().isNotFound());
	}

	@Test
	void rechazaReasignarAUnUsuarioQueNoEsVendedor() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		long id = crearProspecto(vendedor, "Ana López", "4771234567");

		mockMvc.perform(reasignacion(token(gerente), id, gerente.getId())).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El usuario " + gerente.getId() + " no es un vendedor activo"));
	}

	@Test
	void rechazaReasignarAUnVendedorInactivo() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario inactivo = registrar("vendedor2", Role.VENTAS);
		ReflectionTestUtils.setField(inactivo, "activo", false);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		long id = crearProspecto(vendedor1, "Ana López", "4771234567");

		mockMvc.perform(reasignacion(token(gerente), id, inactivo.getId())).andExpect(status().isBadRequest());
	}

	@Test
	void exigeElVendedorEnElCuerpo() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		long id = crearProspecto(vendedor, "Ana López", "4771234567");

		mockMvc.perform(conToken(put(URL + "/{id}/vendedor", id), token(gerente))
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private long crearProspecto(Usuario vendedor, String nombre, String telefono) throws Exception {
		String respuesta = mockMvc
				.perform(conToken(post(URL), token(vendedor)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"%s\",\"telefono\":\"%s\"}".formatted(nombre, telefono)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private static MockHttpServletRequestBuilder reasignacion(String token, long prospectoId, long vendedorId) {
		return conToken(put(URL + "/{id}/vendedor", prospectoId), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"vendedorId\":" + vendedorId + "}");
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
