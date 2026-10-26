package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.FacturaEmitida;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.DatosFiscalesDePrueba;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.MotivoCierre;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntregaAlFacturarServiceTest {

	private static final Instant REGISTRO = Instant.parse("2026-10-21T16:00:00Z");
	private static final Instant EMITIDA = Instant.parse("2026-10-23T18:30:00Z");
	private static final FacturaEmitida FACTURA = new FacturaEmitida(1L, 50L, "A-0001", EMITIDA);

	@Mock
	private ProspectoRepository prospectoRepository;

	private EntregaAlFacturarService service;

	@BeforeEach
	void setUp() {
		service = new EntregaAlFacturarService(prospectoRepository);
	}

	@Test
	void laFacturaEmitidaPasaElProspectoAEntrega() {
		Prospecto ana = enFacturacion();
		when(prospectoRepository.findById(1L)).thenReturn(Optional.of(ana));

		service.alEmitirFactura(FACTURA);

		assertThat(ana.getEtapa()).isEqualTo(Etapa.ENTREGA);
		assertThat(ana.getEtapaActualizadaEn()).isEqualTo(EMITIDA);
	}

	@Test
	void siElEventoLlegaDosVecesLaSegundaNoHaceNada() {
		Prospecto ana = enFacturacion();
		when(prospectoRepository.findById(1L)).thenReturn(Optional.of(ana));

		service.alEmitirFactura(FACTURA);
		service.alEmitirFactura(FACTURA);

		assertThat(ana.getEtapa()).isEqualTo(Etapa.ENTREGA);
		assertThat(ana.getHistorial()).extracting(m -> m.getEtapa()).filteredOn(Etapa.ENTREGA::equals).hasSize(1);
	}

	@Test
	void unProspectoQueYaNoEstaEnFacturacionSeQuedaComoEsta() {
		Prospecto ana = Prospecto.registrar("Ana López", "4771234567", null, 7L, REGISTRO);
		ana.moverPorVendedor(Etapa.CERRADO, MotivoCierre.PERDIO_EL_INTERES, 7L, REGISTRO);
		when(prospectoRepository.findById(1L)).thenReturn(Optional.of(ana));

		service.alEmitirFactura(FACTURA);

		assertThat(ana.getEtapa()).isEqualTo(Etapa.CERRADO);
	}

	@Test
	void unProspectoQueNoExisteNoRompeElListener() {
		when(prospectoRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatNoException().isThrownBy(() -> service.alEmitirFactura(FACTURA));
	}

	private static Prospecto enFacturacion() {
		Prospecto ana = Prospecto.registrar("Ana López", "4771234567", null, 7L, REGISTRO);
		DatosFiscalesDePrueba.pasarAPago(ana, 7L, REGISTRO);
		ana.validarPago(30L, REGISTRO);
		return ana;
	}
}
