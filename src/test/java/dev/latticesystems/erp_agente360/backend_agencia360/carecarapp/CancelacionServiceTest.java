package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AnonimizadorDeCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CancelacionService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudesArco;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.DemasiadosIntentosException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ProhibidoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reglas de V2-M22.03 sin base de datos: quién puede cancelar, qué cuenta como
 * intento y qué se llama, en qué orden. El borrado real lo prueba la
 * integración.
 */
class CancelacionServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
	private static final long CLIENTE = 7;
	private static final String CONTRASENA = "Correcta123";

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private CuentaApp cuenta;
	private Llamadas llamadas;
	private CancelacionService servicio;

	@BeforeEach
	void servicio() {
		cuenta = CuentaApp.abrir(CLIENTE, "ana@correo.mx", encoder.encode(CONTRASENA));
		ReflectionTestUtils.setField(cuenta, "id", 5L);
		llamadas = new Llamadas();
		CuentaAppRepository cuentas = mock(CuentaAppRepository.class);
		when(cuentas.findByClienteId(CLIENTE)).thenReturn(Optional.of(cuenta));
		servicio = new CancelacionService(cuentas, encoder, llamadas.solicitudes, llamadas.anonimizador,
				llamadas.directorio, Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	void conLaContrasenaCorrectaCancelaYDejaLaSolicitudResuelta() {
		var solicitud = servicio.cancelar(CLIENTE, CONTRASENA);

		assertThat(solicitud.tipo()).isEqualTo("CANCELACION");
		assertThat(solicitud.estado()).isEqualTo("RESUELTA");
		assertThat(solicitud.creadaEn()).isEqualTo(AHORA);
		assertThat(solicitud.resueltaEn()).isEqualTo(AHORA);
		assertThat(solicitud.venceEn()).isNull();
		assertThat(llamadas.solicitudes.guardadas).singleElement().satisfies(s -> {
			assertThat(s.clienteId()).isEqualTo(CLIENTE);
			assertThat(s.tipo()).isEqualTo("CANCELACION");
			assertThat(s.estado()).isEqualTo("RESUELTA");
			assertThat(s.archivoUrl()).isNull();
		});
	}

	@Test
	void desactivaLaCuentaAnonimizaElUsuarioEInvalidaLaContrasena() {
		servicio.cancelar(CLIENTE, CONTRASENA);

		assertThat(cuenta.isActiva()).isFalse();
		assertThat(cuenta.getUsuario()).isEqualTo("eliminada-5");
		assertThat(encoder.matches(CONTRASENA, cuenta.getContrasenaHash())).isFalse();
		assertThat(cuenta.isDebeCambiarContrasena()).isFalse();
	}

	@Test
	void borraYAnonimizaLosDatosDelClienteYSoloDeEse() {
		servicio.cancelar(CLIENTE, CONTRASENA);

		assertThat(llamadas.anonimizados).containsExactly(CLIENTE);
		assertThat(llamadas.clientesAnonimizados).containsExactly(CLIENTE);
	}

	@Test
	void unaContrasenaEquivocadaDa400CuentaComoIntentoYNoToca_Nada() {
		assertThatThrownBy(() -> servicio.cancelar(CLIENTE, "Equivocada1")).isInstanceOfSatisfying(
				SolicitudInvalidaException.class,
				e -> assertThat(e.getCodigo()).isEqualTo(CodigosError.DATOS_INVALIDOS));

		assertThat(cuenta.isActiva()).isTrue();
		assertThat(cuenta.getUsuario()).isEqualTo("ana@correo.mx");
		assertThat(encoder.matches(CONTRASENA, cuenta.getContrasenaHash())).isTrue();
		assertThat(llamadas.anonimizados).isEmpty();
		assertThat(llamadas.clientesAnonimizados).isEmpty();
		assertThat(llamadas.solicitudes.guardadas).isEmpty();
	}

	@Test
	void cincoIntentosEquivocadosBloqueanYHastaLaCorrectaDa429() {
		for (int i = 0; i < CuentaApp.INTENTOS_PERMITIDOS; i++) {
			assertThatThrownBy(() -> servicio.cancelar(CLIENTE, "Equivocada1"))
					.isInstanceOf(SolicitudInvalidaException.class);
		}

		assertThat(cuenta.estaBloqueada(AHORA)).isTrue();
		assertThatThrownBy(() -> servicio.cancelar(CLIENTE, CONTRASENA)).isInstanceOfSatisfying(
				DemasiadosIntentosException.class, e -> assertThat(e.getSegundosParaReintentar()).isPositive());
		assertThat(cuenta.isActiva()).isTrue();
		assertThat(llamadas.anonimizados).isEmpty();
	}

	@Test
	void sinContrasenaEsUnaSolicitudInvalidaQueNoCuentaComoIntento() {
		for (int i = 0; i < CuentaApp.INTENTOS_PERMITIDOS + 2; i++) {
			assertThatThrownBy(() -> servicio.cancelar(CLIENTE, null)).isInstanceOf(SolicitudInvalidaException.class);
			assertThatThrownBy(() -> servicio.cancelar(CLIENTE, "  ")).isInstanceOf(SolicitudInvalidaException.class);
		}

		assertThat(cuenta.estaBloqueada(AHORA)).isFalse();
		assertThat(llamadas.anonimizados).isEmpty();
	}

	@Test
	void unaCuentaYaDesactivadaNoSeCancelaOtraVez() {
		cuenta.desactivar();

		assertThatThrownBy(() -> servicio.cancelar(CLIENTE, CONTRASENA)).isInstanceOfSatisfying(
				ProhibidoException.class, e -> assertThat(e.getCodigo()).isEqualTo(CodigosError.CUENTA_DESACTIVADA));
		assertThat(llamadas.anonimizados).isEmpty();
	}

	@Test
	void unClienteSinCuentaDa403() {
		assertThatThrownBy(() -> servicio.cancelar(999, CONTRASENA)).isInstanceOf(ProhibidoException.class);
		assertThat(llamadas.anonimizados).isEmpty();
	}

	@Test
	void siElAnonimizadorFallaElErrorSube_ParaQueLaTransaccionRevierta() {
		llamadas.anonimizador.falla = new IllegalStateException("fallo");

		assertThatThrownBy(() -> servicio.cancelar(CLIENTE, CONTRASENA)).isInstanceOf(IllegalStateException.class);
		assertThat(llamadas.clientesAnonimizados).isEmpty();
	}

	/** Las llamadas que hace el servicio, para comprobar qué y a quién. */
	private static final class Llamadas {

		final List<Long> anonimizados = new ArrayList<>();
		final List<Long> clientesAnonimizados = new ArrayList<>();
		final SolicitudesGuardadas solicitudes = new SolicitudesGuardadas();
		final AnonimizadorFalso anonimizador = new AnonimizadorFalso(anonimizados);
		final DirectorioClientes directorio = new DirectorioFalso(clientesAnonimizados);
	}

	private static final class AnonimizadorFalso implements AnonimizadorDeCuenta {

		private final List<Long> anonimizados;
		RuntimeException falla;

		AnonimizadorFalso(List<Long> anonimizados) {
			this.anonimizados = anonimizados;
		}

		@Override
		public void anonimizar(long clienteId) {
			if (falla != null) {
				throw falla;
			}
			anonimizados.add(clienteId);
		}
	}

	private record DirectorioFalso(List<Long> anonimizados) implements DirectorioClientes {

		@Override
		public Optional<ClienteRegistrado> buscarPorCorreo(String correo) {
			return Optional.empty();
		}

		@Override
		public Optional<ClienteRegistrado> buscarPorId(Long id) {
			return Optional.empty();
		}

		@Override
		public ClienteRegistrado cambiarCorreo(Long clienteId, String correo) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void anonimizar(Long clienteId) {
			anonimizados.add(clienteId);
		}
	}

	private static final class SolicitudesGuardadas implements SolicitudesArco {

		final List<Solicitud> guardadas = new ArrayList<>();

		@Override
		public Optional<Solicitud> crear(long clienteId, String tipo, Instant ahora) {
			Solicitud s = new Solicitud(guardadas.size() + 1L, clienteId, tipo, "PENDIENTE", null, ahora, null);
			guardadas.add(s);
			return Optional.of(s);
		}

		@Override
		public Optional<Solicitud> buscar(long id) {
			return guardadas.stream().filter(s -> s.id() == id).findFirst();
		}

		@Override
		public Optional<Solicitud> pendienteDe(long clienteId, String tipo) {
			return Optional.empty();
		}

		@Override
		public List<Solicitud> delCliente(long clienteId, int limite) {
			return List.of();
		}

		@Override
		public List<Solicitud> conArchivo(long clienteId, String tipo) {
			return List.of();
		}

		@Override
		public List<Solicitud> conArchivoResueltasAntesDe(Instant limite) {
			return List.of();
		}

		@Override
		public void resolver(long id, String archivoUrl, Instant ahora) {
			for (int i = 0; i < guardadas.size(); i++) {
				Solicitud s = guardadas.get(i);
				if (s.id() == id) {
					guardadas.set(i, new Solicitud(s.id(), s.clienteId(), s.tipo(), "RESUELTA", archivoUrl,
							s.creadaEn(), ahora));
				}
			}
		}

		@Override
		public void fallar(long id, Instant ahora) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void quitarArchivo(long id) {
			throw new UnsupportedOperationException();
		}
	}
}
