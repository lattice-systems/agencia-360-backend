package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * V2-M06.07: el texto del push de cada evento, en español y sin datos
 * personales (V2-M06.02).
 */
class RedactorDeAvisosTest {

	private final RedactorDeAvisos redactor = new RedactorDeAvisos();

	private ContenidoAviso de(String evento, Map<String, Object> datos) {
		Optional<ContenidoAviso> contenido = redactor.redactar(evento, datos);
		assertThat(contenido).as("debe haber texto para " + evento).isPresent();
		return contenido.get();
	}

	@Test
	void elRecordatorioPorKilometrajeDiceCuantoFalta() {
		ContenidoAviso aviso = de("mantenimiento.recordatorio", Map.of("vehiculoId", 7L, "servicioId", 3L, "nombre",
				"Servicio de 10,000 km", "faltanKm", 850, "faltanDias", 40, "motivo", "km"));

		assertThat(aviso.categoria()).isEqualTo("mantenimiento");
		assertThat(aviso.titulo()).isEqualTo("Se acerca tu próximo servicio");
		assertThat(aviso.cuerpo())
				.isEqualTo("Servicio de 10,000 km: faltan unos 850 km. Agenda tu cita cuando quieras.");
		assertThat(aviso.destino()).isEqualTo("/mantenimiento/7");
	}

	@Test
	void elRecordatorioPorTiempoDiceCuantosDiasFaltan() {
		ContenidoAviso aviso = de("mantenimiento.recordatorio", Map.of("vehiculoId", 7L, "servicioId", 3L, "nombre",
				"Servicio de 10,000 km", "faltanKm", 3000, "faltanDias", 14, "motivo", "tiempo"));

		assertThat(aviso.cuerpo()).isEqualTo("Servicio de 10,000 km: faltan 14 días. Agenda tu cita cuando quieras.");
	}

	@Test
	void unDiaSeEscribeEnSingular() {
		ContenidoAviso aviso = de("mantenimiento.recordatorio", Map.of("vehiculoId", 7L, "servicioId", 3L, "nombre",
				"Servicio de 10,000 km", "faltanKm", 3000, "faltanDias", 1, "motivo", "tiempo"));

		assertThat(aviso.cuerpo()).contains("falta 1 día.");
	}

	@Test
	void laEtapaListaParaEntregaAvisaQueElAutoEstaListo() {
		ContenidoAviso aviso = de("orden", Map.of("ordenId", 12L, "etapa", "LISTO_PARA_ENTREGA", "motivo", "ETAPA"));

		assertThat(aviso.categoria()).isEqualTo("taller");
		assertThat(aviso.titulo()).isEqualTo("Tu auto está listo");
		assertThat(aviso.destino()).isEqualTo("/taller/12");
	}

	@Test
	void otraEtapaDiceEnQuePasoEstaElAuto() {
		ContenidoAviso aviso = de("orden", Map.of("ordenId", 12L, "etapa", "EN_REPARACION", "motivo", "ETAPA"));

		assertThat(aviso.titulo()).isEqualTo("Tu auto está en reparación");
	}

	@Test
	void unTrabajoAdicionalPideRespuesta() {
		ContenidoAviso aviso = de("orden",
				Map.of("ordenId", 12L, "etapa", "ESPERANDO_APROBACION", "motivo", "ADICIONAL"));

		assertThat(aviso.titulo()).isEqualTo("Hay un trabajo adicional por aprobar");
	}

	@Test
	void losCambiosMenoresDeLaOrdenNoHacenRuido() {
		for (String motivo : new String[]{"INSPECCION", "RESPUESTA", "RENGLON", "REGISTRO"}) {
			assertThat(redactor.redactar("orden", Map.of("ordenId", 12L, "motivo", motivo))).as(motivo).isEmpty();
		}
	}

	@Test
	void cadaEstadoDeAsistenciaTieneSuTexto() {
		assertThat(de("asistencia.estado", Map.of("solicitudId", 4L, "estado", "en_camino")).titulo())
				.isEqualTo("Tu asistencia va en camino");
		assertThat(de("asistencia.estado", Map.of("solicitudId", 4L, "estado", "atendida")).titulo())
				.isEqualTo("Asistencia atendida");
		assertThat(de("asistencia.estado", Map.of("solicitudId", 4L, "estado", "cancelada")).titulo())
				.isEqualTo("Asistencia cancelada");
		assertThat(de("asistencia.estado", Map.of("solicitudId", 4L, "estado", "recibida")).categoria())
				.isEqualTo("citas");
	}

	@Test
	void cadaEstadoDelTrasladoTieneSuTexto() {
		ContenidoAviso camino = de("traslado.estado", Map.of("trasladoId", 9L, "estado", "en_camino"));
		assertThat(camino.titulo()).isEqualTo("Tu chofer va en camino");
		assertThat(camino.destino()).isEqualTo("/recoleccion/traslados/9");
		assertThat(de("traslado.estado", Map.of("trasladoId", 9L, "estado", "entregado")).titulo())
				.isEqualTo("Tu auto fue entregado");
		assertThat(de("traslado.estado", Map.of("trasladoId", 9L, "estado", "cancelado")).titulo())
				.isEqualTo("Traslado cancelado");
	}

	@Test
	void laUbicacionDelChoferNoGeneraPushPorqueLlegaCadaPocosSegundos() {
		assertThat(redactor.redactar("traslado.ubicacion", Map.of("trasladoId", 9L))).isEmpty();
	}

	@Test
	void unaCampanaCriticaSeDistingueDeUnaNormal() {
		ContenidoAviso critica = de("campana.nueva",
				Map.of("vehiculoId", 7L, "campanaId", 2L, "critica", true, "titulo", "Bolsas de aire"));
		ContenidoAviso normal = de("campana.nueva",
				Map.of("vehiculoId", 7L, "campanaId", 2L, "critica", false, "titulo", "Revisión de frenos"));

		assertThat(critica.titulo()).isEqualTo("Campaña de seguridad urgente");
		assertThat(normal.titulo()).isEqualTo("Nueva campaña para tu auto");
		assertThat(critica.destino()).isEqualTo("/garantia/7/campanas/2");
	}

	@Test
	void laGarantiaPorVencerDiceLosDiasOLosKilometros() {
		ContenidoAviso dias = de("garantia.por_vencer", Map.of("vehiculoId", 7L, "garantiaId", 1L, "componente",
				"Motor", "venceEn", "2027-01-31", "diasRestantes", 15, "motivo", "dias_15"));
		ContenidoAviso km = de("garantia.por_vencer", Map.of("vehiculoId", 7L, "garantiaId", 1L, "componente", "Motor",
				"venceEn", "2027-01-31", "diasRestantes", 200, "kmRestantes", 1000, "motivo", "km_1000"));

		assertThat(dias.titulo()).isEqualTo("Tu garantía está por vencer");
		assertThat(dias.cuerpo()).isEqualTo("La garantía de Motor vence en 15 días.");
		assertThat(km.cuerpo()).isEqualTo("A la garantía de Motor le quedan unos 1,000 km.");
		assertThat(dias.destino()).isEqualTo("/garantia/7");
	}

	@Test
	void elPagoDistingueConfirmadoDeFallido() {
		assertThat(de("pago.confirmado", Map.of("pagoId", 5L, "estado", "PAGADO")).titulo())
				.isEqualTo("Pago confirmado");
		assertThat(de("pago.confirmado", Map.of("pagoId", 5L, "estado", "FALLIDO")).titulo())
				.isEqualTo("No pudimos procesar tu pago");
		assertThat(redactor.redactar("pago.confirmado", Map.of("pagoId", 5L, "estado", "PENDIENTE"))).isEmpty();
	}

	@Test
	void soloAvisaDelPedidoCuandoEstaListoOCancelado() {
		assertThat(de("pedido.estado", Map.of("pedidoId", 3L, "estado", "listo")).titulo())
				.isEqualTo("Tu pedido está listo");
		assertThat(de("pedido.estado", Map.of("pedidoId", 3L, "estado", "cancelado")).titulo())
				.isEqualTo("Tu pedido fue cancelado");
		assertThat(redactor.redactar("pedido.estado", Map.of("pedidoId", 3L, "estado", "apartado"))).isEmpty();
	}

	@Test
	void losPuntosGanadosAvisanPeroLosGastadosNo() {
		assertThat(de("puntos.movidos", Map.of("puntos", 150, "motivo", "SERVICIO", "saldo", 900)).cuerpo())
				.isEqualTo("Sumaste 150 puntos CareCar.");
		assertThat(redactor.redactar("puntos.movidos", Map.of("puntos", -300, "motivo", "CANJE", "saldo", 600)))
				.isEmpty();
	}

	@Test
	void otrosEventosTienenSuTexto() {
		assertThat(de("factura.timbrada", Map.of("facturaId", 1L, "pagoId", 5L)).titulo())
				.isEqualTo("Tu factura está lista");
		assertThat(de("valuacion.oferta", Map.of("valuacionId", 1L, "vehiculoId", 7L, "vigenciaHasta", "2027-02-01"))
				.titulo()).isEqualTo("Tienes una oferta por tu auto");
		assertThat(de("arco.estado", Map.of("solicitudId", 1L, "tipo", "ACCESO", "estado", "RESUELTA")).titulo())
				.isEqualTo("Actualizamos tu solicitud de privacidad");
	}

	@Test
	void unEventoDesconocidoNoGeneraAviso() {
		assertThat(redactor.redactar("algo.raro", Map.of())).isEmpty();
	}

	@Test
	void unDatoQueFaltaNoRompeElTexto() {
		assertThat(redactor.redactar("mantenimiento.recordatorio", Map.of("vehiculoId", 7L))).isPresent();
		assertThat(redactor.redactar("orden", Map.of())).isEmpty();
	}

	@Test
	void nadaDeLoQueSePublicaLlevaDatosPersonales() {
		ContenidoAviso aviso = de("campana.nueva", Map.of("vehiculoId", 7L, "campanaId", 2L, "critica", true, "titulo",
				"Bolsas de aire", "codigo", "VIN-1HGCM82633A004352"));

		assertThat(aviso.titulo() + aviso.cuerpo() + aviso.destino()).doesNotContain("VIN").doesNotContain("1HGCM");
	}
}
