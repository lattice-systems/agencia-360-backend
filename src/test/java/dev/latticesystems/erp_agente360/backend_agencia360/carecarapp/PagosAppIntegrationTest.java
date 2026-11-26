package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AnonimizadorDeCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ExportadorDeDatos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Pagos simulados de punta a punta (V2-M08.02, V2-M08.04 y V2-M08.07): cobrar
 * con las tarjetas de prueba, idempotencia, 3-D Secure, tarjetas guardadas, mis
 * pagos, el ticket, la caja, los puntos y los derechos ARCO. No es
 * transaccional porque los puntos llegan por un evento asíncrono que necesita su
 * commit; cada prueba usa clientes y vehículos propios y no asume totales.
 */
@AutoConfigureMockMvc
class PagosAppIntegrationTest extends AbstractIntegrationTest {

	private static final String APP = "/api/v1/app";
	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

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
	private ExportadorDeDatos exportador;

	@Autowired
	private AnonimizadorDeCuenta anonimizador;

	@Autowired
	private ObjectMapper json;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String unico;
	private Long cliente;
	private String token;
	private String tokenDeOtro;
	private String tokenDeServicio;
	private String tokenDeCaja;

	@BeforeEach
	void clientesYPersonal() {
		unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		cliente = registrar("Paula Pagos");
		token = tokenDe(cliente);
		tokenDeOtro = tokenDe(registrar("Otro Cliente"));
		tokenDeServicio = personal("servicio-pagos-" + unico, "SERVICIO", Role.SERVICIO);
		tokenDeCaja = personal("caja-pagos-" + unico, "CAJA", Role.CAJA);
	}

	@Test
	void conTarjetaAprobadaQuedaPagadaYElTicketLoDice() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "1650.00");
		con(token, get(APP + "/servicios/{id}", orden)).andExpect(jsonPath("$.pagable").value(true))
				.andExpect(jsonPath("$.pago").isEmpty());

		pagar(orden, "llave-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PAGADO")).andExpect(jsonPath("$.monto").value(1650.0))
				.andExpect(jsonPath("$.moneda").value("MXN")).andExpect(jsonPath("$.medio").value("TARJETA"))
				.andExpect(jsonPath("$.marca").value("VISA")).andExpect(jsonPath("$.ultimos4").value("4242"))
				.andExpect(jsonPath("$.tipoTarjeta").value("CREDITO")).andExpect(jsonPath("$.simulado").value(true))
				.andExpect(jsonPath("$.referencia").value(startsWith("sim_pi_")))
				.andExpect(jsonPath("$.pagadoEn").isNotEmpty()).andExpect(jsonPath("$.metodoPagoId").isEmpty());

		con(token, get(APP + "/servicios/{id}", orden)).andExpect(jsonPath("$.pagable").value(false))
				.andExpect(jsonPath("$.pago.estado").value("PAGADO"))
				.andExpect(jsonPath("$.pago.ultimos4").value("4242"));
		pagar(orden, "otra-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_NO_PAGABLE"));
		Long puntos = esperar(
				"select puntos from movimiento_puntos where motivo = 'SERVICIO' and referencia_tipo = 'ORDEN' "
						+ "and referencia_id = ?",
				orden);
		assertThat(puntos).isEqualTo(165L);
	}

	@Test
	void laMismaLlaveNoCobraDosVecesYUnRechazoSeReintentaConOtra() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "500.00");
		String rechazo = pagar(orden, "fondos-" + unico, tarjeta("tok_sim_fondos_insuficientes"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("FALLIDO"))
				.andExpect(jsonPath("$.motivoFallo").value("FONDOS_INSUFICIENTES")).andReturn().getResponse()
				.getContentAsString();
		Number fallido = JsonPath.read(rechazo, "$.id");
		pagar(orden, "fondos-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(fallido.longValue()))
				.andExpect(jsonPath("$.estado").value("FALLIDO"));

		pagar(orden, "bien-" + unico, tarjeta("tok_sim_mastercard")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PAGADO")).andExpect(jsonPath("$.marca").value("MASTERCARD"));
		pagar(orden, "bien-" + unico, tarjeta("tok_sim_mastercard")).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADO"));
		Integer pagados = jdbc.queryForObject(
				"select count(*) from pago_app where orden_servicio_id = ? and estado = 'PAGADO'", Integer.class,
				orden);
		assertThat(pagados).isEqualTo(1);

		Long otraOrden = ordenLista(vehiculo(cliente), "100.00");
		pagar(otraOrden, "bien-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isBadRequest());
	}

	@Test
	void elTresDeSecureConfirmadoCobraYGuardaLaTarjeta() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "800.00");
		String pendiente = pagar(orden, "tds-" + unico, tarjetaGuardada("tok_sim_3ds"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.requiereAutenticacion").value(true)).andReturn().getResponse()
				.getContentAsString();
		Number pago = JsonPath.read(pendiente, "$.id");

		pagar(orden, "otro-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PAGO_EN_CURSO"));
		autenticar(tokenDeOtro, pago.longValue(), true).andExpect(status().isNotFound());

		autenticar(token, pago.longValue(), true).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADO"))
				.andExpect(jsonPath("$.requiereAutenticacion").value(false))
				.andExpect(jsonPath("$.metodoPagoId").isNotEmpty());
		autenticar(token, pago.longValue(), true).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PAGO_NO_PENDIENTE"));

		con(token, get(APP + "/metodos-pago")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].ultimos4").value("3220")).andExpect(jsonPath("$[0].predeterminado").value(true))
				.andExpect(jsonPath("$[0].vencida").value(false)).andExpect(jsonPath("$[0].token").doesNotExist());
	}

	@Test
	void elTresDeSecureRechazadoFallaYUnoVencidoYaNoBloquea() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "300.00");
		Long cancelado = id(pagar(orden, "cancela-" + unico, tarjeta("tok_sim_3ds")));
		autenticar(token, cancelado, false).andExpect(jsonPath("$.estado").value("FALLIDO"))
				.andExpect(jsonPath("$.motivoFallo").value("AUTENTICACION_FALLIDA"));

		Long falla = id(pagar(orden, "falla-" + unico, tarjeta("tok_sim_3ds_falla")));
		autenticar(token, falla, true).andExpect(jsonPath("$.estado").value("FALLIDO"))
				.andExpect(jsonPath("$.motivoFallo").value("AUTENTICACION_FALLIDA"));

		Long olvidado = id(pagar(orden, "olvido-" + unico, tarjeta("tok_sim_3ds")));
		jdbc.update("update pago_app set creado_en = now() - interval '20 minutes' where id = ?", olvidado);
		pagar(orden, "nuevo-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PAGADO"));
		con(token, get(APP + "/pagos/{id}", olvidado)).andExpect(jsonPath("$.estado").value("FALLIDO"))
				.andExpect(jsonPath("$.motivoFallo").value("AUTENTICACION_VENCIDA"));
	}

	@Test
	void conUnaTarjetaGuardadaSePagaSinCapturarlaYSeBorra() throws Exception {
		Long primera = ordenLista(vehiculo(cliente), "200.00");
		pagar(primera, "guarda-" + unico, tarjetaGuardada("tok_sim_visa_debito")).andExpect(status().isCreated());
		pagar(ordenLista(vehiculo(cliente), "250.00"), "guarda2-" + unico, tarjetaGuardada("tok_sim_mastercard"))
				.andExpect(status().isCreated());
		String lista = con(token, get(APP + "/metodos-pago")).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].ultimos4").value("5556")).andExpect(jsonPath("$[0].tipo").value("DEBITO"))
				.andExpect(jsonPath("$[1].predeterminado").value(false)).andReturn().getResponse()
				.getContentAsString();
		Number debito = JsonPath.read(lista, "$[0].id");
		Number master = JsonPath.read(lista, "$[1].id");

		Long tercera = ordenLista(vehiculo(cliente), "320.00");
		pagar(tercera, "conguardada-" + unico, "{\"metodoPagoId\":" + master + "}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PAGADO")).andExpect(jsonPath("$.ultimos4").value("4444"))
				.andExpect(jsonPath("$.metodoPagoId").value(master.longValue()));
		con(tokenDeOtro, put(APP + "/metodos-pago/{id}/predeterminado", master)).andExpect(status().isNotFound());
		con(token, put(APP + "/metodos-pago/{id}/predeterminado", master)).andExpect(status().isOk())
				.andExpect(jsonPath("$.predeterminado").value(true));
		con(token, get(APP + "/metodos-pago")).andExpect(jsonPath("$[0].id").value(master.longValue()));

		con(tokenDeOtro, delete(APP + "/metodos-pago/{id}", master)).andExpect(status().isNotFound());
		con(token, delete(APP + "/metodos-pago/{id}", master)).andExpect(status().isNoContent());
		con(token, get(APP + "/metodos-pago")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(debito.longValue()))
				.andExpect(jsonPath("$[0].predeterminado").value(true));
		Integer conservados = jdbc.queryForObject(
				"select count(*) from pago_app where orden_servicio_id = ? and estado = 'PAGADO'", Integer.class,
				tercera);
		assertThat(conservados).isEqualTo(1);
	}

	@Test
	void misPagosVanDelMasNuevoYSoloLosMios() throws Exception {
		Long una = ordenLista(vehiculo(cliente), "100.00");
		Long otra = ordenLista(vehiculo(cliente), "200.00");
		pagar(una, "uno-" + unico, tarjeta("tok_sim_fondos_insuficientes")).andExpect(status().isCreated());
		Long ultimo = id(pagar(otra, "dos-" + unico, tarjeta("tok_sim_visa")));

		con(token, get(APP + "/pagos").param("tamano", "1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2)).andExpect(jsonPath("$.totalPaginas").value(2))
				.andExpect(jsonPath("$.contenido[0].id").value(ultimo))
				.andExpect(jsonPath("$.contenido[0].ordenId").value(otra));
		con(tokenDeOtro, get(APP + "/pagos")).andExpect(jsonPath("$.totalElementos").value(0));
		con(tokenDeOtro, get(APP + "/pagos/{id}", ultimo)).andExpect(status().isNotFound());
		con(token, get(APP + "/pagos").param("tamano", "51")).andExpect(status().isBadRequest());
	}

	@Test
	void noSePagaLoAjenoNiLoQueAunNoEstaListoNiLoRegistradoYaHecho() throws Exception {
		Long vehiculo = vehiculo(cliente);
		Long abierta = abrir(vehiculo);
		pagar(abierta, "abierta-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_NO_PAGABLE"));
		con(tokenDeOtro,
				post(APP + "/ordenes/{id}/pagos", abierta).header("Idempotency-Key", "ajena-" + unico)
						.contentType(MediaType.APPLICATION_JSON).content(tarjeta("tok_sim_visa")))
				.andExpect(status().isNotFound());

		Long registrada = registrada(vehiculo(cliente));
		con(token, get(APP + "/servicios/{id}", registrada)).andExpect(jsonPath("$.pagable").value(false));
		pagar(registrada, "registrada-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_NO_PAGABLE"));
	}

	@Test
	void unaPeticionMalArmadaSeRechaza() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "100.00");
		pagar(orden, "corta", tarjeta("tok_sim_visa")).andExpect(status().isBadRequest());
		pagar(orden, "desconocida-" + unico, tarjeta("tok_otro_procesador")).andExpect(status().isBadRequest());
		pagar(orden, "vencida-" + unico,
				"{\"tarjeta\":{\"token\":\"tok_sim_visa\",\"mesVence\":1,\"anioVence\":2020}}")
				.andExpect(status().isBadRequest());
		pagar(orden, "doble-" + unico,
				"{\"tarjeta\":{\"token\":\"tok_sim_visa\",\"mesVence\":12,\"anioVence\":" + anioQueViene()
						+ "},\"metodoPagoId\":1}")
				.andExpect(status().isBadRequest());
		pagar(orden, "ninguna-" + unico, "{}").andExpect(status().isBadRequest());
		pagar(orden, "perfil-" + unico, "{\"tarjeta\":{\"token\":\"tok_sim_visa\",\"mesVence\":12,\"anioVence\":"
				+ anioQueViene() + "},\"perfilFiscalId\":" + Long.MAX_VALUE + "}").andExpect(status().isNotFound());
		con(token, post(APP + "/ordenes/{id}/pagos", orden).contentType(MediaType.APPLICATION_JSON)
				.content(tarjeta("tok_sim_visa"))).andExpect(status().isBadRequest());
		Integer intentos = jdbc.queryForObject("select count(*) from pago_app where orden_servicio_id = ?",
				Integer.class, orden);
		assertThat(intentos).isZero();
	}

	@Test
	void laCajaMarcaPagadaYLaAppYaNoCobraNiDejaElTresDeSecureAbierto() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "450.00");
		Long abierto = id(pagar(orden, "tds-caja-" + unico, tarjeta("tok_sim_3ds")));

		con(token, post("/api/v1/caja/ordenes/{id}/pagada-en-caja", orden)).andExpect(status().isForbidden());
		con(tokenDeCaja,
				post("/api/v1/caja/ordenes/{id}/pagada-en-caja", orden).contentType(MediaType.APPLICATION_JSON)
						.content("{\"referencia\":\"F-" + unico + "\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PAGADO"))
				.andExpect(jsonPath("$.medio").value("CAJA")).andExpect(jsonPath("$.simulado").value(false))
				.andExpect(jsonPath("$.referencia").value("F-" + unico));
		con(tokenDeCaja, post("/api/v1/caja/ordenes/{id}/pagada-en-caja", orden)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_NO_PAGABLE"));

		con(token, get(APP + "/pagos/{id}", abierto)).andExpect(jsonPath("$.estado").value("FALLIDO"))
				.andExpect(jsonPath("$.motivoFallo").value("PAGADA_EN_CAJA"));
		pagar(orden, "despues-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ORDEN_NO_PAGABLE"));
		con(token, get(APP + "/servicios/{id}", orden)).andExpect(jsonPath("$.pago.medio").value("CAJA"));
		con(tokenDeCaja, post("/api/v1/caja/ordenes/{id}/pagada-en-caja", Long.MAX_VALUE))
				.andExpect(status().isNotFound());
	}

	@Test
	void soloElClienteDeLaApp() throws Exception {
		mockMvc.perform(get(APP + "/pagos")).andExpect(status().isUnauthorized());
		con(tokenDeCaja, get(APP + "/pagos")).andExpect(status().isForbidden());
		con(tokenDeCaja, get(APP + "/metodos-pago")).andExpect(status().isForbidden());
	}

	@Test
	void laCopiaDeMisDatosTraeMisPagosYCancelarLaCuentaBorraLasTarjetas() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "900.00");
		pagar(orden, "arco-" + unico, tarjetaGuardada("tok_sim_visa")).andExpect(status().isCreated());

		JsonNode copia = json.readTree(exportador.exportar(cliente, Instant.now()));
		assertThat(copia.at("/pagos/0/ultimos4").asText()).isEqualTo("4242");
		assertThat(copia.at("/pagos/0/token_procesador").isMissingNode()).isTrue();
		assertThat(copia.at("/tarjetasGuardadas/0/marca").asText()).isEqualTo("VISA");
		assertThat(copia.at("/alcance/incluye").toString()).contains("tarjetas guardadas");

		anonimizador.anonimizar(cliente);
		assertThat(jdbc.queryForObject("select count(*) from metodo_pago where cliente_id = ?", Integer.class, cliente))
				.isZero();
		assertThat(jdbc.queryForObject(
				"select count(*) from pago_app where cliente_id = ? and token_procesador is null", Integer.class,
				cliente)).isEqualTo(1);
	}

	private Long registrar(String nombre) {
		String correo = "pagos-" + UUID.randomUUID().toString().substring(0, 8) + "@correo.com";
		Long id = registroClientesService.registrar(new NuevoCliente(nombre, correo, null, null)).id();
		cuentasAppService.abrir(id, "Temporal123");
		return id;
	}

	private String tokenDe(Long clienteId) {
		String correo = jdbc.queryForObject("select correo from cliente where id = ?", String.class, clienteId);
		return jwtService.generateToken(clienteId, correo, List.of(Role.CLIENTE_CARECAR));
	}

	private String personal(String usuario, String rol, Role permiso) {
		Usuario guardado = usuarioRepository.save(
				new Usuario(usuario, "hash-de-prueba", "Persona " + rol, rolRepository.findByNombre(rol).orElseThrow()));
		return jwtService.generateToken(guardado.getId(), usuario, List.of(permiso));
	}

	private Long vehiculo(Long clienteId) {
		String vin = String.format("3N1PAG%08d%03d", ThreadLocalRandom.current().nextInt(100_000_000),
				ThreadLocalRandom.current().nextInt(1000));
		return registroClientesService
				.asignarVehiculo(clienteId, new VehiculoNuevo(null, vin, "Nissan", "March", 2026, null)).vehiculos()
				.stream().filter(v -> v.vin().equals(vin)).findFirst().orElseThrow().id();
	}

	private Long abrir(Long vehiculoId) throws Exception {
		return id(con(tokenDeServicio, post("/api/v1/taller/ordenes").contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehiculoId\":" + vehiculoId + ",\"kilometraje\":15200}")).andExpect(status().isCreated()));
	}

	/** Una orden del taller con un renglón, lista para entrega. */
	private Long ordenLista(Long vehiculoId, String precio) throws Exception {
		Long orden = abrir(vehiculoId);
		con(tokenDeServicio,
				post("/api/v1/taller/ordenes/{id}/renglones", orden).contentType(MediaType.APPLICATION_JSON)
						.content("{\"tipo\":\"MANO_DE_OBRA\",\"concepto\":\"Afinación\",\"cantidad\":1,"
								+ "\"precioUnitario\":" + precio + "}"))
				.andExpect(status().isCreated());
		con(tokenDeServicio, post("/api/v1/taller/ordenes/{id}/etapas", orden).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"LISTO_PARA_ENTREGA\"}")).andExpect(status().isOk());
		return orden;
	}

	/** Un servicio registrado en el expediente ya hecho: no tiene línea de tiempo. */
	private Long registrada(Long vehiculoId) throws Exception {
		return id(con(tokenDeServicio,
				post("/api/v1/expedientes/{id}/servicios", vehiculoId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"fecha\":\"" + LocalDate.now(ZONA) + "\",\"kilometraje\":1000,\"renglones\":["
								+ "{\"tipo\":\"MANO_DE_OBRA\",\"concepto\":\"Afinación\",\"cantidad\":1,"
								+ "\"precioUnitario\":900.00}]}"))
				.andExpect(status().isCreated()));
	}

	private static int anioQueViene() {
		return LocalDate.now(ZONA).getYear() + 1;
	}

	private static String tarjeta(String tokenDePrueba) {
		return "{\"tarjeta\":{\"token\":\"" + tokenDePrueba + "\",\"mesVence\":12,\"anioVence\":" + anioQueViene()
				+ "}}";
	}

	private static String tarjetaGuardada(String tokenDePrueba) {
		return "{\"tarjeta\":{\"token\":\"" + tokenDePrueba + "\",\"mesVence\":12,\"anioVence\":" + anioQueViene()
				+ "},\"guardarTarjeta\":true}";
	}

	private ResultActions pagar(Long ordenId, String llave, String cuerpo) throws Exception {
		return con(token, post(APP + "/ordenes/{id}/pagos", ordenId).header("Idempotency-Key", llave)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions autenticar(String jwt, Long pagoId, boolean confirmada) throws Exception {
		return con(jwt, post(APP + "/pagos/{id}/autenticacion", pagoId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"confirmada\":" + confirmada + "}"));
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

	private static Long id(ResultActions respuesta) throws Exception {
		Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}

	private ResultActions con(String jwt, MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt));
	}
}
