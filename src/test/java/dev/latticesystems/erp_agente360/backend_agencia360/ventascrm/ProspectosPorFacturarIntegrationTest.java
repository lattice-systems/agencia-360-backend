package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

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
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.ProspectoParaFacturar;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
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
 * Lista de trabajo de Facturación (ERP-1.0) y la consulta que usa facturacion
 * para emitir (ERP-1.3): los prospectos que ya pasaron Pago y esperan su
 * factura.
 */
@AutoConfigureMockMvc
@Transactional
class ProspectosPorFacturarIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/prospectos/por-facturar";

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

	@Autowired
	private ConsultaFacturacion consultaFacturacion;

	@Test
	void facturacionVeSoloLosProspectosEnFacturacionConSuVendedor() throws Exception {
		Usuario vendedor = registrar("vendedor.facturar", Role.VENTAS);
		long luis = prospecto(vendedor, "Luis Herrera", "4775550104", "FACTURACION");
		prospecto(vendedor, "Ana López", "4775550101", "PAGO");
		prospecto(vendedor, "Valeria Díaz", "4775550105", "ENTREGA");

		mockMvc.perform(conToken(get(URL), token(registrar("facturacion.facturar", Role.FACTURACION))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(luis)).andExpect(jsonPath("$[0].nombre").value("Luis Herrera"))
				.andExpect(jsonPath("$[0].telefono").value("4775550104"))
				.andExpect(jsonPath("$[0].vendedorId").value(vendedor.getId()))
				.andExpect(jsonPath("$[0].vendedor").value("Persona vendedor.facturar"))
				.andExpect(jsonPath("$[0].enFacturacionDesde").isNotEmpty());
	}

	@Test
	void soloFacturacionVeLaLista() throws Exception {
		mockMvc.perform(conToken(get(URL), token(registrar("vendedor.facturar", Role.VENTAS))))
				.andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(URL), token(registrar("inventario.facturar", Role.INVENTARIO))))
				.andExpect(status().isForbidden());
	}

	@Test
	void laConsultaDeFacturacionCalculaElSaldoSinLosPagosAnulados() throws Exception {
		Usuario vendedor = registrar("vendedor.saldo", Role.VENTAS);
		Usuario caja = registrar("caja.saldo", Role.CAJA);
		long luis = prospecto(vendedor, "Luis Herrera", "4775550104", "FACTURACION");
		long ana = prospecto(vendedor, "Ana López", "4775550101", "FACTURACION");
		long sinPrecio = prospecto(vendedor, "Carlos Núñez", "4775550109", "FACTURACION");
		long enPago = prospecto(vendedor, "Valeria Díaz", "4775550105", "PAGO");
		conPagos(luis, "489900.00", caja, "400000.00", "89900.00");
		conPagos(ana, "329900.00", caja, "329900.00", null);

		List<ProspectoParaFacturar> lista = consultaFacturacion.enFacturacion();

		assertThat(lista).extracting(ProspectoParaFacturar::prospectoId).containsExactly(sinPrecio, ana, luis);
		ProspectoParaFacturar deLuis = consultaFacturacion.paraFacturar(luis).orElseThrow();
		assertThat(deLuis.nombre()).isEqualTo("Luis Herrera");
		assertThat(deLuis.vendedor()).isEqualTo("Persona vendedor.saldo");
		assertThat(deLuis.total()).isEqualByComparingTo("489900.00");
		assertThat(deLuis.saldo()).isEqualByComparingTo("89900.00");
		assertThat(deLuis.receptor()).as("los datos fiscales llegan con la V17").isNull();
		assertThat(consultaFacturacion.paraFacturar(ana).orElseThrow().saldo()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(consultaFacturacion.paraFacturar(sinPrecio).orElseThrow().saldo()).isNull();
		assertThat(consultaFacturacion.paraFacturar(enPago)).isEmpty();
		assertThat(consultaFacturacion.paraFacturar(999_999L)).isEmpty();
	}

	/**
	 * Precio total y pagos de la venta; el segundo pago, si lo hay, está anulado y
	 * no cuenta.
	 */
	private void conPagos(long prospecto, String precio, Usuario caja, String pagado, String anulado) {
		jdbcTemplate.update("update prospecto set precio_total = ? where id = ?", new BigDecimal(precio), prospecto);
		Long liquidacion = jdbcTemplate.queryForObject("""
				insert into liquidacion (prospecto_id, estado, created_at, updated_at)
				values (?, 'VALIDADA', now(), now()) returning id""", Long.class, prospecto);
		pago(liquidacion, pagado, caja, false);
		if (anulado != null) {
			pago(liquidacion, anulado, caja, true);
		}
	}

	private void pago(Long liquidacion, String monto, Usuario caja, boolean anulado) {
		jdbcTemplate.update("""
				insert into pago (liquidacion_id, monto, fecha_pago, registrado_por_id, anulado, created_at, updated_at)
				values (?, ?, current_date, ?, ?, now(), now())""", liquidacion, new BigDecimal(monto), caja.getId(),
				anulado);
	}

	private long prospecto(Usuario vendedor, String nombre, String telefono, String etapa) throws Exception {
		String respuesta = mockMvc
				.perform(conToken(post("/api/v1/prospectos"), token(vendedor)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","telefono":"%s","correo":null}
								""".formatted(nombre, telefono)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
		entityManager.flush();
		jdbcTemplate.update("update prospecto set etapa = ? where id = ?", etapa, id);
		entityManager.clear();
		return id;
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
