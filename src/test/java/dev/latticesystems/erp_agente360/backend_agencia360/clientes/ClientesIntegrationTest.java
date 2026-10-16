package dev.latticesystems.erp_agente360.backend_agencia360.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.Cliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.infrastructure.ClienteRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba el módulo de clientes de punta a punta (CRM-1.5): migración,
 * seguridad, endpoints y base de datos real (Testcontainers). Cada prueba se
 * revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class ClientesIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/clientes";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private ClienteRepository clienteRepository;

	private int usuariosCreados;

	@Test
	void elGerenteListaLosClientesConCuantosVehiculosTienen() throws Exception {
		Long anaId = registrar("Ana López", "ana@correo.com");
		agregarVehiculo(anaId, "3VWFE21C04M000001", "Jetta", 2026);
		agregarVehiculo(anaId, "3VWFE21C04M000002", "Tiguan", 2025);
		registrar("Bruno Díaz", null);

		mockMvc.perform(conToken(get(URL), "GERENTE_MARKETING")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].nombre").value("Ana López"))
				.andExpect(jsonPath("$[0].vehiculos").value(2)).andExpect(jsonPath("$[1].nombre").value("Bruno Díaz"))
				.andExpect(jsonPath("$[1].correo").isEmpty());
	}

	@Test
	void buscaPorNombreOCorreoSinDistinguirMayusculas() throws Exception {
		registrar("Ana López", "ana@correo.com");
		registrar("Bruno Díaz", "bruno@otro.mx");

		mockMvc.perform(conToken(get(URL).param("buscar", "LÓPEZ"), "GERENTE_MARKETING"))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("Ana López"));
		mockMvc.perform(conToken(get(URL).param("buscar", "OTRO.MX"), "GERENTE_MARKETING"))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("Bruno Díaz"));
	}

	@Test
	void muestraUnClienteConSusVehiculos() throws Exception {
		Long id = registrar("Ana López", "ana@correo.com");
		agregarVehiculo(id, "3VWFE21C04M000001", "Jetta", 2026);

		mockMvc.perform(conToken(get(URL + "/{id}", id), "GERENTE_MARKETING")).andExpect(status().isOk())
				.andExpect(jsonPath("$.correo").value("ana@correo.com"))
				.andExpect(jsonPath("$.vehiculos[0].vin").value("3VWFE21C04M000001"))
				.andExpect(jsonPath("$.vehiculos[0].modelo").value("Jetta"))
				.andExpect(jsonPath("$.vehiculos[0].anio").value(2026));
	}

	@Test
	void respondeNoEncontradoSiElClienteNoExiste() throws Exception {
		mockMvc.perform(conToken(get(URL + "/{id}", 999_999), "GERENTE_MARKETING")).andExpect(status().isNotFound());
	}

	@Test
	void soloElGerenteDeMarketingConsultaClientes() throws Exception {
		mockMvc.perform(conToken(get(URL), "VENTAS")).andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(URL), "ADMINISTRADOR")).andExpect(status().isForbidden());
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	@Test
	void noRegistraDosClientesConElMismoCorreo() {
		registrar("Ana López", "ana@correo.com");

		assertThatThrownBy(() -> registrar("Otra Ana", "ANA@correo.com")).isInstanceOf(ConflictoException.class)
				.hasMessage("Ya existe un cliente con el correo ana@correo.com");
	}

	@Test
	void variosClientesPuedenNoTenerCorreo() {
		registrar("Ana López", null);
		registrar("Bruno Díaz", null);
		clienteRepository.flush();

		assertThat(clienteRepository.findAll()).filteredOn(c -> c.getCorreo() == null).hasSize(2);
	}

	private Long registrar(String nombre, String correo) {
		return registroClientesService.registrar(new NuevoCliente(nombre, correo, null, null)).id();
	}

	private void agregarVehiculo(Long clienteId, String vin, String modelo, int anio) {
		Cliente cliente = clienteRepository.findById(clienteId).orElseThrow();
		cliente.asignarVehiculo(null, vin, modelo, anio);
		clienteRepository.flush();
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
}
