package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.ClienteDetalle;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expediente del vehículo (ERP-3.0) de punta a punta: migración V18, seguridad,
 * endpoints y base de datos real (Testcontainers). Cada prueba se revierte al
 * terminar.
 */
@AutoConfigureMockMvc
@Transactional
class ExpedienteIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/expedientes";
	private static final String VIN = "3N1AB8CV5RY000001";
	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Mexico_City"));

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

	private int usuariosCreados;
	private Long vehiculoId;

	@BeforeEach
	void registrarClienteConSuVehiculo() {
		ClienteDetalle cliente = registroClientesService
				.registrar(new NuevoCliente("Ana López", "ana@correo.com", "4775550101", null));
		vehiculoId = registroClientesService
				.asignarVehiculo(cliente.id(), new VehiculoNuevo(null, VIN, "Nissan", "Sentra", 2026, "Advance"))
				.vehiculos().getFirst().id();
	}

	@Test
	void servicioBuscaElVehiculoPorParteDelVinSinDistinguirMayusculas() throws Exception {
		mockMvc.perform(conToken(get(URL).param("vin", "ry000001"), "SERVICIO")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(vehiculoId))
				.andExpect(jsonPath("$[0].vin").value(VIN)).andExpect(jsonPath("$[0].marca").value("Nissan"))
				.andExpect(jsonPath("$[0].cliente").value("Ana López"));
		mockMvc.perform(conToken(get(URL).param("vin", "RY00"), "SERVICIO")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(conToken(get(URL).param("vin", "%%%%%"), "SERVICIO")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void elExpedienteMuestraElVehiculoSuDuenoYSusServiciosDelMasReciente() throws Exception {
		registrarServicio(HOY.minusDays(30), "Afinación", "1200.00");
		registrarServicio(HOY, "Cambio de aceite", "450.00");

		mockMvc.perform(conToken(get(URL + "/{id}", vehiculoId), "ADMINISTRADOR")).andExpect(status().isOk())
				.andExpect(jsonPath("$.vehiculo.vin").value(VIN))
				.andExpect(jsonPath("$.vehiculo.marca").value("Nissan"))
				.andExpect(jsonPath("$.vehiculo.modelo").value("Sentra"))
				.andExpect(jsonPath("$.vehiculo.anio").value(2026))
				.andExpect(jsonPath("$.vehiculo.version").value("Advance"))
				.andExpect(jsonPath("$.cliente.nombre").value("Ana López"))
				.andExpect(jsonPath("$.cliente.telefono").value("4775550101"))
				.andExpect(jsonPath("$.servicios.length()").value(2))
				.andExpect(jsonPath("$.servicios[0].fecha").value(HOY.toString()))
				.andExpect(jsonPath("$.servicios[0].total").value(450.0))
				.andExpect(jsonPath("$.servicios[0].atendidoPor").value("Persona SERVICIO"))
				.andExpect(jsonPath("$.servicios[1].fecha").value(HOY.minusDays(30).toString()));
	}

	@Test
	void registraUnServicioYMuestraSusDetallesComoTicket() throws Exception {
		String cuerpo = """
				{"fecha": "%s", "kilometraje": 15000, "renglones": [
				  {"tipo": "MANO_DE_OBRA", "concepto": "Cambio de aceite", "cantidad": 1, "precioUnitario": 450.00},
				  {"tipo": "REFACCION", "concepto": "Filtro de aceite", "cantidad": 2, "precioUnitario": 175.50}
				]}""".formatted(HOY);
		String respuesta = mockMvc
				.perform(conToken(post(URL + "/{id}/servicios", vehiculoId), "SERVICIO")
						.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION))
				.andExpect(jsonPath("$.total").value(801.0)).andExpect(jsonPath("$.subtotal").value(690.52))
				.andExpect(jsonPath("$.iva").value(110.48)).andReturn().getResponse().getContentAsString();
		Integer servicioId = JsonPath.read(respuesta, "$.id");

		mockMvc.perform(conToken(get(URL + "/{id}/servicios/{servicioId}", vehiculoId, servicioId), "ADMINISTRADOR"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.kilometraje").value(15000))
				.andExpect(jsonPath("$.atendidoPor").value("Persona SERVICIO"))
				.andExpect(jsonPath("$.renglones.length()").value(2))
				.andExpect(jsonPath("$.renglones[0].tipo").value("MANO_DE_OBRA"))
				.andExpect(jsonPath("$.renglones[1].concepto").value("Filtro de aceite"))
				.andExpect(jsonPath("$.renglones[1].cantidad").value(2))
				.andExpect(jsonPath("$.renglones[1].precioUnitario").value(175.5))
				.andExpect(jsonPath("$.renglones[1].importe").value(351.0));
	}

	@Test
	void rechazaServiciosNoValidos() throws Exception {
		String sinRenglones = """
				{"fecha": "%s", "kilometraje": 100, "renglones": []}""".formatted(HOY);
		mockMvc.perform(conToken(post(URL + "/{id}/servicios", vehiculoId), "SERVICIO")
				.contentType(MediaType.APPLICATION_JSON).content(sinRenglones)).andExpect(status().isBadRequest());

		String deManana = """
				{"fecha": "%s", "kilometraje": 100, "renglones": [
				  {"tipo": "REFACCION", "concepto": "Filtro", "cantidad": 1, "precioUnitario": 10}
				]}""".formatted(HOY.plusDays(1));
		mockMvc.perform(conToken(post(URL + "/{id}/servicios", vehiculoId), "SERVICIO")
				.contentType(MediaType.APPLICATION_JSON).content(deManana)).andExpect(status().isBadRequest());

		String deOtroVehiculo = """
				{"fecha": "%s", "kilometraje": 100, "renglones": [
				  {"tipo": "REFACCION", "concepto": "Filtro", "cantidad": 1, "precioUnitario": 10}
				]}""".formatted(HOY);
		mockMvc.perform(conToken(post(URL + "/{id}/servicios", 999_999), "SERVICIO")
				.contentType(MediaType.APPLICATION_JSON).content(deOtroVehiculo)).andExpect(status().isNotFound());
	}

	@Test
	void servicioCorrigeLasPlacasYElKilometraje() throws Exception {
		mockMvc.perform(conToken(patch(URL + "/{id}", vehiculoId), "SERVICIO").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"placas": "abc-123-d", "kilometraje": 15000}""")).andExpect(status().isOk())
				.andExpect(jsonPath("$.placas").value("ABC-123-D")).andExpect(jsonPath("$.kilometraje").value(15000));

		mockMvc.perform(conToken(patch(URL + "/{id}", vehiculoId), "SERVICIO").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"placas": "ABC", "kilometraje": -1}""")).andExpect(status().isBadRequest());
	}

	@Test
	void elDetalleDeUnServicioDeOtroVehiculoResponde404() throws Exception {
		Integer servicioId = registrarServicio(HOY, "Afinación", "900.00");

		mockMvc.perform(conToken(get(URL + "/{id}/servicios/{servicioId}", 999_999, servicioId), "SERVICIO"))
				.andExpect(status().isNotFound());
		mockMvc.perform(conToken(get(URL + "/{id}", 999_999), "SERVICIO")).andExpect(status().isNotFound());
	}

	@Test
	void soloServicioYElAdministradorConsultanYSoloServicioModifica() throws Exception {
		mockMvc.perform(conToken(get(URL + "/{id}", vehiculoId), "VENTAS")).andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(URL + "/{id}", vehiculoId), "GERENTE_MARKETING"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(URL + "/{id}", vehiculoId)).andExpect(status().isUnauthorized());
		mockMvc.perform(conToken(patch(URL + "/{id}", vehiculoId), "ADMINISTRADOR")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"placas": "ABC"}""")).andExpect(status().isForbidden());
		mockMvc.perform(conToken(post(URL + "/{id}/servicios", vehiculoId), "ADMINISTRADOR")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"fecha": "%s", "kilometraje": 100, "renglones": [
						  {"tipo": "REFACCION", "concepto": "Filtro", "cantidad": 1, "precioUnitario": 10}
						]}""".formatted(HOY))).andExpect(status().isForbidden());
	}

	private Integer registrarServicio(LocalDate fecha, String concepto, String precio) throws Exception {
		String cuerpo = """
				{"fecha": "%s", "kilometraje": 15000, "renglones": [
				  {"tipo": "MANO_DE_OBRA", "concepto": "%s", "cantidad": 1, "precioUnitario": %s}
				]}""".formatted(fecha, concepto, precio);
		String respuesta = mockMvc
				.perform(conToken(post(URL + "/{id}/servicios", vehiculoId), "SERVICIO")
						.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.id");
	}

	private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String rol) {
		Rol encontrado = rolRepository.findByNombre(rol).orElseThrow();
		usuariosCreados++;
		Usuario usuario = usuarioRepository
				.save(new Usuario("expediente" + usuariosCreados, "hash-de-prueba", "Persona " + rol, encontrado));
		String token = jwtService.generateToken(usuario.getId(), usuario.getUsuario(),
				List.of(usuario.getRol().comoRole()));
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
