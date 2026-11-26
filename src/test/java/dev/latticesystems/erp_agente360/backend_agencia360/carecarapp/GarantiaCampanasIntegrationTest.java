package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FranjaHorario;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaHorarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garantía y campañas de seguridad de punta a punta (V2-M10.01 a V2-M10.04): el
 * personal registra coberturas y publica campañas; el cliente ve su garantía
 * contra su km, sus campañas por modelo, año y VIN, y agenda la atención. El
 * modelo del auto lleva un sufijo propio para que el cruce no dependa de lo que
 * dejen otras pruebas en la base compartida. Cada prueba se revierte al
 * terminar.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class GarantiaCampanasIntegrationTest extends AbstractIntegrationTest {

	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

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
	private FranjaHorarioRepository franjaHorarioRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ApplicationEvents eventos;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String modelo;
	private Long ana;
	private Long autoDeAna;
	private String tokenDeAna;
	private String tokenDeBruno;
	private String tokenDeServicio;

	@BeforeEach
	void registrarClientesYPersonal() {
		modelo = "Prueba" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
		ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		autoDeAna = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000150", "Nissan", modelo, 2025, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));
		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		registroClientesService.asignarVehiculo(bruno,
				new VehiculoNuevo(null, "3N1CK3CD5RL000250", "Nissan", modelo, 2025, null));
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));
		Usuario asesor = usuarioRepository.save(new Usuario("servicio-garantias", "hash-de-prueba", "Laura Méndez",
				rolRepository.findByNombre("SERVICIO").orElseThrow()));
		tokenDeServicio = jwtService.generateToken(asesor.getId(), asesor.getUsuario(), List.of(Role.SERVICIO));
	}

	@Test
	void laGarantiaDiceCadaComponenteContraElKmActual() throws Exception {
		LocalDate hoy = LocalDate.now(ZONA_AGENCIA);
		garantia(autoDeAna, "Motor y transmisión", hoy.minusYears(1), hoy.plusYears(2), 60_000)
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("VIGENTE"))
				.andExpect(jsonPath("$.kmRestantes").isEmpty());
		garantia(autoDeAna, "Batería", hoy.minusYears(1), hoy.plusDays(30), null).andExpect(status().isCreated());
		comoAna(post("/api/v1/app/vehiculos/{id}/lecturas-km", autoDeAna).contentType(MediaType.APPLICATION_JSON)
				.content("{\"km\":59200}")).andExpect(status().isCreated());

		comoAna(get("/api/v1/app/vehiculos/{id}/garantia", autoDeAna)).andExpect(status().isOk())
				.andExpect(jsonPath("$.vehiculoId").value(autoDeAna)).andExpect(jsonPath("$.kmActual").value(59_200))
				.andExpect(jsonPath("$.coberturas.length()").value(2))
				.andExpect(jsonPath("$.coberturas[0].componente").value("Batería"))
				.andExpect(jsonPath("$.coberturas[0].estado").value("POR_VENCER"))
				.andExpect(jsonPath("$.coberturas[0].diasRestantes").value(30))
				.andExpect(jsonPath("$.coberturas[0].limite").value("fecha"))
				.andExpect(jsonPath("$.coberturas[1].componente").value("Motor y transmisión"))
				.andExpect(jsonPath("$.coberturas[1].kmLimite").value(60_000))
				.andExpect(jsonPath("$.coberturas[1].kmRestantes").value(800))
				.andExpect(jsonPath("$.coberturas[1].estado").value("POR_VENCER"))
				.andExpect(jsonPath("$.coberturas[1].limite").value("km"))
				.andExpect(jsonPath("$.coberturas[1].consumo").value(98));
		comoBruno(get("/api/v1/app/vehiculos/{id}/garantia", autoDeAna)).andExpect(status().isNotFound());
	}

	@Test
	void registrarUnaGarantiaPideDatosValidosYEsDelPersonal() throws Exception {
		LocalDate hoy = LocalDate.now(ZONA_AGENCIA);
		garantia(autoDeAna, "Motor", hoy, hoy.minusDays(1), null).andExpect(status().isBadRequest());
		garantia(autoDeAna, " ", hoy, hoy.plusYears(1), null).andExpect(status().isBadRequest());
		garantia(autoDeAna, "Motor", hoy, hoy.plusYears(1), 0).andExpect(status().isBadRequest());
		garantia(999_999L, "Motor", hoy, hoy.plusYears(1), null).andExpect(status().isNotFound());
		comoAna(post("/api/v1/garantias").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoGarantia(autoDeAna, "Motor", hoy, hoy.plusYears(1), null)))
				.andExpect(status().isForbidden());
	}

	@Test
	void unaCampanaPublicadaAvisaAlDuenoYSeAgendaConMotivoCampana() throws Exception {
		String codigo = "RC-" + modelo;
		String publicada = publicar(codigo, "CRITICA", "3N1CK3CD5RL000100", "3N1CK3CD5RL000199")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.codigo").value(codigo.toUpperCase(Locale.ROOT)))
				.andExpect(jsonPath("$.vehiculosAfectados").value(1)).andReturn().getResponse().getContentAsString();
		Integer campanaId = JsonPath.read(publicada, "$.id");
		List<AvisoAlCliente> avisos = eventos.stream(AvisoAlCliente.class)
				.filter(aviso -> aviso.evento().equals("campana.nueva")).toList();
		assertThat(avisos).singleElement().satisfies(aviso -> {
			assertThat(aviso.clienteId()).isEqualTo(ana);
			assertThat(aviso.datos()).containsEntry("vehiculoId", autoDeAna).containsEntry("critica", true);
		});

		comoAna(get("/api/v1/app/vehiculos/{id}/campanas", autoDeAna)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(campanaId))
				.andExpect(jsonPath("$[0].gravedad").value("CRITICA"))
				.andExpect(jsonPath("$[0].estado").value("ABIERTA")).andExpect(jsonPath("$[0].citaId").isEmpty())
				.andExpect(jsonPath("$[0].queHacer").value("Agenda una revisión; el cambio no tiene costo."));

		LocalDate dia = diaHabil();
		String cita = agendar(tokenDeAna, campanaId, franja(dia), dia).andExpect(status().isCreated())
				.andExpect(jsonPath("$.motivo").value("campana")).andExpect(jsonPath("$.vehiculoId").value(autoDeAna))
				.andReturn().getResponse().getContentAsString();
		Integer citaId = JsonPath.read(cita, "$.id");
		comoAna(get("/api/v1/app/vehiculos/{id}/campanas", autoDeAna))
				.andExpect(jsonPath("$[0].estado").value("AGENDADA")).andExpect(jsonPath("$[0].citaId").value(citaId));
		agendar(tokenDeAna, campanaId, franja(dia), dia).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CAMPANA_NO_AGENDABLE"));

		comoAna(post("/api/v1/app/citas/{id}/cancelar", citaId)).andExpect(status().isOk());
		comoAna(get("/api/v1/app/vehiculos/{id}/campanas", autoDeAna))
				.andExpect(jsonPath("$[0].estado").value("ABIERTA")).andExpect(jsonPath("$[0].citaId").isEmpty());

		comoServicio(post("/api/v1/campanas/{id}/vehiculos/{vehiculo}/atendida", campanaId, autoDeAna))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ATENDIDA"));
		agendar(tokenDeAna, campanaId, franja(dia), dia).andExpect(status().isConflict());
	}

	@Test
	void lasCampanasSonPorModeloAnioYVinYSoloDelDueno() throws Exception {
		publicar("RC-OTRO-" + modelo, "MEDIA", "3N1CK3CD5RL000200", "3N1CK3CD5RL000299")
				.andExpect(jsonPath("$.vehiculosAfectados").value(1));
		comoAna(get("/api/v1/app/vehiculos/{id}/campanas", autoDeAna)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		String sinRango = publicar("RC-TODOS-" + modelo, "BAJA", null, null)
				.andExpect(jsonPath("$.vehiculosAfectados").value(2)).andReturn().getResponse().getContentAsString();
		Integer campanaId = JsonPath.read(sinRango, "$.id");

		comoAna(get("/api/v1/app/vehiculos/{id}/campanas", autoDeAna)).andExpect(jsonPath("$.length()").value(1));
		comoBruno(get("/api/v1/app/vehiculos/{id}/campanas", autoDeAna)).andExpect(status().isNotFound());
		LocalDate dia = diaHabil();
		agendar(tokenDeBruno, campanaId, franja(dia), dia).andExpect(status().isNotFound());
		publicar("RC-TODOS-" + modelo, "BAJA", null, null).andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/campanas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpoCampana("RC-X", "BAJA", null, null)))
				.andExpect(status().isForbidden());
	}

	private ResultActions garantia(Long vehiculoId, String componente, LocalDate inicia, LocalDate vence,
			Integer kmLimite) throws Exception {
		return comoServicio(post("/api/v1/garantias").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoGarantia(vehiculoId, componente, inicia, vence, kmLimite)));
	}

	private static String cuerpoGarantia(Long vehiculoId, String componente, LocalDate inicia, LocalDate vence,
			Integer kmLimite) {
		return "{\"vehiculoId\":" + vehiculoId + ",\"componente\":\"" + componente + "\",\"iniciaEn\":\"" + inicia
				+ "\",\"venceEn\":\"" + vence + "\",\"kmLimite\":" + kmLimite + "}";
	}

	private ResultActions publicar(String codigo, String gravedad, String vinDesde, String vinHasta) throws Exception {
		return comoServicio(post("/api/v1/campanas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoCampana(codigo, gravedad, vinDesde, vinHasta)));
	}

	private String cuerpoCampana(String codigo, String gravedad, String vinDesde, String vinHasta) {
		return "{\"codigo\":\"" + codigo + "\",\"titulo\":\"Bolsa de aire del conductor\","
				+ "\"descripcion\":\"El inflador puede fallar.\","
				+ "\"queHacer\":\"Agenda una revisión; el cambio no tiene costo.\",\"gravedad\":\"" + gravedad
				+ "\",\"modelo\":\"" + modelo + "\",\"anioDesde\":2024,\"anioHasta\":2026,\"vinDesde\":"
				+ texto(vinDesde) + ",\"vinHasta\":" + texto(vinHasta) + "}";
	}

	private static String texto(String valor) {
		return valor == null ? "null" : "\"" + valor + "\"";
	}

	private ResultActions agendar(String token, Integer campanaId, Long franjaId, LocalDate dia) throws Exception {
		return mockMvc.perform(post("/api/v1/app/vehiculos/{id}/campanas/{campana}/cita", autoDeAna, campanaId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"franjaId\":" + franjaId + ",\"fecha\":\"" + dia + "\"}"));
	}

	private Long franja(LocalDate dia) {
		List<FranjaHorario> delDia = franjaHorarioRepository.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc().stream()
				.filter(franja -> franja.esDe(dia)).toList();
		return delDia.getFirst().getId();
	}

	private static LocalDate diaHabil() {
		LocalDate dia = LocalDate.now(ZONA_AGENCIA).plusDays(3);
		while (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
			dia = dia.plusDays(1);
		}
		return dia;
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
}
