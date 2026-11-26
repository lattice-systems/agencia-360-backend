package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.VencimientoDeApartados;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
 * La tienda de punta a punta (V2-M14.01, V2-M14.02, V2-M14.03 y V2-M14.05)
 * contra PostgreSQL real. No revierte cada prueba porque una compite con dos
 * hilos: cada una siembra sus clientes y sus refacciones con un sufijo propio y
 * al terminar borra sus citas para no gastar el cupo de otras pruebas.
 */
@AutoConfigureMockMvc
@RecordApplicationEvents
class TiendaIntegrationTest extends AbstractIntegrationTest {

	private static final String APP = "/api/v1/app/tienda";
	private static final String PERSONAL = "/api/v1/tienda";
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
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FranjaHorarioRepository franjas;

	@Autowired
	private VencimientoDeApartados vencimiento;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	@Autowired
	private ApplicationEvents eventos;

	private record Cliente(long id, long vehiculo, String token) {
	}

	private final List<Long> sembrados = new ArrayList<>();
	private String unico;
	private String modelo;
	private Cliente ana;
	private Cliente beto;
	private String almacen;
	private String taller;
	private long tapetes;
	private long filtro;

	@BeforeEach
	void sembrar() throws Exception {
		unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		modelo = "Mod" + unico;
		ana = cliente("ana", "A", modelo, 2020);
		beto = cliente("beto", "B", "Otro" + unico, 2020);
		almacen = personal("almacen-" + unico, "REFACCIONES", Role.REFACCIONES);
		taller = personal("taller-" + unico, "SERVICIO", Role.SERVICIO);
		tapetes = refaccion("TAP" + unico, "Tapetes " + unico, "850.00", 5);
		publicar(tapetes, "ACCESORIO", false, modelo, 2019, 2022);
		filtro = refaccion("FIL" + unico, "Filtro " + unico, "200.00", 3);
		publicar(filtro, "REFACCION", true, null, 0, 0);
	}

	@AfterEach
	void liberarLaAgenda() {
		for (Long cliente : sembrados) {
			jdbc.update("delete from movimiento_inventario where pedido_app_id in "
					+ "(select id from pedido_app where cliente_id = ?)", cliente);
			jdbc.update("delete from pedido_app_renglon where pedido_app_id in "
					+ "(select id from pedido_app where cliente_id = ?)", cliente);
			jdbc.update("delete from pedido_app where cliente_id = ?", cliente);
			jdbc.update("delete from etapa_orden where orden_servicio_id in (select id from orden_servicio "
					+ "where cita_id in (select id from cita where cliente_id = ?))", cliente);
			jdbc.update("delete from orden_servicio where cita_id in (select id from cita where cliente_id = ?)",
					cliente);
			jdbc.update("delete from cita where cliente_id = ?", cliente);
		}
	}

	@Test
	void elCatalogoSeFiltraPorElVehiculoYMuestraLaExistencia() throws Exception {
		como(ana.token(), get(APP + "/productos").param("vehiculoId", "" + ana.vehiculo()).param("q", unico))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.contenido[1].nombre").value("Tapetes " + unico))
				.andExpect(jsonPath("$.contenido[1].existencia").value(5))
				.andExpect(jsonPath("$.contenido[1].categoria").value("accesorio"))
				.andExpect(jsonPath("$.contenido[1].compatible").value(true))
				.andExpect(jsonPath("$.contenido[1].costo").doesNotExist());
		// A otro modelo solo le queda el universal.
		como(beto.token(), get(APP + "/productos").param("vehiculoId", "" + beto.vehiculo()).param("q", unico))
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].id").value(filtro));
		como(beto.token(), get(APP + "/productos/{id}", tapetes).param("vehiculoId", "" + beto.vehiculo()))
				.andExpect(jsonPath("$.compatible").value(false));
		como(ana.token(), get(APP + "/productos").param("categoria", "refaccion").param("q", unico))
				.andExpect(jsonPath("$.totalElementos").value(1));
		como(ana.token(), get(APP + "/productos").param("q", "%" + unico + "_"))
				.andExpect(jsonPath("$.totalElementos").value(0));
		como(ana.token(), get(APP + "/productos").param("vehiculoId", "" + beto.vehiculo()))
				.andExpect(status().isNotFound());
		como(ana.token(), get(APP + "/productos").param("categoria", "llantas")).andExpect(status().isBadRequest());
	}

	@Test
	void unaRefaccionSinPublicarNoSeVeNiSeAparta() throws Exception {
		long oculta = refaccion("OCU" + unico, "Oculta " + unico, "10.00", 4);

		como(ana.token(), get(APP + "/productos/{id}", oculta)).andExpect(status().isNotFound());
		apartar(ana, llave(), pieza(oculta, 1)).andExpect(status().isNotFound());
		assertThat(existencia(oculta)).isEqualTo(4);
	}

	@Test
	void apartarReservaConMovimientosYRepetirLaLlaveNoApartaDosVeces() throws Exception {
		String llave = llave();

		String creado = apartar(ana, llave, pieza(tapetes, 2) + "," + pieza(filtro, 1)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("apartado")).andExpect(jsonPath("$.entrega").value("mostrador"))
				.andExpect(jsonPath("$.total").value(1900.0)).andExpect(jsonPath("$.venceEn").isString())
				.andExpect(jsonPath("$.renglones.length()").value(2)).andExpect(jsonPath("$.clienteId").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		apartar(ana, llave, pieza(tapetes, 2)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(numero(creado, "$.id")));

		assertThat(existencia(tapetes)).isEqualTo(3);
		assertThat(existencia(filtro)).isEqualTo(2);
		assertThat(movimientos(numero(creado, "$.id"), "SALIDA")).isEqualTo(3);
		assertThat(eventos.stream(AvisoAlCliente.class).filter(aviso -> aviso.evento().equals("pedido.estado")))
				.hasSize(1);
	}

	@Test
	void sinExistenciaNoSeApartaNadaDelPedido() throws Exception {
		apartar(ana, llave(), pieza(tapetes, 1) + "," + pieza(filtro, 4)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("SIN_EXISTENCIA"));

		assertThat(existencia(tapetes)).isEqualTo(5);
		assertThat(existencia(filtro)).isEqualTo(3);
		como(ana.token(), get(APP + "/pedidos")).andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	void dosClientesALaVezNoApartanLaMismaPieza() throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Integer>> respuestas = new ArrayList<>();
			for (Cliente cliente : List.of(ana, beto)) {
				respuestas.add(hilos.submit(() -> {
					salida.await();
					return apartar(cliente, llave(), pieza(filtro, 2)).andReturn().getResponse().getStatus();
				}));
			}
			salida.countDown();
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> respuesta : respuestas) {
				estados.add(respuesta.get(30, TimeUnit.SECONDS));
			}

			assertThat(estados).containsExactlyInAnyOrder(201, 409);
			assertThat(existencia(filtro)).isEqualTo(1);
		} finally {
			hilos.shutdownNow();
		}
	}

	@Test
	void noSePasaDeTresPedidosAbiertosYLaPeticionSeValida() throws Exception {
		for (int numero = 0; numero < 3; numero++) {
			apartar(ana, llave(), pieza(tapetes, 1)).andExpect(status().isCreated());
		}

		apartar(ana, llave(), pieza(tapetes, 1)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("LIMITE_APARTADOS"));
		apartar(beto, llave(), pieza(filtro, 11)).andExpect(status().isBadRequest());
		apartar(beto, llave(), pieza(filtro, 1) + "," + pieza(filtro, 1)).andExpect(status().isBadRequest());
		apartar(beto, "corta", pieza(filtro, 1)).andExpect(status().isBadRequest());
		como(beto.token(), json(post(APP + "/pedidos"), cuerpo(pieza(filtro, 1), "mostrador", null)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void cancelarLiberaLasPiezasUnaSolaVezYNadieVeLoAjeno() throws Exception {
		long pedido = apartado(ana, pieza(tapetes, 2));

		como(beto.token(), get(APP + "/pedidos/{id}", pedido)).andExpect(status().isNotFound());
		como(beto.token(), post(APP + "/pedidos/{id}/cancelar", pedido)).andExpect(status().isNotFound());
		como(ana.token(), post(APP + "/pedidos/{id}/cancelar", pedido)).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("cancelado"))
				.andExpect(jsonPath("$.motivoCancelacion").value("cliente"))
				.andExpect(jsonPath("$.venceEn").doesNotExist());
		como(ana.token(), post(APP + "/pedidos/{id}/cancelar", pedido)).andExpect(status().isOk());

		assertThat(existencia(tapetes)).isEqualTo(5);
		assertThat(movimientos(pedido, "ENTRADA")).isEqualTo(2);
		// Las liberaciones no aparecen como recepciones del proveedor.
		como(almacen, get("/api/v1/refacciones/{id}/recepciones", tapetes)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unApartadoVencidoSeLiberaSoloOAlConsultarlo() throws Exception {
		long primero = apartado(ana, pieza(tapetes, 2));
		long segundo = apartado(beto, pieza(filtro, 1));
		vencer(primero);
		vencer(segundo);

		como(ana.token(), get(APP + "/pedidos/{id}", primero)).andExpect(jsonPath("$.estado").value("cancelado"))
				.andExpect(jsonPath("$.motivoCancelacion").value("vencido"));
		vencimiento.liberarVencidos();
		vencimiento.liberarVencidos();

		assertThat(existencia(tapetes)).isEqualTo(5);
		assertThat(existencia(filtro)).isEqualTo(3);
		assertThat(movimientos(segundo, "ENTRADA")).isEqualTo(1);
	}

	@Test
	void conInstalacionLaVigenciaSigueALaCitaYElTallerLoVeEnLaOrden() throws Exception {
		LocalDate dia = diaHabil(5);
		long cita = cita(ana, dia);

		String creado = como(ana.token(),
				json(post(APP + "/pedidos").header("Idempotency-Key", llave()),
						cuerpo(pieza(tapetes, 1), "cita", cita)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.entrega").value("cita"))
				.andExpect(jsonPath("$.cita.id").value(cita)).andExpect(jsonPath("$.cita.fecha").value(dia.toString()))
				.andExpect(jsonPath("$.cita.vehiculoId").value(ana.vehiculo())).andReturn().getResponse()
				.getContentAsString();
		long pedido = numero(creado, "$.id");
		assertThat(venceEn(pedido)).isEqualTo(dia.plusDays(2).atStartOfDay(ZONA_AGENCIA).toInstant());

		String orden = como(taller,
				json(post("/api/v1/taller/ordenes"),
						"{\"vehiculoId\":" + ana.vehiculo() + ",\"kilometraje\":1000,\"citaId\":" + cita + "}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.pedidosApp[0].pedidoId").value(pedido))
				.andExpect(jsonPath("$.pedidosApp[0].piezas[0].codigo").value("TAP" + unico.toUpperCase(Locale.ROOT)))
				.andExpect(jsonPath("$.pedidosApp[0].piezas[0].cantidad").value(1)).andReturn().getResponse()
				.getContentAsString();
		como(taller, get("/api/v1/taller/ordenes/{id}", numero(orden, "$.id")))
				.andExpect(jsonPath("$.pedidosApp.length()").value(1));
	}

	@Test
	void reprogramarMueveLaVigenciaYCancelarLaCitaRegresaAMostrador() throws Exception {
		long cita = cita(ana, diaHabil(5));
		long pedido = apartado(ana, pieza(tapetes, 1));
		instalar(ana, pedido, "" + cita).andExpect(status().isOk()).andExpect(jsonPath("$.entrega").value("cita"));
		LocalDate otro = diaHabil(9);

		como(ana.token(), json(post("/api/v1/app/citas/{id}/reprogramar", cita),
				"{\"franjaId\":" + franja(otro) + ",\"fecha\":\"" + otro + "\"}")).andExpect(status().isOk());
		assertThat(venceEn(pedido)).isEqualTo(otro.plusDays(2).atStartOfDay(ZONA_AGENCIA).toInstant());

		como(ana.token(), post("/api/v1/app/citas/{id}/cancelar", cita)).andExpect(status().isOk());
		como(ana.token(), get(APP + "/pedidos/{id}", pedido)).andExpect(jsonPath("$.entrega").value("mostrador"))
				.andExpect(jsonPath("$.estado").value("apartado")).andExpect(jsonPath("$.cita").doesNotExist());
		instalar(ana, pedido, "" + cita).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CITA_NO_DISPONIBLE"));
	}

	@Test
	void laInstalacionExigeUnaCitaPropiaYProductosCompatibles() throws Exception {
		long deBeto = cita(beto, diaHabil(6));
		long pedidoDeAna = apartado(ana, pieza(tapetes, 1));

		instalar(ana, pedidoDeAna, "" + deBeto).andExpect(status().isNotFound());
		como(beto.token(),
				json(post(APP + "/pedidos").header("Idempotency-Key", llave()),
						cuerpo(pieza(tapetes, 1), "cita", deBeto)))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("PRODUCTO_INCOMPATIBLE"));
		instalar(ana, pedidoDeAna, "null").andExpect(status().isOk())
				.andExpect(jsonPath("$.entrega").value("mostrador"));
		assertThat(existencia(tapetes)).isEqualTo(4);
	}

	@Test
	void laAgenciaLoDejaListoYLoEntregaYElClienteLoSigue() throws Exception {
		long pedido = apartado(ana, pieza(tapetes, 1));

		como(almacen, get(PERSONAL + "/pedidos").param("estado", "APARTADO").param("tamano", "50"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[?(@.id == " + pedido + ")].clienteId").value((int) ana.id()));
		como(almacen, post(PERSONAL + "/pedidos/{id}/entregado", pedido)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		como(almacen, post(PERSONAL + "/pedidos/{id}/listo", pedido)).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("listo")).andExpect(jsonPath("$.listoEn").isString());
		como(almacen, post(PERSONAL + "/pedidos/{id}/entregado", pedido)).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("entregado")).andExpect(jsonPath("$.venceEn").doesNotExist());

		como(ana.token(), post(APP + "/pedidos/{id}/cancelar", pedido)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PEDIDO_NO_CANCELABLE"));
		instalar(ana, pedido, "null").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PEDIDO_NO_MODIFICABLE"));
		como(ana.token(), get(APP + "/pedidos")).andExpect(jsonPath("$.contenido[0].estado").value("entregado"));
		assertThat(existencia(tapetes)).isEqualTo(4);
		assertThat(eventos.stream(AvisoAlCliente.class)
				.filter(aviso -> aviso.evento().equals("pedido.estado") && aviso.clienteId().equals(ana.id()))
				.map(aviso -> aviso.datos().get("estado"))).containsExactly("apartado", "listo", "entregado");
	}

	@Test
	void cadaRutaExigeSuRol() throws Exception {
		como(taller, get(PERSONAL + "/pedidos")).andExpect(status().isForbidden());
		como(ana.token(), get(PERSONAL + "/pedidos")).andExpect(status().isForbidden());
		como(ana.token(), get(PERSONAL + "/productos/{id}", tapetes)).andExpect(status().isForbidden());
		como(almacen, get(APP + "/productos")).andExpect(status().isForbidden());
		como(almacen,
				json(put(PERSONAL + "/productos/{id}", tapetes),
						"{\"publicada\":true,\"categoria\":\"ACCESORIO\",\"universal\":false}"))
				.andExpect(status().isBadRequest());
	}

	private Cliente cliente(String nombre, String letra, String auto, int anio) {
		String correo = nombre + "-" + unico + "@correo.com";
		long id = registroClientesService.registrar(new NuevoCliente(nombre + " " + unico, correo, null, null)).id();
		sembrados.add(id);
		String vin = (letra + "TIE" + unico + "000000000").substring(0, 17).toUpperCase(Locale.ROOT);
		long vehiculo = registroClientesService
				.asignarVehiculo(id, new VehiculoNuevo(null, vin, "Nissan", auto, anio, null)).vehiculos().getFirst()
				.id();
		cuentasAppService.abrir(id, "Temporal123");
		return new Cliente(id, vehiculo, jwtService.generateToken(id, correo, List.of(Role.CLIENTE_CARECAR)));
	}

	private String personal(String nombreUsuario, String rol, Role permiso) {
		Usuario usuario = usuarioRepository.save(new Usuario(nombreUsuario, "hash-de-prueba", "Persona " + rol,
				rolRepository.findByNombre(rol).orElseThrow()));
		return jwtService.generateToken(usuario.getId(), nombreUsuario, List.of(permiso));
	}

	private long refaccion(String codigo, String descripcion, String precio, int existencia) {
		return jdbc.queryForObject("""
				insert into refaccion (codigo, descripcion, costo, precio, existencia, minimo, created_at, updated_at)
				values (?, ?, 1, cast(? as numeric), ?, 0, now(), now()) returning id
				""", Long.class, codigo.toUpperCase(Locale.ROOT), descripcion, precio, existencia);
	}

	private void publicar(long refaccion, String categoria, boolean universal, String auto, int desde, int hasta)
			throws Exception {
		String modelos = auto == null
				? "[]"
				: "[{\"modelo\":\"" + auto + "\",\"anioDesde\":" + desde + ",\"anioHasta\":" + hasta + "}]";
		como(almacen,
				json(put(PERSONAL + "/productos/{id}", refaccion),
						"{\"publicada\":true,\"categoria\":\"" + categoria + "\",\"universal\":" + universal
								+ ",\"compatibilidad\":" + modelos + "}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.publicada").value(true));
	}

	private static String llave() {
		return UUID.randomUUID().toString();
	}

	private static String pieza(long producto, int cantidad) {
		return "{\"productoId\":" + producto + ",\"cantidad\":" + cantidad + "}";
	}

	private static String cuerpo(String piezas, String entrega, Long cita) {
		return "{\"renglones\":[" + piezas + "],\"entrega\":\"" + entrega + "\""
				+ (cita == null ? "" : ",\"citaId\":" + cita) + "}";
	}

	private ResultActions apartar(Cliente cliente, String llave, String piezas) throws Exception {
		return como(cliente.token(),
				json(post(APP + "/pedidos").header("Idempotency-Key", llave), cuerpo(piezas, "mostrador", null)));
	}

	private long apartado(Cliente cliente, String piezas) throws Exception {
		return numero(apartar(cliente, llave(), piezas).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.id");
	}

	private ResultActions instalar(Cliente cliente, long pedido, String cita) throws Exception {
		return como(cliente.token(), json(put(APP + "/pedidos/{id}/instalacion", pedido), "{\"citaId\":" + cita + "}"));
	}

	private long cita(Cliente cliente, LocalDate dia) throws Exception {
		String datos = "{\"vehiculoId\":%d,\"franjaId\":%d,\"fecha\":\"%s\"}".formatted(cliente.vehiculo(), franja(dia),
				dia);
		String creada = como(cliente.token(), json(post("/api/v1/app/citas"), datos)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return numero(creada, "$.id");
	}

	private long franja(LocalDate dia) {
		return franjas.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc().stream().filter(franja -> franja.esDe(dia))
				.findFirst().orElseThrow().getId();
	}

	private static LocalDate diaHabil(int enDias) {
		LocalDate dia = LocalDate.now(ZONA_AGENCIA).plusDays(enDias);
		return dia.getDayOfWeek() == DayOfWeek.SUNDAY ? dia.plusDays(1) : dia;
	}

	private int existencia(long refaccion) {
		return jdbc.queryForObject("select existencia from refaccion where id = ?", Integer.class, refaccion);
	}

	/** Piezas movidas por el pedido en ese sentido. */
	private int movimientos(long pedido, String tipo) {
		return jdbc.queryForObject(
				"select coalesce(sum(cantidad), 0) from movimiento_inventario where pedido_app_id = ? and tipo = ?",
				Integer.class, pedido, tipo);
	}

	private void vencer(long pedido) {
		jdbc.update("update pedido_app set vence_en = now() - interval '1 minute' where id = ?", pedido);
	}

	private java.time.Instant venceEn(long pedido) {
		return jdbc.queryForObject("select vence_en from pedido_app where id = ?", java.sql.Timestamp.class, pedido)
				.toInstant();
	}

	private static long numero(String json, String ruta) {
		return JsonPath.<Number>read(json, ruta).longValue();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private ResultActions como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}
}
