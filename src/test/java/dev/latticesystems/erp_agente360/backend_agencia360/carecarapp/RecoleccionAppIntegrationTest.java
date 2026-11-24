package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Direcciones guardadas, cotización y recolección al agendar (V2-M17.01 y
 * V2-M17.02) de punta a punta con migraciones V27 y V28, la cobertura por
 * omisión (León, Gto., 15 km) y base de datos real (Testcontainers).
 */
@AutoConfigureMockMvc
@Transactional
class RecoleccionAppIntegrationTest extends AbstractIntegrationTest {

	private static final String DIRECCIONES = "/api/v1/app/direcciones";
	/** Cerca del centro de la cobertura (a menos de 2 km). */
	private static final String DENTRO = "{\"alias\": \"Casa\", \"calle\": \"Av. Siempre Viva 742\", "
			+ "\"colonia\": \"Centro\", \"cp\": \"37000\", \"ciudad\": \"León\", \"lat\": 21.13, \"lng\": -101.69}";
	/** Ciudad de México: a cientos de km de la cobertura. */
	private static final String FUERA = "{\"alias\": \"Oficina\", \"calle\": \"Reforma 1\", "
			+ "\"colonia\": \"Juárez\", \"cp\": \"06600\", \"ciudad\": \"CDMX\", \"lat\": 19.4326, \"lng\": -99.1332}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private CitaRepository citaRepository;

	@Autowired
	private TrasladoRepository trasladoRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeAna;
	private String tokenDeBruno;
	private Long vehiculoDeAna;
	private LocalDate diaHabil;

	@Autowired
	private FranjaHorarioRepository franjaHorarioRepository;

	@BeforeEach
	void registrarClientes() {
		Long ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		vehiculoDeAna = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000051", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));

		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));

		diaHabil = LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(3);
		while (diaHabil.getDayOfWeek() == DayOfWeek.SUNDAY) {
			diaHabil = diaHabil.plusDays(1);
		}
	}

	@Test
	void guardaListaEditaYBorraSoloLasPropias() throws Exception {
		long id = crearDireccion(tokenDeAna, DENTRO);
		mandar(post(DIRECCIONES), tokenDeAna, DENTRO).andExpect(status().isCreated())
				.andExpect(jsonPath("$.alias").value("Casa")).andExpect(jsonPath("$.cp").value("37000"))
				.andExpect(jsonPath("$.lat").value(21.13));

		mandar(get(DIRECCIONES), tokenDeAna, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(id));
		mandar(get(DIRECCIONES), tokenDeBruno, null).andExpect(jsonPath("$.length()").value(0));

		mandar(put(DIRECCIONES + "/" + id), tokenDeAna, DENTRO.replace("Casa", "Casa de Ana"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.alias").value("Casa de Ana"));

		mandar(put(DIRECCIONES + "/" + id), tokenDeBruno, DENTRO).andExpect(status().isNotFound());
		mandar(delete(DIRECCIONES + "/" + id), tokenDeBruno, null).andExpect(status().isNotFound());
		mandar(delete(DIRECCIONES + "/" + id), tokenDeAna, null).andExpect(status().isNoContent());
		mandar(delete(DIRECCIONES + "/" + id), tokenDeAna, null).andExpect(status().isNotFound());
	}

	@Test
	void unaDireccionFueraDeLaCoberturaResponde422() throws Exception {
		mandar(post(DIRECCIONES), tokenDeAna, FUERA).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("FUERA_DE_COBERTURA"))
				.andExpect(jsonPath("$.path").value(DIRECCIONES));
		long id = crearDireccion(tokenDeAna, DENTRO);
		mandar(put(DIRECCIONES + "/" + id), tokenDeAna, FUERA).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("FUERA_DE_COBERTURA"));
		mandar(get(DIRECCIONES), tokenDeAna, null).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].alias").value("Casa"));
	}

	@Test
	void rechazaDatosIncompletosOCodigoPostalMalFormado() throws Exception {
		mandar(post(DIRECCIONES), tokenDeAna, DENTRO.replace("37000", "3700")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		mandar(post(DIRECCIONES), tokenDeAna, DENTRO.replace("\"lat\": 21.13, ", ""))
				.andExpect(status().isBadRequest());
		mandar(post(DIRECCIONES), tokenDeAna, DENTRO.replace("Casa", "")).andExpect(status().isBadRequest());
	}

	@Test
	void laCotizacionDaCostoYFranjas() throws Exception {
		long id = crearDireccion(tokenDeAna, DENTRO);
		cotizar(tokenDeAna, id, diaHabil).andExpect(status().isOk()).andExpect(jsonPath("$.direccionId").value(id))
				.andExpect(jsonPath("$.fecha").value(diaHabil.toString()))
				.andExpect(jsonPath("$.costoPorTraslado").isNumber())
				.andExpect(jsonPath("$.recoleccion.inicio").value("07:00"))
				.andExpect(jsonPath("$.recoleccion.fin").value("09:00"))
				.andExpect(jsonPath("$.entrega.inicio").value("17:00"))
				.andExpect(jsonPath("$.entrega.fin").value("19:00"));
		String json = cotizar(tokenDeAna, id, diaHabil).andReturn().getResponse().getContentAsString();
		double porTraslado = JsonPath.<Number>read(json, "$.costoPorTraslado").doubleValue();
		double total = JsonPath.<Number>read(json, "$.costoTotal").doubleValue();
		assertThat(porTraslado).isGreaterThanOrEqualTo(80.0);
		assertThat(total).isEqualTo(porTraslado * 2);
	}

	@Test
	void laCotizacionResponde404ADireccionAjenaYFechasInvalidas() throws Exception {
		long id = crearDireccion(tokenDeAna, DENTRO);
		cotizar(tokenDeBruno, id, diaHabil).andExpect(status().isNotFound());
		cotizar(tokenDeAna, 999_999L, diaHabil).andExpect(status().isNotFound());
		cotizar(tokenDeAna, id, LocalDate.now(ZoneId.of("America/Mexico_City")).minusDays(1))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		LocalDate domingo = diaHabil;
		while (domingo.getDayOfWeek() != DayOfWeek.SUNDAY) {
			domingo = domingo.plusDays(1);
		}
		cotizar(tokenDeAna, id, domingo).andExpect(status().isBadRequest());
		mockMvc.perform(
				get("/api/v1/app/recoleccion/cotizacion").param("direccionId", "1").param("fecha", diaHabil.toString()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void agendarConRecoleccionCreaLaCitaYLosDosTrasladosJuntos() throws Exception {
		long id = crearDireccion(tokenDeAna, DENTRO);
		String respuesta = agendar(tokenDeAna, primeraFranja(), "{\"direccionId\": " + id + "}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.recoleccion.direccionId").value(id))
				.andExpect(jsonPath("$.recoleccion.costoTotal").isNumber())
				.andExpect(jsonPath("$.recoleccion.traslados.length()").value(2))
				.andExpect(jsonPath("$.recoleccion.traslados[0].tipo").value("recoger"))
				.andExpect(jsonPath("$.recoleccion.traslados[0].estado").value("programado"))
				.andExpect(jsonPath("$.recoleccion.traslados[0].ventanaInicio").value("07:00"))
				.andExpect(jsonPath("$.recoleccion.traslados[1].tipo").value("entregar"))
				.andExpect(jsonPath("$.recoleccion.traslados[1].ventanaFin").value("19:00")).andReturn().getResponse()
				.getContentAsString();
		long citaId = JsonPath.<Number>read(respuesta, "$.id").longValue();
		assertThat(trasladoRepository.findByCitaIdOrderByIdAsc(citaId)).hasSize(2)
				.allSatisfy(traslado -> assertThat(traslado.getFecha()).isEqualTo(diaHabil));

		mandar(delete(DIRECCIONES + "/" + id), tokenDeAna, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("DIRECCION_EN_USO"));
	}

	@Test
	void agendarSinRecoleccionNoCreaTraslados() throws Exception {
		agendar(tokenDeAna, primeraFranja(), null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.recoleccion").doesNotExist());
		assertThat(trasladoRepository.count()).isZero();
	}

	@Test
	void unaDireccionAjenaResponde404SinDejarLaCita() throws Exception {
		long deBruno = crearDireccion(tokenDeBruno, DENTRO);
		long citasAntes = citaRepository.count();
		agendar(tokenDeAna, primeraFranja(), "{\"direccionId\": " + deBruno + "}").andExpect(status().isNotFound());
		assertThat(citaRepository.count()).isEqualTo(citasAntes);
		assertThat(trasladoRepository.count()).isZero();
	}

	@Test
	void unaDireccionFueraDeCoberturaAlAgendarResponde422SinDejarLaCita() throws Exception {
		// La cobertura puede cambiar después de guardar la dirección: se simula
		// moviendo el pin por SQL.
		long id = crearDireccion(tokenDeAna, DENTRO);
		jdbcTemplate.update("update direccion_cliente set lat = 19.4326, lng = -99.1332 where id = ?", id);
		entityManager.clear();
		long citasAntes = citaRepository.count();
		agendar(tokenDeAna, primeraFranja(), "{\"direccionId\": " + id + "}")
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("FUERA_DE_COBERTURA"));
		assertThat(citaRepository.count()).isEqualTo(citasAntes);
	}

	private long crearDireccion(String token, String cuerpo) throws Exception {
		String json = mandar(post(DIRECCIONES), token, cuerpo).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	private ResultActions cotizar(String token, long direccionId, LocalDate fecha) throws Exception {
		return mockMvc.perform(get("/api/v1/app/recoleccion/cotizacion").param("direccionId", "" + direccionId)
				.param("fecha", fecha.toString()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions agendar(String token, Long franjaId, String recoleccion) throws Exception {
		String cuerpo = "{\"vehiculoId\": %d, \"franjaId\": %d, \"fecha\": \"%s\"%s}".formatted(vehiculoDeAna, franjaId,
				diaHabil, recoleccion == null ? "" : ", \"recoleccion\": " + recoleccion);
		return mandar(post("/api/v1/app/citas"), token, cuerpo);
	}

	private ResultActions mandar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
			String token, String cuerpo) throws Exception {
		peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		if (cuerpo != null) {
			peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		}
		return mockMvc.perform(peticion);
	}

	private Long primeraFranja() {
		return franjaHorarioRepository.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc().stream()
				.filter(franja -> franja.esDe(diaHabil)).findFirst().orElseThrow().getId();
	}
}
