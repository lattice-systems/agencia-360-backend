package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.application.ExpedienteService;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.application.NuevoServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.RenglonNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TipoRenglon;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historial de servicios y ticket en PDF de la app (APP-3.2) de punta a punta,
 * con los servicios registrados en el expediente del ERP y base de datos real
 * (Testcontainers). Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class ServiciosAppIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private ExpedienteService expedienteService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Long ana;
	private String tokenDeAna;
	private Long marchDeAna;
	private Long vehiculoDeBruno;
	private UsuarioAutenticado servicio;

	@BeforeEach
	void registrarClientesYServicios() {
		ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		marchDeAna = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000011", "Nissan", "March", 2026, "Sense"))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));
		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		vehiculoDeBruno = registroClientesService
				.asignarVehiculo(bruno, new VehiculoNuevo(null, "3N1CK3CD5RL000012", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		Usuario tecnico = usuarioRepository.save(new Usuario("servicio-app", "hash-de-prueba", "Técnico",
				rolRepository.findByNombre("SERVICIO").orElseThrow()));
		servicio = new UsuarioAutenticado(tecnico.getId(), tecnico.getUsuario(), Set.of(Role.SERVICIO));
	}

	@Test
	void elClienteVeElHistorialDeSuVehiculoDelMasRecientePaginado() throws Exception {
		Long viejo = registrar(marchDeAna, hoy().minusDays(60), "1200.00");
		Long nuevo = registrar(marchDeAna, hoy().minusDays(1), "450.50");
		registrar(vehiculoDeBruno, hoy(), "99.00");

		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/servicios", marchDeAna).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(2))
				.andExpect(jsonPath("$.contenido[0].id").value(nuevo))
				.andExpect(jsonPath("$.contenido[0].estatus").value("TERMINADO"))
				.andExpect(jsonPath("$.contenido[0].total").value(450.5))
				.andExpect(jsonPath("$.contenido[1].id").value(viejo)).andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamano").value(20)).andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.totalPaginas").value(1));

		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/servicios", marchDeAna).param("pagina", "1")
				.param("tamano", "1").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(viejo))
				.andExpect(jsonPath("$.totalPaginas").value(2));
	}

	@Test
	void unVehiculoAjenoResponde404YUnaPaginaInvalida400() throws Exception {
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/servicios", vehiculoDeBruno).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/servicios", marchDeAna).param("tamano", "51")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void elClienteDescargaElTicketDeSuServicioEnPdf() throws Exception {
		Long servicioId = registrar(marchDeAna, hoy(), "1500.00");

		byte[] pdf = mockMvc
				.perform(get("/api/v1/app/servicios/{id}/ticket", servicioId).header(HttpHeaders.AUTHORIZATION,
						"Bearer " + tokenDeAna))
				.andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"ticket-" + servicioId + ".pdf\""))
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
	}

	@Test
	void elTicketDeUnServicioAjenoOInexistenteResponde404() throws Exception {
		Long deBruno = registrar(vehiculoDeBruno, hoy(), "300.00");

		mockMvc.perform(get("/api/v1/app/servicios/{id}/ticket", deBruno).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/servicios/{id}/ticket", 999_999).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/servicios/{id}/ticket", deBruno)).andExpect(status().isUnauthorized());
	}

	@Test
	void filtraElHistorialPorAnioYDiceQueAniosTienenServicios() throws Exception {
		int esteAnio = hoy().getYear();
		Long delAnioPasado = registrar(marchDeAna, hoy().minusYears(1), "800.00");
		registrar(marchDeAna, hoy(), "450.00");

		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/servicios/anios", marchDeAna).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0]").value(esteAnio)).andExpect(jsonPath("$[1]").value(esteAnio - 1));
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/servicios", marchDeAna)
				.param("anio", String.valueOf(esteAnio - 1)).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].id").value(delAnioPasado))
				.andExpect(jsonPath("$.contenido[0].fechaCierre")
						.value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/servicios", marchDeAna).param("anio", "1800")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/servicios/anios", vehiculoDeBruno)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)).andExpect(status().isNotFound());
	}

	@Test
	void elTicketTraeLaManoDeObraYLasRefaccionesQueSumanElTotal() throws Exception {
		RenglonNuevo filtro = new RenglonNuevo(TipoRenglon.REFACCION, "Filtro de aceite", 2, new BigDecimal("175.50"));
		RenglonNuevo aceite = new RenglonNuevo(TipoRenglon.REFACCION, "Aceite sintético", 4, new BigDecimal("120.25"));
		NuevoServicio nuevo = new NuevoServicio(hoy(), 15000, List.of(filtro, afinacion("450.00"), aceite));
		Long servicioId = expedienteService.registrar(marchDeAna, nuevo, servicio).id();

		mockMvc.perform(
				get("/api/v1/app/servicios/{id}", servicioId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(servicioId))
				.andExpect(jsonPath("$.vehiculoId").value(marchDeAna))
				.andExpect(jsonPath("$.estatus").value("TERMINADO")).andExpect(jsonPath("$.kilometraje").value(15000))
				.andExpect(jsonPath("$.manoDeObra.length()").value(1))
				.andExpect(jsonPath("$.manoDeObra[0].concepto").value("Afinación"))
				.andExpect(jsonPath("$.refacciones.length()").value(2))
				.andExpect(jsonPath("$.refacciones[0].concepto").value("Filtro de aceite"))
				.andExpect(jsonPath("$.refacciones[0].cantidad").value(2))
				.andExpect(jsonPath("$.refacciones[0].precio").value(175.5))
				.andExpect(jsonPath("$.refacciones[0].importe").value(351.0))
				.andExpect(jsonPath("$.totalManoDeObra").value(450.0))
				.andExpect(jsonPath("$.totalRefacciones").value(832.0)).andExpect(jsonPath("$.total").value(1282.0))
				.andExpect(jsonPath("$.subtotal").value(1105.17)).andExpect(jsonPath("$.iva").value(176.83));
	}

	@Test
	void elTicketDiceCuantosPuntosDioLaOrden() throws Exception {
		Long conPuntos = registrar(marchDeAna, hoy(), "1650.00");
		Long sinPuntos = registrar(marchDeAna, hoy(), "300.00");
		jdbc.update("insert into movimiento_puntos (cliente_id, puntos, motivo, referencia_tipo, referencia_id, "
				+ "creado_en) values (?, 165, 'SERVICIO', 'ORDEN', ?, now())", ana, conPuntos);

		mockMvc.perform(
				get("/api/v1/app/servicios/{id}", conPuntos).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isOk()).andExpect(jsonPath("$.puntosGanados").value(165));
		mockMvc.perform(
				get("/api/v1/app/servicios/{id}", sinPuntos).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isOk()).andExpect(jsonPath("$.puntosGanados").doesNotExist());
	}

	@Test
	void elTicketDeUnServicioAjenoNoSeMuestra() throws Exception {
		Long deBruno = registrar(vehiculoDeBruno, hoy(), "300.00");

		mockMvc.perform(
				get("/api/v1/app/servicios/{id}", deBruno).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isNotFound());
		mockMvc.perform(
				get("/api/v1/app/servicios/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isNotFound());
	}

	private static LocalDate hoy() {
		return LocalDate.now(ZoneId.of("America/Mexico_City"));
	}

	private static RenglonNuevo afinacion(String precio) {
		return new RenglonNuevo(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, new BigDecimal(precio));
	}

	private Long registrar(Long vehiculoId, LocalDate fecha, String precio) {
		return expedienteService
				.registrar(vehiculoId, new NuevoServicio(fecha, 15000, List.of(afinacion(precio))), servicio).id();
	}
}
