package dev.latticesystems.erp_agente360.backend_agencia360.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.FacturaEmitida;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.DatosFiscales;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.ProspectoParaFacturar;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emisión de la factura (ERP-1.3) de punta a punta contra la base: la unidad
 * pasa a Vendido, se registra la venta, se guarda el PDF y se publica
 * FacturaEmitida. Los datos del prospecto vienen de {@link ConsultaFacturacion}
 * de ventascrm, que se simula porque los datos fiscales llegan con la V17 de
 * Coper. Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class FacturasIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/facturas";
	private static final DatosFiscales RECEPTOR = new DatosFiscales("HELL800101AB1", "Luis Herrera López",
			"Calle Hidalgo 10, Col. Centro, C.P. 37000, León, Gto.",
			"612 - Personas Físicas con Actividades " + "Empresariales");

	@MockitoBean
	private ConsultaFacturacion consultaFacturacion;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private ProspectoRepository prospectoRepository;

	@Autowired
	private VehiculoRepository vehiculoRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private ApplicationEvents eventos;

	private Usuario facturacion;
	private Usuario vendedor;
	private long luis;
	private long sentra;

	@BeforeEach
	void setUp() {
		facturacion = registrar("facturacion.facturas", "FACTURACION");
		vendedor = registrar("vendedor.facturas", "VENTAS");
		luis = prospecto("Luis Herrera", "4775550104");
		sentra = unidad("3N1AB8CV5PY123456", null);
		when(consultaFacturacion.paraFacturar(anyLong()))
				.thenAnswer(llamada -> Optional.of(listo(llamada.getArgument(0))));
	}

	@Test
	void emiteLaFacturaVendeLaUnidadYPublicaElEvento() throws Exception {
		String respuesta = mockMvc.perform(emitir(token(facturacion), luis, sentra)).andExpect(status().isCreated())
				.andExpect(header().exists(HttpHeaders.LOCATION)).andExpect(jsonPath("$.prospectoId").value(luis))
				.andExpect(jsonPath("$.folio").value(matchesPattern("F-\\d{6}")))
				.andExpect(jsonPath("$.cliente").value("Luis Herrera López"))
				.andExpect(jsonPath("$.rfc").value("HELL800101AB1")).andExpect(jsonPath("$.vehiculoId").value(sentra))
				.andExpect(jsonPath("$.vin").value("3N1AB8CV5PY123456")).andExpect(jsonPath("$.marca").value("Nissan"))
				.andExpect(jsonPath("$.modelo").value("Sentra")).andExpect(jsonPath("$.version").value("Advance"))
				.andExpect(jsonPath("$.anio").value(2026)).andExpect(jsonPath("$.subtotal").value(422327.59))
				.andExpect(jsonPath("$.iva").value(67572.41)).andExpect(jsonPath("$.total").value(489900.00))
				.andExpect(jsonPath("$.emitidaEn").isNotEmpty()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
		String folio = JsonPath.read(respuesta, "$.folio");

		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("select estatus from vehiculo where id = ?", String.class, sentra))
				.isEqualTo("VENDIDO");
		assertThat(jdbcTemplate.queryForObject("select estatus from venta where prospecto_id = ?", String.class, luis))
				.isEqualTo("REGISTRADA");
		assertThat(eventos.stream(FacturaEmitida.class)).singleElement().satisfies(evento -> {
			assertThat(evento.prospectoId()).isEqualTo(luis);
			assertThat(evento.vehiculoId()).isEqualTo(sentra);
			assertThat(evento.folio()).isEqualTo(folio);
			assertThat(evento.emitidaEn()).isNotNull();
		});

		mockMvc.perform(conToken(get(URL), token(facturacion))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].folio").value(folio));
		byte[] pdf = mockMvc.perform(conToken(get(URL + "/{id}/pdf", id), token(facturacion)))
				.andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"factura-" + folio + ".pdf\""))
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
	}

	@Test
	void porEmitirSoloMuestraLosQueNoTienenFacturaYPorQueNoSePuede() throws Exception {
		long carlos = prospecto("Carlos Núñez", "4775550109");
		long ana = prospecto("Ana López", "4775550101");
		when(consultaFacturacion.enFacturacion())
				.thenReturn(List.of(listo(luis), conSaldo(carlos, new BigDecimal("89900.00")), sinDatosFiscales(ana)));
		mockMvc.perform(emitir(token(facturacion), luis, sentra)).andExpect(status().isCreated());

		mockMvc.perform(conToken(get(URL + "/por-emitir"), token(facturacion))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].prospectoId").value(carlos))
				.andExpect(jsonPath("$[0].saldo").value(89900.00))
				.andExpect(jsonPath("$[0].bloqueo")
						.value("Tiene un saldo de $89,900.00: la factura se emite con el " + "saldo en 0"))
				.andExpect(jsonPath("$[1].prospectoId").value(ana)).andExpect(jsonPath("$[1].receptor").isEmpty())
				.andExpect(jsonPath("$[1].bloqueo").value(startsWith("Faltan los datos fiscales")));
	}

	@Test
	void unProspectoListoParaFacturarNoTieneBloqueo() throws Exception {
		when(consultaFacturacion.enFacturacion()).thenReturn(List.of(listo(luis)));

		mockMvc.perform(conToken(get(URL + "/por-emitir"), token(facturacion))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].receptor.rfc").value("HELL800101AB1"))
				.andExpect(jsonPath("$[0].vendedor").value("Persona vendedor.facturas"))
				.andExpect(jsonPath("$[0].bloqueo").isEmpty());
	}

	@Test
	void noSeEmiteConSaldoPendiente() throws Exception {
		when(consultaFacturacion.paraFacturar(luis)).thenReturn(Optional.of(conSaldo(luis, new BigDecimal("1.00"))));

		mockMvc.perform(emitir(token(facturacion), luis, sentra)).andExpect(status().isConflict()).andExpect(
				jsonPath("$.message").value("Tiene un saldo de $1.00: la factura se emite con el saldo en 0"));
		sinVenta();
	}

	@Test
	void noSeEmiteSinLosDatosFiscalesDelCliente() throws Exception {
		when(consultaFacturacion.paraFacturar(luis)).thenReturn(Optional.of(sinDatosFiscales(luis)));

		mockMvc.perform(emitir(token(facturacion), luis, sentra)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(startsWith("Faltan los datos fiscales")));
		sinVenta();
	}

	@Test
	void soloSeFacturaUnProspectoEnFacturacion() throws Exception {
		when(consultaFacturacion.paraFacturar(luis)).thenReturn(Optional.empty());

		mockMvc.perform(emitir(token(facturacion), luis, sentra)).andExpect(status().isConflict()).andExpect(
				jsonPath("$.message").value("Solo se factura un prospecto que está en la etapa Facturación"));
		sinVenta();
	}

	@Test
	void unProspectoNoSeFacturaDosVeces() throws Exception {
		long kicks = unidad("3N1CP5CV6RL123458", null);
		String folio = JsonPath.read(mockMvc.perform(emitir(token(facturacion), luis, sentra))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.folio");

		mockMvc.perform(emitir(token(facturacion), luis, kicks)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("El prospecto ya tiene la factura " + folio));
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("select estatus from vehiculo where id = ?", String.class, kicks))
				.isEqualTo("DISPONIBLE");
	}

	@Test
	void soloSeVendeUnaUnidadDisponible() throws Exception {
		long danada = unidad("3N6AD33A2RK123459", EstatusVehiculo.NO_DISPONIBLE);
		long carlos = prospecto("Carlos Núñez", "4775550109");
		mockMvc.perform(emitir(token(facturacion), carlos, sentra)).andExpect(status().isCreated());

		mockMvc.perform(emitir(token(facturacion), luis, sentra)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("La unidad 3N1AB8CV5PY123456 ya no está disponible"));
		mockMvc.perform(emitir(token(facturacion), luis, danada)).andExpect(status().isConflict());
		assertThat(
				jdbcTemplate.queryForObject("select count(*) from factura where prospecto_id = ?", Integer.class, luis))
				.isZero();
	}

	@Test
	void respondeNoEncontradoSiNoExisteElProspectoOLaUnidad() throws Exception {
		mockMvc.perform(emitir(token(facturacion), 999_999, sentra)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe el prospecto 999999"));
		mockMvc.perform(emitir(token(facturacion), luis, 999_999)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe el vehículo 999999"));
		mockMvc.perform(conToken(get(URL + "/{id}/pdf", 999_999), token(facturacion))).andExpect(status().isNotFound());
	}

	@Test
	void pideElProspectoYLaUnidad() throws Exception {
		mockMvc.perform(conToken(post(URL), token(facturacion)).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void soloFacturacionEmiteYElAdministradorConsulta() throws Exception {
		Usuario admin = registrar("admin.facturas", "ADMINISTRADOR");
		Usuario contabilidad = registrar("contabilidad.facturas", "CONTABILIDAD");

		mockMvc.perform(emitir(token(admin), luis, sentra)).andExpect(status().isForbidden());
		mockMvc.perform(emitir(token(vendedor), luis, sentra)).andExpect(status().isForbidden());
		mockMvc.perform(emitir(token(contabilidad), luis, sentra)).andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(URL), token(admin))).andExpect(status().isOk());
		mockMvc.perform(conToken(get(URL), token(vendedor))).andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(URL + "/por-emitir"), token(admin))).andExpect(status().isForbidden());
		sinVenta();
	}

	private void sinVenta() {
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("select estatus from vehiculo where id = ?", String.class, sentra))
				.isEqualTo("DISPONIBLE");
		assertThat(jdbcTemplate.queryForObject("select count(*) from venta", Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from factura", Integer.class)).isZero();
	}

	private ProspectoParaFacturar listo(long prospectoId) {
		return new ProspectoParaFacturar(prospectoId, "Luis Herrera", vendedor.getId(), "Persona vendedor.facturas",
				new BigDecimal("489900.00"), new BigDecimal("0.00"), Instant.now(), RECEPTOR);
	}

	private ProspectoParaFacturar conSaldo(long prospectoId, BigDecimal saldo) {
		return new ProspectoParaFacturar(prospectoId, "Carlos Núñez", vendedor.getId(), "Persona vendedor.facturas",
				new BigDecimal("489900.00"), saldo, Instant.now(), RECEPTOR);
	}

	private ProspectoParaFacturar sinDatosFiscales(long prospectoId) {
		return new ProspectoParaFacturar(prospectoId, "Ana López", vendedor.getId(), "Persona vendedor.facturas",
				new BigDecimal("329900.00"), BigDecimal.ZERO, Instant.now(), null);
	}

	private long prospecto(String nombre, String telefono) {
		Prospecto prospecto = prospectoRepository
				.saveAndFlush(Prospecto.registrar(nombre, telefono, null, vendedor.getId(), Instant.now()));
		return prospecto.getId();
	}

	private long unidad(String vin, EstatusVehiculo estatus) {
		return vehiculoRepository.saveAndFlush(Vehiculo.registrar(vin, vin.startsWith("3N1CP") ? "Kicks" : "Sentra",
				"Advance", 2026, "Blanco", new BigDecimal("489900.00"), estatus)).getId();
	}

	private static MockHttpServletRequestBuilder emitir(String token, long prospectoId, long vehiculoId) {
		return conToken(post(URL), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"prospectoId\":%d,\"vehiculoId\":%d}".formatted(prospectoId, vehiculoId));
	}

	private Usuario registrar(String usuario, String rol) {
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario,
				rolRepository.findByNombre(rol).orElseThrow()));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
