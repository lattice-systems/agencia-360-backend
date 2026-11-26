package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LealtadDominioTest {

	private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");

	private static Recompensa recompensa(int puntos, Integer existencia) {
		Recompensa recompensa = new Recompensa("Lavado exprés", null, puntos, existencia);
		ReflectionTestUtils.setField(recompensa, "id", 7L);
		return recompensa;
	}

	@Test
	void unMovimientoNoPuedeSerDeCeroNiTenerElSignoEquivocado() {
		assertThatThrownBy(() -> new MovimientoPuntos(1L, 0, MotivoPuntos.AJUSTE, null, null, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MovimientoPuntos(1L, 300, MotivoPuntos.CANJE, "CANJE", 4L, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MovimientoPuntos(1L, -10, MotivoPuntos.SERVICIO, "ORDEN", 4L, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MovimientoPuntos(1L, 10, MotivoPuntos.SERVICIO, "ORDEN", null, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new MovimientoPuntos(1L, -50, MotivoPuntos.AJUSTE, null, null, AHORA).getPuntos()).isEqualTo(-50);
		assertThat(new MovimientoPuntos(1L, -300, MotivoPuntos.CANJE, "CANJE", 4L, AHORA).getPuntos()).isEqualTo(-300);
	}

	@Test
	void soloServicioReferidoYAjusteCuentanComoGanados() {
		assertThat(MotivoPuntos.SERVICIO.ganado()).isTrue();
		assertThat(MotivoPuntos.REFERIDO.ganado()).isTrue();
		assertThat(MotivoPuntos.AJUSTE.ganado()).isTrue();
		assertThat(MotivoPuntos.CANJE.ganado()).isFalse();
		assertThat(MotivoPuntos.DEVOLUCION_CANJE.ganado()).isFalse();
	}

	@Test
	void unaRecompensaConExistenciaSeAgotaYUnCanjeVencidoLaRegresa() {
		Recompensa limitada = recompensa(300, 1);
		limitada.descontar();
		assertThat(limitada.agotada()).isTrue();
		assertThatThrownBy(limitada::descontar).isInstanceOf(IllegalStateException.class);
		limitada.devolver();
		assertThat(limitada.getExistencia()).isEqualTo(1);

		Recompensa ilimitada = recompensa(300, null);
		ilimitada.descontar();
		ilimitada.devolver();
		assertThat(ilimitada.agotada()).isFalse();
		assertThat(ilimitada.getExistencia()).isNull();
	}

	@Test
	void unCanjeSeUsaUnaVezYSoloAntesDeVencer() {
		Canje canje = new Canje(1L, recompensa(300, null), "K7XQ2MPA", AHORA, AHORA.plus(Duration.ofDays(30)));
		assertThat(canje.getEstado()).isEqualTo(EstadoCanje.VIGENTE);
		assertThat(canje.getPuntos()).isEqualTo(300);
		assertThatThrownBy(() -> canje.vencer(AHORA)).isInstanceOf(IllegalStateException.class);

		canje.usar(AHORA.plusSeconds(60));
		assertThat(canje.getEstado()).isEqualTo(EstadoCanje.USADO);
		assertThat(canje.getUsadoEn()).isEqualTo(AHORA.plusSeconds(60));
		assertThatThrownBy(() -> canje.usar(AHORA.plusSeconds(120))).isInstanceOf(IllegalStateException.class);

		Canje viejo = new Canje(1L, recompensa(300, null), "PQRSTUVW", AHORA, AHORA.plus(Duration.ofDays(30)));
		Instant despues = AHORA.plus(Duration.ofDays(31));
		assertThat(viejo.usable(despues)).isFalse();
		assertThatThrownBy(() -> viejo.usar(despues)).isInstanceOf(IllegalStateException.class);
		viejo.vencer(despues);
		assertThat(viejo.getEstado()).isEqualTo(EstadoCanje.VENCIDO);
	}

	@Test
	void unReferidoPasaASeguimientoYSoloCompraUnaVez() {
		assertThatThrownBy(() -> new Referido(1L, "CC00001", "Luis", null, null, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Referido(1L, "CC00001", " ", "4421234567", null, AHORA))
				.isInstanceOf(IllegalArgumentException.class);

		Referido referido = new Referido(1L, "CC00001", " Luis Pérez ", "4421234567", null, AHORA);
		assertThat(referido.getNombre()).isEqualTo("Luis Pérez");
		assertThat(referido.getEstado()).isEqualTo(EstadoReferido.REGISTRADO);
		referido.ligarProspecto(40L);
		assertThat(referido.getEstado()).isEqualTo(EstadoReferido.EN_SEGUIMIENTO);
		assertThat(referido.marcarCompra(AHORA)).isTrue();
		assertThat(referido.marcarCompra(AHORA)).isFalse();
		assertThat(referido.getRecompensadoEn()).isEqualTo(AHORA);
		referido.descartar();
		assertThat(referido.getEstado()).isEqualTo(EstadoReferido.COMPRO);
	}

	@Test
	void unReferidoAbiertoSePuedeDescartar() {
		Referido referido = new Referido(1L, "CC00001", "Luis", null, "luis@correo.com", AHORA);
		referido.ligarProspecto(40L);
		referido.descartar();
		assertThat(referido.getEstado()).isEqualTo(EstadoReferido.DESCARTADO);
		assertThat(EstadoReferido.DESCARTADO.abierto()).isFalse();
	}
}
