package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SolicitudCierreTest {

	private static final Long VENDEDOR = 7L;
	private static final Long ADMINISTRADOR = 1L;
	private static final Instant AHORA = Instant.parse("2026-10-13T10:00:00Z");
	private static final String MOTIVO = "El cliente canceló la compra";

	private static Prospecto enPago() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);
		prospecto.moverPorVendedor(Etapa.PAGO, null, VENDEDOR, AHORA);
		return prospecto;
	}

	@Test
	void elVendedorSolicitaElCierreYQuedaPendiente() {
		Prospecto prospecto = enPago();

		SolicitudCierre solicitud = SolicitudCierre.solicitar(prospecto, " " + MOTIVO + " ", VENDEDOR);

		assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
		assertThat(solicitud.getMotivo()).isEqualTo(MOTIVO);
		assertThat(solicitud.getSolicitanteId()).isEqualTo(VENDEDOR);
		assertThat(prospecto.isCierrePendiente()).isTrue();
		assertThat(prospecto.getEtapa()).isEqualTo(Etapa.PAGO);
	}

	@Test
	void exigeMotivo() {
		assertThatThrownBy(() -> SolicitudCierre.solicitar(enPago(), " ", VENDEDOR))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El motivo de cierre es obligatorio");
	}

	@Test
	void enLasPrimerasEtapasNoSeSolicitaPorqueLoCierraElVendedor() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		assertThatThrownBy(() -> SolicitudCierre.solicitar(prospecto, MOTIVO, VENDEDOR))
				.isInstanceOf(ConflictoException.class);
	}

	@Test
	void soloPuedeHaberUnaSolicitudPendiente() {
		Prospecto prospecto = enPago();
		SolicitudCierre.solicitar(prospecto, MOTIVO, VENDEDOR);

		assertThatThrownBy(() -> SolicitudCierre.solicitar(prospecto, MOTIVO, VENDEDOR))
				.isInstanceOf(ConflictoException.class)
				.hasMessage("Este prospecto ya tiene una solicitud de cierre pendiente");
	}

	@Test
	void alAprobarseElProspectoSeCierraConElMotivoDesdeElErp() {
		Prospecto prospecto = enPago();
		SolicitudCierre solicitud = SolicitudCierre.solicitar(prospecto, MOTIVO, VENDEDOR);

		solicitud.aprobar(ADMINISTRADOR, AHORA);

		assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
		assertThat(solicitud.getResueltaPorId()).isEqualTo(ADMINISTRADOR);
		assertThat(solicitud.getResueltaEn()).isEqualTo(AHORA);
		assertThat(prospecto.getEtapa()).isEqualTo(Etapa.CERRADO);
		assertThat(prospecto.getMotivoCierre()).isEqualTo(MOTIVO);
		assertThat(prospecto.getUltimaEtapa()).isEqualTo(Etapa.PAGO);
		assertThat(prospecto.isCierrePendiente()).isFalse();
		assertThat(prospecto.getHistorial()).last().satisfies(movimiento -> {
			assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.ERP);
			assertThat(movimiento.getUsuarioId()).isEqualTo(ADMINISTRADOR);
		});
	}

	@Test
	void alRechazarseElProspectoSigueEnSuEtapaYSePuedeVolverASolicitar() {
		Prospecto prospecto = enPago();
		SolicitudCierre solicitud = SolicitudCierre.solicitar(prospecto, MOTIVO, VENDEDOR);

		solicitud.rechazar(ADMINISTRADOR, AHORA);

		assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
		assertThat(prospecto.getEtapa()).isEqualTo(Etapa.PAGO);
		assertThat(prospecto.isCierrePendiente()).isFalse();
		assertThat(SolicitudCierre.solicitar(prospecto, MOTIVO, VENDEDOR).getEstado())
				.isEqualTo(EstadoSolicitud.PENDIENTE);
	}

	@Test
	void unaSolicitudResueltaNoSeVuelveAResolver() {
		SolicitudCierre solicitud = SolicitudCierre.solicitar(enPago(), MOTIVO, VENDEDOR);
		solicitud.rechazar(ADMINISTRADOR, AHORA);

		assertThatThrownBy(() -> solicitud.aprobar(ADMINISTRADOR, AHORA)).isInstanceOf(ConflictoException.class)
				.hasMessage("La solicitud ya fue resuelta");
	}
}
