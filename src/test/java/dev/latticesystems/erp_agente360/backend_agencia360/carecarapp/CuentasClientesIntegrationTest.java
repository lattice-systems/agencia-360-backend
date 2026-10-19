package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * Prueba de punta a punta lo que hace el Gerente de marketing en CRM-1.3: alta
 * manual del cliente, asignación de vehículos y la cuenta de la app.
 */
@AutoConfigureMockMvc
@Transactional
class CuentasClientesIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/clientes";
	private static final String VIN = "3VWFE21C04M000001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private int usuariosCreados;

	@Test
	void elGerenteDaDeAltaUnClienteAMano() throws Exception {
		registrar("Ana López", "Ana@Correo.com", "4775550101").andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value("Ana López"))
				.andExpect(jsonPath("$.correo").value("ana@correo.com")).andExpect(jsonPath("$.vehiculos").isEmpty());

		registrar("Otra Ana", "ana@correo.com", null).andExpect(status().isConflict());
		registrar(" ", null, null).andExpect(status().isBadRequest());
	}

	@Test
	void leAsignaVehiculosAlCliente() throws Exception {
		long anaId = idDe(registrar("Ana López", "ana@correo.com", null));
		long brunoId = idDe(registrar("Bruno Díaz", null, null));

		asignarVehiculo(anaId, VIN.toLowerCase(), "Jetta", 2026).andExpect(status().isOk())
				.andExpect(jsonPath("$.vehiculos[0].vin").value(VIN))
				.andExpect(jsonPath("$.vehiculos[0].modelo").value("Jetta"));
		asignarVehiculo(anaId, VIN, "Jetta", 2026).andExpect(status().isOk())
				.andExpect(jsonPath("$.vehiculos.length()").value(1));

		asignarVehiculo(brunoId, VIN, "Jetta", 2026).andExpect(status().isConflict());
		asignarVehiculo(anaId, "CORTO", "Jetta", 2026).andExpect(status().isBadRequest());
		asignarVehiculo(999_999L, "3VWFE21C04M000002", "Jetta", 2026).andExpect(status().isNotFound());
	}

	@Test
	void abreLaCuentaYMandaLaContrasenaTemporalPorCorreo() throws Exception {
		long id = idDe(registrar("Ana López", "ana@correo.com", null));
		mockMvc.perform(gerente(get(URL + "/{id}/cuenta-app", id))).andExpect(jsonPath("$.estado").value("SIN_CUENTA"));

		mockMvc.perform(gerente(post(URL + "/{id}/cuenta-app", id))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("ACTIVA"))
				.andExpect(jsonPath("$.debeCambiarContrasena").value(true));

		ArgumentCaptor<String> temporal = ArgumentCaptor.forClass(String.class);
		verify(envioCorreos).enviarCuentaNueva(eq("ana@correo.com"), eq("Ana López"), temporal.capture());
		loginApp("ana@correo.com", temporal.getValue()).andExpect(status().isOk())
				.andExpect(jsonPath("$.debeCambiarContrasena").value(true));

		mockMvc.perform(gerente(post(URL + "/{id}/cuenta-app", id))).andExpect(status().isConflict());
	}

	@Test
	void sinCorreoNoSeAbreCuenta() throws Exception {
		long id = idDe(registrar("Bruno Díaz", null, null));

		mockMvc.perform(gerente(post(URL + "/{id}/cuenta-app", id))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("El cliente no tiene correo para entrar a la app"));
	}

	@Test
	void desactivaYReactivaLaCuenta() throws Exception {
		long id = idDe(registrar("Ana López", "ana@correo.com", null));
		mockMvc.perform(gerente(post(URL + "/{id}/cuenta-app", id))).andExpect(status().isCreated());
		ArgumentCaptor<String> temporal = ArgumentCaptor.forClass(String.class);
		verify(envioCorreos).enviarCuentaNueva(eq("ana@correo.com"), eq("Ana López"), temporal.capture());

		mockMvc.perform(gerente(post(URL + "/{id}/cuenta-app/desactivar", id))).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("DESACTIVADA"));
		loginApp("ana@correo.com", temporal.getValue()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));

		mockMvc.perform(gerente(post(URL + "/{id}/cuenta-app/activar", id))).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ACTIVA"));
		loginApp("ana@correo.com", temporal.getValue()).andExpect(status().isOk());
	}

	@Test
	void soloElGerenteAdministraClientesYCuentas() throws Exception {
		mockMvc.perform(
				conToken(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Ana\"}"), "VENTAS"))
				.andExpect(status().isForbidden());
		mockMvc.perform(conToken(post(URL + "/{id}/cuenta-app", 1), "VENTAS")).andExpect(status().isForbidden());
	}

	private ResultActions registrar(String nombre, String correo, String telefono) throws Exception {
		String cuerpo = "{\"nombre\":%s,\"correo\":%s,\"telefono\":%s}".formatted(json(nombre), json(correo),
				json(telefono));
		return mockMvc.perform(gerente(post(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
	}

	private ResultActions asignarVehiculo(long clienteId, String vin, String modelo, int anio) throws Exception {
		String cuerpo = "{\"vin\":\"%s\",\"modelo\":\"%s\",\"anio\":%d}".formatted(vin, modelo, anio);
		return mockMvc.perform(gerente(
				post(URL + "/{id}/vehiculos", clienteId).contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
	}

	private ResultActions loginApp(String correo, String contrasena) throws Exception {
		return mockMvc.perform(post("/api/v1/app/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
	}

	private MockHttpServletRequestBuilder gerente(MockHttpServletRequestBuilder peticion) {
		return conToken(peticion, "GERENTE_MARKETING");
	}

	private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String rol) {
		Rol encontrado = rolRepository.findByNombre(rol).orElseThrow();
		usuariosCreados++;
		Usuario usuario = usuarioRepository
				.save(new Usuario("usuario" + usuariosCreados, "hash-de-prueba", "Persona " + rol, encontrado));
		String token = jwtService.generateToken(usuario.getId(), usuario.getUsuario(),
				List.of(usuario.getRol().comoRole()));
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static long idDe(ResultActions resultado) throws Exception {
		return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private static String json(String valor) {
		return valor == null ? "null" : "\"" + valor + "\"";
	}
}
