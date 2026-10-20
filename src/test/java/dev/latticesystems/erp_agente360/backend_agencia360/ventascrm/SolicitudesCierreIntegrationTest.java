package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de cierre de punta a punta (decisión del PO del 12 de octubre):
 * desde Pago en adelante el vendedor solicita el cierre y el Administrador del
 * ERP lo aprueba o lo rechaza. Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class SolicitudesCierreIntegrationTest extends AbstractIntegrationTest {

	private static final String PROSPECTOS = "/api/v1/prospectos";
	private static final String SOLICITUDES = "/api/v1/solicitudes-cierre";
	private static final String MOTIVO = "PERDIO_EL_INTERES";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Test
	void elAdministradorApruebaYElProspectoQuedaCerradoConElMotivo() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario admin = registrar("admin1", Role.ADMINISTRADOR);
		long prospecto = prospectoEnPago(vendedor);

		long solicitud = idDe(mockMvc.perform(solicitud(token(vendedor), prospecto, MOTIVO))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.solicitanteNombre").value("Persona vendedor1")).andReturn().getResponse()
				.getContentAsString());

		mockMvc.perform(conToken(get(PROSPECTOS), token(vendedor)))
				.andExpect(jsonPath("$[0].cierrePendiente").value(true));
		mockMvc.perform(conToken(get(SOLICITUDES), token(admin))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].motivo").value(MOTIVO))
				.andExpect(jsonPath("$[0].etapa").value("PAGO"));

		mockMvc.perform(conToken(post(SOLICITUDES + "/{id}/aprobar", solicitud), token(admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADA"))
				.andExpect(jsonPath("$.etapa").value("CERRADO"));

		mockMvc.perform(conToken(get(PROSPECTOS), token(vendedor))).andExpect(jsonPath("$[0].etapa").value("CERRADO"))
				.andExpect(jsonPath("$[0].motivoCierre").value(MOTIVO))
				.andExpect(jsonPath("$[0].ultimaEtapa").value("PAGO"))
				.andExpect(jsonPath("$[0].cierrePendiente").value(false));
		mockMvc.perform(conToken(get(SOLICITUDES), token(admin))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void alRechazarseElProspectoSigueEnSuEtapa() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario admin = registrar("admin1", Role.ADMINISTRADOR);
		long prospecto = prospectoEnPago(vendedor);
		long solicitud = idDe(mockMvc.perform(solicitud(token(vendedor), prospecto, MOTIVO)).andReturn().getResponse()
				.getContentAsString());

		mockMvc.perform(conToken(post(SOLICITUDES + "/{id}/rechazar", solicitud), token(admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"))
				.andExpect(jsonPath("$.etapa").value("PAGO"));
		mockMvc.perform(conToken(post(SOLICITUDES + "/{id}/aprobar", solicitud), token(admin)))
				.andExpect(status().isConflict());
	}

	@Test
	void soloPuedeHaberUnaSolicitudPendientePorProspecto() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long prospecto = prospectoEnPago(vendedor);
		mockMvc.perform(solicitud(token(vendedor), prospecto, MOTIVO)).andExpect(status().isCreated());

		mockMvc.perform(solicitud(token(vendedor), prospecto, MOTIVO)).andExpect(status().isConflict());
	}

	@Test
	void enLasPrimerasEtapasNoSeSolicitaPorqueLoCierraElVendedor() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long prospecto = crearProspecto(vendedor);

		mockMvc.perform(solicitud(token(vendedor), prospecto, MOTIVO)).andExpect(status().isConflict());
	}

	@Test
	void exigeMotivo() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long prospecto = prospectoEnPago(vendedor);

		mockMvc.perform(conToken(post(PROSPECTOS + "/" + prospecto + "/solicitudes-cierre"), token(vendedor))
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El motivo de cierre es obligatorio"));
		mockMvc.perform(solicitud(token(vendedor), prospecto, "El cliente canceló la compra"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unVendedorNoSolicitaElCierreDeUnProspectoAjeno() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		long prospecto = prospectoEnPago(vendedor1);

		mockMvc.perform(solicitud(token(vendedor2), prospecto, MOTIVO)).andExpect(status().isNotFound());
	}

	@Test
	void soloElAdministradorRevisaYResuelve() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		long prospecto = prospectoEnPago(vendedor);
		long solicitud = idDe(mockMvc.perform(solicitud(token(vendedor), prospecto, MOTIVO)).andReturn().getResponse()
				.getContentAsString());

		mockMvc.perform(conToken(get(SOLICITUDES), token(gerente))).andExpect(status().isForbidden());
		mockMvc.perform(conToken(post(SOLICITUDES + "/{id}/aprobar", solicitud), token(vendedor)))
				.andExpect(status().isForbidden());
	}

	private long prospectoEnPago(Usuario vendedor) throws Exception {
		long id = crearProspecto(vendedor);
		mockMvc.perform(conToken(patch(PROSPECTOS + "/{id}/etapa", id), token(vendedor))
				.contentType(MediaType.APPLICATION_JSON).content("{\"etapa\":\"PAGO\",\"precioTotal\":489900}"))
				.andExpect(status().isOk());
		return id;
	}

	private long crearProspecto(Usuario vendedor) throws Exception {
		return idDe(mockMvc
				.perform(conToken(post(PROSPECTOS), token(vendedor)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Ana López\",\"telefono\":\"4771234567\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private static MockHttpServletRequestBuilder solicitud(String token, long prospectoId, String motivo) {
		return conToken(post(PROSPECTOS + "/{id}/solicitudes-cierre", prospectoId), token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + motivo + "\"}");
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static long idDe(String respuesta) {
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}
}
