package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ServicioNoDisponibleException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaseWalletServiceTest {

	private static final Long CLIENTE = 7L;
	private static final Long CITA = 42L;
	private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres";

	private final CitaRepository citas = mock(CitaRepository.class);
	private final VehiculosDeClientes vehiculos = mock(VehiculosDeClientes.class);
	private final FirmaCitas firma = new FirmaCitas(SECRETO);
	private Cita cita;

	@BeforeEach
	void prepararCita() {
		cita = Cita.confirmar(CLIENTE, 1L, LocalDate.now().plusDays(5), 1L, LocalTime.of(9, 0), LocalTime.of(10, 0));
		ReflectionTestUtils.setField(cita, "id", CITA);
		when(citas.findByIdAndClienteId(CITA, CLIENTE)).thenReturn(Optional.of(cita));
		when(vehiculos.buscarPorId(1L)).thenReturn(Optional.empty());
	}

	private PaseWalletService servicio(ConfiguracionWallet configuracion) {
		return new PaseWalletService(citas, vehiculos, firma, configuracion, new PaseAppleGenerador(configuracion),
				new PaseGoogleGenerador(configuracion));
	}

	@Test
	void applePorOtraPersonaOCitaInexistenteEs404() {
		PaseWalletService servicio = servicio(CredencialesWalletDePrueba.nuevas().configuracion());
		assertThatThrownBy(() -> servicio.pase(99L, CITA, PlataformaWallet.APPLE))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void sinCredencialesResponde503ConWalletNoConfiguradoParaCadaPlataforma() {
		PaseWalletService servicio = servicio(CredencialesWalletDePrueba.sinCredenciales());
		for (PlataformaWallet plataforma : PlataformaWallet.values()) {
			assertThatThrownBy(() -> servicio.pase(CLIENTE, CITA, plataforma)).isInstanceOfSatisfying(
					ServicioNoDisponibleException.class,
					ex -> assertThat(ex.getCodigo()).isEqualTo("WALLET_NO_CONFIGURADO"));
		}
	}

	@Test
	void unaCitaCanceladaResponde409AunqueFalteLaConfiguracion() {
		cita.cancelar(LocalDateTime.now(), Duration.ofHours(1), Instant.now());
		PaseWalletService servicio = servicio(CredencialesWalletDePrueba.sinCredenciales());
		assertThatThrownBy(() -> servicio.pase(CLIENTE, CITA, PlataformaWallet.GOOGLE)).isInstanceOfSatisfying(
				ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("CITA_CANCELADA"));
	}

	@Test
	void applePidioUnPkpassYGoogleUnaUrlConElMismoQrDelComprobante() {
		PaseWalletService servicio = servicio(CredencialesWalletDePrueba.nuevas().configuracion());

		PaseWalletApp apple = servicio.pase(CLIENTE, CITA, PlataformaWallet.APPLE);
		assertThat(apple.pkpass()).isNotEmpty();
		assertThat(apple.url()).isNull();
		assertThat(new String(apple.pkpass(), java.nio.charset.StandardCharsets.ISO_8859_1)).contains("pass.json");

		PaseWalletApp google = servicio.pase(CLIENTE, CITA, PlataformaWallet.GOOGLE);
		assertThat(google.pkpass()).isNull();
		assertThat(google.url()).startsWith("https://pay.google.com/gp/v/save/");
		assertThat(firma.citaDe(firma.contenidoQr(CITA))).contains(CITA);
	}

	@Test
	void laPlataformaSeLeeSinImportarMayusculasYRechazaOtrosValores() {
		assertThat(PlataformaWallet.de("Apple")).isEqualTo(PlataformaWallet.APPLE);
		assertThat(PlataformaWallet.de("google")).isEqualTo(PlataformaWallet.GOOGLE);
		assertThatThrownBy(() -> PlataformaWallet.de("samsung")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PlataformaWallet.de(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
