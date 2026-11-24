package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoTraslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Traslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaHorarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.TrasladoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
 * Seguimiento del traslado por el cliente y el chofer (V2-M17.03) y efecto de
 * cancelar o reprogramar la cita sobre sus traslados (V2-M17.05).
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class TrasladoSeguimientoIntegrationTest extends AbstractIntegrationTest {

	private static final String DIRECCION = "{\"alias\": \"Casa\", \"calle\": \"Av. Siempre Viva 742\", "
			+ "\"colonia\": \"Centro\", \"cp\": \"37000\", \"ciudad\": \"León\", \"lat\": 21.13, \"lng\": -101.69}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private TrasladoRepository trasladoRepository;

	@Autowired
	private FranjaHorarioRepository franjaHorarioRepository;

	@Autowired
	private ApplicationEvents eventos;

	@PersistenceContext
	private EntityManager entityManager;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Long anaId;
	private String tokenDeAna;
	private String tokenDeBruno;
	private String tokenDelPersonal;
	private Long vehiculoDeAna;
	private Long direccionDeAna;
	private LocalDate diaHabil;

	@BeforeEach
	void registrarClientes() throws Exception {
		anaId = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		vehiculoDeAna = registroClientesService
				.asignarVehiculo(anaId, new VehiculoNuevo(null, "3N1CK3CD5RL000071", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(anaId, "Temporal123");
		tokenDeAna = jwtService.generateToken(anaId, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));
		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));
		tokenDelPersonal = jwtService.generateToken(900_001L, "marketing", List.of(Role.GERENTE_MARKETING));

		diaHabil = diaHabilDesde(LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(3));
		String direccion = enviar(post("/api/v1/app/direcciones"), tokenDeAna, DIRECCION).andReturn().getResponse()
				.getContentAsString();
		direccionDeAna = JsonPath.<Number>read(direccion, "$.id").longValue();
	}

	@Test
	void elDuenoVeElTrasladoYUnAjenoRecibe404() throws Exception {
		long citaId = agendarConRecoleccion();
		long recoger = trasladoDe(citaId, "recoger");

		enviar(get("/api/v1/app/traslados/" + recoger), tokenDeAna, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(recoger)).andExpect(jsonPath("$.citaId").value(citaId))
				.andExpect(jsonPath("$.tipo").value("recoger")).andExpect(jsonPath("$.estado").value("programado"))
				.andExpect(jsonPath("$.ventanaInicio").value("07:00")).andExpect(jsonPath("$.costo").isNumber())
				.andExpect(jsonPath("$.ubicacionChofer").doesNotExist());
		enviar(get("/api/v1/app/traslados/" + recoger), tokenDeBruno, null).andExpect(status().isNotFound());
		enviar(get("/api/v1/app/traslados/999999"), tokenDeAna, null).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/traslados/" + recoger)).andExpect(status().isUnauthorized());
	}

	@Test
	void elChoferLlevaLaRecoleccionDeProgramadoAEnTaller() throws Exception {
		long recoger = trasladoDe(agendarConRecoleccion(), "recoger");

		cambiar(recoger, "en_camino").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("en_camino"));
		cambiar(recoger, "recogido").andExpect(status().isOk());
		cambiar(recoger, "en_taller").andExpect(status().isOk());
		enviar(get("/api/v1/app/traslados/" + recoger), tokenDeAna, null)
				.andExpect(jsonPath("$.estado").value("en_taller"));
		assertThat(trasladoRepository.findById(recoger).orElseThrow().getChoferId()).isEqualTo(900_001L);
	}

	@Test
	void laEntregaVaPorDeRegresoYEntregado() throws Exception {
		long entregar = trasladoDe(agendarConRecoleccion(), "entregar");

		cambiar(entregar, "de_regreso").andExpect(status().isOk());
		cambiar(entregar, "entregado").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("entregado"));
	}

	@Test
	void unaTransicionInvalidaResponde409YUnEstadoDesconocido400() throws Exception {
		long recoger = trasladoDe(agendarConRecoleccion(), "recoger");

		cambiar(recoger, "recogido").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		cambiar(recoger, "de_regreso").andExpect(status().isConflict());
		cambiar(recoger, "cancelado").andExpect(status().isConflict());
		cambiar(recoger, "volando").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		cambiar(999_999L, "en_camino").andExpect(status().isNotFound());
	}

	@Test
	void soloElPersonalMueveElTraslado() throws Exception {
		long recoger = trasladoDe(agendarConRecoleccion(), "recoger");

		enviar(patch("/api/v1/traslados/" + recoger + "/estado"), tokenDeAna, "{\"estado\": \"en_camino\"}")
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/traslados/" + recoger + "/estado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"estado\": \"en_camino\"}")).andExpect(status().isUnauthorized());
		enviar(put("/api/v1/traslados/" + recoger + "/ubicacion"), tokenDeAna, "{\"lat\": 21.1, \"lng\": -101.6}")
				.andExpect(status().isForbidden());
	}

	@Test
	void laUbicacionDelChoferSoloSeVeEnCaminoODeRegreso() throws Exception {
		long recoger = trasladoDe(agendarConRecoleccion(), "recoger");

		ubicar(recoger, 21.13, -101.69).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		cambiar(recoger, "en_camino").andExpect(status().isOk());
		enviar(get("/api/v1/app/traslados/" + recoger), tokenDeAna, null)
				.andExpect(jsonPath("$.ubicacionChofer").doesNotExist());

		ubicar(recoger, 21.13, -101.69).andExpect(status().isNoContent());
		enviar(get("/api/v1/app/traslados/" + recoger), tokenDeAna, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.ubicacionChofer.lat").value(21.13))
				.andExpect(jsonPath("$.ubicacionChofer.lng").value(-101.69))
				.andExpect(jsonPath("$.ubicacionChofer.actualizadaEn").exists());

		cambiar(recoger, "recogido").andExpect(status().isOk());
		enviar(get("/api/v1/app/traslados/" + recoger), tokenDeAna, null)
				.andExpect(jsonPath("$.ubicacionChofer").doesNotExist());
		ubicar(recoger, 21.13, -101.69).andExpect(status().isConflict());
	}

	@Test
	void unaUbicacionFueraDeRangoResponde400() throws Exception {
		long recoger = trasladoDe(agendarConRecoleccion(), "recoger");
		cambiar(recoger, "en_camino").andExpect(status().isOk());

		ubicar(recoger, 91, -101.69).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		ubicar(999_999L, 21.13, -101.69).andExpect(status().isNotFound());
	}

	@Test
	void cadaEstadoYCadaReporteAvisanAlClienteDueno() throws Exception {
		long citaId = agendarConRecoleccion();
		long recoger = trasladoDe(citaId, "recoger");

		cambiar(recoger, "en_camino").andExpect(status().isOk());
		ubicar(recoger, 21.13, -101.69).andExpect(status().isNoContent());
		cambiar(recoger, "recogido").andExpect(status().isOk());
		cambiar(recoger, "de_regreso").andExpect(status().isConflict());

		List<AvisoAlCliente> avisos = eventos.stream(AvisoAlCliente.class).toList();
		assertThat(avisos).extracting(AvisoAlCliente::evento).containsExactly("traslado.estado", "traslado.ubicacion",
				"traslado.estado");
		assertThat(avisos).allSatisfy(aviso -> assertThat(aviso.clienteId()).isEqualTo(anaId));
		assertThat(avisos.get(0).datos()).containsEntry("trasladoId", recoger).containsEntry("citaId", citaId)
				.containsEntry("tipo", "recoger").containsEntry("estado", "en_camino");
		assertThat(avisos.get(1).datos()).containsEntry("trasladoId", recoger).containsKeys("lat", "lng",
				"actualizadaEn");
		assertThat(avisos.get(2).datos()).containsEntry("estado", "recogido");
	}

	@Test
	void cancelarLaCitaCancelaSusTrasladosProgramadosYNoTocaLosQueYaSalieron() throws Exception {
		long citaId = agendarConRecoleccion();
		long recoger = trasladoDe(citaId, "recoger");
		long entregar = trasladoDe(citaId, "entregar");
		cambiar(recoger, "en_camino").andExpect(status().isOk());

		enviar(post("/api/v1/app/citas/" + citaId + "/cancelar"), tokenDeAna, null).andExpect(status().isOk());

		entityManager.clear();
		assertThat(trasladoRepository.findById(entregar).orElseThrow().getEstado()).isEqualTo(EstadoTraslado.CANCELADO);
		assertThat(trasladoRepository.findById(recoger).orElseThrow().getEstado()).isEqualTo(EstadoTraslado.EN_CAMINO);
		cambiar(entregar, "de_regreso").andExpect(status().isConflict());
	}

	@Test
	void reprogramarLaCitaMueveLaFechaDeSusTrasladosProgramados() throws Exception {
		long citaId = agendarConRecoleccion();
		long recoger = trasladoDe(citaId, "recoger");
		long entregar = trasladoDe(citaId, "entregar");
		cambiar(recoger, "en_camino").andExpect(status().isOk());
		LocalDate otroDia = diaHabilDesde(diaHabil.plusDays(1));

		enviar(post("/api/v1/app/citas/" + citaId + "/reprogramar"), tokenDeAna,
				"{\"franjaId\": " + franjaDelDia(otroDia) + ", \"fecha\": \"" + otroDia + "\"}")
				.andExpect(status().isOk());

		entityManager.clear();
		Traslado programado = trasladoRepository.findById(entregar).orElseThrow();
		assertThat(programado.getFecha()).isEqualTo(otroDia);
		assertThat(programado.getVentanaInicio().toString()).isEqualTo("17:00");
		assertThat(trasladoRepository.findById(recoger).orElseThrow().getFecha()).isEqualTo(diaHabil);
	}

	private long agendarConRecoleccion() throws Exception {
		String cuerpo = "{\"vehiculoId\": %d, \"franjaId\": %d, \"fecha\": \"%s\", \"recoleccion\": {\"direccionId\": %d}}"
				.formatted(vehiculoDeAna, franjaDelDia(diaHabil), diaHabil, direccionDeAna);
		String json = enviar(post("/api/v1/app/citas"), tokenDeAna, cuerpo).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	private long trasladoDe(long citaId, String tipo) {
		return trasladoRepository.findByCitaIdOrderByIdAsc(citaId).stream()
				.filter(traslado -> traslado.getTipo().valor().equals(tipo)).findFirst().orElseThrow().getId();
	}

	private ResultActions cambiar(long id, String estado) throws Exception {
		return enviar(patch("/api/v1/traslados/" + id + "/estado"), tokenDelPersonal,
				"{\"estado\": \"" + estado + "\"}");
	}

	private ResultActions ubicar(long id, double lat, double lng) throws Exception {
		return enviar(put("/api/v1/traslados/" + id + "/ubicacion"), tokenDelPersonal,
				"{\"lat\": " + lat + ", \"lng\": " + lng + "}");
	}

	private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, String cuerpo) throws Exception {
		peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		if (cuerpo != null) {
			peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		}
		return mockMvc.perform(peticion);
	}

	private Long franjaDelDia(LocalDate dia) {
		return franjaHorarioRepository.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc().stream()
				.filter(franja -> franja.esDe(dia)).findFirst().orElseThrow().getId();
	}

	private static LocalDate diaHabilDesde(LocalDate dia) {
		LocalDate habil = dia;
		while (habil.getDayOfWeek() == DayOfWeek.SUNDAY) {
			habil = habil.plusDays(1);
		}
		return habil;
	}
}
