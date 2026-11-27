package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Sucursal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.SucursalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** V2-M27.02: sucursales con ubicación y horarios, con la migración sembrada y base real. */
@AutoConfigureMockMvc
@Transactional
class SucursalesAppIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private SucursalRepository sucursales;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String token;

	@BeforeEach
	void prepararToken() {
		Long cliente = registroClientesService.registrar(new NuevoCliente("Rosa Ibarra", "rosa@correo.com", null, null))
				.id();
		cuentasAppService.abrir(cliente, "Temporal123");
		token = jwtService.generateToken(cliente, "rosa@correo.com", List.of(Role.CLIENTE_CARECAR));
	}

	@Test
	void laMigracionSiembraLaSucursalDeLeon() throws Exception {
		mockMvc.perform(get("/api/v1/app/sucursales").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].latitud").value(21.125))
				.andExpect(jsonPath("$[0].longitud").value(-101.686))
				.andExpect(jsonPath("$[0].horarios[?(@.departamento == 'SERVICIO')]").isNotEmpty())
				.andExpect(jsonPath("$[0].abiertaAhora").isBoolean())
				.andExpect(jsonPath("$[0].horarios[0].abiertoAhora").isBoolean());
	}

	@Test
	void soloMuestraLasActivasYPorOrden() throws Exception {
		sucursales.save(new Sucursal("Anexo Sur", "Calle Sur 2", new BigDecimal("21.100000"),
				new BigDecimal("-101.700000"), null, null, List.of(), 99));
		Sucursal retirada = new Sucursal("Cerrada", "Calle Vieja 1", new BigDecimal("21.000000"),
				new BigDecimal("-101.000000"), null, null, List.of(), 50);
		retirada.retirar();
		sucursales.save(retirada);

		mockMvc.perform(get("/api/v1/app/sucursales").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$[?(@.nombre == 'Cerrada')]").isEmpty())
				.andExpect(jsonPath("$[-1:].nombre").value("Anexo Sur"))
				.andExpect(jsonPath("$[-1:].abiertaAhora").value(false)).andExpect(jsonPath("$[-1:].telefono").isEmpty());
	}

	@Test
	void sinTokenResponde401() throws Exception {
		mockMvc.perform(get("/api/v1/app/sucursales")).andExpect(status().isUnauthorized());
	}
}
