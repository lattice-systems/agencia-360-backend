package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ExportadorDeDatos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.VencimientoDeApartados;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Pagar en la app un pedido de la tienda (V2-M14.04) contra PostgreSQL real: el
 * cobro simulado de las órdenes, la idempotencia, el 3-D Secure, lo ajeno, lo
 * que no se puede pagar, el vencimiento y la cancelación de un pedido pagado,
 * mis pagos y el aviso. No es transaccional porque los puntos llegan por un
 * evento asíncrono que necesita su commit. Cada prueba siembra sus clientes y
 * su refacción con un sufijo propio y al terminar borra sus pedidos.
 */
@AutoConfigureMockMvc
@RecordApplicationEvents
class PagarPedidoAppIntegrationTest extends AbstractIntegrationTest {

	private static final String APP = "/api/v1/app";
	private static final String PERSONAL = "/api/v1/tienda";
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
	private VencimientoDeApartados vencimiento;

	@Autowired
	private ApplicationEvents eventos;

	@Autowired
	private ExportadorDeDatos exportador;

	@Autowired
	private ObjectMapper mapeador;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private record Cliente(long id, String token) {
	}

	private final List<Long> sembrados = new ArrayList<>();
	private String unico;
	private Cliente ana;
	private Cliente beto;
	private String almacen;
	private long tapetes;

	@BeforeEach
	void sembrar() throws Exception {
		unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		ana = cliente("ana");
		beto = cliente("beto");
		almacen = personal("almacen-pago-" + unico);
		tapetes = jdbc.queryForObject("""
				insert into refaccion (codigo, descripcion, costo, precio, existencia, minimo, created_at, updated_at)
				values (?, ?, 1, 850.00, 10, 0, now(), now()) returning id
				""", Long.class, ("PAG" + unico).toUpperCase(Locale.ROOT), "Tapetes " + unico);
		String publicada = "{\"publicada\":true,\"categoria\":\"ACCESORIO\",\"universal\":true,"
				+ "\"compatibilidad\":[]}";
		como(almacen, json(put(PERSONAL + "/productos/{id}", tapetes), publicada)).andExpect(status().isOk());
	}

	@AfterEach
	void borrarLosPedidos() {
		for (Long cliente : sembrados) {
			String suyos = "(select id from pedido_app where cliente_id = ?)";
			jdbc.update("delete from pago_app where pedido_app_id in " + suyos, cliente);
			jdbc.update("delete from metodo_pago where cliente_id = ?", cliente);
			jdbc.update("delete from movimiento_inventario where pedido_app_id in " + suyos, cliente);
			jdbc.update("delete from pedido_app_renglon where pedido_app_id in " + suyos, cliente);
			jdbc.update("delete from pedido_app where cliente_id = ?", cliente);
		}
	}

	@Test
	void conTarjetaAprobadaQuedaPagadoYElPedidoLoDice() throws Exception {
		long pedido = apartado(ana, 2);
		como(ana.token(), get(APP + "/tienda/pedidos/{id}", pedido)).andExpect(jsonPath("$.pagable").value(true))
				.andExpect(jsonPath("$.pago").doesNotExist()).andExpect(jsonPath("$.pagadoEn").doesNotExist());

		long pago = id(pagar(ana, pedido, "llave-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PAGADO")).andExpect(jsonPath("$.pedidoId").value(pedido))
				.andExpect(jsonPath("$.ordenId").isEmpty()).andExpect(jsonPath("$.vehiculoId").isEmpty())
				.andExpect(jsonPath("$.monto").value(1700.0)).andExpect(jsonPath("$.marca").value("VISA"))
				.andExpect(jsonPath("$.referencia").value(startsWith("sim_pi_"))));

		como(ana.token(), get(APP + "/tienda/pedidos/{id}", pedido)).andExpect(jsonPath("$.pagable").value(false))
				.andExpect(jsonPath("$.estado").value("apartado")).andExpect(jsonPath("$.pagadoEn").isString())
				.andExpect(jsonPath("$.pago.id").value(pago)).andExpect(jsonPath("$.pago.ultimos4").value("4242"));
		como(ana.token(), get(APP + "/tienda/pedidos")).andExpect(jsonPath("$.contenido[0].id").value(pedido))
				.andExpect(jsonPath("$.contenido[0].pagable").value(false))
				.andExpect(jsonPath("$.contenido[0].pago.estado").value("PAGADO"))
				.andExpect(jsonPath("$.contenido[0].pagadoEn").isString());
		como(ana.token(), get(APP + "/pagos")).andExpect(jsonPath("$.contenido[0].id").value(pago))
				.andExpect(jsonPath("$.contenido[0].pedidoId").value(pedido));
		como(ana.token(), get(APP + "/pagos/{id}", pago)).andExpect(jsonPath("$.pedidoId").value(pedido));
		assertThat(eventos.stream(AvisoAlCliente.class)
				.filter(aviso -> aviso.evento().equals("pago.confirmado") && aviso.clienteId().equals(ana.id())))
				.singleElement().satisfies(aviso -> {
					assertThat(aviso.datos()).containsEntry("pedidoId", pedido).doesNotContainKey("ordenId");
					assertThat(aviso.datos()).containsEntry("estado", "PAGADO");
				});

		pagar(ana, pedido, "otra-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PEDIDO_NO_PAGABLE"));
		// Los puntos son por servicio: el pedido no suma.
		Thread.sleep(500);
		assertThat(jdbc.queryForObject("select count(*) from movimiento_puntos where cliente_id = ?", Integer.class,
				ana.id())).isZero();
	}

	@Test
	void laMismaLlaveNoCobraDosVecesYConOtroPedidoEsInvalida() throws Exception {
		long pedido = apartado(ana, 1);
		long rechazo = id(pagar(ana, pedido, "fondos-" + unico, tarjeta("tok_sim_fondos_insuficientes"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("FALLIDO")));
		pagar(ana, pedido, "fondos-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(rechazo)).andExpect(jsonPath("$.estado").value("FALLIDO"));

		pagar(ana, pedido, "bien-" + unico, tarjeta("tok_sim_mastercard")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PAGADO"));
		pagar(ana, pedido, "bien-" + unico, tarjeta("tok_sim_mastercard")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from pago_app where pedido_app_id = ? and estado = 'PAGADO'",
				Integer.class, pedido)).isEqualTo(1);

		long otro = apartado(ana, 1);
		pagar(ana, otro, "bien-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isBadRequest());
		pagar(ana, otro, "corta", tarjeta("tok_sim_visa")).andExpect(status().isBadRequest());
		pagar(ana, otro, "ninguna-" + unico, "{}").andExpect(status().isBadRequest());
	}

	@Test
	void unPedidoAjenoOQueNoExisteNoSePaga() throws Exception {
		long pedido = apartado(ana, 1);

		pagar(beto, pedido, "ajena-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isNotFound());
		pagar(ana, Long.MAX_VALUE, "nadie-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select count(*) from pago_app where pedido_app_id = ?", Integer.class, pedido))
				.isZero();
	}

	@Test
	void noSePagaVencidoCanceladoEntregadoNiSinTotal() throws Exception {
		long vencido = apartado(ana, 1);
		jdbc.update("update pedido_app set vence_en = now() - interval '1 minute' where id = ?", vencido);
		noPagable(ana, vencido);

		long cancelado = apartado(ana, 1);
		como(ana.token(), post(APP + "/tienda/pedidos/{id}/cancelar", cancelado)).andExpect(status().isOk());
		noPagable(ana, cancelado);

		long entregado = apartado(ana, 1);
		como(almacen, post(PERSONAL + "/pedidos/{id}/listo", entregado)).andExpect(status().isOk());
		como(ana.token(), get(APP + "/tienda/pedidos/{id}", entregado)).andExpect(jsonPath("$.pagable").value(true));
		como(almacen, post(PERSONAL + "/pedidos/{id}/entregado", entregado)).andExpect(status().isOk());
		como(ana.token(), get(APP + "/tienda/pedidos/{id}", entregado)).andExpect(jsonPath("$.pagable").value(false));
		noPagable(ana, entregado);

		long sinTotal = jdbc.queryForObject("""
				insert into pedido_app (cliente_id, estado, entrega, total, vence_en, llave_idempotencia, created_at,
				  updated_at)
				values (?, 'APARTADO', 'MOSTRADOR', 0, now() + interval '1 day', ?, now(), now()) returning id
				""", Long.class, beto.id(), "cero-" + unico);
		como(beto.token(), get(APP + "/tienda/pedidos/{id}", sinTotal)).andExpect(jsonPath("$.pagable").value(false));
		noPagable(beto, sinTotal);
	}

	@Test
	void elTresDeSecureDetieneElVencimientoYAlConfirmarLoPaga() throws Exception {
		long pedido = apartado(ana, 3);
		long pendiente = id(pagar(ana, pedido, "tds-" + unico, tarjeta("tok_sim_3ds")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.requiereAutenticacion").value(true)));

		pagar(ana, pedido, "otro-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PAGO_EN_CURSO"));
		como(ana.token(), post(APP + "/tienda/pedidos/{id}/cancelar", pedido)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PAGO_EN_CURSO"));
		jdbc.update("update pedido_app set vence_en = now() - interval '1 minute' where id = ?", pedido);
		vencimiento.liberarVencidos();
		como(ana.token(), get(APP + "/tienda/pedidos/{id}", pedido)).andExpect(jsonPath("$.estado").value("apartado"));
		autenticar(beto, pendiente, true).andExpect(status().isNotFound());

		autenticar(ana, pendiente, true).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PAGADO"))
				.andExpect(jsonPath("$.pedidoId").value(pedido));

		// Pagado ya no vence aunque su fecha haya pasado.
		vencimiento.liberarVencidos();
		como(ana.token(), get(APP + "/tienda/pedidos/{id}", pedido)).andExpect(jsonPath("$.estado").value("apartado"))
				.andExpect(jsonPath("$.pagadoEn").isString()).andExpect(jsonPath("$.pago.id").value(pendiente));
		assertThat(existencia()).isEqualTo(7);
	}

	@Test
	void unIntentoOlvidadoYaNoDetieneElVencimiento() throws Exception {
		long pedido = apartado(ana, 2);
		long olvidado = id(pagar(ana, pedido, "olvido-" + unico, tarjeta("tok_sim_3ds")));
		jdbc.update("update pago_app set creado_en = now() - interval '20 minutes' where id = ?", olvidado);
		jdbc.update("update pedido_app set vence_en = now() - interval '1 minute' where id = ?", pedido);

		vencimiento.liberarVencidos();

		como(ana.token(), get(APP + "/tienda/pedidos/{id}", pedido)).andExpect(jsonPath("$.estado").value("cancelado"))
				.andExpect(jsonPath("$.motivoCancelacion").value("vencido"));
		assertThat(existencia()).isEqualTo(10);
	}

	@Test
	void pagadoNoSeCancelaDesdeLaAppPeroCambiaDeInstalacionYLaAgenciaLoCancela() throws Exception {
		long pedido = apartado(ana, 1);
		pagar(ana, pedido, "pago-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isCreated());

		como(ana.token(), post(APP + "/tienda/pedidos/{id}/cancelar", pedido)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PEDIDO_PAGADO"));
		como(ana.token(), json(put(APP + "/tienda/pedidos/{id}/instalacion", pedido), "{\"citaId\":null}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entrega").value("mostrador"));
		como(almacen, post(PERSONAL + "/pedidos/{id}/listo", pedido)).andExpect(status().isOk());
		como(almacen, post(PERSONAL + "/pedidos/{id}/cancelar", pedido)).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("cancelado"))
				.andExpect(jsonPath("$.motivoCancelacion").value("agencia"));
		assertThat(existencia()).isEqualTo(10);
	}

	@Test
	void laBaseExigeQueElPagoSeaDeUnaOrdenODeUnPedido() throws Exception {
		long pedido = apartado(ana, 1);
		String insertar = """
				insert into pago_app (orden_servicio_id, pedido_app_id, cliente_id, monto, procesador, intencion_id,
				  llave_idempotencia, metodo, estado)
				values (%s, %s, ?, 10, 'SIMULADO', ?, ?, 'TARJETA', 'PENDIENTE')
				""";

		assertThatThrownBy(
				() -> jdbc.update(insertar.formatted("null", "null"), ana.id(), "sim_pi_n" + unico, "n-" + unico))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_pago_app");
		Long orden = jdbc.queryForList("select id from orden_servicio limit 1", Long.class).stream().findFirst()
				.orElse(null);
		if (orden != null) {
			assertThatThrownBy(
					() -> jdbc.update(insertar.formatted(orden, pedido), ana.id(), "sim_pi_d" + unico, "d-" + unico))
					.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_pago_app");
		}
		jdbc.update(insertar.formatted("null", pedido), ana.id(), "sim_pi_p" + unico, "p-" + unico);
	}

	@Test
	void laCopiaDeMisDatosDiceQuePedidoPague() throws Exception {
		long pedido = apartado(ana, 1);
		pagar(ana, pedido, "arco-" + unico, tarjeta("tok_sim_visa")).andExpect(status().isCreated());

		var copia = mapeador.readTree(exportador.exportar(ana.id(), Instant.now()));

		assertThat(copia.at("/pagos/0/pedido_app_id").asLong()).isEqualTo(pedido);
		assertThat(copia.at("/pagos/0/orden_servicio_id").isNull()).isTrue();
	}

	private Cliente cliente(String nombre) {
		String correo = nombre + "-pedido-" + unico + "@correo.com";
		long id = registroClientesService.registrar(new NuevoCliente(nombre + " " + unico, correo, null, null)).id();
		sembrados.add(id);
		cuentasAppService.abrir(id, "Temporal123");
		return new Cliente(id, jwtService.generateToken(id, correo, List.of(Role.CLIENTE_CARECAR)));
	}

	private String personal(String nombreUsuario) {
		Usuario usuario = usuarioRepository.save(new Usuario(nombreUsuario, "hash-de-prueba", "Persona REFACCIONES",
				rolRepository.findByNombre("REFACCIONES").orElseThrow()));
		return jwtService.generateToken(usuario.getId(), nombreUsuario, List.of(Role.REFACCIONES));
	}

	private long apartado(Cliente cliente, int piezas) throws Exception {
		return id(como(cliente.token(),
				json(post(APP + "/tienda/pedidos").header("Idempotency-Key", UUID.randomUUID().toString()),
						"{\"renglones\":[{\"productoId\":" + tapetes + ",\"cantidad\":" + piezas
								+ "}],\"entrega\":\"mostrador\"}"))
				.andExpect(status().isCreated()));
	}

	private void noPagable(Cliente cliente, long pedido) throws Exception {
		pagar(cliente, pedido, UUID.randomUUID().toString(), tarjeta("tok_sim_visa")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PEDIDO_NO_PAGABLE"));
	}

	private int existencia() {
		return jdbc.queryForObject("select existencia from refaccion where id = ?", Integer.class, tapetes);
	}

	private static String tarjeta(String tokenDePrueba) {
		return "{\"tarjeta\":{\"token\":\"" + tokenDePrueba + "\",\"mesVence\":12,\"anioVence\":"
				+ (LocalDate.now(ZONA).getYear() + 1) + "}}";
	}

	private ResultActions pagar(Cliente cliente, long pedido, String llave, String cuerpo) throws Exception {
		return como(cliente.token(),
				json(post(APP + "/pedidos/{id}/pagos", pedido).header("Idempotency-Key", llave), cuerpo));
	}

	private ResultActions autenticar(Cliente cliente, long pago, boolean confirmada) throws Exception {
		return como(cliente.token(),
				json(post(APP + "/pagos/{id}/autenticacion", pago), "{\"confirmada\":" + confirmada + "}"));
	}

	private static long id(ResultActions respuesta) throws Exception {
		Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private ResultActions como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}
}
