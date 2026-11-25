package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisosPrivacidad;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PrivacidadService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reglas de V2-M22.01 sin base de datos: qué aviso es el vigente y quién puede
 * publicar.
 */
class PrivacidadServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");

	private AvisosEnMemoria avisos;
	private PrivacidadService servicio;

	@BeforeEach
	void servicio() {
		avisos = new AvisosEnMemoria();
		servicio = new PrivacidadService(avisos, Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	void elVigenteEsLaMayorVersionYaEnVigorYNoUnaProgramada() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));
		avisos.agregar("dos", AHORA.minus(Duration.ofDays(1)));
		avisos.agregar("tres", AHORA.plus(Duration.ofDays(7)));

		var vigente = servicio.vigente(10);

		assertThat(vigente.version()).isEqualTo(2);
		assertThat(vigente.texto()).isEqualTo("dos");
		assertThat(vigente.aceptado()).isFalse();
		assertThat(vigente.aceptadoEn()).isNull();
	}

	@Test
	void sinAvisosPublicadosDa404() {
		assertThatThrownBy(() -> servicio.vigente(10)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void aceptarEsIdempotenteYLaPrimeraFechaSeConserva() {
		long id = avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));

		var primera = servicio.aceptar(10, id);
		var segunda = new PrivacidadService(avisos, Clock.fixed(AHORA.plusSeconds(90), ZoneOffset.UTC)).aceptar(10, id);

		assertThat(primera.aceptadoEn()).isEqualTo(AHORA);
		assertThat(segunda.aceptadoEn()).isEqualTo(AHORA);
		assertThat(avisos.aceptaciones).hasSize(1);
		assertThat(servicio.vigente(10).aceptado()).isTrue();
		assertThat(servicio.vigente(11).aceptado()).isFalse();
	}

	@Test
	void aceptarUnAvisoQueNoEsElVigenteDa409AunqueExista() {
		long viejo = avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));
		avisos.agregar("dos", AHORA.minus(Duration.ofDays(1)));
		long programado = avisos.agregar("tres", AHORA.plus(Duration.ofDays(7)));

		for (long id : List.of(viejo, programado)) {
			assertThatThrownBy(() -> servicio.aceptar(10, id)).isInstanceOf(ConflictoException.class)
					.extracting(e -> ((ConflictoException) e).getCodigo()).isEqualTo(CodigosError.AVISO_NO_VIGENTE);
		}
		assertThat(avisos.aceptaciones).isEmpty();
	}

	@Test
	void aceptarUnAvisoQueNoExisteDa404() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));
		assertThatThrownBy(() -> servicio.aceptar(10, 999)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void publicarUsaLaVersionSiguienteYAhoraSiNoSeDiceCuando() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));

		var nuevo = servicio.publicar("  Texto nuevo  ", null);

		assertThat(nuevo.version()).isEqualTo(2);
		assertThat(nuevo.texto()).isEqualTo("Texto nuevo");
		assertThat(nuevo.vigenteDesde()).isEqualTo(AHORA);
		assertThat(nuevo.aceptado()).isFalse();
		assertThat(servicio.vigente(10).version()).isEqualTo(2);
	}

	@Test
	void publicarProgramadoNoCambiaElVigenteHastaQueLlegueLaFecha() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));

		servicio.publicar("Para el mes que entra", AHORA.plus(Duration.ofDays(30)));

		assertThat(servicio.vigente(10).version()).isEqualTo(1);
	}

	@Test
	void publicarRechazaUnTextoVacioONulo() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));

		assertThatThrownBy(() -> servicio.publicar(null, null)).isInstanceOf(SolicitudInvalidaException.class);
		assertThatThrownBy(() -> servicio.publicar("", null)).isInstanceOf(SolicitudInvalidaException.class);
		assertThatThrownBy(() -> servicio.publicar("   \n ", null)).isInstanceOf(SolicitudInvalidaException.class);
		assertThat(avisos.avisos).hasSize(1);
	}

	@Test
	void publicarRechazaUnTextoDeMasDelLargoMaximo() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));

		assertThatThrownBy(() -> servicio.publicar("a".repeat(20_001), null))
				.isInstanceOf(SolicitudInvalidaException.class);
		assertThat(avisos.avisos).hasSize(1);
	}

	@Test
	void publicarRechazaUnaFechaAnteriorAAhoraMasLaTolerancia() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));

		// Con 1 minuto de tolerancia por si el reloj de quien publica va atrasado.
		assertThatThrownBy(() -> servicio.publicar("Texto", AHORA.minusSeconds(61)))
				.isInstanceOf(SolicitudInvalidaException.class);
		assertThat(avisos.avisos).hasSize(1);
	}

	@Test
	void publicarRechazaUnaFechaAnteriorALaVersionProgramadaQueYaExiste() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));
		avisos.agregar("programado", AHORA.plus(Duration.ofDays(7)));

		// Quedaría tapada para siempre por la programada, que tiene versión mayor.
		assertThatThrownBy(() -> servicio.publicar("Texto", AHORA.plus(Duration.ofDays(1))))
				.isInstanceOf(SolicitudInvalidaException.class);
		assertThat(avisos.avisos).hasSize(2);
	}

	@Test
	void publicarAceptaUnaFechaDeHaceUnosSegundos() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));
		var nuevo = servicio.publicar("Texto", AHORA.minusSeconds(59));
		assertThat(nuevo.version()).isEqualTo(2);
	}

	@Test
	void publicarAceptaUnTextoDeExactamenteElLargoMaximo() {
		avisos.agregar("uno", AHORA.minus(Duration.ofDays(30)));
		var nuevo = servicio.publicar("a".repeat(20_000), null);
		assertThat(nuevo.texto()).hasSize(20_000);
	}

	/** Doble en memoria de la tabla; la integración prueba la real. */
	private static final class AvisosEnMemoria implements AvisosPrivacidad {

		final List<Aviso> avisos = new ArrayList<>();
		final Map<String, Aceptacion> aceptaciones = new HashMap<>();

		long agregar(String texto, Instant vigenteDesde) {
			Aviso a = new Aviso(avisos.size() + 1L, avisos.size() + 1, texto, vigenteDesde);
			avisos.add(a);
			return a.id();
		}

		@Override
		public Optional<Aviso> vigente(Instant ahora) {
			return avisos.stream().filter(a -> !a.vigenteDesde().isAfter(ahora))
					.max(java.util.Comparator.comparingInt(Aviso::version));
		}

		@Override
		public Optional<Aviso> buscar(long id) {
			return avisos.stream().filter(a -> a.id() == id).findFirst();
		}

		@Override
		public Optional<Aviso> ultima() {
			return avisos.stream().max(java.util.Comparator.comparingInt(Aviso::version));
		}

		@Override
		public Aviso publicar(String texto, Instant vigenteDesde) {
			long id = agregar(texto, vigenteDesde);
			return buscar(id).orElseThrow();
		}

		@Override
		public Optional<Aceptacion> aceptacionDe(long clienteId, long avisoId) {
			return Optional.ofNullable(aceptaciones.get(clienteId + ":" + avisoId));
		}

		@Override
		public Aceptacion aceptar(long clienteId, long avisoId, Instant ahora) {
			Aviso aviso = buscar(avisoId).orElseThrow();
			return aceptaciones.computeIfAbsent(clienteId + ":" + avisoId,
					k -> new Aceptacion(avisoId, aviso.version(), ahora));
		}
	}
}
