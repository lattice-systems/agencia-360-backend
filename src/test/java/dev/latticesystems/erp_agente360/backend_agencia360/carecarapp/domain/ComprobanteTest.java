package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante.Concepto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El desglose del IVA de la factura de un pago de la app (V2-M08.06). */
class ComprobanteTest {

	private static final Comprobante.Emisor EMISOR = new Comprobante.Emisor("EKU9003173C9", "ESCUELA KEMPER URGATE",
			"601", "42501");
	private static final Comprobante.Receptor RECEPTOR = new Comprobante.Receptor("GODE561231GR8", "DOLORES GÓMEZ",
			"37000", "612", "G03");

	@Test
	void cadaConceptoSeDesglosaEnBaseEIvaQueSumanLoPagado() {
		Concepto afinacion = Concepto.manoDeObra("Afinación", 1, new BigDecimal("1200.00"));

		assertThat(afinacion.importe()).isEqualByComparingTo("1034.48");
		assertThat(afinacion.iva()).isEqualByComparingTo("165.52");
		assertThat(afinacion.importeConIva()).isEqualByComparingTo("1200.00");
		assertThat(afinacion.claveProdServ()).isEqualTo("78181500");
		assertThat(afinacion.claveUnidad()).isEqualTo("E48");

		Concepto filtros = Concepto.refaccion("Filtro de aceite", 3, new BigDecimal("450.00"));
		assertThat(filtros.valorUnitario()).isEqualByComparingTo("129.310000");
		assertThat(filtros.claveProdServ()).isEqualTo("25174800");
		assertThat(filtros.claveUnidad()).isEqualTo("H87");
	}

	@Test
	void elTotalEsLaSumaDeLoPagadoAunqueElRedondeoNoCuadrePorConcepto() {
		Comprobante comprobante = new Comprobante("A", 124, LocalDateTime.of(2026, 11, 26, 12, 0), EMISOR, RECEPTOR,
				List.of(Concepto.manoDeObra("Afinación", 1, new BigDecimal("1200.00")),
						Concepto.refaccion("Filtro de aceite", 3, new BigDecimal("450.00")),
						Concepto.refaccion("Tornillo", 7, new BigDecimal("0.07"))),
				Comprobante.FORMA_CREDITO);

		assertThat(comprobante.subtotal()).isEqualByComparingTo("1422.47");
		assertThat(comprobante.iva()).isEqualByComparingTo("227.60");
		assertThat(comprobante.total()).isEqualByComparingTo("1650.07");
		assertThat(comprobante.folioCompleto()).isEqualTo("A-000124");
		assertThat(comprobante.folioSinSerie()).isEqualTo("000124");
	}

	@Test
	void sinConceptosOConUnImporteEnCeroNoHayFactura() {
		assertThatThrownBy(() -> new Comprobante("A", 1, LocalDateTime.now(), EMISOR, RECEPTOR, List.of(),
				Comprobante.FORMA_CREDITO)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Concepto.refaccion("Nada", 1, BigDecimal.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Concepto.refaccion("Nada", 0, BigDecimal.TEN))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unaPersonaMoralTieneRfcDeDoce() {
		assertThat(RECEPTOR.esMoral()).isFalse();
		assertThat(new Comprobante.Receptor("EKU9003173C9", "ESCUELA KEMPER URGATE", "42501", "601", "G03").esMoral())
				.isTrue();
	}
}
