package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.application.ExpedienteService;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.application.NuevoServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.RenglonNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TipoRenglon;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
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
 * Plan de mantenimiento y lecturas de km (V2-M05.01, M05.02, M05.03 y M05.06)
 * de punta a punta con las migraciones V38 y V39 y base de datos real
 * (Testcontainers). Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class MantenimientoAppIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private VehiculosDeClientes vehiculosDeClientes;

	@Autowired
	private ExpedienteService expedienteService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeAna;
	private Long marchDeAna;
	private Long autoSinPlan;
	private Long vehiculoDeBruno;
	private UsuarioAutenticado servicio;

	@BeforeEach
	void registrarClientes() {
		Long ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		marchDeAna = asignar(ana, "3N1CK3CD5RL000051", "March");
		autoSinPlan = asignar(ana, "3N1CK3CD5RL000052", "Modelo Sin Plan");
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));
		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		vehiculoDeBruno = asignar(bruno, "3N1CK3CD5RL000053", "March");
		Usuario tecnico = usuarioRepository.save(new Usuario("servicio-mant", "hash-de-prueba", "Técnico",
				rolRepository.findByNombre("SERVICIO").orElseThrow()));
		servicio = new UsuarioAutenticado(tecnico.getId(), tecnico.getUsuario(), Set.of(Role.SERVICIO));
	}

	@Test
	void elPlanDelModeloTrae3ServiciosConConceptosYPrecioSinKmConocido() throws Exception {
		consultar(marchDeAna).andExpect(status().isOk()).andExpect(jsonPath("$.vehiculoId").value(marchDeAna))
				.andExpect(jsonPath("$.kmActual").isEmpty()).andExpect(jsonPath("$.kmFuente").isEmpty())
				.andExpect(jsonPath("$.promedioKmDiario").isEmpty())
				.andExpect(jsonPath("$.servicios.length()").value(3))
				.andExpect(jsonPath("$.servicios[0].km").value(10000))
				.andExpect(jsonPath("$.servicios[0].meses").value(6))
				.andExpect(jsonPath("$.servicios[0].estado").value("proximo"))
				.andExpect(jsonPath("$.servicios[0].precioEstimado").value(1800.0))
				.andExpect(jsonPath("$.servicios[0].conceptos.length()").value(3))
				.andExpect(jsonPath("$.servicios[1].estado").value("pendiente"))
				.andExpect(jsonPath("$.servicios[2].km").value(40000))
				.andExpect(jsonPath("$.proximo.servicioId").value(org.hamcrest.Matchers.notNullValue()))
				.andExpect(jsonPath("$.proximo.motivo").value("tiempo"));
	}

	@Test
	void unModeloSinPlanResponde200ConServiciosVaciosYProximoNulo() throws Exception {
		consultar(autoSinPlan).andExpect(status().isOk()).andExpect(jsonPath("$.servicios.length()").value(0))
				.andExpect(jsonPath("$.proximo").isEmpty());
	}

	@Test
	void unServicioCerradoDelHistorialMarcaElDelPlanComoHecho() throws Exception {
		LocalDate hoy = hoy();
		registrarServicio(marchDeAna, hoy.minusDays(10), 10200);

		consultar(marchDeAna).andExpect(status().isOk()).andExpect(jsonPath("$.kmActual").value(10200))
				.andExpect(jsonPath("$.kmFuente").value("taller"))
				.andExpect(jsonPath("$.servicios[0].estado").value("hecho"))
				.andExpect(jsonPath("$.servicios[0].hechoEn").value(hoy.minusDays(10).toString()))
				.andExpect(jsonPath("$.servicios[1].estado").value("proximo"))
				.andExpect(jsonPath("$.proximo.kmObjetivo").value(20000))
				.andExpect(jsonPath("$.proximo.faltanKm").value(9800));
	}

	@Test
	void unaLecturaDelClienteSubeElKmActualSinTocarElOficial() throws Exception {
		vehiculosDeClientes.corregir(marchDeAna, null, 12000, null);

		registrarLectura(tokenDeAna, marchDeAna, "{\"km\":12500}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.km").value(12500))
				.andExpect(jsonPath("$.fuente").value("cliente")).andExpect(jsonPath("$.registradaEn").isString());

		consultar(marchDeAna).andExpect(jsonPath("$.kmActual").value(12500))
				.andExpect(jsonPath("$.kmFuente").value("cliente"))
				.andExpect(jsonPath("$.kmActualizadoEn").value(hoy().toString()))
				.andExpect(jsonPath("$.servicios[0].estado").value("vencido"))
				.andExpect(jsonPath("$.servicios[1].estado").value("proximo"));
		org.assertj.core.api.Assertions
				.assertThat(vehiculosDeClientes.buscarPorId(marchDeAna).orElseThrow().kilometraje()).isEqualTo(12000);
	}

	@Test
	void unKmMenorAlOficialOAlUltimoDelClienteResponde422() throws Exception {
		vehiculosDeClientes.corregir(marchDeAna, null, 12000, null);

		registrarLectura(tokenDeAna, marchDeAna, "{\"km\":11999}").andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("KM_INVALIDO"));
		registrarLectura(tokenDeAna, marchDeAna, "{\"km\":12000}").andExpect(status().isCreated());
		registrarLectura(tokenDeAna, marchDeAna, "{\"km\":13000}").andExpect(status().isCreated());
		registrarLectura(tokenDeAna, marchDeAna, "{\"km\":12500}").andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("KM_INVALIDO"));
	}

	@Test
	void unKmInvalidoOAusenteResponde400() throws Exception {
		registrarLectura(tokenDeAna, marchDeAna, "{\"km\":0}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		registrarLectura(tokenDeAna, marchDeAna, "{}").andExpect(status().isBadRequest());
		registrarLectura(tokenDeAna, marchDeAna, "{\"km\":3000000}").andExpect(status().isBadRequest());
	}

	@Test
	void unVehiculoAjenoOSinSesionNoSeVe() throws Exception {
		consultar(vehiculoDeBruno).andExpect(status().isNotFound());
		registrarLectura(tokenDeAna, vehiculoDeBruno, "{\"km\":5000}").andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/mantenimiento", marchDeAna))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/app/vehiculos/{id}/lecturas-km", marchDeAna)
				.contentType(MediaType.APPLICATION_JSON).content("{\"km\":5000}")).andExpect(status().isUnauthorized());
	}

	@Test
	void elPromedioDiarioSalePorElKmDelTallerYLasLecturasDelCliente() throws Exception {
		registrarServicio(marchDeAna, hoy().minusDays(20), 10200);
		registrarLectura(tokenDeAna, marchDeAna, "{\"km\":10400}").andExpect(status().isCreated());

		consultar(marchDeAna).andExpect(jsonPath("$.promedioKmDiario").value(10.0))
				.andExpect(jsonPath("$.proximo.motivo").value("tiempo"));
	}

	@Test
	void sinDiasDeDiferenciaNoHayPromedio() throws Exception {
		registrarServicio(marchDeAna, hoy(), 10200);
		registrarLectura(tokenDeAna, marchDeAna, "{\"km\":10400}").andExpect(status().isCreated());

		consultar(marchDeAna).andExpect(jsonPath("$.promedioKmDiario").isEmpty());
	}

	private ResultActions consultar(Long vehiculoId) throws Exception {
		return mockMvc.perform(get("/api/v1/app/vehiculos/{id}/mantenimiento", vehiculoId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna));
	}

	private ResultActions registrarLectura(String token, Long vehiculoId, String cuerpo) throws Exception {
		return mockMvc.perform(post("/api/v1/app/vehiculos/{id}/lecturas-km", vehiculoId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo));
	}

	private Long asignar(Long clienteId, String vin, String modelo) {
		return registroClientesService
				.asignarVehiculo(clienteId, new VehiculoNuevo(null, vin, "Nissan", modelo, 2026, null)).vehiculos()
				.stream().filter(vehiculo -> vin.equals(vehiculo.vin())).findFirst().orElseThrow().id();
	}

	private void registrarServicio(Long vehiculoId, LocalDate fecha, int km) {
		RenglonNuevo afinacion = new RenglonNuevo(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, new BigDecimal("450.00"));
		expedienteService.registrar(vehiculoId, new NuevoServicio(fecha, km, List.of(afinacion)), servicio);
	}

	private static LocalDate hoy() {
		return LocalDate.now(ZoneId.of("America/Mexico_City"));
	}
}
