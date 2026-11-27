package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.PushApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FranjaHorario;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.RegistroPaseApple;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaHorarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.RegistroPaseAppleRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Que los eventos de verdad lleguen a Apple (V2-M25.05 y V2-M25.06): cancelar
 * una cita y cambiar de etapa una orden publican su evento, y solo después de
 * confirmar la transacción los escuchan los avisos de APNs. A diferencia del
 * resto, esta prueba NO es transaccional (un AFTER_COMMIT no corre dentro de
 * una transacción que se revierte); los datos usan correo y VIN únicos para no
 * chocar con otras pruebas, y la cita queda cancelada.
 */
@AutoConfigureMockMvc
class AvisosDeAppleIntegrationTest extends AbstractIntegrationTest {

	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String TOKEN_DE_ACTIVIDAD = "00ff00ff00ff00ff00ff00ff00ff00ff";
	private static final String BUNDLE = "mx.carecar.prueba";

	/** APNs de mentira con credenciales completas y Apple con URL pública. */
	@TestConfiguration
	static class ConfiguracionDeApple {
		@Bean
		@Primary
		EnvioApnsFake envioApnsFake() {
			return new EnvioApnsFake();
		}

		@Bean
		@Primary
		ConfiguracionApns configuracionApnsDePrueba() {
			return new ConfiguracionApns("KEY1234567", "ABCDE12345", "cDhi", BUNDLE, true);
		}

		@Bean
		@Primary
		ConfiguracionWallet configuracionWalletConServicioWeb() {
			ConfiguracionWallet base = CredencialesWalletDePrueba.nuevas().configuracion();
			return new ConfiguracionWallet(base.applePassTypeId(), base.appleTeamId(), base.appleCertP12Base64(),
					base.appleCertPassword(), base.appleWwdrPemBase64(), "", "", "https://carecar.example.com");
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EnvioApnsFake apns;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private FranjaHorarioRepository franjas;

	@Autowired
	private RegistroPaseAppleRepository registros;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ConfiguracionWallet configuracionWallet;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeCliente;
	private Long vehiculoId;

	@BeforeEach
	void crearCliente() {
		apns.limpiar();
		long unico = System.nanoTime();
		String correo = "ana" + unico + "@apple.com";
		Long cliente = registroClientesService.registrar(new NuevoCliente("Ana López", correo, null, null)).id();
		vehiculoId = registroClientesService.asignarVehiculo(cliente,
				new VehiculoNuevo(null, "3N1CK3CD5RL%06d".formatted(unico % 1_000_000), "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(cliente, "Temporal123");
		tokenDeCliente = jwtService.generateToken(cliente, correo, List.of(Role.CLIENTE_CARECAR));
	}

	@Test
	void cancelarUnaCitaAvisaAWalletSoloDespuesDeConfirmar() throws Exception {
		long citaId = agendarCita();
		registros.save(new RegistroPaseApple("dispositivo-1", "pushdelpase", configuracionWallet.applePassTypeId(),
				"cita-" + citaId, citaId));

		mockMvc.perform(post("/api/v1/app/citas/{id}/cancelar", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeCliente)).andExpect(status().isOk());

		assertThat(apns.enviados()).hasSize(1);
		PushApns push = apns.enviados().getFirst();
		assertThat(push.token()).isEqualTo("pushdelpase");
		assertThat(push.topic()).isEqualTo(configuracionWallet.applePassTypeId());
		assertThat(push.carga()).isEqualTo("{}");
	}

	@Test
	void cambiarDeEtapaLaOrdenActualizaLaLiveActivity() throws Exception {
		Usuario asesor = usuarioRepository.save(new Usuario("servicio-" + System.nanoTime(), "hash-de-prueba",
				"Laura Méndez", rolRepository.findByNombre("SERVICIO").orElseThrow()));
		String tokenDeServicio = jwtService.generateToken(asesor.getId(), asesor.getUsuario(), List.of(Role.SERVICIO));
		String entrega = LocalDate.now(ZONA_AGENCIA).plusDays(1).atTime(18, 0) + ":00";
		String abierta = mockMvc
				.perform(post("/api/v1/taller/ordenes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeServicio)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"vehiculoId\":" + vehiculoId + ",\"kilometraje\":15200,\"entregaEstimada\":\""
								+ entrega + "\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long ordenId = ((Number) JsonPath.read(abierta, "$.id")).longValue();
		mockMvc.perform(put("/api/v1/app/ordenes/{id}/actividad-en-vivo", ordenId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pushToken\": \"" + TOKEN_DE_ACTIVIDAD + "\"}")).andExpect(status().isNoContent());
		apns.limpiar();

		mockMvc.perform(post("/api/v1/taller/ordenes/{id}/etapas", ordenId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeServicio).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"DIAGNOSTICO\"}")).andExpect(status().isOk());

		assertThat(apns.enviados()).hasSize(1);
		PushApns push = apns.enviados().getFirst();
		assertThat(push.token()).isEqualTo(TOKEN_DE_ACTIVIDAD);
		assertThat(push.topic()).isEqualTo(BUNDLE + ".push-type.liveactivity");
		JsonNode aps = JSON.readTree(push.carga()).path("aps");
		assertThat(aps.path("event").asText()).isEqualTo("update");
		assertThat(aps.at("/content-state/etapa").asText()).isEqualTo("DIAGNOSTICO");
	}

	private long agendarCita() throws Exception {
		LocalDate dia = LocalDate.now(ZONA_AGENCIA).plusDays(3);
		while (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
			dia = dia.plusDays(1);
		}
		LocalDate fecha = dia;
		Long franja = franjas.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc().stream()
				.filter(candidata -> candidata.esDe(fecha)).map(FranjaHorario::getId).findFirst().orElseThrow();
		String creada = mockMvc
				.perform(post("/api/v1/app/citas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"vehiculoId\": %d, \"franjaId\": %d, \"fecha\": \"%s\"}".formatted(vehiculoId,
								franja, fecha)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(creada, "$.id").longValue();
	}
}
