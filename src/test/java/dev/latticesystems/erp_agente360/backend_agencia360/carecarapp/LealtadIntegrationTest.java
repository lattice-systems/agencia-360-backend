package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.LealtadAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoFinalizado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lealtad de punta a punta (V2-M12.01, V2-M12.03, V2-M12.04 y V2-M12.05). No es
 * transaccional porque los referidos pasan por eventos asíncronos que necesitan
 * su commit; cada prueba usa un cliente con correo propio y no asume totales.
 */
@AutoConfigureMockMvc
class LealtadIntegrationTest extends AbstractIntegrationTest {

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
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private LealtadAppService lealtadAppService;

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String unico;
	private Long cliente;
	private String token;
	private String tokenDeCaja;

	@BeforeEach
	void clienteYPersonal() {
		unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String correo = "lealtad-" + unico + "@correo.com";
		cliente = registroClientesService.registrar(new NuevoCliente("Lalo Leal", correo, null, null)).id();
		cuentasAppService.abrir(cliente, "Temporal123");
		token = jwtService.generateToken(cliente, correo, List.of(Role.CLIENTE_CARECAR));
		Rol caja = rolRepository.findByNombre("CAJA").orElseThrow();
		Usuario cajero = usuarioRepository.save(new Usuario("caja-" + unico, "hash", "Carla Caja", caja));
		tokenDeCaja = jwtService.generateToken(cajero.getId(), cajero.getUsuario(), List.of(Role.CAJA));
	}

	@Test
	void unClienteNuevoEmpiezaEnBronceConSuCodigo() throws Exception {
		comoCliente(get("/api/v1/app/lealtad")).andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(0))
				.andExpect(jsonPath("$.nivel").value("BRONCE")).andExpect(jsonPath("$.siguienteNivel").value("PLATA"))
				.andExpect(jsonPath("$.puntosParaSiguiente").value(1500))
				.andExpect(jsonPath("$.codigoReferido").value(org.hamcrest.Matchers.startsWith("CC")));
		comoCliente(get("/api/v1/app/lealtad/movimientos")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	void canjearDescuentaLosPuntosYElPersonalUsaElCodigoUnaVez() throws Exception {
		ajuste(2000);
		comoCliente(get("/api/v1/app/lealtad")).andExpect(jsonPath("$.saldo").value(2000))
				.andExpect(jsonPath("$.nivel").value("PLATA"));
		Long lavado = recompensa("Lavado " + unico, 300, null);
		comoCliente(get("/api/v1/app/lealtad/recompensas")).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + lavado + ")].puntos").value(300));

		String cuerpo = canjear(lavado).andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("VIGENTE"))
				.andExpect(jsonPath("$.puntos").value(300)).andExpect(jsonPath("$.recompensa").value("Lavado " + unico))
				.andReturn().getResponse().getContentAsString();
		String codigo = JsonPath.read(cuerpo, "$.codigo");
		assertThat(codigo).hasSize(8);

		comoCliente(get("/api/v1/app/lealtad")).andExpect(jsonPath("$.saldo").value(1700))
				.andExpect(jsonPath("$.nivel").value("PLATA"));
		comoCliente(get("/api/v1/app/lealtad/movimientos")).andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.contenido[0].motivo").value("CANJE"))
				.andExpect(jsonPath("$.contenido[0].puntos").value(-300))
				.andExpect(jsonPath("$.contenido[0].referenciaTipo").value("CANJE"));
		comoCliente(get("/api/v1/app/lealtad/canjes")).andExpect(jsonPath("$[0].codigo").value(codigo));

		comoCliente(post("/api/v1/lealtad/canjes/{codigo}/usar", codigo)).andExpect(status().isForbidden());
		String minusculas = codigo.toLowerCase(Locale.ROOT);
		comoCaja(post("/api/v1/lealtad/canjes/{codigo}/usar", minusculas)).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("USADO"));
		comoCaja(post("/api/v1/lealtad/canjes/{codigo}/usar", codigo)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CANJE_NO_VIGENTE"));
	}

	@Test
	void sinPuntosOSinExistenciaNoSeCanjea() throws Exception {
		Long lavado = recompensa("Lavado " + unico, 300, null);
		Long agotada = recompensa("Agotada " + unico, 10, 0);
		ajuste(100);

		canjear(lavado).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
		canjear(agotada).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RECOMPENSA_AGOTADA"));
		canjear(Long.MAX_VALUE).andExpect(status().isNotFound());
		comoCliente(get("/api/v1/app/lealtad")).andExpect(jsonPath("$.saldo").value(100));
	}

	@Test
	void unCanjeVencidoRegresaSusPuntos() throws Exception {
		ajuste(500);
		Long lavado = recompensa("Lavado " + unico, 300, 1);
		String cuerpo = canjear(lavado).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		Integer canje = JsonPath.read(cuerpo, "$.id");
		jdbc.update("update canje set vence_en = now() - interval '1 minute' where id = ?", canje.longValue());

		lealtadAppService.vencerTodos();

		comoCliente(get("/api/v1/app/lealtad")).andExpect(jsonPath("$.saldo").value(500));
		comoCliente(get("/api/v1/app/lealtad/canjes")).andExpect(jsonPath("$[0].estado").value("VENCIDO"));
		Integer existencia = jdbc.queryForObject("select existencia from recompensa where id = ?", Integer.class,
				lavado);
		assertThat(existencia).isEqualTo(1);
	}

	@Test
	void unReferidoEntraComoProspectoYSuVentaDaPuntosUnaVez() throws Exception {
		Rol ventas = rolRepository.findByNombre("VENTAS").orElseThrow();
		usuarioRepository.save(new Usuario("vendedor-" + unico, "hash", "Vera Vendedora", ventas));
		String telefono = String.format("44%08d", ThreadLocalRandom.current().nextInt(100_000_000));

		referir("{\"nombre\":\"Luis Pérez\",\"telefono\":\"" + telefono + "\",\"consentimiento\":true}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("REGISTRADO"));
		Long prospecto = esperar("select prospecto_id from referido where cliente_id = ? and telefono = ?", cliente,
				telefono);
		assertThat(prospecto).isNotNull();
		String origen = jdbc.queryForObject(
				"select origen from historial_etapa where prospecto_id = ? order by id limit 1", String.class,
				prospecto);
		assertThat(origen).isEqualTo("APP");
		comoCliente(get("/api/v1/app/lealtad/referidos")).andExpect(jsonPath("$[0].estado").value("EN_SEGUIMIENTO"))
				.andExpect(jsonPath("$[0].nombre").value("Luis Pérez"));

		referir("{\"nombre\":\"Luis otra vez\",\"telefono\":\"" + telefono + "\",\"consentimiento\":true}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("REFERIDO_DUPLICADO"));

		finalizar(prospecto);
		finalizar(prospecto);
		Long movimiento = esperar(
				"select id from movimiento_puntos where cliente_id = ? and motivo = 'REFERIDO' and puntos = ?", cliente,
				500);
		assertThat(movimiento).isNotNull();
		Thread.sleep(300);
		Long referidos = jdbc.queryForObject(
				"select count(*) from movimiento_puntos where cliente_id = ? and motivo = 'REFERIDO'", Long.class,
				cliente);
		assertThat(referidos).isEqualTo(1);
		comoCliente(get("/api/v1/app/lealtad/referidos")).andExpect(jsonPath("$[0].estado").value("COMPRO"))
				.andExpect(jsonPath("$[0].puntos").value(500));
	}

	@Test
	void referirPideContactoYConsentimiento() throws Exception {
		referir("{\"nombre\":\"Luis\",\"consentimiento\":true}").andExpect(status().isBadRequest());
		referir("{\"nombre\":\"Luis\",\"correo\":\"luis-" + unico + "@correo.com\",\"consentimiento\":false}")
				.andExpect(status().isBadRequest());
		referir("{\"nombre\":\"Luis\",\"telefono\":\"123\",\"consentimiento\":true}")
				.andExpect(status().isBadRequest());
		comoCliente(get("/api/v1/app/lealtad/referidos")).andExpect(jsonPath("$.length()").value(0));
	}

	/**
	 * Con la hora de la JVM y un minuto antes, no con {@code now()} de la base: el
	 * reloj del contenedor puede ir adelantado al de la JVM y el ajuste saldría
	 * después del canje que se hace enseguida.
	 */
	private void ajuste(int puntos) {
		jdbc.update("insert into movimiento_puntos (cliente_id, puntos, motivo, creado_en) values (?, ?, 'AJUSTE', ?)",
				cliente, puntos, Timestamp.from(Instant.now().minusSeconds(60)));
	}

	private Long recompensa(String nombre, int puntos, Integer existencia) {
		return jdbc.queryForObject("insert into recompensa (nombre, puntos, existencia) values (?, ?, ?) returning id",
				Long.class, nombre, puntos, existencia);
	}

	private void finalizar(Long prospecto) {
		ProspectoFinalizado finalizado = new ProspectoFinalizado(prospecto, null, "Luis Pérez", null, Instant.now());
		new TransactionTemplate(transacciones).executeWithoutResult(estado -> eventos.publishEvent(finalizado));
	}

	private Long esperar(String consulta, Object... argumentos) throws InterruptedException {
		for (int intento = 0; intento < 50; intento++) {
			List<Long> encontrados = jdbc.queryForList(consulta, Long.class, argumentos);
			if (!encontrados.isEmpty() && encontrados.getFirst() != null) {
				return encontrados.getFirst();
			}
			Thread.sleep(100);
		}
		return null;
	}

	private ResultActions canjear(Long recompensa) throws Exception {
		return comoCliente(post("/api/v1/app/lealtad/canjes").contentType(MediaType.APPLICATION_JSON)
				.content("{\"recompensaId\":" + recompensa + "}"));
	}

	private ResultActions referir(String cuerpo) throws Exception {
		return comoCliente(
				post("/api/v1/app/lealtad/referidos").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions comoCliente(MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions comoCaja(MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCaja));
	}
}
