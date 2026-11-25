package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SolicitudAsistenciaTest {

	private static SolicitudAsistencia solicitud() {
		return SolicitudAsistencia.recibir(1L, 2L, TipoAsistencia.LLANTA, new BigDecimal("20.5"),
				new BigDecimal("-100.25"), null, Instant.EPOCH);
	}

	@Test
	void avanzaDeRecibidaAAtendidaPasandoPorEnCamino() {
		SolicitudAsistencia solicitud = solicitud();
		solicitud.cambiarEstado(EstadoAsistencia.EN_CAMINO, Instant.EPOCH);
		solicitud.cambiarEstado(EstadoAsistencia.ATENDIDA, Instant.EPOCH.plusSeconds(60));
		assertThat(solicitud.getEstado()).isEqualTo(EstadoAsistencia.ATENDIDA);
		assertThat(solicitud.getCerradaEn()).isEqualTo(Instant.EPOCH.plusSeconds(60));
	}

	@Test
	void noSaltaDeRecibidaAAtendidaNiVuelveAtras() {
		SolicitudAsistencia solicitud = solicitud();
		assertThatThrownBy(() -> solicitud.cambiarEstado(EstadoAsistencia.ATENDIDA, Instant.EPOCH))
				.isInstanceOf(ConflictoException.class);
		solicitud.cambiarEstado(EstadoAsistencia.EN_CAMINO, Instant.EPOCH);
		assertThatThrownBy(() -> solicitud.cambiarEstado(EstadoAsistencia.RECIBIDA, Instant.EPOCH))
				.isInstanceOf(ConflictoException.class);
	}

	@Test
	void sePuedeCancelarMientrasEstaAbiertaYBorraLaUbicacion() {
		SolicitudAsistencia solicitud = solicitud();
		solicitud.cambiarEstado(EstadoAsistencia.CANCELADA, Instant.EPOCH);
		assertThat(solicitud.getLat()).isNull();
		assertThat(solicitud.getLng()).isNull();
		assertThatThrownBy(() -> solicitud.cambiarEstado(EstadoAsistencia.EN_CAMINO, Instant.EPOCH))
				.isInstanceOf(ConflictoException.class);
	}

	@Test
	void laUbicacionSigueMientrasLaSolicitudEstaAbierta() {
		SolicitudAsistencia solicitud = solicitud();
		solicitud.cambiarEstado(EstadoAsistencia.EN_CAMINO, Instant.EPOCH);
		assertThat(solicitud.getLat()).isEqualByComparingTo("20.5");
		assertThat(solicitud.getCerradaEn()).isNull();
	}
}
