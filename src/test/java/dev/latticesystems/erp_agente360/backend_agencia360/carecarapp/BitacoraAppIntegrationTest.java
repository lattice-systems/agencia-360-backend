package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bitácora del conductor (V2-M18.01 y V2-M18.04) de punta a punta con la
 * migración V38 y base de datos real (Testcontainers). Cada prueba se revierte
 * al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class BitacoraAppIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeAna;
	private String tokenDeBruno;
	private Long vehiculoDeAna;
	private Long vehiculoDeBruno;

	@BeforeEach
	void registrarClientes() {
		Long ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		vehiculoDeAna = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000061", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));
		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		vehiculoDeBruno = registroClientesService
				.asignarVehiculo(bruno, new VehiculoNuevo(null, "3N1CK3CD5RL000062", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));
	}

	@Test
	void unaCargaDeGasolinaSeGuardaYCreaUnaLecturaDeKmDelCliente() throws Exception {
		crear(vehiculoDeAna, gasolina("850.50", "35.2", 45210, hoy())).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.vehiculoId").value(vehiculoDeAna))
				.andExpect(jsonPath("$.tipo").value("gasolina")).andExpect(jsonPath("$.monto").value(850.5))
				.andExpect(jsonPath("$.litros").value(35.2)).andExpect(jsonPath("$.km").value(45210))
				.andExpect(jsonPath("$.fecha").value(hoy().toString())).andExpect(jsonPath("$.descripcion").isEmpty());

		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/mantenimiento", vehiculoDeAna).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(jsonPath("$.kmActual").value(45210))
				.andExpect(jsonPath("$.kmFuente").value("cliente"));
	}

	@Test
	void unKmMenorAlUltimoResponde422YNoGuardaNada() throws Exception {
		crear(vehiculoDeAna, gasolina("800", "30", 45000, hoy())).andExpect(status().isCreated());

		crear(vehiculoDeAna, gasolina("800", "30", 44999, hoy())).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("KM_INVALIDO"));
		listar(vehiculoDeAna, "").andExpect(jsonPath("$.contenido.length()").value(1));
	}

	@Test
	void losDemasTiposSeGuardanSinLitrosNiLectura() throws Exception {
		crear(vehiculoDeAna, "{\"tipo\":\"estacionamiento\",\"monto\":45,\"fecha\":\"" + hoy() + "\"}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("estacionamiento"))
				.andExpect(jsonPath("$.litros").isEmpty());
		crear(vehiculoDeAna,
				"{\"tipo\":\"otro\",\"monto\":300,\"fecha\":\"" + hoy() + "\",\"descripcion\":\"Verificación\"}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.descripcion").value("Verificación"));

		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/mantenimiento", vehiculoDeAna).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(jsonPath("$.kmActual").isEmpty());
	}

	@Test
	void losDatosIncompletosOInvalidosResponden400() throws Exception {
		crear(vehiculoDeAna, "{\"tipo\":\"gasolina\",\"monto\":800,\"km\":100,\"fecha\":\"" + hoy() + "\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		crear(vehiculoDeAna, "{\"tipo\":\"gasolina\",\"monto\":800,\"litros\":20,\"fecha\":\"" + hoy() + "\"}")
				.andExpect(status().isBadRequest());
		crear(vehiculoDeAna, "{\"tipo\":\"otro\",\"monto\":300,\"fecha\":\"" + hoy() + "\"}")
				.andExpect(status().isBadRequest());
		crear(vehiculoDeAna, "{\"tipo\":\"lavado\",\"monto\":0,\"fecha\":\"" + hoy() + "\"}")
				.andExpect(status().isBadRequest());
		crear(vehiculoDeAna, "{\"tipo\":\"lavado\",\"monto\":90,\"fecha\":\"" + hoy().plusDays(1) + "\"}")
				.andExpect(status().isBadRequest());
		crear(vehiculoDeAna, "{\"tipo\":\"combustible\",\"monto\":90,\"fecha\":\"" + hoy() + "\"}")
				.andExpect(status().isBadRequest());
		crear(vehiculoDeAna, "{\"monto\":90,\"fecha\":\"" + hoy() + "\"}").andExpect(status().isBadRequest());
	}

	@Test
	void listaPorMesLosMasRecientesPrimeroYPaginado() throws Exception {
		LocalDate hoy = hoy();
		LocalDate delMesPasado = hoy.withDayOfMonth(1).minusDays(1);
		Long viejo = idDe(
				crear(vehiculoDeAna, "{\"tipo\":\"lavado\",\"monto\":90,\"fecha\":\"" + delMesPasado + "\"}"));
		Long primero = idDe(crear(vehiculoDeAna, "{\"tipo\":\"casetas\",\"monto\":120,\"fecha\":\"" + hoy + "\"}"));
		Long segundo = idDe(crear(vehiculoDeAna, "{\"tipo\":\"casetas\",\"monto\":60,\"fecha\":\"" + hoy + "\"}"));

		listar(vehiculoDeAna, "").andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(3))
				.andExpect(jsonPath("$.contenido[0].id").value(segundo))
				.andExpect(jsonPath("$.contenido[1].id").value(primero))
				.andExpect(jsonPath("$.contenido[2].id").value(viejo)).andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.totalPaginas").value(1));
		listar(vehiculoDeAna, "?mes=" + hoy.toString().substring(0, 7))
				.andExpect(jsonPath("$.contenido.length()").value(2));
		listar(vehiculoDeAna, "?mes=" + delMesPasado.toString().substring(0, 7))
				.andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].id").value(viejo));
		listar(vehiculoDeAna, "?tamano=2&pagina=1").andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].id").value(viejo)).andExpect(jsonPath("$.totalPaginas").value(2));
		listar(vehiculoDeAna, "?mes=2026-13").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		listar(vehiculoDeAna, "?tamano=51").andExpect(status().isBadRequest());
	}

	@Test
	void elClienteCorrigeUnGastoPropio() throws Exception {
		Long id = idDe(crear(vehiculoDeAna, gasolina("800", "30", 45000, hoy())));

		corregir(tokenDeAna, id, gasolina("900", "33.5", 45000, hoy())).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.monto").value(900.0))
				.andExpect(jsonPath("$.litros").value(33.5));
		corregir(tokenDeAna, id, "{\"tipo\":\"lavado\",\"monto\":90,\"fecha\":\"" + hoy() + "\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("lavado"))
				.andExpect(jsonPath("$.litros").isEmpty());
		corregir(tokenDeAna, id, "{\"tipo\":\"otro\",\"monto\":90,\"fecha\":\"" + hoy() + "\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void corregirUnaGasolinaValidaElKmSinContarSuPropiaLectura() throws Exception {
		crear(vehiculoDeAna, gasolina("800", "30", 45000, hoy().minusDays(5))).andExpect(status().isCreated());
		Long id = idDe(crear(vehiculoDeAna, gasolina("800", "30", 45500, hoy())));

		corregir(tokenDeAna, id, gasolina("800", "30", 45300, hoy())).andExpect(status().isOk());
		corregir(tokenDeAna, id, gasolina("800", "30", 44000, hoy())).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("KM_INVALIDO"));
	}

	@Test
	void borrarUnGastoDevuelve204YConservaLaLecturaDeKm() throws Exception {
		Long id = idDe(crear(vehiculoDeAna, gasolina("800", "30", 45000, hoy())));

		mockMvc.perform(delete("/api/v1/app/gastos/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isNoContent());

		listar(vehiculoDeAna, "").andExpect(jsonPath("$.contenido.length()").value(0));
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/mantenimiento", vehiculoDeAna).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(jsonPath("$.kmActual").value(45000));
		mockMvc.perform(delete("/api/v1/app/gastos/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isNotFound());
	}

	@Test
	void unGastoOUnVehiculoAjenoResponde404YSinSesion401() throws Exception {
		Long deAna = idDe(crear(vehiculoDeAna, gasolina("800", "30", 45000, hoy())));

		listar(vehiculoDeBruno, "").andExpect(status().isNotFound());
		crear(vehiculoDeBruno, gasolina("800", "30", 100, hoy())).andExpect(status().isNotFound());
		corregir(tokenDeBruno, deAna, gasolina("1", "1", 50000, hoy())).andExpect(status().isNotFound());
		mockMvc.perform(
				delete("/api/v1/app/gastos/{id}", deAna).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeBruno))
				.andExpect(status().isNotFound());
		corregir(tokenDeAna, 999_999L, gasolina("1", "1", 50000, hoy())).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/gastos", vehiculoDeAna)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/app/gastos/{id}", deAna)).andExpect(status().isUnauthorized());
	}

	private ResultActions crear(Long vehiculoId, String cuerpo) throws Exception {
		return mockMvc.perform(post("/api/v1/app/vehiculos/{id}/gastos", vehiculoId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo));
	}

	private ResultActions corregir(String token, Long gastoId, String cuerpo) throws Exception {
		return mockMvc
				.perform(put("/api/v1/app/gastos/{id}", gastoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions listar(Long vehiculoId, String consulta) throws Exception {
		return mockMvc.perform(get("/api/v1/app/vehiculos/{id}/gastos" + consulta, vehiculoId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna));
	}

	private static Long idDe(ResultActions respuesta) throws Exception {
		respuesta.andExpect(status().isCreated());
		Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}

	private static String gasolina(String monto, String litros, int km, LocalDate fecha) {
		return "{\"tipo\":\"gasolina\",\"monto\":" + monto + ",\"litros\":" + litros + ",\"km\":" + km + ",\"fecha\":\""
				+ fecha + "\"}";
	}

	private static LocalDate hoy() {
		return LocalDate.now(ZoneId.of("America/Mexico_City"));
	}
}
