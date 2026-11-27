package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.nio.charset.StandardCharsets;
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
 * Billeteras simuladas y facturas simuladas de punta a punta (V2-M08.03 y
 * V2-M08.06): pagar con Apple Pay o Google Pay, la factura que se timbra sola,
 * facturar después, un solo timbrado por pago, el rechazo del timbrado, la
 * descarga del PDF y del XML y que el ERP siga viendo solo sus facturas. No es
 * transaccional porque la factura automática llega por un evento asíncrono que
 * necesita su commit; cada prueba usa clientes y vehículos propios.
 */
@AutoConfigureMockMvc
class FacturasAppIntegrationTest extends AbstractIntegrationTest {

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

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String unico;
	private Long cliente;
	private String token;
	private String tokenDeOtro;
	private String tokenDeServicio;

	@BeforeEach
	void clientesYPersonal() {
		unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		cliente = registrar("Fabiola Factura");
		token = tokenDe(cliente);
		tokenDeOtro = tokenDe(registrar("Otro Cliente"));
		tokenDeServicio = personal("servicio-facturas-" + unico, "SERVICIO", Role.SERVICIO);
	}

	@Test
	void conUnaBilleteraSimuladaQuedaPagadaComoCredito() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "900.00");

		pagar(orden, "billetera-" + unico, "{\"billetera\":\"APPLE_PAY\"}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PAGADO")).andExpect(jsonPath("$.medio").value("APPLE_PAY"))
				.andExpect(jsonPath("$.tipoTarjeta").value("CREDITO")).andExpect(jsonPath("$.marca").isEmpty())
				.andExpect(jsonPath("$.ultimos4").isEmpty()).andExpect(jsonPath("$.requiereAutenticacion").value(false))
				.andExpect(jsonPath("$.facturable").value(true)).andExpect(jsonPath("$.factura").isEmpty());

		Long otra = ordenLista(vehiculo(cliente), "900.00");
		pagar(otra, "tarjeta-y-billetera-" + unico,
				"{\"billetera\":\"GOOGLE_PAY\",\"tarjeta\":{\"token\":\"tok_sim_visa\",\"mesVence\":12,\"anioVence\":"
						+ anioQueViene() + "}}")
				.andExpect(status().isBadRequest());
		pagar(otra, "caja-como-billetera-" + unico, "{\"billetera\":\"CAJA\"}").andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("select count(*) from metodo_pago where cliente_id = ?", Integer.class, cliente))
				.isZero();
	}

	@Test
	void conDatosFiscalesAlPagarLaFacturaSeTimbraSolaYSeDescarga() throws Exception {
		Long perfil = perfil(token, "GODE561231GR8", "Dolores Gómez", "612", "37000");
		Long orden = ordenLista(vehiculo(cliente), "1650.00");
		Long pago = id(pagar(orden, "con-factura-" + unico, tarjetaCon("tok_sim_visa_debito", perfil))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.perfilFiscalId").value(perfil)));

		Long factura = esperar("select id from factura where pago_app_id = ?", pago);
		assertThat(factura).isNotNull();
		con(token, get(APP + "/pagos/{id}", pago)).andExpect(status().isOk())
				.andExpect(jsonPath("$.facturable").value(false)).andExpect(jsonPath("$.factura.id").value(factura))
				.andExpect(jsonPath("$.factura.pagoId").value(pago))
				.andExpect(jsonPath("$.factura.folio").value(startsWith("A-")))
				.andExpect(jsonPath("$.factura.rfcReceptor").value("GODE561231GR8"))
				.andExpect(jsonPath("$.factura.razonSocialReceptor").value("DOLORES GÓMEZ"))
				.andExpect(jsonPath("$.factura.total").value(1650.0))
				.andExpect(jsonPath("$.factura.simulada").value(true))
				.andExpect(jsonPath("$.factura.uuid").isNotEmpty());
		assertThat(jdbc.queryForObject("select tipo from factura where id = ?", String.class, factura))
				.isEqualTo("SERVICIO");
		assertThat(jdbc.queryForObject("select forma_pago from factura where id = ?", String.class, factura))
				.isEqualTo("28");

		byte[] pdf = con(token, get(APP + "/facturas/{id}/pdf", factura)).andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("factura-A-"))).andReturn()
				.getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		String xml = con(token, get(APP + "/facturas/{id}/xml", factura)).andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/xml")).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
		assertThat(xml).contains("cfdi:Comprobante", "Version=\"4.0\"", "Total=\"1650.00\"", "FormaPago=\"28\"",
				"Rfc=\"GODE561231GR8\"", "tfd:TimbreFiscalDigital", "Factura simulada, sin validez fiscal");

		con(tokenDeOtro, get(APP + "/facturas/{id}/pdf", factura)).andExpect(status().isNotFound());
		con(tokenDeOtro, get(APP + "/facturas/{id}/xml", factura)).andExpect(status().isNotFound());
	}

	@Test
	void unPagoSinDatosFiscalesSeFacturaDespuesUnaSolaVez() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "500.00");
		Long pago = id(
				pagar(orden, "sin-factura-" + unico, "{\"billetera\":\"GOOGLE_PAY\"}").andExpect(status().isCreated()));
		Long perfil = perfil(token, "GODE561231GR8", "Dolores Gómez", "612", "37000");
		Long perfilDeOtro = perfil(tokenDeOtro, "EKU9003173C9", "Escuela Kemper Urgate", "601", "42501");

		facturar(token, pago, perfilDeOtro).andExpect(status().isNotFound());
		facturar(tokenDeOtro, pago, perfilDeOtro).andExpect(status().isNotFound());
		con(token, post(APP + "/pagos/{id}/factura", pago).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());

		facturar(token, pago, perfil).andExpect(status().isCreated())
				.andExpect(jsonPath("$.folio").value(startsWith("A-"))).andExpect(jsonPath("$.total").value(500.0))
				.andExpect(jsonPath("$.simulada").value(true));
		facturar(token, pago, perfil).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("YA_FACTURADO"));
		con(token, get(APP + "/pagos/{id}", pago)).andExpect(jsonPath("$.facturable").value(false))
				.andExpect(jsonPath("$.perfilFiscalId").value(perfil))
				.andExpect(jsonPath("$.factura.folio").value(startsWith("A-")));
		assertThat(jdbc.queryForObject("select forma_pago from factura where pago_app_id = ?", String.class, pago))
				.isEqualTo("04");
	}

	@Test
	void elTimbradoRechazaLaRazonSocialConRegimenSocietario() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "500.00");
		Long pago = id(pagar(orden, "rechazo-" + unico, tarjetaCon("tok_sim_visa", null)));
		Long perfil = perfil(token, "EKU9003173C9", "Escuela Kemper Urgate SA de CV", "601", "42501");

		facturar(token, pago, perfil).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("TIMBRADO_RECHAZADO"))
				.andExpect(jsonPath("$.campos.razonSocial").isNotEmpty());
		con(token, get(APP + "/pagos/{id}", pago)).andExpect(jsonPath("$.facturable").value(true))
				.andExpect(jsonPath("$.factura").isEmpty());
	}

	@Test
	void unPagoDeCajaNoSeFacturaEnLaAppYElErpSoloVeLasSuyas() throws Exception {
		Long orden = ordenLista(vehiculo(cliente), "700.00");
		String tokenDeCaja = personal("caja-facturas-" + unico, "CAJA", Role.CAJA);
		Long pago = id(con(tokenDeCaja, post("/api/v1/caja/ordenes/{id}/pagada-en-caja", orden)
				.contentType(MediaType.APPLICATION_JSON).content("{\"referencia\":\"CAJA-" + unico + "\"}"))
				.andExpect(status().isCreated()));
		Long perfil = perfil(token, "GODE561231GR8", "Dolores Gómez", "612", "37000");

		facturar(token, pago, perfil).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PAGO_NO_FACTURABLE"));

		Long otraOrden = ordenLista(vehiculo(cliente), "300.00");
		Long otroPago = id(pagar(otraOrden, "erp-" + unico, "{\"billetera\":\"APPLE_PAY\"}"));
		facturar(token, otroPago, perfil).andExpect(status().isCreated());
		String tokenDeFacturacion = personal("facturacion-" + unico, "FACTURACION", Role.FACTURACION);
		String emitidas = con(tokenDeFacturacion, get("/api/v1/facturas")).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);
		List<String> folios = JsonPath.read(emitidas, "$[*].folio");
		assertThat(folios).noneMatch(folio -> folio.startsWith("A-"));
	}

	private Long registrar(String nombre) {
		String correo = "facturas-" + UUID.randomUUID().toString().substring(0, 8) + "@correo.com";
		Long id = registroClientesService.registrar(new NuevoCliente(nombre, correo, null, null)).id();
		cuentasAppService.abrir(id, "Temporal123");
		return id;
	}

	private String tokenDe(Long clienteId) {
		String correo = jdbc.queryForObject("select correo from cliente where id = ?", String.class, clienteId);
		return jwtService.generateToken(clienteId, correo, List.of(Role.CLIENTE_CARECAR));
	}

	private String personal(String usuario, String rol, Role permiso) {
		Usuario guardado = usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + rol,
				rolRepository.findByNombre(rol).orElseThrow()));
		return jwtService.generateToken(guardado.getId(), usuario, List.of(permiso));
	}

	private Long vehiculo(Long clienteId) {
		String vin = String.format("3N1FAC%08d%03d", ThreadLocalRandom.current().nextInt(100_000_000),
				ThreadLocalRandom.current().nextInt(1000));
		return registroClientesService
				.asignarVehiculo(clienteId, new VehiculoNuevo(null, vin, "Nissan", "March", 2026, null)).vehiculos()
				.stream().filter(v -> v.vin().equals(vin)).findFirst().orElseThrow().id();
	}

	/** Una orden del taller con un renglón, lista para entrega. */
	private Long ordenLista(Long vehiculoId, String precio) throws Exception {
		Long orden = id(con(tokenDeServicio,
				post("/api/v1/taller/ordenes").contentType(MediaType.APPLICATION_JSON)
						.content("{\"vehiculoId\":" + vehiculoId + ",\"kilometraje\":15200}"))
				.andExpect(status().isCreated()));
		con(tokenDeServicio,
				post("/api/v1/taller/ordenes/{id}/renglones", orden).contentType(MediaType.APPLICATION_JSON)
						.content("{\"tipo\":\"MANO_DE_OBRA\",\"concepto\":\"Afinación\",\"cantidad\":1,"
								+ "\"precioUnitario\":" + precio + "}"))
				.andExpect(status().isCreated());
		con(tokenDeServicio, post("/api/v1/taller/ordenes/{id}/etapas", orden).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"LISTO_PARA_ENTREGA\"}")).andExpect(status().isOk());
		return orden;
	}

	private Long perfil(String jwt, String rfc, String razonSocial, String regimen, String codigoPostal)
			throws Exception {
		return id(con(jwt, post(APP + "/perfiles-fiscales").contentType(MediaType.APPLICATION_JSON).content("""
				{"rfc":"%s","razonSocial":"%s","regimenFiscal":"%s","codigoPostal":"%s","usoCfdi":"G03",\
				"correo":null,"predeterminado":null}""".formatted(rfc, razonSocial, regimen, codigoPostal)))
				.andExpect(status().isCreated()));
	}

	private static int anioQueViene() {
		return LocalDate.now(ZONA).getYear() + 1;
	}

	private static String tarjetaCon(String tokenDePrueba, Long perfilFiscalId) {
		return "{\"tarjeta\":{\"token\":\"" + tokenDePrueba + "\",\"mesVence\":12,\"anioVence\":" + anioQueViene()
				+ "},\"perfilFiscalId\":" + perfilFiscalId + "}";
	}

	private ResultActions pagar(Long ordenId, String llave, String cuerpo) throws Exception {
		return con(token, post(APP + "/ordenes/{id}/pagos", ordenId).header("Idempotency-Key", llave)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions facturar(String jwt, Long pagoId, Long perfilFiscalId) throws Exception {
		return con(jwt, post(APP + "/pagos/{id}/factura", pagoId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"perfilFiscalId\":" + perfilFiscalId + "}"));
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
