package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ModeloCatalogo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ParametroTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.EstimacionTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ModeloCatalogoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ParametroTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toma a cuenta de la app (V2-M03.04, V2-M03.06 y V2-M03.07) de punta a punta:
 * migración V34, seguridad, fórmula, historial y base real (Testcontainers).
 * Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class TomaCuentaIntegrationTest extends AbstractIntegrationTest {

	private static final String RUTA = "/api/v1/app/toma-a-cuenta/estimaciones";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ParametroTomaCuentaRepository parametroRepository;

	@Autowired
	private EstimacionTomaCuentaRepository estimacionRepository;

	@Autowired
	private ModeloCatalogoRepository catalogoRepository;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Long anaId;
	private String tokenDeAna;
	private String tokenDeBruno;
	private Long marchDeAnaId;
	private Long marchDeBrunoId;
	private ParametroTomaCuenta parametroMarch;

	@BeforeEach
	void prepararClientesYParametros() {
		anaId = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		marchDeAnaId = registroClientesService
				.asignarVehiculo(anaId, new VehiculoNuevo(null, "3N1CK3CD5RL000001", "Nissan", "March", 2020, "Sense"))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(anaId, "Temporal123");
		tokenDeAna = jwtService.generateToken(anaId, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));

		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		marchDeBrunoId = registroClientesService
				.asignarVehiculo(bruno, new VehiculoNuevo(null, "3N1CK3CD5RL000003", "Nissan", "March", 2020, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));

		parametroMarch = parametroRepository.save(ParametroTomaCuenta.nuevo("Nissan", "March", 2020,
				new BigDecimal("200000"), new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.10")));
	}

	private ResultActions estimar(String token, String cuerpo) throws Exception {
		return mockMvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions listar(String token, String consulta) throws Exception {
		return mockMvc.perform(get(RUTA + consulta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String conVehiculo(Long vehiculoId, int km, String estado) {
		return "{\"vehiculoId\":" + vehiculoId + ",\"km\":" + km + ",\"estado\":\"" + estado + "\"}";
	}

	private static String capturado(String marca, String modelo, int anio, int km, String estado) {
		return "{\"marca\":\"" + marca + "\",\"modelo\":\"" + modelo + "\",\"anio\":" + anio + ",\"km\":" + km
				+ ",\"estado\":\"" + estado + "\"}";
	}

	@Test
	void estimaConUnVehiculoDelClienteYLoGuarda() throws Exception {
		int anioActual = Year.now().getValue();

		estimar(tokenDeAna, conVehiculo(marchDeAnaId, 60_000, "BUENO")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.creadaEn").isString())
				.andExpect(jsonPath("$.vehiculoId").value(marchDeAnaId.intValue()))
				.andExpect(jsonPath("$.marca").value("Nissan")).andExpect(jsonPath("$.modelo").value("March"))
				.andExpect(jsonPath("$.anio").value(2020)).andExpect(jsonPath("$.km").value(60000))
				.andExpect(jsonPath("$.estado").value("BUENO")).andExpect(jsonPath("$.modeloDestino").isEmpty())
				.andExpect(jsonPath("$.precioDestino").isEmpty()).andExpect(jsonPath("$.minimo").isNumber())
				.andExpect(jsonPath("$.maximo").isNumber()).andExpect(jsonPath("$.supuestos.valorBase").value(200000))
				.andExpect(jsonPath("$.supuestos.edadAnios").value(anioActual - 2020))
				.andExpect(jsonPath("$.supuestos.kmEsperado").value((anioActual - 2020) * 15_000))
				.andExpect(jsonPath("$.supuestos.margen").value(0.08));

		assertThat(estimacionRepository.count()).isEqualTo(1);
	}

	@Test
	void estimaConUnAutoCapturadoSinDistinguirMayusculas() throws Exception {
		estimar(tokenDeAna, capturado("nissan", "MARCH", 2020, 30_000, "EXCELENTE")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.vehiculoId").isEmpty()).andExpect(jsonPath("$.marca").value("nissan"))
				.andExpect(jsonPath("$.estado").value("EXCELENTE"));
	}

	@Test
	void unMejorEstadoDaUnRangoMasAlto() throws Exception {
		String malo = estimar(tokenDeAna, capturado("Nissan", "March", 2020, 60_000, "MALO")).andReturn().getResponse()
				.getContentAsString();
		String excelente = estimar(tokenDeAna, capturado("Nissan", "March", 2020, 60_000, "EXCELENTE")).andReturn()
				.getResponse().getContentAsString();

		double minimoMalo = JsonPath.<Number>read(malo, "$.minimo").doubleValue();
		double minimoExcelente = JsonPath.<Number>read(excelente, "$.minimo").doubleValue();
		assertThat(minimoExcelente).isGreaterThan(minimoMalo);
		assertThat(JsonPath.<Number>read(malo, "$.minimo").doubleValue() % 100).isZero();
	}

	@Test
	void guardaElModeloDestinoConSuNombreYPrecio() throws Exception {
		ModeloCatalogo versa = catalogoRepository.save(ModeloCatalogo.nuevo("Nissan", "Versa", 2025, "Sense",
				new BigDecimal("329900"), "https://example.com/versa.png", null));
		String cuerpo = "{\"vehiculoId\":" + marchDeAnaId + ",\"km\":60000,\"estado\":\"BUENO\",\"modeloCatalogoId\":"
				+ versa.getId() + "}";

		estimar(tokenDeAna, cuerpo).andExpect(status().isCreated())
				.andExpect(jsonPath("$.modeloCatalogoId").value(versa.getId().intValue()))
				.andExpect(jsonPath("$.modeloDestino").value("Nissan Versa 2025"))
				.andExpect(jsonPath("$.precioDestino").value(329900));
	}

	@Test
	void unaEstimacionGuardadaNoCambiaAunqueCambieElParametro() throws Exception {
		String creada = estimar(tokenDeAna, conVehiculo(marchDeAnaId, 60_000, "BUENO")).andReturn().getResponse()
				.getContentAsString();
		double minimo = JsonPath.<Number>read(creada, "$.minimo").doubleValue();

		parametroMarch.desactivar();
		parametroRepository.save(ParametroTomaCuenta.nuevo("Nissan", "March", 2019, new BigDecimal("1"),
				new BigDecimal("0.1"), new BigDecimal("0.05"), new BigDecimal("0.1")));
		parametroRepository.flush();

		listar(tokenDeAna, "").andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].minimo").value(minimo))
				.andExpect(jsonPath("$.contenido[0].supuestos.valorBase").value(200000));
	}

	@Test
	void rechazaDatosInvalidosConDatosInvalidos() throws Exception {
		int anioSiguiente = Year.now().getValue() + 1;
		List<String> cuerpos = List.of(
				// las dos formas del auto
				"{\"vehiculoId\":" + marchDeAnaId + ",\"marca\":\"Nissan\",\"modelo\":\"March\",\"anio\":2020,"
						+ "\"km\":1000,\"estado\":\"BUENO\"}",
				// ninguna forma
				"{\"km\":1000,\"estado\":\"BUENO\"}",
				// auto capturado incompleto
				"{\"marca\":\"Nissan\",\"km\":1000,\"estado\":\"BUENO\"}",
				capturado("Nissan", "March", 2020, -1, "BUENO"), capturado("Nissan", "March", 2020, 1_000_000, "BUENO"),
				capturado("Nissan", "March", 1899, 1000, "BUENO"),
				capturado("Nissan", "March", anioSiguiente, 1000, "BUENO"),
				capturado("Nissan", "March", 2020, 1000, "PERFECTO"),
				// sin estado ni km
				"{\"marca\":\"Nissan\",\"modelo\":\"March\",\"anio\":2020,\"km\":1000}",
				"{\"marca\":\"Nissan\",\"modelo\":\"March\",\"anio\":2020,\"estado\":\"BUENO\"}");

		for (String cuerpo : cuerpos) {
			estimar(tokenDeAna, cuerpo).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		}
		assertThat(estimacionRepository.count()).isZero();
	}

	@Test
	void unVehiculoAjenoOInexistenteEsNoEncontrado() throws Exception {
		estimar(tokenDeAna, conVehiculo(marchDeBrunoId, 1000, "BUENO")).andExpect(status().isNotFound());
		estimar(tokenDeAna, conVehiculo(999_999L, 1000, "BUENO")).andExpect(status().isNotFound());
	}

	@Test
	void unModeloRetiradoOInexistenteEsNoEncontrado() throws Exception {
		ModeloCatalogo retirado = ModeloCatalogo.nuevo("Nissan", "Tsuru", 2017, null, null, null, null);
		retirado.retirar();
		catalogoRepository.save(retirado);

		String conModelo = "{\"vehiculoId\":" + marchDeAnaId
				+ ",\"km\":1000,\"estado\":\"BUENO\",\"modeloCatalogoId\":";
		estimar(tokenDeAna, conModelo + retirado.getId() + "}").andExpect(status().isNotFound());
		estimar(tokenDeAna, conModelo + "999999}").andExpect(status().isNotFound());
	}

	@Test
	void sinParametrosActivosResponde422ConSuCodigo() throws Exception {
		estimar(tokenDeAna, capturado("Mazda", "CX-5", 2022, 1000, "BUENO")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("SIN_PARAMETROS_DE_VALUACION"));

		parametroMarch.desactivar();
		parametroRepository.flush();
		estimar(tokenDeAna, conVehiculo(marchDeAnaId, 1000, "BUENO")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("SIN_PARAMETROS_DE_VALUACION"));
		assertThat(estimacionRepository.count()).isZero();
	}

	@Test
	void elListadoVieneOrdenadoPaginadoYSoloDelCliente() throws Exception {
		for (int km = 10_000; km <= 30_000; km += 10_000) {
			estimar(tokenDeAna, capturado("Nissan", "March", 2020, km, "BUENO")).andExpect(status().isCreated());
		}
		estimar(tokenDeBruno, capturado("Nissan", "March", 2020, 5_000, "BUENO")).andExpect(status().isCreated());

		listar(tokenDeAna, "?pagina=0&tamano=2").andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido.length()").value(2)).andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamano").value(2)).andExpect(jsonPath("$.totalElementos").value(3))
				.andExpect(jsonPath("$.totalPaginas").value(2)).andExpect(jsonPath("$.contenido[0].km").value(30000))
				.andExpect(jsonPath("$.contenido[1].km").value(20000));
		listar(tokenDeAna, "?pagina=1&tamano=2").andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].km").value(10000));
		listar(tokenDeBruno, "").andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].km").value(5000));
	}

	@Test
	void elListadoVacioNoEsUnError() throws Exception {
		listar(tokenDeAna, "").andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(0))
				.andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	void elListadoRechazaUnaPaginaInvalida() throws Exception {
		listar(tokenDeAna, "?pagina=-1").andExpect(status().isBadRequest());
		listar(tokenDeAna, "?tamano=51").andExpect(status().isBadRequest());
		listar(tokenDeAna, "?tamano=0").andExpect(status().isBadRequest());
	}

	@Test
	void sinTokenEsNoAutenticado() throws Exception {
		mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(conVehiculo(1L, 1, "BUENO")))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
	}

	@Test
	void unaCuentaDesactivadaRecibe403() throws Exception {
		cuentasAppService.desactivar(anaId);

		estimar(tokenDeAna, conVehiculo(marchDeAnaId, 1000, "BUENO")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
		listar(tokenDeAna, "").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
	}
}
