package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AnonimizadorDeCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V2-M22.03 de punta a punta. Dos clientes con las mismas filas en cada tabla:
 * al cancelar uno, lo suyo se borra o se anonimiza, lo que se conserva sigue
 * ahí, y lo del otro no se toca.
 */
@AutoConfigureMockMvc
class CancelacionIntegrationTest extends AbstractIntegrationTest {

	private static final String CONTRASENA = "Temporal123";

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

	@MockitoSpyBean
	private AlmacenArchivos archivos;

	@Autowired
	private AnonimizadorDeCuenta anonimizador;

	@Autowired
	private DirectorioClientes directorioClientes;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	/** Lo que se sembró de un cliente, para buscarlo después. */
	private record Cliente(long id, String correo, String token, String fotoClave, String ticketClave,
			String valuacionFotoClave) {
	}

	private String unico;
	private long modeloCatalogo;
	private long parametroTomaCuenta;
	private long personal;
	private Cliente ana;
	private Cliente beto;

	@BeforeEach
	void dosClientesConTodo() {
		unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		Rol ventas = rolRepository.findByNombre("VENTAS").orElseThrow();
		personal = usuarioRepository.save(new Usuario("personal-" + unico, "hash", "Personal", ventas)).getId();
		modeloCatalogo = jdbc.queryForObject("""
				insert into modelo_catalogo (marca, modelo, anio, version, precio_desde, activo, created_at, updated_at)
				values ('Nissan', ?, 2026, 'Advance', 300000, true, now(), now()) returning id
				""", Long.class, "Kicks" + unico);
		parametroTomaCuenta = jdbc.queryForObject("""
				insert into parametro_toma_cuenta
				  (marca, modelo, anio, valor_base, depreciacion_anual, factor_km, factor_estado, activo,
				   created_at, updated_at)
				values (?, 'Versa', 2020, 200000, 0.1, 0.05, 0.1, true, now(), now()) returning id
				""", Long.class, "Marca" + unico);
		ana = sembrar("Ana Cancelacion", "ana");
		beto = sembrar("Beto Conserva", "beto");
	}

	private Cliente sembrar(String nombre, String clave) {
		String correo = clave + "-" + unico + "@correo.com";
		long id = registroClientesService.registrar(new NuevoCliente(nombre, correo, "5512345678", null)).id();
		cuentasAppService.abrir(id, CONTRASENA);
		String token = jwtService.generateToken(id, correo, List.of(Role.CLIENTE_CARECAR));
		long cuenta = jdbc.queryForObject("select id from cuenta_app where cliente_id = ?", Long.class, id);

		long vehiculo = jdbc.queryForObject("""
				insert into vehiculo_cliente
				  (cliente_id, vin, modelo, anio, placas, kilometraje, created_at, updated_at)
				values (?, ?, 'Kicks', 2023, 'ABC123', 12000, now(), now()) returning id
				""", Long.class, id, ("1HGCM" + clave + unico + "00000000000").substring(0, 17).toUpperCase());
		long direccion = jdbc.queryForObject("""
				insert into direccion_cliente
				  (cliente_id, alias, calle, colonia, cp, ciudad, lat, lng, created_at, updated_at)
				values (?, 'Casa', 'Calle Secreta 123', 'Centro', '91000', 'Xalapa', 19.5, -96.9, now(), now())
				returning id
				""", Long.class, id);
		long cita = jdbc.queryForObject("""
				insert into cita (cliente_id, vehiculo_cliente_id, fecha, franja_id, hora_inicio, hora_fin, estado,
				  created_at, updated_at)
				values (?, ?, current_date + 3, 1, time '10:00', time '11:00', 'CONFIRMADA', now(), now()) returning id
				""", Long.class, id, vehiculo);
		jdbc.update("""
				insert into traslado (cita_id, direccion_cliente_id, tipo, estado, costo, fecha, ventana_inicio,
				  ventana_fin, created_at, updated_at)
				values (?, ?, 'RECOGER', 'PROGRAMADO', 0, current_date + 3, time '08:00', time '09:00', now(), now())
				""", cita, direccion);
		jdbc.update("""
				insert into solicitud_asistencia
				  (cliente_id, vehiculo_cliente_id, tipo, lat, lng, comentario, estado, creada_en)
				values (?, ?, 'LLANTA', 19.51, -96.91, 'Estoy frente a mi casa', 'ATENDIDA', now())
				""", id, vehiculo);
		jdbc.update("""
				insert into consulta_asistente
				  (cliente_id, vehiculo_cliente_id, pregunta, respuesta, encontrado, proveedor)
				values (?, ?, 'Qué significa la luz del aceite', 'Presión baja', true, 'prueba')
				""", id, vehiculo);
		long estimacion = jdbc.queryForObject("""
				insert into estimacion_toma_cuenta
				  (cliente_id, vehiculo_cliente_id, marca, modelo, anio, km, estado, minimo, maximo,
				   parametro_toma_cuenta_id, supuestos, created_at, updated_at)
				values (?, ?, 'Nissan', 'Versa', 2020, 40000, 'BUENO', 100000, 120000, ?, 'supuestos', now(), now())
				returning id
				""", Long.class, id, vehiculo, parametroTomaCuenta);
		jdbc.update("""
				insert into solicitud_estreno (cliente_id, modelo_catalogo_id, tipo, estimacion_toma_cuenta_id)
				values (?, ?, 'COTIZACION', ?)
				""", id, modeloCatalogo, estimacion);

		// Una valuación con su foto: referencia a la estimación (V2-M26).
		String fotoValuacion = archivos.guardar("valuacion-fotos", "image/jpeg", new byte[]{1, 1, 1}).clave();
		jdbc.update("""
				insert into valuacion (estimacion_toma_cuenta_id, cliente_id, vehiculo_cliente_id,
				  fotos, cita_id, estado,
				  created_at, updated_at)
				values (?, ?, ?, ?, ?, 'CITA_AGENDADA', now(), now())
				""", estimacion, id, vehiculo,
				"{\"FRENTE\":{\"clave\":\"" + fotoValuacion + "\",\"subidaEn\":\"2026-11-25T10:00:00Z\"}}", cita);

		String foto = archivos.guardar("tablero", "image/png", new byte[]{1, 2, 3}).clave();
		String ticket = archivos.guardar("tickets", "image/png", new byte[]{4, 5, 6}).clave();
		long lectura = jdbc.queryForObject("""
				insert into lectura_km (vehiculo_cliente_id, km, fuente, foto_url, registrada_en)
				values (?, 12500, 'CLIENTE', ?, now()) returning id
				""", Long.class, vehiculo, foto);
		jdbc.update("""
				insert into lectura_km (vehiculo_cliente_id, km, fuente, registrada_en)
				values (?, 12000, 'TALLER', now())
				""", vehiculo);
		jdbc.update("""
				insert into gasto_vehiculo
				  (vehiculo_cliente_id, tipo, monto, fecha, ticket_url, lectura_km_id, created_at, updated_at)
				values (?, 'GASOLINA', 500, current_date, ?, ?, now(), now())
				""", vehiculo, ticket, lectura);

		jdbc.update("""
				insert into sesion_app (cuenta_id, token_hash, expira_en, created_at, updated_at)
				values (?, ?, now() + interval '30 days', now(), now())
				""", cuenta,
				UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
		jdbc.update("""
				insert into token_recuperacion (cuenta_app_id, token_hash, codigo_hash, expira, created_at, updated_at)
				values (?, ?, 'hash', now() + interval '1 day', now(), now())
				""", cuenta,
				UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));

		long solicitudArco = jdbc.queryForObject("""
				insert into solicitud_arco (cliente_id, tipo, estado, archivo_url, creada_en, resuelta_en)
				values (?, 'ACCESO', 'RESUELTA', '/api/v1/app/privacidad/arco/0/archivo', now(), now()) returning id
				""", Long.class, id);
		jdbc.update("insert into archivo_arco (solicitud_arco_id, contenido, tamano_bytes) values (?, ?, 3)",
				solicitudArco, new byte[]{7, 8, 9});
		jdbc.update("""
				insert into aceptacion_aviso (cuenta_app_id, aviso_privacidad_id, aceptado_en)
				values (?, (select id from aviso_privacidad where version = 1), now())
				""", cuenta);
		jdbc.update("""
				insert into orden_servicio (vehiculo_cliente_id, fecha, kilometraje, atendido_por, subtotal, iva, total,
				  mano_de_obra, refacciones, estatus, created_at, updated_at)
				values (?, current_date, 12000, ?, 100, 16, 116, 100, 16, 'RECIBIDO', now(), now())
				""", vehiculo, personal);
		return new Cliente(id, correo, token, foto, ticket, fotoValuacion);
	}

	@Test
	void cancelarBorraYAnonimizaLoDeLaCuentaYSoloDeEsa() throws Exception {
		Map<String, Long> antesDeBeto = conteos(beto.id());

		cancelar(ana, CONTRASENA).andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("CANCELACION"))
				.andExpect(jsonPath("$.estado").value("RESUELTA")).andExpect(jsonPath("$.resueltaEn").isNotEmpty())
				.andExpect(jsonPath("$.venceEn").doesNotExist());

		// La cuenta: desactivada, sin correo como usuario, y la contraseña ya no sirve.
		Map<String, Object> cuenta = jdbc.queryForMap(
				"select activa, usuario, contrasena_hash, debe_cambiar_contrasena from cuenta_app where cliente_id = ?",
				ana.id());
		assertThat(cuenta.get("activa")).isEqualTo(false);
		assertThat((String) cuenta.get("usuario")).startsWith("eliminada-").doesNotContain("@");
		assertThat(cuenta.get("debe_cambiar_contrasena")).isEqualTo(false);
		assertThat(jdbc.queryForObject(
				"select count(*) from sesion_app where cuenta_id = (select id from cuenta_app where cliente_id = ?)",
				Integer.class, ana.id())).isZero();
		assertThat(jdbc.queryForObject("select count(*) from token_recuperacion where cuenta_app_id = "
				+ "(select id from cuenta_app where cliente_id = ?)", Integer.class, ana.id())).isZero();

		// El cliente: sin nombre, correo ni teléfono; la fila sigue.
		Map<String, Object> cliente = jdbc.queryForMap("select nombre, correo, telefono from cliente where id = ?",
				ana.id());
		assertThat(cliente.get("nombre")).isEqualTo("Cliente eliminado");
		assertThat(cliente.get("correo")).isNull();
		assertThat(cliente.get("telefono")).isNull();

		// Las direcciones se vacían pero siguen ahí: un traslado las referencia.
		Map<String, Object> direccion = jdbc.queryForMap(
				"select alias, calle, colonia, cp, ciudad, lat, lng from direccion_cliente where cliente_id = ?",
				ana.id());
		assertThat(direccion.values().stream().map(String::valueOf)).doesNotContain("Calle Secreta 123", "Xalapa",
				"Centro", "91000", "Casa");
		assertThat(jdbc.queryForObject("""
				select count(*) from traslado t join direccion_cliente d on d.id = t.direccion_cliente_id
				where d.cliente_id = ?
				""", Integer.class, ana.id())).isEqualTo(1);

		// La asistencia queda como registro, sin dónde estaba ni lo que contó.
		Map<String, Object> asistencia = jdbc.queryForMap(
				"select tipo, estado, lat, lng, comentario from solicitud_asistencia where cliente_id = ?", ana.id());
		assertThat(asistencia.get("tipo")).isEqualTo("LLANTA");
		assertThat(asistencia.get("lat")).isNull();
		assertThat(asistencia.get("lng")).isNull();
		assertThat(asistencia.get("comentario")).isNull();

		// Lo que no es obligatorio: fuera.
		assertThat(contar("consulta_asistente", "cliente_id", ana.id())).isZero();
		assertThat(contar("valuacion", "cliente_id", ana.id())).isZero();
		assertThat(archivos.leer(ana.valuacionFotoClave())).isEmpty();
		assertThat(contar("estimacion_toma_cuenta", "cliente_id", ana.id())).isZero();
		assertThat(contar("solicitud_estreno", "cliente_id", ana.id())).isZero();
		assertThat(jdbc.queryForObject("""
				select count(*) from gasto_vehiculo g join vehiculo_cliente v on v.id = g.vehiculo_cliente_id
				where v.cliente_id = ?
				""", Integer.class, ana.id())).isZero();
		assertThat(jdbc.queryForObject("""
				select count(*) from lectura_km l join vehiculo_cliente v on v.id = l.vehiculo_cliente_id
				where v.cliente_id = ? and l.fuente = 'CLIENTE'
				""", Integer.class, ana.id())).isZero();

		// Los archivos que subió se borran del almacén; la copia de datos, también.
		assertThat(archivos.leer(ana.fotoClave())).isEmpty();
		assertThat(archivos.leer(ana.ticketClave())).isEmpty();
		assertThat(jdbc.queryForObject("""
				select count(*) from archivo_arco a join solicitud_arco s on s.id = a.solicitud_arco_id
				where s.cliente_id = ?
				""", Integer.class, ana.id())).isZero();

		// Lo que se conserva: historial de servicio, prueba del aviso y de lo pedido.
		assertThat(contar("vehiculo_cliente", "cliente_id", ana.id())).isEqualTo(1);
		assertThat(contar("cita", "cliente_id", ana.id())).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				select count(*) from lectura_km l join vehiculo_cliente v on v.id = l.vehiculo_cliente_id
				where v.cliente_id = ? and l.fuente = 'TALLER'
				""", Integer.class, ana.id())).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				select count(*) from orden_servicio o join vehiculo_cliente v on v.id = o.vehiculo_cliente_id
				where v.cliente_id = ?
				""", Integer.class, ana.id())).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				select count(*) from aceptacion_aviso a join cuenta_app c on c.id = a.cuenta_app_id
				where c.cliente_id = ?
				""", Integer.class, ana.id())).isEqualTo(1);
		List<Map<String, Object>> solicitudes = jdbc.queryForList(
				"select tipo, estado, archivo_url from solicitud_arco where cliente_id = ? order by id", ana.id());
		assertThat(solicitudes).extracting(s -> s.get("tipo")).containsExactly("ACCESO", "CANCELACION");
		assertThat(solicitudes).extracting(s -> s.get("estado")).containsExactly("RESUELTA", "RESUELTA");
		assertThat(solicitudes).extracting(s -> s.get("archivo_url")).containsOnlyNulls();

		// Y el otro cliente, intacto.
		assertThat(conteos(beto.id())).isEqualTo(antesDeBeto);
		assertThat(jdbc.queryForObject("select nombre from cliente where id = ?", String.class, beto.id()))
				.isEqualTo("Beto Conserva");
		assertThat(jdbc.queryForObject("select activa from cuenta_app where cliente_id = ?", Boolean.class, beto.id()))
				.isTrue();
		assertThat(jdbc.queryForObject("select calle from direccion_cliente where cliente_id = ?", String.class,
				beto.id())).isEqualTo("Calle Secreta 123");
		assertThat(archivos.leer(beto.fotoClave())).isPresent();
		assertThat(archivos.leer(beto.ticketClave())).isPresent();
		assertThat(archivos.leer(beto.valuacionFotoClave())).isPresent();
	}

	@Test
	void despuesDeCancelarElTokenYLaContrasenaYaNoSirven() throws Exception {
		cancelar(ana, CONTRASENA).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/app/privacidad/arco").header(HttpHeaders.AUTHORIZATION, "Bearer " + ana.token()))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
		mockMvc.perform(post("/api/v1/app/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + ana.correo() + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
				.andExpect(status().isUnauthorized());
		// Cancelar otra vez es un 403, no un 200.
		cancelar(ana, CONTRASENA).andExpect(status().isForbidden());
	}

	@Test
	void elCrmNoPuedeReactivarUnaCuentaCancelada() throws Exception {
		cancelar(ana, CONTRASENA).andExpect(status().isOk());
		String gerente = jwtService.generateToken(1L, "gerente", List.of(Role.GERENTE_MARKETING));

		mockMvc.perform(post("/api/v1/clientes/" + ana.id() + "/cuenta-app/activar").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + gerente)).andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("select activa from cuenta_app where cliente_id = ?", Boolean.class, ana.id()))
				.isFalse();
		// Una cuenta desactivada a mano, no cancelada, sí se reactiva.
		mockMvc.perform(post("/api/v1/clientes/" + beto.id() + "/cuenta-app/desactivar")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + gerente)).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/clientes/" + beto.id() + "/cuenta-app/activar").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + gerente)).andExpect(status().isOk());
	}

	@Test
	void unaContrasenaEquivocadaDa400YNoToca_Nada() throws Exception {
		Map<String, Long> antes = conteos(ana.id());

		cancelar(ana, "Equivocada1").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		cancelar(ana, "").andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/app/privacidad/arco/cancelacion").contentType(MediaType.APPLICATION_JSON)
				.content("{}").header(HttpHeaders.AUTHORIZATION, "Bearer " + ana.token()))
				.andExpect(status().isBadRequest());

		assertThat(conteos(ana.id())).isEqualTo(antes);
		assertThat(jdbc.queryForObject("select activa from cuenta_app where cliente_id = ?", Boolean.class, ana.id()))
				.isTrue();
		assertThat(jdbc.queryForObject("select nombre from cliente where id = ?", String.class, ana.id()))
				.isEqualTo("Ana Cancelacion");
		assertThat(archivos.leer(ana.fotoClave())).isPresent();
	}

	@Test
	void cincoIntentosEquivocadosBloqueanLaCancelacionYElLogin() throws Exception {
		for (int i = 0; i < 5; i++) {
			cancelar(ana, "Equivocada1").andExpect(status().isBadRequest());
		}

		// Con la correcta tambien: la cuenta esta bloqueada 15 minutos.
		cancelar(ana, CONTRASENA).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
		assertThat(jdbc.queryForObject("select activa from cuenta_app where cliente_id = ?", Boolean.class, ana.id()))
				.isTrue();
		assertThat(jdbc.queryForObject("select nombre from cliente where id = ?", String.class, ana.id()))
				.isEqualTo("Ana Cancelacion");
	}

	@Test
	void sinSesionYSoloParaClientesDeLaApp() throws Exception {
		mockMvc.perform(post("/api/v1/app/privacidad/arco/cancelacion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"contrasena\":\"" + CONTRASENA + "\"}")).andExpect(status().isUnauthorized());
		// Un usuario del personal cuyo id coincide con el de una clienta, y con su
		// contraseña correcta, tampoco puede: el rol manda, no el id.
		String personal = jwtService.generateToken(ana.id(), "gerente", List.of(Role.GERENTE_MARKETING));
		mockMvc.perform(post("/api/v1/app/privacidad/arco/cancelacion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"contrasena\":\"" + CONTRASENA + "\"}")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + personal)).andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("select activa from cuenta_app where cliente_id = ?", Boolean.class, ana.id()))
				.isTrue();
		assertThat(jdbc.queryForObject("select nombre from cliente where id = ?", String.class, ana.id()))
				.isEqualTo("Ana Cancelacion");
	}

	@Test
	void losArchivosSoloSeBorranCuandoLaTransaccionSeConfirma() {
		TransactionTemplate transaccion = new TransactionTemplate(transacciones);
		Map<String, Long> antes = conteos(ana.id());

		transaccion.executeWithoutResult(estado -> {
			anonimizador.anonimizar(ana.id());
			estado.setRollbackOnly();
		});

		// Todo revirtió: las filas siguen y, con ellas, los archivos a los que apuntan.
		// Se mira cuándo se llama a borrar, no solo el resultado: con el almacén en
		// disco un borrado no se revierte.
		assertThat(conteos(ana.id())).isEqualTo(antes);
		verify(archivos, never()).borrar(anyString());
		assertThat(archivos.leer(ana.fotoClave())).isPresent();
		assertThat(archivos.leer(ana.ticketClave())).isPresent();

		transaccion.executeWithoutResult(estado -> anonimizador.anonimizar(ana.id()));

		verify(archivos).borrar(ana.fotoClave());
		verify(archivos).borrar(ana.ticketClave());
		verify(archivos).borrar(ana.valuacionFotoClave());
		assertThat(archivos.leer(ana.fotoClave())).isEmpty();
		assertThat(archivos.leer(ana.ticketClave())).isEmpty();
		assertThat(archivos.leer(ana.valuacionFotoClave())).isEmpty();
	}

	@Test
	void anonimizarUnClienteQueNoExisteDa404() {
		assertThatThrownBy(() -> directorioClientes.anonimizar(999_999L))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	private ResultActions cancelar(Cliente cliente, String contrasena) throws Exception {
		return mockMvc.perform(post("/api/v1/app/privacidad/arco/cancelacion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"contrasena\":\"" + contrasena + "\"}")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()));
	}

	private int contar(String tabla, String columna, long id) {
		return jdbc.queryForObject("select count(*) from " + tabla + " where " + columna + " = ?", Integer.class, id);
	}

	/**
	 * Cuántas filas tiene un cliente en cada tabla que la cancelación toca o
	 * respeta.
	 */
	private Map<String, Long> conteos(long cliente) {
		String deSusVehiculos = "(select id from vehiculo_cliente where cliente_id = " + cliente + ")";
		String deSuCuenta = "(select id from cuenta_app where cliente_id = " + cliente + ")";
		return Map.ofEntries(Map.entry("cliente", n("select count(*) from cliente where id = " + cliente)),
				Map.entry("cuenta_app", n("select count(*) from cuenta_app where cliente_id = " + cliente)),
				Map.entry("vehiculos", n("select count(*) from vehiculo_cliente where cliente_id = " + cliente)),
				Map.entry("direcciones", n("select count(*) from direccion_cliente where cliente_id = " + cliente)),
				Map.entry("citas", n("select count(*) from cita where cliente_id = " + cliente)),
				Map.entry("asistencias", n("select count(*) from solicitud_asistencia where cliente_id = " + cliente)),
				Map.entry("consultas", n("select count(*) from consulta_asistente where cliente_id = " + cliente)),
				Map.entry("estimaciones",
						n("select count(*) from estimacion_toma_cuenta where cliente_id = " + cliente)),
				Map.entry("valuaciones", n("select count(*) from valuacion where cliente_id = " + cliente)),
				Map.entry("estrenos", n("select count(*) from solicitud_estreno where cliente_id = " + cliente)),
				Map.entry("lecturas",
						n("select count(*) from lectura_km where vehiculo_cliente_id in " + deSusVehiculos)),
				Map.entry("gastos",
						n("select count(*) from gasto_vehiculo where vehiculo_cliente_id in " + deSusVehiculos)),
				Map.entry("ordenes",
						n("select count(*) from orden_servicio where vehiculo_cliente_id in " + deSusVehiculos)),
				Map.entry("sesiones", n("select count(*) from sesion_app where cuenta_id in " + deSuCuenta)),
				Map.entry("tokens", n("select count(*) from token_recuperacion where cuenta_app_id in " + deSuCuenta)),
				Map.entry("aceptaciones",
						n("select count(*) from aceptacion_aviso where cuenta_app_id in " + deSuCuenta)),
				Map.entry("solicitudesArco", n("select count(*) from solicitud_arco where cliente_id = " + cliente)),
				Map.entry("archivosArco", n("""
						select count(*) from archivo_arco
						where solicitud_arco_id in (select id from solicitud_arco where cliente_id = %d)
						""".formatted(cliente))));
	}

	private long n(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}
}
