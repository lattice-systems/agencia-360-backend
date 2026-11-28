package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Taller en vivo de punta a punta (V2-M04.01 a V2-M04.07): Servicio abre la
 * orden, la pasa de etapa, registra la inspección con fotos y videos, propone
 * trabajos adicionales y carga los renglones del ticket; el cliente lo ve en la
 * app, responde con su firma, sabe cuándo recogerlo y al entregarse lo
 * encuentra en su historial. Base de datos real (Testcontainers); cada prueba
 * se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class TallerAppIntegrationTest extends AbstractIntegrationTest {

	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");
	private static final byte[] FOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
	private static final byte[] VIDEO = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};
	private static final byte[] FIRMA = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};

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
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Long ana;
	private String tokenDeAna;
	private String tokenDeBruno;
	private Long marchDeAna;
	private String tokenDeServicio;
	private UsuarioAutenticado tecnico;

	@BeforeEach
	void registrarClientesYTaller() {
		ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		marchDeAna = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000021", "Nissan", "March", 2026, "Sense"))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));
		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		registroClientesService.asignarVehiculo(bruno,
				new VehiculoNuevo(null, "3N1CK3CD5RL000022", "Nissan", "Versa", 2025, null));
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));
		Usuario asesor = usuarioRepository.save(new Usuario("servicio-taller", "hash-de-prueba", "Laura Méndez",
				rolRepository.findByNombre("SERVICIO").orElseThrow()));
		tecnico = new UsuarioAutenticado(asesor.getId(), asesor.getUsuario(), Set.of(Role.SERVICIO));
		tokenDeServicio = jwtService.generateToken(asesor.getId(), asesor.getUsuario(), List.of(Role.SERVICIO));
	}

	@Test
	void laOrdenAbiertaApareceEnInicioYDesapareceAlEntregarse() throws Exception {
		RenglonNuevo afinacion = new RenglonNuevo(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, new BigDecimal("900.00"));
		expedienteService.registrar(marchDeAna, new NuevoServicio(hoy(), 14_000, List.of(afinacion)), tecnico);
		Long ordenId = abrir();

		comoAna(get("/api/v1/app/ordenes/activas")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(ordenId))
				.andExpect(jsonPath("$[0].vehiculoId").value(marchDeAna))
				.andExpect(jsonPath("$[0].vehiculo").value("Nissan March 2026"))
				.andExpect(jsonPath("$[0].etapa").value("RECIBIDO"))
				.andExpect(jsonPath("$[0].estatus").value("RECIBIDO"))
				.andExpect(jsonPath("$[0].asesor").value("Laura Méndez")).andExpect(jsonPath("$[0].avance").value(0))
				.andExpect(jsonPath("$[0].adicionalesPendientes").value(0))
				.andExpect(jsonPath("$[0].entregaEstimada").value(manana().toString() + ":00"));
		comoBruno(get("/api/v1/app/ordenes/activas")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		avanzar(ordenId, "LISTO_PARA_ENTREGA").andExpect(status().isOk())
				.andExpect(jsonPath("$.estatus").value("TERMINADO"));
		comoAna(get("/api/v1/app/ordenes/activas")).andExpect(jsonPath("$[0].etapa").value("LISTO_PARA_ENTREGA"));
		avanzar(ordenId, "ENTREGADO").andExpect(status().isOk()).andExpect(jsonPath("$.estatus").value("ENTREGADO"));
		comoAna(get("/api/v1/app/ordenes/activas")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		avanzar(ordenId, "ENTREGADO").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_CERRADA"));
	}

	@Test
	void laEncuestaSeOfreceAlEntregarLaOrdenYSeRespondeUnaSolaVez() throws Exception {
		Long ordenId = abrir();
		String pendientes = "/api/v1/app/encuestas/pendientes";
		String posponer = "/api/v1/app/encuestas/{id}/posponer";
		String responder = "/api/v1/app/encuestas/{id}/responder";
		String calificacion = """
				{"nps":6,"tiempo":5,"trato":5,"limpieza":5,"asesor":5}
				""";

		comoAna(get(pendientes)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		avanzar(ordenId, "LISTO_PARA_ENTREGA").andExpect(status().isOk());
		comoAna(get(pendientes)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		comoAna(post(posponer, ordenId)).andExpect(status().isNotFound());
		comoAna(post(responder, ordenId).contentType(MediaType.APPLICATION_JSON).content(calificacion))
				.andExpect(status().isNotFound());

		avanzar(ordenId, "ENTREGADO").andExpect(status().isOk());
		comoAna(get("/api/v1/app/vehiculos/{id}/servicios", marchDeAna)).andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[0].id").value(ordenId))
				.andExpect(jsonPath("$.contenido[0].estatus").value("ENTREGADO"));
		comoAna(get("/api/v1/app/servicios/{id}/ticket", ordenId)).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
		comoAna(get(pendientes)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].ordenId").value(ordenId));
		comoBruno(get(pendientes)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		comoBruno(post(posponer, ordenId)).andExpect(status().isNotFound());
		comoAna(post(posponer, ordenId)).andExpect(status().isNoContent());
		comoAna(get(pendientes)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

		jdbcTemplate.update("update encuesta_servicio set pospuesta_hasta = now() - interval '1 second' "
				+ "where orden_servicio_id = ?", ordenId);
		comoAna(get(pendientes)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		comoAna(post(responder, ordenId).contentType(MediaType.APPLICATION_JSON).content(calificacion))
				.andExpect(status().isNoContent());
		comoAna(get(pendientes)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		comoAna(post(responder, ordenId).contentType(MediaType.APPLICATION_JSON).content(calificacion))
				.andExpect(status().isConflict());

		String contacto = "/api/v1/app/encuestas/{id}/contacto";
		comoAna(post(contacto, ordenId)).andExpect(status().isNoContent());
		comoAna(post(contacto, ordenId)).andExpect(status().isNoContent());
		String tareas = "/api/v1/servicio/encuestas/contactos";
		String pendientesDeAtender = comoServicio(get(tareas)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].ordenId").value(ordenId))
				.andReturn().getResponse().getContentAsString();
		Number tarea = JsonPath.read(pendientesDeAtender, "$[0].id");
		String otroAsesor = jwtService.generateToken(999L, "otro-asesor", List.of(Role.SERVICIO));
		String administrador = jwtService.generateToken(998L, "admin", List.of(Role.ADMINISTRADOR));
		String respuesta = tareas + "/{id}/respuesta";
		String mensaje = "{\"texto\":\"Revisaremos su caso\"}";
		comoAna(post(respuesta, tarea).contentType(MediaType.APPLICATION_JSON).content(mensaje))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(respuesta, tarea).header(HttpHeaders.AUTHORIZATION, "Bearer " + otroAsesor)
				.contentType(MediaType.APPLICATION_JSON).content(mensaje)).andExpect(status().isNotFound());
		mockMvc.perform(get(tareas).header(HttpHeaders.AUTHORIZATION, "Bearer " + administrador))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(tarea.longValue()));
		mockMvc.perform(post(respuesta, tarea).header(HttpHeaders.AUTHORIZATION, "Bearer " + administrador)
				.contentType(MediaType.APPLICATION_JSON).content(mensaje)).andExpect(status().isNoContent());
		comoServicio(post(respuesta, tarea).contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\":\"Otra respuesta\"}")).andExpect(status().isNotFound());
		assertThat(jdbcTemplate.queryForObject(
				"select respuesta from tarea_contacto_encuesta where id = ? and cerrada_en is not null", String.class,
				tarea.longValue())).isEqualTo("Revisaremos su caso");
		comoServicio(get(tareas)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

		String nps = "/api/v1/analitica/nps";
		String autorizacion = "Bearer " + administrador;
		String dia = hoy().toString();
		mockMvc.perform(
				get(nps).param("desde", dia).param("hasta", dia).header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].asesorId").value(tecnico.id())).andExpect(jsonPath("$[0].total").value(1))
				.andExpect(jsonPath("$[0].detractores").value(1)).andExpect(jsonPath("$[0].nps").value(-100));
		String ayer = hoy().minusDays(1).toString();
		mockMvc.perform(
				get(nps).param("desde", ayer).param("hasta", ayer).header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/analitica/objetivos").header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("NPS de los últimos 30 días: -100 (1 encuestas)")))
				.andExpect(content().string(containsString("Respuestas registradas por la agencia: 1")));
	}

	@Test
	void laLineaDeTiempoTraeCadaEtapaConSuHoraYLaEntregaEstimada() throws Exception {
		Long ordenId = abrir();
		avanzar(ordenId, "DIAGNOSTICO").andExpect(status().isOk());
		comoServicio(post("/api/v1/taller/ordenes/{id}/etapas", ordenId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"EN_REPARACION\",\"nota\":\"Cambio de balatas\",\"entregaEstimada\":\""
						+ manana().plusHours(2) + ":00\"}"))
				.andExpect(status().isOk());

		comoAna(get("/api/v1/app/ordenes/{id}", ordenId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.etapa").value("EN_REPARACION"))
				.andExpect(jsonPath("$.estatus").value("EN_PROCESO")).andExpect(jsonPath("$.avance").value(43))
				.andExpect(jsonPath("$.etapas.length()").value(3))
				.andExpect(jsonPath("$.etapas[0].etapa").value("RECIBIDO"))
				.andExpect(jsonPath("$.etapas[0].fin").isNotEmpty())
				.andExpect(jsonPath("$.etapas[2].etapa").value("EN_REPARACION"))
				.andExpect(jsonPath("$.etapas[2].nota").value("Cambio de balatas"))
				.andExpect(jsonPath("$.etapas[2].fin").isEmpty())
				.andExpect(jsonPath("$.entregaEstimada").value(manana().plusHours(2).toString() + ":00"));
		avanzar(ordenId, "DIAGNOSTICO").andExpect(status().isBadRequest());
		comoServicio(post("/api/v1/taller/ordenes").contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehiculoId\":" + marchDeAna + ",\"kilometraje\":15000}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_ABIERTA"));
	}

	@Test
	void laInspeccionTraeSemaforoFotosYVideosConUrlFirmada() throws Exception {
		Long ordenId = abrir();
		String inspeccionJson = "{\"punto\":\"Frenos\",\"resultado\":\"ROJO\",\"comentario\":\"Balatas al 10 %\"}";
		String revisada = comoServicio(post("/api/v1/taller/ordenes/{id}/inspeccion", ordenId)
				.contentType(MediaType.APPLICATION_JSON).content(inspeccionJson)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
		Integer puntoId = JsonPath.read(revisada, "$.inspeccion[0].id");
		comoServicio(post("/api/v1/taller/ordenes/{id}/inspeccion", ordenId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"punto\":\"Llantas\",\"resultado\":\"VERDE\"}")).andExpect(status().isOk());
		subir(ordenId, new MockMultipartFile("archivo", "frenos.jpg", "image/jpeg", FOTO), puntoId)
				.andExpect(status().isCreated());
		subir(ordenId, new MockMultipartFile("archivo", "recorrido.mp4", "video/mp4", VIDEO), null)
				.andExpect(status().isCreated());
		subir(ordenId, new MockMultipartFile("archivo", "nota.txt", "text/plain", FOTO), null)
				.andExpect(status().isBadRequest());

		String detalle = comoAna(get("/api/v1/app/ordenes/{id}", ordenId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.inspeccion.length()").value(2))
				.andExpect(jsonPath("$.inspeccion[0].punto").value("Frenos"))
				.andExpect(jsonPath("$.inspeccion[0].resultado").value("ROJO"))
				.andExpect(jsonPath("$.inspeccion[0].comentario").value("Balatas al 10 %"))
				.andExpect(jsonPath("$.inspeccion[0].evidencias[0].tipo").value("FOTO"))
				.andExpect(jsonPath("$.inspeccion[0].evidencias[0].url", startsWith("/api/v1/archivos/evidencias/")))
				.andExpect(jsonPath("$.inspeccion[1].resultado").value("VERDE"))
				.andExpect(jsonPath("$.inspeccion[1].evidencias.length()").value(0))
				.andExpect(jsonPath("$.evidencias.length()").value(1))
				.andExpect(jsonPath("$.evidencias[0].tipo").value("VIDEO")).andReturn().getResponse()
				.getContentAsString();

		String foto = JsonPath.read(detalle, "$.inspeccion[0].evidencias[0].url");
		mockMvc.perform(get(foto)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
				.andExpect(content().bytes(FOTO));
		String video = JsonPath.read(detalle, "$.evidencias[0].url");
		mockMvc.perform(get(video)).andExpect(status().isOk()).andExpect(content().contentType("video/mp4"));
	}

	@Test
	void elClienteApruebaUnAdicionalConSuFirmaYElTotalSeRecalcula() throws Exception {
		Long ordenId = abrir();
		String propuesta = proponer(ordenId, "Cambio de balatas", "450.00", "1200.50").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Integer balatas = JsonPath.read(propuesta, "$.adicionales[0].id");
		String otra = proponer(ordenId, "Lavado de motor", "200.00", "0.00").andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();
		Integer lavado = JsonPath.read(otra, "$.adicionales[1].id");

		comoAna(get("/api/v1/app/ordenes/activas")).andExpect(jsonPath("$[0].adicionalesPendientes").value(2));
		comoAna(get("/api/v1/app/ordenes/{id}", ordenId))
				.andExpect(jsonPath("$.adicionales[0].estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.adicionales[0].manoDeObra").value(450.0))
				.andExpect(jsonPath("$.adicionales[0].refacciones").value(1200.5))
				.andExpect(jsonPath("$.adicionales[0].total").value(1650.5))
				.andExpect(jsonPath("$.totalAdicionalesPendientes").value(1850.5))
				.andExpect(jsonPath("$.totalEstimado").value(0.0));

		responder(tokenDeAna, ordenId, balatas, true).andExpect(status().isOk())
				.andExpect(jsonPath("$.adicionales[0].estado").value("APROBADO"))
				.andExpect(jsonPath("$.adicionales[0].respondidoEn").isNotEmpty())
				.andExpect(jsonPath("$.totalAdicionalesAprobados").value(1650.5))
				.andExpect(jsonPath("$.totalAdicionalesPendientes").value(200.0))
				.andExpect(jsonPath("$.totalEstimado").value(1650.5));
		responder(tokenDeAna, ordenId, lavado, false).andExpect(status().isOk())
				.andExpect(jsonPath("$.adicionales[1].estado").value("RECHAZADO"))
				.andExpect(jsonPath("$.totalEstimado").value(1650.5));
		responder(tokenDeAna, ordenId, balatas, false).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ADICIONAL_YA_RESPONDIDO"));

		Map<String, Object> auditoria = jdbcTemplate.queryForMap(
				"select estado, respondido_por, firma_url, respondido_en from trabajo_adicional where id = ?",
				balatas.longValue());
		assertThat(auditoria.get("estado")).isEqualTo("APROBADO");
		assertThat(((Number) auditoria.get("respondido_por")).longValue()).isEqualTo(ana);
		assertThat((String) auditoria.get("firma_url")).startsWith("firmas/");
		assertThat(auditoria.get("respondido_en")).isNotNull();
	}

	@Test
	void listaParaEntregaTraeElTotalYElHorarioYAlEntregarseQuedaEnElHistorialConSuTicket() throws Exception {
		Long ordenId = abrir();
		renglon(ordenId, "MANO_DE_OBRA", "Afinación", "900.00").andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalEstimado").value(900.0));
		String propuesta = proponer(ordenId, "Cambio de balatas", "450.00", "1200.50").andReturn().getResponse()
				.getContentAsString();
		Integer balatas = JsonPath.read(propuesta, "$.adicionales[0].id");
		String otra = proponer(ordenId, "Lavado de motor", "200.00", "0.00").andReturn().getResponse()
				.getContentAsString();
		Integer lavado = JsonPath.read(otra, "$.adicionales[1].id");
		responder(tokenDeAna, ordenId, balatas, true).andExpect(status().isOk());
		responder(tokenDeAna, ordenId, lavado, false).andExpect(status().isOk());

		comoAna(get("/api/v1/app/ordenes/activas")).andExpect(jsonPath("$[0].totalEstimado").value(2550.5))
				.andExpect(jsonPath("$[0].horarioEntrega").isEmpty());
		avanzar(ordenId, "LISTO_PARA_ENTREGA").andExpect(status().isOk())
				.andExpect(jsonPath("$.totalEstimado").value(2550.5));
		comoAna(get("/api/v1/app/ordenes/activas")).andExpect(jsonPath("$[0].etapa").value("LISTO_PARA_ENTREGA"))
				.andExpect(jsonPath("$[0].totalEstimado").value(2550.5))
				.andExpect(jsonPath("$[0].horarioEntrega.fecha").isNotEmpty())
				.andExpect(jsonPath("$[0].horarioEntrega.desde").value("09:00"))
				.andExpect(jsonPath("$[0].horarioEntrega.hasta").value("17:00"));
		comoAna(get("/api/v1/app/ordenes/{id}", ordenId)).andExpect(jsonPath("$.horarioEntrega.desde").value("09:00"))
				.andExpect(jsonPath("$.totalEstimado").value(2550.5));
		comoAna(get("/api/v1/app/servicios/{id}", ordenId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.manoDeObra.length()").value(2))
				.andExpect(jsonPath("$.manoDeObra[1].concepto").value("Cambio de balatas"))
				.andExpect(jsonPath("$.refacciones.length()").value(1))
				.andExpect(jsonPath("$.refacciones[0].importe").value(1200.5))
				.andExpect(jsonPath("$.total").value(2550.5));
		renglon(ordenId, "REFACCION", "Bujías", "95.50").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_CERRADA"));

		avanzar(ordenId, "ENTREGADO").andExpect(status().isOk()).andExpect(jsonPath("$.totalEstimado").value(2550.5));
		comoAna(get("/api/v1/app/vehiculos/{id}/servicios", marchDeAna)).andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].id").value(ordenId))
				.andExpect(jsonPath("$.contenido[0].estatus").value("ENTREGADO"))
				.andExpect(jsonPath("$.contenido[0].total").value(2550.5));
		assertThat(jdbcTemplate.queryForObject("select incorporado_en from trabajo_adicional where id = ?",
				Object.class, balatas.longValue())).isNotNull();
		assertThat(jdbcTemplate.queryForObject("select incorporado_en from trabajo_adicional where id = ?",
				Object.class, lavado.longValue())).isNull();
	}

	@Test
	void unRenglonInvalidoOParaUnaOrdenQueNoExisteSeRechaza() throws Exception {
		Long ordenId = abrir();

		renglon(ordenId, "MANO_DE_OBRA", " ", "900.00").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		renglon(ordenId, "REFACCION", "Bujías", "-1.00").andExpect(status().isBadRequest());
		renglon(999_999L, "REFACCION", "Bujías", "95.50").andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/taller/ordenes/{id}/renglones", ordenId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"REFACCION\",\"concepto\":\"Bujías\",\"cantidad\":1,\"precioUnitario\":1}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void unaFirmaInvalidaUnaOrdenAjenaOTerminadaNoSeResponden() throws Exception {
		Long ordenId = abrir();
		String propuesta = proponer(ordenId, "Cambio de balatas", "450.00", "0.00").andReturn().getResponse()
				.getContentAsString();
		Integer balatas = JsonPath.read(propuesta, "$.adicionales[0].id");

		comoAna(post("/api/v1/app/ordenes/{id}/adicionales/{aid}/respuesta", ordenId, balatas)
				.contentType(MediaType.APPLICATION_JSON).content("{\"aprobado\":true,\"firma\":\"no-es-base64!\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		comoAna(post("/api/v1/app/ordenes/{id}/adicionales/{aid}/respuesta", ordenId, balatas)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"aprobado\":true,\"firma\":\"" + Base64.getEncoder().encodeToString(FOTO) + "\"}"))
				.andExpect(status().isBadRequest());
		comoBruno(get("/api/v1/app/ordenes/{id}", ordenId)).andExpect(status().isNotFound());
		responder(tokenDeBruno, ordenId, balatas, true).andExpect(status().isNotFound());
		responder(tokenDeAna, ordenId, 999_999, true).andExpect(status().isNotFound());

		avanzar(ordenId, "LISTO_PARA_ENTREGA").andExpect(status().isOk());
		responder(tokenDeAna, ordenId, balatas, true).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_CERRADA"));
		proponer(ordenId, "Lavado", "100.00", "0.00").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_CERRADA"));
	}

	@Test
	void soloServicioOperaElTallerYSoloElClienteVeSusOrdenes() throws Exception {
		mockMvc.perform(post("/api/v1/taller/ordenes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehiculoId\":" + marchDeAna + ",\"kilometraje\":15000}"))
				.andExpect(status().isForbidden());
		comoServicio(get("/api/v1/app/ordenes/activas")).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/app/ordenes/activas")).andExpect(status().isUnauthorized());
		comoServicio(post("/api/v1/taller/ordenes").contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehiculoId\":999999,\"kilometraje\":15000}")).andExpect(status().isNotFound());
	}

	private Long abrir() throws Exception {
		String abierta = comoServicio(post("/api/v1/taller/ordenes").contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehiculoId\":" + marchDeAna + ",\"kilometraje\":15200,\"entregaEstimada\":\"" + manana()
						+ ":00\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estatus").value("RECIBIDO")).andReturn()
				.getResponse().getContentAsString();
		return ((Number) JsonPath.read(abierta, "$.id")).longValue();
	}

	private ResultActions avanzar(Long ordenId, String etapa) throws Exception {
		return comoServicio(post("/api/v1/taller/ordenes/{id}/etapas", ordenId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"" + etapa + "\"}"));
	}

	private ResultActions proponer(Long ordenId, String concepto, String manoDeObra, String refacciones)
			throws Exception {
		return comoServicio(post("/api/v1/taller/ordenes/{id}/adicionales", ordenId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"concepto\":\"" + concepto + "\",\"manoDeObra\":"
						+ manoDeObra + ",\"refacciones\":" + refacciones + "}"));
	}

	private ResultActions renglon(Long ordenId, String tipo, String concepto, String precio) throws Exception {
		return comoServicio(post("/api/v1/taller/ordenes/{id}/renglones", ordenId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"" + tipo + "\",\"concepto\":\"" + concepto
						+ "\",\"cantidad\":1,\"precioUnitario\":" + precio + "}"));
	}

	private ResultActions subir(Long ordenId, MockMultipartFile archivo, Integer puntoId) throws Exception {
		var peticion = multipart("/api/v1/taller/ordenes/{id}/evidencias", ordenId).file(archivo);
		if (puntoId != null) {
			peticion.param("puntoInspeccionId", puntoId.toString());
		}
		return comoServicio(peticion);
	}

	private ResultActions responder(String token, Long ordenId, Integer adicionalId, boolean aprobado)
			throws Exception {
		return mockMvc.perform(post("/api/v1/app/ordenes/{id}/adicionales/{aid}/respuesta", ordenId, adicionalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"aprobado\":" + aprobado + ",\"firma\":\"" + Base64.getEncoder().encodeToString(FIRMA)
						+ "\"}"));
	}

	private ResultActions comoAna(MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna));
	}

	private ResultActions comoBruno(MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeBruno));
	}

	private ResultActions comoServicio(MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeServicio));
	}

	private static LocalDate hoy() {
		return LocalDate.now(ZONA_AGENCIA);
	}

	private static LocalDateTime manana() {
		return hoy().plusDays(1).atTime(18, 0);
	}
}
