package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V2-M22.02 de punta a punta: la solicitud se abre, el archivo se prepara fuera
 * de la petición con los datos reales de la cuenta, y solo su dueña lo
 * descarga.
 */
@AutoConfigureMockMvc
class ArcoIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper json;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private long clienteId;
	private String token;
	private String unico;

	@BeforeEach
	void clienteConDatos() {
		unico = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String correo = "arco-" + unico + "@correo.com";
		clienteId = registroClientesService.registrar(new NuevoCliente("Ana Arco", correo, "5512345678", null)).id();
		cuentasAppService.abrir(clienteId, "Temporal123");
		token = jwtService.generateToken(clienteId, correo, List.of(Role.CLIENTE_CARECAR));

		long vehiculo = jdbc.queryForObject("""
				insert into vehiculo_cliente
				  (cliente_id, vin, modelo, anio, placas, kilometraje, created_at, updated_at)
				values (?, ?, 'Kicks', 2023, 'ABC123', 12000, now(), now()) returning id
				""", Long.class, clienteId, ("1HGCM" + unico + "0000000000").substring(0, 17).toUpperCase());
		jdbc.update("""
				insert into direccion_cliente
				  (cliente_id, alias, calle, colonia, cp, ciudad, lat, lng, created_at, updated_at)
				values (?, 'Casa', 'Calle Secreta 123', 'Centro', '91000', 'Xalapa', 19.5, -96.9, now(), now())
				""", clienteId);
		jdbc.update("""
				insert into lectura_km (vehiculo_cliente_id, km, fuente, registrada_en)
				values (?, 12500, 'CLIENTE', now())
				""", vehiculo);
		jdbc.update("""
				insert into gasto_vehiculo
				  (vehiculo_cliente_id, tipo, monto, fecha, descripcion, created_at, updated_at)
				values (?, 'LAVADO', 150, current_date, 'Lavado con cera', now(), now())
				""", vehiculo);
		jdbc.update("""
				insert into consulta_asistente
				  (cliente_id, vehiculo_cliente_id, pregunta, respuesta, encontrado, proveedor)
				values (?, ?, 'Qué significa la luz del aceite', 'Presión baja', true, 'prueba')
				""", clienteId, vehiculo);
	}

	@Test
	void pedirAccesoResponde202YElArchivoSeAtiendeFueraDeLaPeticion() throws Exception {
		String cuerpo = pedirAcceso(token).andExpect(status().isAccepted())
				.andExpect(jsonPath("$.tipo").value("ACCESO")).andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.creadaEn").isNotEmpty()).andExpect(jsonPath("$.venceEn").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();

		esperarResuelta(id);

		mockMvc.perform(get("/api/v1/app/privacidad/arco").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id))
				.andExpect(jsonPath("$[0].estado").value("RESUELTA"))
				.andExpect(jsonPath("$[0].resueltaEn").isNotEmpty()).andExpect(jsonPath("$[0].venceEn").isNotEmpty());
	}

	@Test
	void elArchivoTraeLasValuacionesSinLasClavesDeSusFotosNiQuienOferto() throws Exception {
		long vehiculo = jdbc.queryForObject("select id from vehiculo_cliente where cliente_id = ?", Long.class,
				clienteId);
		long parametro = jdbc.queryForObject("""
				insert into parametro_toma_cuenta
				  (marca, modelo, anio, valor_base, depreciacion_anual, factor_km, factor_estado, activo,
				   created_at, updated_at)
				values (?, 'Kicks', 2023, 200000, 0.1, 0.05, 0.1, true, now(), now()) returning id
				""", Long.class, "Arco" + unico);
		long estimacion = jdbc.queryForObject("""
				insert into estimacion_toma_cuenta
				  (cliente_id, vehiculo_cliente_id, marca, modelo, anio, km, estado, minimo, maximo,
				   parametro_toma_cuenta_id, supuestos, created_at, updated_at)
				values (?, ?, 'Nissan', 'Kicks', 2023, 12000, 'BUENO', 100000, 120000, ?, '{}', now(), now())
				returning id
				""", Long.class, clienteId, vehiculo, parametro);
		jdbc.update("""
				insert into valuacion (estimacion_toma_cuenta_id, cliente_id, vehiculo_cliente_id, fotos, estado, oferta,
				  vigencia_hasta, oferta_nota, ofertada_por, ofertada_en, created_at, updated_at)
				values (?, ?, ?, ?, 'OFERTADA', 111000, current_date + 5, 'Con papeles', 987654, now(), now(), now())
				""", estimacion, clienteId, vehiculo,
				"{\"FRENTE\":{\"clave\":\"valuacion-fotos/clave-secreta\",\"subidaEn\":\"2026-11-25T10:00:00Z\"}}");

		long id = pedirYEsperar(token);
		String texto = mockMvc
				.perform(get("/api/v1/app/privacidad/arco/" + id + "/archivo").header(HttpHeaders.AUTHORIZATION,
						"Bearer " + token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		JsonNode archivo = json.readTree(texto);

		assertThat(archivo.at("/valuaciones")).hasSize(1);
		assertThat(archivo.at("/valuaciones/0/estado").asText()).isEqualTo("OFERTADA");
		assertThat(archivo.at("/valuaciones/0/oferta").asInt()).isEqualTo(111_000);
		assertThat(archivo.at("/valuaciones/0/oferta_nota").asText()).isEqualTo("Con papeles");
		assertThat(archivo.at("/alcance/incluye").toString()).contains("valuaciones");
		assertThat(texto).doesNotContain("clave-secreta").doesNotContain("ofertada_por").doesNotContain("987654");
	}

	@Test
	void elArchivoTrae_LosDatosDeLaCuentaYNoLaContrasena() throws Exception {
		long id = pedirYEsperar(token);

		String texto = mockMvc
				.perform(get("/api/v1/app/privacidad/arco/" + id + "/archivo").header(HttpHeaders.AUTHORIZATION,
						"Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						org.hamcrest.Matchers.startsWith("attachment; filename=\"mis-datos-carecar-")))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
				.andReturn().getResponse().getContentAsString();
		JsonNode archivo = json.readTree(texto);

		assertThat(archivo.at("/cliente/id").asLong()).isEqualTo(clienteId);
		assertThat(archivo.at("/cliente/nombre").asText()).isEqualTo("Ana Arco");
		assertThat(archivo.at("/cliente/correo").asText()).isEqualTo("arco-" + unico + "@correo.com");
		assertThat(archivo.at("/cliente/telefono").asText()).isEqualTo("5512345678");
		assertThat(archivo.at("/cuentaApp/usuario").asText()).isEqualTo("arco-" + unico + "@correo.com");
		assertThat(archivo.at("/cuentaApp/activa").asBoolean()).isTrue();
		assertThat(archivo.at("/vehiculos")).hasSize(1);
		assertThat(archivo.at("/vehiculos/0/placas").asText()).isEqualTo("ABC123");
		assertThat(archivo.at("/direcciones/0/calle").asText()).isEqualTo("Calle Secreta 123");
		assertThat(archivo.at("/lecturasKm/0/km").asInt()).isEqualTo(12500);
		assertThat(archivo.at("/gastos/0/descripcion").asText()).isEqualTo("Lavado con cera");
		assertThat(archivo.at("/consultasAsistente/0/pregunta").asText()).contains("luz del aceite");
		assertThat(archivo.at("/solicitudesArco/0/tipo").asText()).isEqualTo("ACCESO");
		assertThat(archivo.at("/generadoEn").asText()).isNotEmpty();
		assertThat(archivo.at("/alcance/incluye")).isNotEmpty();
		assertThat(archivo.at("/alcance/noIncluye").toString()).contains("fiscal");

		// Las secciones sin datos existen y van vacías: el archivo dice qué no hay.
		for (String vacia : List.of("citas", "traslados", "checklistsDeTraslado", "servicios", "estimacionesTomaCuenta",
				"valuaciones", "solicitudesEstreno", "solicitudesAsistencia", "avisosAceptados")) {
			assertThat(archivo.has(vacia)).as(vacia).isTrue();
			assertThat(archivo.get(vacia).isArray()).as(vacia).isTrue();
		}
		// Nada de lo que abre la cuenta ni de lo interno de la sesión.
		assertThat(texto).doesNotContain("contrasena_hash").doesNotContain("contrasenaHash")
				.doesNotContain("token_hash").doesNotContain("$2a$");
	}

	@Test
	void soloLaCuentaDuenaDescargaElArchivo() throws Exception {
		long id = pedirYEsperar(token);
		long otroCliente = registroClientesService
				.registrar(new NuevoCliente("Otra Persona", "otra-" + unico + "@correo.com", null, null)).id();
		cuentasAppService.abrir(otroCliente, "Temporal123");
		String tokenAjeno = jwtService.generateToken(otroCliente, "otra-" + unico + "@correo.com",
				List.of(Role.CLIENTE_CARECAR));

		mockMvc.perform(get("/api/v1/app/privacidad/arco/" + id + "/archivo").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenAjeno)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/privacidad/arco/" + id + "/archivo")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/app/privacidad/arco/999999999/archivo").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token)).andExpect(status().isNotFound());
		// Ni siquiera aparece en la lista de la otra cuenta.
		mockMvc.perform(get("/api/v1/app/privacidad/arco").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAjeno))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void pedirDeNuevoBorraElArchivoAnteriorYDejaUnoSolo() throws Exception {
		long primera = pedirYEsperar(token);
		assertThat(archivosGuardados(primera)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select archivo_url from solicitud_arco where id = ?", String.class, primera))
				.isEqualTo("/api/v1/app/privacidad/arco/" + primera + "/archivo");

		long segunda = pedirYEsperar(token);

		assertThat(segunda).isNotEqualTo(primera);
		assertThat(archivosGuardados(primera)).isZero();
		assertThat(archivosGuardados(segunda)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select archivo_url from solicitud_arco where id = ?", String.class, primera))
				.isNull();
		// La lista trae primero la más reciente.
		mockMvc.perform(get("/api/v1/app/privacidad/arco").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$[0].id").value(segunda)).andExpect(jsonPath("$[1].id").value(primera));
		mockMvc.perform(get("/api/v1/app/privacidad/arco/" + primera + "/archivo").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/privacidad/arco/" + segunda + "/archivo").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token)).andExpect(status().isOk());
	}

	@Test
	void unaSolicitudResueltaHaceMasDeSieteDiasYaNoSeDescarga() throws Exception {
		long id = pedirYEsperar(token);
		jdbc.update("update solicitud_arco set resuelta_en = now() - interval '8 days' where id = ?", id);

		mockMvc.perform(get("/api/v1/app/privacidad/arco").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$[0].estado").value("VENCIDA"));
		mockMvc.perform(get("/api/v1/app/privacidad/arco/" + id + "/archivo").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token)).andExpect(status().isNotFound());
	}

	@Test
	void laBaseNoDejaDosSolicitudesPendientesDelMismoTipoPeroSiDeTiposDistintos() {
		String abrirAcceso = "insert into solicitud_arco (cliente_id, tipo, estado, creada_en) "
				+ "values (?, 'ACCESO', 'PENDIENTE', now())";
		jdbc.update(abrirAcceso, clienteId);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(abrirAcceso, clienteId))
				.isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
		// Otro tipo, o una ya resuelta, sí caben.
		jdbc.update("insert into solicitud_arco (cliente_id, tipo, estado, creada_en) "
				+ "values (?, 'CANCELACION', 'PENDIENTE', now())", clienteId);
		jdbc.update("insert into solicitud_arco (cliente_id, tipo, estado, creada_en, resuelta_en) "
				+ "values (?, 'ACCESO', 'RESUELTA', now(), now())", clienteId);
	}

	@Test
	void sinSesionDa401() throws Exception {
		mockMvc.perform(post("/api/v1/app/privacidad/arco/acceso")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/app/privacidad/arco")).andExpect(status().isUnauthorized());
	}

	@Test
	void soloLosClientesDeLaAppPidenYDescargan() throws Exception {
		String personal = jwtService.generateToken(1L, "gerente", List.of(Role.GERENTE_MARKETING));
		pedirAcceso(personal).andExpect(status().isForbidden());
	}

	private ResultActions pedirAcceso(String bearer) throws Exception {
		return mockMvc.perform(
				post("/api/v1/app/privacidad/arco/acceso").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer));
	}

	private long pedirYEsperar(String bearer) throws Exception {
		String cuerpo = pedirAcceso(bearer).andExpect(status().isAccepted()).andReturn().getResponse()
				.getContentAsString();
		long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
		esperarResuelta(id);
		return id;
	}

	/** El archivo se prepara después de confirmar la petición, en otro hilo. */
	private void esperarResuelta(long id) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			String estado = jdbc.queryForObject("select estado from solicitud_arco where id = ?", String.class, id);
			if ("RESUELTA".equals(estado)) {
				return;
			}
			assertThat(estado).as("la solicitud no debe fallar").isNotEqualTo("FALLIDA");
			Thread.sleep(100);
		}
		throw new AssertionError("La solicitud " + id + " no se resolvió en 10 s");
	}

	private int archivosGuardados(long solicitudId) {
		return jdbc.queryForObject("select count(*) from archivo_arco where solicitud_arco_id = ?", Integer.class,
				solicitudId);
	}
}
