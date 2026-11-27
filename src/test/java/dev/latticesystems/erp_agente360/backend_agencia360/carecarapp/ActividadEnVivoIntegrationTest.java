package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ActividadEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ActividadEnVivoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de la Live Activity de una orden (V2-M25.05) con base real: el
 * cliente manda el push token de su actividad, solo puede hacerlo para sus
 * órdenes y la baja la desactiva. El push a Apple se prueba aparte, con un
 * envío falso.
 */
@AutoConfigureMockMvc
@Transactional
class ActividadEnVivoIntegrationTest extends AbstractIntegrationTest {

	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");
	private static final String TOKEN_A = "aabbccddeeff00112233445566778899";
	private static final String TOKEN_B = "99887766554433221100ffeeddccbbaa";

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
	private ActividadEnVivoRepository actividades;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeAna;
	private String tokenDeBruno;
	private String tokenDeServicio;
	private Long ordenDeAna;

	@BeforeEach
	void abrirUnaOrden() throws Exception {
		Long ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@live.com", null, null)).id();
		Long march = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000081", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@live.com", List.of(Role.CLIENTE_CARECAR));
		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@live.com", null, null))
				.id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@live.com", List.of(Role.CLIENTE_CARECAR));
		Usuario asesor = usuarioRepository.save(new Usuario("servicio-live", "hash-de-prueba", "Laura Méndez",
				rolRepository.findByNombre("SERVICIO").orElseThrow()));
		tokenDeServicio = jwtService.generateToken(asesor.getId(), asesor.getUsuario(), List.of(Role.SERVICIO));
		LocalDateTime manana = LocalDate.now(ZONA_AGENCIA).plusDays(1).atTime(18, 0);
		String cuerpoDeOrden = "{\"vehiculoId\":" + march + ",\"kilometraje\":15200,\"entregaEstimada\":\"" + manana
				+ ":00\"}";
		String abierta = mockMvc
				.perform(post("/api/v1/taller/ordenes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeServicio)
						.contentType(MediaType.APPLICATION_JSON).content(cuerpoDeOrden))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		ordenDeAna = ((Number) JsonPath.read(abierta, "$.id")).longValue();
	}

	@Test
	void elDuenoRegistraElTokenDeSuActividad() throws Exception {
		registrar(tokenDeAna, ordenDeAna, TOKEN_A).andExpect(status().isNoContent());

		assertThat(actividades.findByOrdenServicioIdAndActivaTrue(ordenDeAna)).hasSize(1)
				.allSatisfy(actividad -> assertThat(actividad.getPushToken()).isEqualTo(TOKEN_A));
	}

	@Test
	void registrarDosVecesElMismoTokenEsIdempotenteYOtroTokenSeSuma() throws Exception {
		registrar(tokenDeAna, ordenDeAna, TOKEN_A).andExpect(status().isNoContent());
		registrar(tokenDeAna, ordenDeAna, TOKEN_A).andExpect(status().isNoContent());
		assertThat(actividades.findByOrdenServicioIdAndActivaTrue(ordenDeAna)).hasSize(1);

		registrar(tokenDeAna, ordenDeAna, TOKEN_B).andExpect(status().isNoContent());
		assertThat(actividades.findByOrdenServicioIdAndActivaTrue(ordenDeAna)).hasSize(2);
	}

	@Test
	void unaOrdenAjenaOInexistenteResponde404() throws Exception {
		registrar(tokenDeBruno, ordenDeAna, TOKEN_A).andExpect(status().isNotFound());
		registrar(tokenDeAna, 999_999L, TOKEN_A).andExpect(status().isNotFound());
		quitar(tokenDeBruno, ordenDeAna, null).andExpect(status().isNotFound());
		assertThat(actividades.findAll()).isEmpty();
	}

	@Test
	void sinSesionOConElPersonalNoSeRegistra() throws Exception {
		mockMvc.perform(put("/api/v1/app/ordenes/{id}/actividad-en-vivo", ordenDeAna)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo(TOKEN_A))).andExpect(status().isUnauthorized());
		registrar(tokenDeServicio, ordenDeAna, TOKEN_A).andExpect(status().isForbidden());
	}

	@Test
	void unTokenInvalidoEsUnaPeticionIncorrecta() throws Exception {
		registrar(tokenDeAna, ordenDeAna, "").andExpect(status().isBadRequest());
		registrar(tokenDeAna, ordenDeAna, "no es hexadecimal!").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		registrar(tokenDeAna, ordenDeAna, "a".repeat(301)).andExpect(status().isBadRequest());
	}

	@Test
	void darDeBajaUnTokenDesactivaSoloEseDispositivo() throws Exception {
		registrar(tokenDeAna, ordenDeAna, TOKEN_A).andExpect(status().isNoContent());
		registrar(tokenDeAna, ordenDeAna, TOKEN_B).andExpect(status().isNoContent());

		quitar(tokenDeAna, ordenDeAna, TOKEN_A).andExpect(status().isNoContent());

		assertThat(actividades.findByOrdenServicioIdAndActivaTrue(ordenDeAna)).extracting(ActividadEnVivo::getPushToken)
				.containsExactly(TOKEN_B);
	}

	@Test
	void darDeBajaSinTokenDesactivaTodasLasDelClienteYEsIdempotente() throws Exception {
		registrar(tokenDeAna, ordenDeAna, TOKEN_A).andExpect(status().isNoContent());
		registrar(tokenDeAna, ordenDeAna, TOKEN_B).andExpect(status().isNoContent());

		quitar(tokenDeAna, ordenDeAna, null).andExpect(status().isNoContent());
		quitar(tokenDeAna, ordenDeAna, null).andExpect(status().isNoContent());

		assertThat(actividades.findByOrdenServicioIdAndActivaTrue(ordenDeAna)).isEmpty();
	}

	@Test
	void unTokenDadoDeBajaSeReactivaAlRegistrarloDeNuevo() throws Exception {
		registrar(tokenDeAna, ordenDeAna, TOKEN_A).andExpect(status().isNoContent());
		quitar(tokenDeAna, ordenDeAna, TOKEN_A).andExpect(status().isNoContent());

		registrar(tokenDeAna, ordenDeAna, TOKEN_A).andExpect(status().isNoContent());

		assertThat(actividades.findByOrdenServicioIdAndActivaTrue(ordenDeAna)).hasSize(1);
	}

	private ResultActions registrar(String token, Long ordenId, String pushToken) throws Exception {
		return conSesion(token, put("/api/v1/app/ordenes/{id}/actividad-en-vivo", ordenId)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo(pushToken)));
	}

	private ResultActions quitar(String token, Long ordenId, String pushToken) throws Exception {
		MockHttpServletRequestBuilder peticion = delete("/api/v1/app/ordenes/{id}/actividad-en-vivo", ordenId);
		if (pushToken != null) {
			peticion.param("pushToken", pushToken);
		}
		return conSesion(token, peticion);
	}

	private ResultActions conSesion(String token, MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private static String cuerpo(String pushToken) {
		return "{\"pushToken\": \"" + pushToken + "\"}";
	}
}
