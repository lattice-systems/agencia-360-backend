package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba ERP-1.1 de punta a punta: Caja registra y envía los pagos de un
 * prospecto en Pago; Contabilidad los valida (el prospecto pasa a Facturación)
 * o los rechaza con motivo. Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class LiquidacionesIntegrationTest extends AbstractIntegrationTest {

	private static final String PROSPECTOS = "/api/v1/prospectos";
	private static final String LIQUIDACIONES = "/api/v1/liquidaciones";

	/**
	 * El backend valida la fecha del pago con el día de la agencia, no con el de
	 * UTC. Si la prueba usara LocalDate.now(), de 6 p. m. a 12 a. m. en León el
	 * pago parecería del día siguiente y se rechazaría por "fecha futura".
	 */
	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private Usuario vendedor;
	private Usuario cajera;
	private Usuario contador;

	@BeforeEach
	void usuarios() {
		vendedor = registrar("vendedor1", Role.VENTAS);
		cajera = registrar("caja1", Role.CAJA);
		contador = registrar("contabilidad1", Role.CONTABILIDAD);
	}

	@Test
	void cajaVeLosProspectosEnPagoConSuVendedorAunqueNoTenganPagos() throws Exception {
		long id = prospectoEnPago("Ana López");
		crearProspecto("Luis Pérez", "4771234568");

		mockMvc.perform(conToken(get(LIQUIDACIONES), token(cajera))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].prospectoId").value(id))
				.andExpect(jsonPath("$[0].prospectoNombre").value("Ana López"))
				.andExpect(jsonPath("$[0].vendedorNombre").value("Persona vendedor1"))
				.andExpect(jsonPath("$[0].estado").value("ABIERTA")).andExpect(jsonPath("$[0].total").value(0))
				.andExpect(jsonPath("$[0].pagos.length()").value(0));
	}

	@Test
	void cajaRegistraPagosYLosVeConElTotal() throws Exception {
		long id = prospectoEnPago("Ana López");

		mockMvc.perform(pago(id, "150000.00", hoyEnLaAgencia().toString(), "TRF-001")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.total").value(150000.00))
				.andExpect(jsonPath("$.pagos[0].monto").value(150000.00))
				.andExpect(jsonPath("$.pagos[0].referencia").value("TRF-001"))
				.andExpect(jsonPath("$.pagos[0].registradoPorNombre").value("Persona caja1"));
		mockMvc.perform(pago(id, "339900.50", hoyEnLaAgencia().toString(), null)).andExpect(status().isCreated());

		mockMvc.perform(conToken(get(LIQUIDACIONES + "/{id}", id), token(cajera))).andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(489900.50)).andExpect(jsonPath("$.pagos.length()").value(2));
	}

	@Test
	void rechazaUnMontoInvalidoOUnaFechaFutura() throws Exception {
		long id = prospectoEnPago("Ana López");

		mockMvc.perform(pago(id, "0", hoyEnLaAgencia().toString(), null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El monto debe ser mayor a cero"));
		mockMvc.perform(pago(id, "100", hoyEnLaAgencia().plusDays(1).toString(), null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La fecha del pago no puede ser futura"));
	}

	@Test
	void soloSeRegistranPagosDeUnProspectoEnPago() throws Exception {
		long id = crearProspecto("Luis Pérez", "4771234568");

		mockMvc.perform(pago(id, "100", hoyEnLaAgencia().toString(), null)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Solo se registran pagos de un prospecto en Pago"));
	}

	@Test
	void cajaAnulaUnPagoMalCapturadoYQuedaRegistrado() throws Exception {
		long id = prospectoEnPago("Ana López");
		String respuesta = mockMvc.perform(pago(id, "100", hoyEnLaAgencia().toString(), null)).andReturn().getResponse()
				.getContentAsString();
		long pagoId = ((Number) JsonPath.read(respuesta, "$.pagos[0].id")).longValue();

		mockMvc.perform(conToken(post(LIQUIDACIONES + "/{id}/pagos/{pago}/anular", id, pagoId), token(cajera)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pagos.length()").value(1))
				.andExpect(jsonPath("$.pagos[0].anulado").value(true))
				.andExpect(jsonPath("$.pagos[0].anuladoPorNombre").value("Persona caja1"))
				.andExpect(jsonPath("$.total").value(0));
		assertThat(jdbcTemplate.queryForObject("select count(*) from pago where id = ?", Integer.class, pagoId))
				.isEqualTo(1);
	}

	@Test
	void contabilidadValidaYElProspectoPasaAFacturacion() throws Exception {
		long id = prospectoEnPago("Ana López");
		mockMvc.perform(pago(id, "489900", hoyEnLaAgencia().toString(), null)).andExpect(status().isCreated());
		mockMvc.perform(conToken(post(LIQUIDACIONES + "/{id}/enviar", id), token(cajera))).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ENVIADA"));

		mockMvc.perform(conToken(post(LIQUIDACIONES + "/{id}/validar", id), token(contador))).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("VALIDADA"));
		entityManager.flush();

		assertThat(jdbcTemplate.queryForObject("select etapa from prospecto where id = ?", String.class, id))
				.isEqualTo("FACTURACION");
		assertThat(jdbcTemplate.queryForList(
				"select origen from historial_etapa where prospecto_id = ? order by fecha_hora, id", String.class, id))
				.last().isEqualTo("ERP");
		// Ya no está en Pago: sale de la lista de Caja y Contabilidad.
		mockMvc.perform(conToken(get(LIQUIDACIONES), token(contador))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void contabilidadRechazaConMotivoYCajaVuelveAEnviar() throws Exception {
		long id = prospectoEnPago("Ana López");
		mockMvc.perform(pago(id, "1000", hoyEnLaAgencia().toString(), null)).andExpect(status().isCreated());
		mockMvc.perform(conToken(post(LIQUIDACIONES + "/{id}/enviar", id), token(cajera))).andExpect(status().isOk());

		mockMvc.perform(conToken(post(LIQUIDACIONES + "/{id}/rechazar", id), token(contador))
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Falta el comprobante\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"))
				.andExpect(jsonPath("$.motivoRechazo").value("Falta el comprobante"));

		mockMvc.perform(pago(id, "500", hoyEnLaAgencia().toString(), null)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("ABIERTA"));
		mockMvc.perform(conToken(post(LIQUIDACIONES + "/{id}/enviar", id), token(cajera))).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ENVIADA"));
	}

	@Test
	void cadaRolHaceSoloLoSuyo() throws Exception {
		long id = prospectoEnPago("Ana López");

		mockMvc.perform(conToken(post(LIQUIDACIONES + "/{id}/pagos", id), token(contador))
				.contentType(MediaType.APPLICATION_JSON).content("{\"monto\":100,\"fechaPago\":\"2026-10-16\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(conToken(post(LIQUIDACIONES + "/{id}/validar", id), token(cajera)))
				.andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(LIQUIDACIONES), token(vendedor))).andExpect(status().isForbidden());
	}

	@Test
	void unProspectoQueNoExisteResponde404() throws Exception {
		mockMvc.perform(conToken(get(LIQUIDACIONES + "/{id}", 999_999), token(cajera)))
				.andExpect(status().isNotFound());
	}

	private static LocalDate hoyEnLaAgencia() {
		return LocalDate.now(ZONA_AGENCIA);
	}

	private MockHttpServletRequestBuilder pago(long prospectoId, String monto, String fecha, String referencia) {
		String cuerpo = "{\"monto\":" + monto + ",\"fechaPago\":\"" + fecha + "\""
				+ (referencia == null ? "" : ",\"referencia\":\"" + referencia + "\"") + "}";
		return conToken(post(LIQUIDACIONES + "/{id}/pagos", prospectoId), token(cajera))
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private long prospectoEnPago(String nombre) throws Exception {
		long id = crearProspecto(nombre, "4771234567");
		mockMvc.perform(conToken(patch(PROSPECTOS + "/{id}/etapa", id), token(vendedor))
				.contentType(MediaType.APPLICATION_JSON).content("{\"etapa\":\"PAGO\"}")).andExpect(status().isOk());
		return id;
	}

	private long crearProspecto(String nombre, String telefono) throws Exception {
		String respuesta = mockMvc
				.perform(conToken(post(PROSPECTOS), token(vendedor)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"" + nombre + "\",\"telefono\":\"" + telefono + "\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
