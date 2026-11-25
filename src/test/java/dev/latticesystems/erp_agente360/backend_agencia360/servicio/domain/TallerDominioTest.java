package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstadoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.ResultadoInspeccion;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TipoEvidencia;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class TallerDominioTest {

	private static final ZonedDateTime AHORA = ZonedDateTime.of(2026, 11, 24, 9, 30, 0, 0,
			ZoneId.of("America/Mexico_City"));

	@Test
	void cadaEtapaFijaElEstatusYElAvanceDeLaOrden() {
		assertThat(EtapaTaller.RECIBIDO.estatus()).isEqualTo(EstatusOrden.RECIBIDO);
		assertThat(EtapaTaller.DIAGNOSTICO.estatus()).isEqualTo(EstatusOrden.EN_PROCESO);
		assertThat(EtapaTaller.LAVADO.estatus()).isEqualTo(EstatusOrden.EN_PROCESO);
		assertThat(EtapaTaller.LISTO_PARA_ENTREGA.estatus()).isEqualTo(EstatusOrden.TERMINADO);
		assertThat(EtapaTaller.ENTREGADO.estatus()).isEqualTo(EstatusOrden.ENTREGADO);
		assertThat(EtapaTaller.RECIBIDO.avance()).isZero();
		assertThat(EtapaTaller.EN_REPARACION.avance()).isEqualTo(43);
		assertThat(EtapaTaller.ENTREGADO.avance()).isEqualTo(100);
	}

	@Test
	void laOrdenAbiertaQuedaRecibidaSinMontosNiCierre() {
		Instant entrega = AHORA.plusHours(8).toInstant();

		OrdenServicio orden = OrdenServicio.abrir(3L, 15_000, 7L, null, entrega, AHORA);

		assertThat(orden.getEstatus()).isEqualTo(EstatusOrden.RECIBIDO);
		assertThat(orden.getFecha()).isEqualTo(AHORA.toLocalDate());
		assertThat(orden.getAsesorId()).isEqualTo(7L);
		assertThat(orden.getAtendidoPor()).isEqualTo(7L);
		assertThat(orden.getEntregaEstimada()).isEqualTo(entrega);
		assertThat(orden.getTotal()).isEqualByComparingTo("0");
		assertThat(orden.getSubtotal().add(orden.getIva())).isEqualByComparingTo(orden.getTotal());
		assertThat(orden.getFechaCierre()).isNull();
		assertThatThrownBy(() -> OrdenServicio.abrir(3L, -1, 7L, null, null, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OrdenServicio.abrir(3L, 10, 7L, null, AHORA.minusMinutes(1).toInstant(), AHORA))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void laOrdenSeCierraAlQuedarListaYNoCambiaDespuesDeEntregarse() {
		OrdenServicio orden = OrdenServicio.abrir(3L, 15_000, 7L, null, null, AHORA);
		Instant lista = AHORA.plusHours(5).toInstant();

		orden.pasarA(EtapaTaller.EN_REPARACION, AHORA.plusHours(1).toInstant());
		assertThat(orden.getEstatus()).isEqualTo(EstatusOrden.EN_PROCESO);
		assertThat(orden.getFechaCierre()).isNull();
		orden.pasarA(EtapaTaller.LISTO_PARA_ENTREGA, lista);
		assertThat(orden.getEstatus()).isEqualTo(EstatusOrden.TERMINADO);
		assertThat(orden.getFechaCierre()).isEqualTo(lista);
		orden.pasarA(EtapaTaller.ENTREGADO, AHORA.plusHours(6).toInstant());
		assertThat(orden.getEstatus()).isEqualTo(EstatusOrden.ENTREGADO);
		assertThat(orden.getFechaCierre()).isEqualTo(lista);
		assertThatThrownBy(() -> orden.pasarA(EtapaTaller.LAVADO, AHORA.plusHours(7).toInstant()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unaEtapaTerminaUnaSolaVezYSuNotaTieneLimite() {
		Instant inicio = AHORA.toInstant();
		EtapaOrden etapa = EtapaOrden.empezar(1L, EtapaTaller.DIAGNOSTICO, inicio, "  Revisando frenos  ");

		etapa.terminar(inicio.plusSeconds(60));
		etapa.terminar(inicio.plusSeconds(600));

		assertThat(etapa.getNota()).isEqualTo("Revisando frenos");
		assertThat(etapa.getFin()).isEqualTo(inicio.plusSeconds(60));
		assertThat(EtapaOrden.empezar(1L, EtapaTaller.LAVADO, inicio, " ").getNota()).isNull();
		assertThatThrownBy(() -> EtapaOrden.empezar(1L, EtapaTaller.LAVADO, inicio, "x".repeat(301)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void elPuntoRevisadoExigeNombreYResultado() {
		PuntoInspeccion punto = PuntoInspeccion.revisar(1L, " Frenos ", ResultadoInspeccion.ROJO, "Balatas al 10 %");

		assertThat(punto.getPunto()).isEqualTo("Frenos");
		assertThat(punto.getResultado()).isEqualTo(ResultadoInspeccion.ROJO);
		assertThatThrownBy(() -> PuntoInspeccion.revisar(1L, " ", ResultadoInspeccion.VERDE, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PuntoInspeccion.revisar(1L, "Llantas", null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void laEvidenciaEsFotoOVideoSegunSuTipoDeContenido() {
		assertThat(EvidenciaOrden.tipoDe("image/jpeg")).isEqualTo(TipoEvidencia.FOTO);
		assertThat(EvidenciaOrden.tipoDe("image/webp")).isEqualTo(TipoEvidencia.FOTO);
		assertThat(EvidenciaOrden.tipoDe("video/mp4")).isEqualTo(TipoEvidencia.VIDEO);
		assertThatThrownBy(() -> EvidenciaOrden.tipoDe("application/pdf")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> EvidenciaOrden.tipoDe(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void elAdicionalSumaManoDeObraYRefaccionesYSeRespondeUnaSolaVez() {
		TrabajoAdicional adicional = TrabajoAdicional.proponer(1L, "Cambio de balatas", new BigDecimal("450.00"),
				new BigDecimal("1200.50"));
		Instant momento = AHORA.toInstant();

		assertThat(adicional.getTotal()).isEqualByComparingTo("1650.50");
		assertThat(adicional.estaPendiente()).isTrue();
		adicional.responder(true, 9L, "firmas/abc", momento);

		assertThat(adicional.getEstado()).isEqualTo(EstadoAdicional.APROBADO);
		assertThat(adicional.getRespondidoPor()).isEqualTo(9L);
		assertThat(adicional.getFirmaUrl()).isEqualTo("firmas/abc");
		assertThat(adicional.getRespondidoEn()).isEqualTo(momento);
		assertThatThrownBy(() -> adicional.responder(false, 9L, "firmas/otra", momento))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unRenglonNuevoRecalculaLosMontosDeLaOrdenAbierta() {
		OrdenServicio orden = OrdenServicio.abrir(3L, 15_000, 7L, null, null, AHORA);

		orden.agregarRenglon(new RenglonNuevo(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, new BigDecimal("900.00")));
		orden.agregarRenglon(new RenglonNuevo(TipoRenglon.REFACCION, "Bujías", 4, new BigDecimal("95.50")));

		assertThat(orden.getManoDeObra()).isEqualByComparingTo("900.00");
		assertThat(orden.getRefacciones()).isEqualByComparingTo("382.00");
		assertThat(orden.getTotal()).isEqualByComparingTo("1282.00");
		assertThat(orden.getSubtotal().add(orden.getIva())).isEqualByComparingTo(orden.getTotal());
		assertThat(orden.getRenglonesManoDeObra()).hasSize(1);
		assertThat(orden.getRenglonesRefaccion()).hasSize(1);
		orden.pasarA(EtapaTaller.LISTO_PARA_ENTREGA, AHORA.toInstant());
		assertThatThrownBy(() -> orden
				.agregarRenglon(new RenglonNuevo(TipoRenglon.MANO_DE_OBRA, "Lavado", 1, new BigDecimal("100.00"))))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unAdicionalAprobadoSeIncorporaUnaSolaVezComoRenglones() {
		TrabajoAdicional balatas = TrabajoAdicional.proponer(1L, "Cambio de balatas", new BigDecimal("450.00"),
				new BigDecimal("1200.50"));
		TrabajoAdicional lavado = TrabajoAdicional.proponer(1L, "Lavado de motor", new BigDecimal("200.00"),
				BigDecimal.ZERO);
		Instant momento = AHORA.toInstant();
		balatas.responder(true, 9L, "firmas/a", momento);
		lavado.responder(false, 9L, "firmas/b", momento);

		assertThat(balatas.incorporar(momento)).containsExactly(
				new RenglonNuevo(TipoRenglon.MANO_DE_OBRA, "Cambio de balatas", 1, new BigDecimal("450.00")),
				new RenglonNuevo(TipoRenglon.REFACCION, "Cambio de balatas", 1, new BigDecimal("1200.50")));
		assertThat(balatas.estaIncorporado()).isTrue();
		assertThat(balatas.incorporar(momento)).isEmpty();
		assertThat(lavado.incorporar(momento)).isEmpty();
		assertThat(lavado.estaIncorporado()).isFalse();
	}

	@Test
	void elAdicionalNoAceptaMontosNegativosNiTotalEnCero() {
		assertThatThrownBy(() -> TrabajoAdicional.proponer(1L, "Lavado", BigDecimal.ZERO, BigDecimal.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TrabajoAdicional.proponer(1L, "Lavado", new BigDecimal("-1"), BigDecimal.TEN))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TrabajoAdicional.proponer(1L, "Lavado", new BigDecimal("1.234"), BigDecimal.TEN))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TrabajoAdicional.proponer(1L, " ", BigDecimal.ONE, BigDecimal.ONE))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
