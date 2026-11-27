package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CambioDePaseDeCita.Tipo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.PushApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.ResultadoApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.RegistroPaseApple;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.RegistroPaseAppleRepository;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class ActualizadorDePasesAppleTest {

	private static final ZonedDateTime INICIO = ZonedDateTime.of(2026, 12, 3, 9, 0, 0, 0,
			ZoneId.of("America/Mexico_City"));

	private final EnvioApnsFake apns = new EnvioApnsFake();
	private RegistroPaseAppleRepository registros;
	private ConfiguracionWallet configuracion;
	private ActualizadorDePasesApple actualizador;

	private static ConfiguracionWallet conServicioWeb(String url) {
		ConfiguracionWallet base = CredencialesWalletDePrueba.nuevas().configuracion();
		return new ConfiguracionWallet(base.applePassTypeId(), base.appleTeamId(), base.appleCertP12Base64(),
				base.appleCertPassword(), base.appleWwdrPemBase64(), "", "", url);
	}

	private static RegistroPaseApple registro(long id, String pushToken) {
		RegistroPaseApple registro = new RegistroPaseApple("dispositivo-" + id, pushToken,
				CredencialesWalletDePrueba.PASS_TYPE_ID, "cita-42", 42L);
		ReflectionTestUtils.setField(registro, "id", id);
		return registro;
	}

	private CambioDePaseDeCita cambio(Tipo tipo) {
		return new CambioDePaseDeCita(42L, tipo, INICIO, INICIO.plusMinutes(30), "Versa");
	}

	@BeforeEach
	void preparar() {
		registros = mock(RegistroPaseAppleRepository.class);
		configuracion = conServicioWeb("https://carecar.example.com");
		actualizador = new ActualizadorDePasesApple(configuracion, apns, registros,
				mock(PlatformTransactionManager.class));
	}

	@Test
	void alCancelarAvisaACadaDispositivoRegistradoConUnPushVacioDelPase() {
		when(registros.findByCitaId(42L)).thenReturn(List.of(registro(1, "token-1"), registro(2, "token-2")));

		actualizador.alCambiar(cambio(Tipo.CANCELADA));

		assertThat(apns.enviados()).extracting(PushApns::token).containsExactlyInAnyOrder("token-1", "token-2");
		assertThat(apns.enviados()).allSatisfy(push -> {
			assertThat(push.topic()).isEqualTo(CredencialesWalletDePrueba.PASS_TYPE_ID);
			assertThat(push.carga()).isEqualTo("{}");
			assertThat(push.sandbox()).isFalse();
		});
	}

	@Test
	void alReprogramarTambienAvisa() {
		when(registros.findByCitaId(42L)).thenReturn(List.of(registro(1, "token-1")));

		actualizador.alCambiar(cambio(Tipo.REPROGRAMADA));

		assertThat(apns.enviados()).hasSize(1);
	}

	@Test
	void unTokenMuertoSeBorraYLosDemasSiguenRecibiendo() {
		when(registros.findByCitaId(42L)).thenReturn(List.of(registro(1, "muerto"), registro(2, "vivo")));
		apns.responderCon("muerto", new ResultadoApns(410, "Unregistered"));

		actualizador.alCambiar(cambio(Tipo.CANCELADA));

		verify(registros).borrarPorId(1L);
		verify(registros, never()).borrarPorId(2L);
		assertThat(apns.enviados()).hasSize(2);
	}

	@Test
	void unFalloQueNoEsDelTokenNoBorraElRegistro() {
		when(registros.findByCitaId(42L)).thenReturn(List.of(registro(1, "token-1")));
		apns.responderCon("token-1", new ResultadoApns(500, "InternalServerError"));

		actualizador.alCambiar(cambio(Tipo.CANCELADA));

		verify(registros, never()).borrarPorId(anyLong());
	}

	@Test
	void sinCredencialesDeApnsNoHaceNada() {
		apns.disponible(false);

		actualizador.alCambiar(cambio(Tipo.CANCELADA));

		assertThat(apns.enviados()).isEmpty();
		verify(registros, never()).findByCitaId(anyLong());
	}

	@Test
	void sinServicioWebPublicoNoHaceNada() {
		ActualizadorDePasesApple sinUrl = new ActualizadorDePasesApple(conServicioWeb(""), apns, registros,
				mock(PlatformTransactionManager.class));

		sinUrl.alCambiar(cambio(Tipo.CANCELADA));

		assertThat(apns.enviados()).isEmpty();
	}

	@Test
	void unaExcepcionNuncaRompeLaCancelacion() {
		when(registros.findByCitaId(42L)).thenThrow(new IllegalStateException("base caída"));

		actualizador.alCambiar(cambio(Tipo.CANCELADA));

		assertThat(apns.enviados()).isEmpty();
	}
}
