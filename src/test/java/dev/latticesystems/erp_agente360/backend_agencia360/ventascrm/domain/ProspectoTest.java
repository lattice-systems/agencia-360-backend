package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProspectoTest {

	private static final Long VENDEDOR = 7L;
	private static final Long CONTADOR = 30L;
	private static final Long FACTURISTA = 40L;
	private static final Instant AHORA = Instant.parse("2026-10-12T18:00:00Z");
	private static final Instant DESPUES = Instant.parse("2026-10-13T10:30:00Z");
	private static final MotivoCierre MOTIVO = MotivoCierre.SE_FUE_POR_OTRA_MARCA;

	private static Prospecto nuevo() {
		return Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);
	}

	/** Lleva el prospecto a la etapa pedida siguiendo el proceso. */
	private static Prospecto en(Etapa etapa) {
		Prospecto prospecto = nuevo();
		switch (etapa) {
			case PRIMER_CONTACTO -> {
			}
			case VISITA, COTIZACION -> prospecto.moverPorVendedor(etapa, null, VENDEDOR, AHORA);
			case PAGO -> DatosFiscalesDePrueba.pasarAPago(prospecto, VENDEDOR, AHORA);
			case FACTURACION -> {
				DatosFiscalesDePrueba.pasarAPago(prospecto, VENDEDOR, AHORA);
				prospecto.validarPago(CONTADOR, AHORA);
			}
			case ENTREGA -> {
				DatosFiscalesDePrueba.pasarAPago(prospecto, VENDEDOR, AHORA);
				prospecto.validarPago(CONTADOR, AHORA);
				prospecto.confirmarFacturacion(FACTURISTA, AHORA);
			}
			case FINALIZADO -> {
				DatosFiscalesDePrueba.pasarAPago(prospecto, VENDEDOR, AHORA);
				prospecto.validarPago(CONTADOR, AHORA);
				prospecto.confirmarFacturacion(FACTURISTA, AHORA);
				prospecto.moverPorVendedor(Etapa.FINALIZADO, null, VENDEDOR, AHORA);
			}
			case CERRADO -> prospecto.moverPorVendedor(Etapa.CERRADO, MOTIVO, VENDEDOR, AHORA);
			default -> throw new IllegalArgumentException(etapa.name());
		}
		return prospecto;
	}

	@Nested
	class Registro {

		@Test
		void seRegistraEnPrimerContactoConElVendedorQueLoCaptura() {
			Prospecto prospecto = nuevo();

			assertThat(prospecto.getNombre()).isEqualTo("Ana López");
			assertThat(prospecto.getTelefono()).isEqualTo("4771234567");
			assertThat(prospecto.getCorreo()).isNull();
			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
			assertThat(prospecto.getVendedorId()).isEqualTo(VENDEDOR);
			assertThat(prospecto.getEtapaActualizadaEn()).isEqualTo(AHORA);
			assertThat(prospecto.isCierrePendiente()).isFalse();
		}

		@Test
		void escribeElPrimerMovimientoDelHistorialConQuienLoRegistro() {
			assertThat(nuevo().getHistorial()).singleElement().satisfies(movimiento -> {
				assertThat(movimiento.getEtapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
				assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.CRM);
				assertThat(movimiento.getUsuarioId()).isEqualTo(VENDEDOR);
				assertThat(movimiento.getFechaHora()).isEqualTo(AHORA);
			});
		}

		@Test
		void aceptaSoloCorreoSinTelefono() {
			Prospecto prospecto = Prospecto.registrar("Luis Pérez", null, "luis@ejemplo.com", VENDEDOR, AHORA);

			assertThat(prospecto.getCorreo()).isEqualTo("luis@ejemplo.com");
			assertThat(prospecto.getTelefono()).isNull();
		}

		@Test
		void quitaEspaciosConvierteVaciosEnNuloYGuardaElCorreoEnMinusculas() {
			Prospecto prospecto = Prospecto.registrar("  Ana López ", "   ", " Ana@Ejemplo.COM ", VENDEDOR, AHORA);

			assertThat(prospecto.getNombre()).isEqualTo("Ana López");
			assertThat(prospecto.getTelefono()).isNull();
			assertThat(prospecto.getCorreo()).isEqualTo("ana@ejemplo.com");
		}

		@Test
		void rechazaUnProspectoSinNombre() {
			assertThatThrownBy(() -> Prospecto.registrar("  ", "4771234567", null, VENDEDOR, AHORA))
					.isInstanceOf(IllegalArgumentException.class).hasMessage("El nombre es obligatorio");
		}

		@Test
		void rechazaUnProspectoSinTelefonoNiCorreo() {
			assertThatThrownBy(() -> Prospecto.registrar("Ana López", " ", null, VENDEDOR, AHORA))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Captura al menos un teléfono o un correo");
		}

		@Test
		void normalizaUnTelefonoDeDiezDigitosEscritoConSeparadores() {
			Prospecto prospecto = Prospecto.registrar("Ana López", "(477) 123-45 67", null, VENDEDOR, AHORA);

			assertThat(prospecto.getTelefono()).isEqualTo("4771234567");
		}

		@Test
		void rechazaTelefonosQueNoSonDeDiezDigitos() {
			for (String telefono : new String[]{"47712345", "+52 477 123 4567", "477ABC4567"}) {
				assertThatThrownBy(() -> Prospecto.registrar("Ana López", telefono, null, VENDEDOR, AHORA))
						.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10 dígitos");
			}
		}
	}

	@Nested
	class ElVendedorEnLasPrimerasEtapas {

		@Test
		void avanzaYRegistraElMovimientoConSuFechaOrigenYUsuario() {
			Prospecto prospecto = nuevo();

			boolean cambio = prospecto.moverPorVendedor(Etapa.VISITA, null, VENDEDOR, DESPUES);

			assertThat(cambio).isTrue();
			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.VISITA);
			assertThat(prospecto.getEtapaActualizadaEn()).isEqualTo(DESPUES);
			assertThat(prospecto.getHistorial()).hasSize(2).last().satisfies(movimiento -> {
				assertThat(movimiento.getEtapa()).isEqualTo(Etapa.VISITA);
				assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.CRM);
				assertThat(movimiento.getUsuarioId()).isEqualTo(VENDEDOR);
				assertThat(movimiento.getFechaHora()).isEqualTo(DESPUES);
			});
		}

		@Test
		void puedeRegresarAUnaEtapaAnterior() {
			Prospecto prospecto = en(Etapa.COTIZACION);

			prospecto.moverPorVendedor(Etapa.VISITA, null, VENDEDOR, DESPUES);

			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.VISITA);
		}

		@Test
		void puedeSaltarseEtapasHastaPago() {
			Prospecto prospecto = nuevo();

			prospecto.moverPorVendedor(Etapa.PAGO, null, VENDEDOR, DESPUES);

			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.PAGO);
		}

		@ParameterizedTest
		@EnumSource(value = Etapa.class, names = {"FACTURACION", "ENTREGA", "FINALIZADO"})
		void noPuedeSaltarseElProcesoDePagoYFacturacion(Etapa destino) {
			Prospecto prospecto = en(Etapa.COTIZACION);

			assertThatThrownBy(() -> prospecto.moverPorVendedor(destino, null, VENDEDOR, DESPUES))
					.isInstanceOf(ConflictoException.class);
			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.COTIZACION);
		}

		@Test
		void pedirLaMismaEtapaNoEscribeHistorial() {
			Prospecto prospecto = nuevo();

			assertThat(prospecto.moverPorVendedor(Etapa.PRIMER_CONTACTO, null, VENDEDOR, DESPUES)).isFalse();
			assertThat(prospecto.getHistorial()).hasSize(1);
		}

		@Test
		void cierraConMotivoYGuardaLaUltimaEtapaAlcanzada() {
			Prospecto prospecto = en(Etapa.COTIZACION);

			prospecto.moverPorVendedor(Etapa.CERRADO, MOTIVO, VENDEDOR, DESPUES);

			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.CERRADO);
			assertThat(prospecto.getMotivoCierre()).isEqualTo(MOTIVO);
			assertThat(prospecto.getUltimaEtapa()).isEqualTo(Etapa.COTIZACION);
		}

		@Test
		void noPuedeCerrarSinMotivo() {
			Prospecto prospecto = nuevo();

			assertThatThrownBy(() -> prospecto.moverPorVendedor(Etapa.CERRADO, null, VENDEDOR, DESPUES))
					.isInstanceOf(IllegalArgumentException.class).hasMessage("El motivo de cierre es obligatorio");
			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
			assertThat(prospecto.getHistorial()).hasSize(1);
		}

		@Test
		void sinCategoriaNoSeEligeAlCerrarPorqueSoloEsDeLosCierresAnteriores() {
			Prospecto prospecto = nuevo();

			assertThatThrownBy(
					() -> prospecto.moverPorVendedor(Etapa.CERRADO, MotivoCierre.SIN_CATEGORIA, VENDEDOR, DESPUES))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Elige uno de los motivos de cierre: Se fue por otra marca, Perdió el interés o "
							+ "Rechazo de financiamiento");
			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
		}

		@Test
		void ignoraElMotivoCuandoNoSeCierra() {
			Prospecto prospecto = nuevo();

			prospecto.moverPorVendedor(Etapa.VISITA, MOTIVO, VENDEDOR, DESPUES);

			assertThat(prospecto.getMotivoCierre()).isNull();
		}
	}

	@Nested
	class PagoYFacturacion {

		@ParameterizedTest
		@EnumSource(value = Etapa.class, names = {"PAGO", "FACTURACION"})
		void elVendedorNoMueveNadaMientrasSeValidaYSeFactura(Etapa etapa) {
			Prospecto prospecto = en(etapa);

			for (Etapa destino : Etapa.values()) {
				if (destino != etapa) {
					assertThatThrownBy(() -> prospecto.moverPorVendedor(destino, MOTIVO, VENDEDOR, DESPUES))
							.isInstanceOf(ConflictoException.class);
				}
			}
			assertThat(prospecto.getEtapa()).isEqualTo(etapa);
		}

		@Test
		void contabilidadValidaElPagoYPasaAFacturacionDesdeElErp() {
			Prospecto prospecto = en(Etapa.PAGO);

			prospecto.validarPago(CONTADOR, DESPUES);

			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.FACTURACION);
			assertThat(prospecto.getHistorial()).last().satisfies(movimiento -> {
				assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.ERP);
				assertThat(movimiento.getUsuarioId()).isEqualTo(CONTADOR);
			});
		}

		@Test
		void sinDatosFiscalesNoPasaAFacturacion() {
			Prospecto prospecto = nuevo();
			prospecto.moverPorVendedor(Etapa.PAGO, null, VENDEDOR, AHORA);

			assertThatThrownBy(() -> prospecto.validarPago(CONTADOR, DESPUES)).isInstanceOf(ConflictoException.class)
					.hasMessageContaining("datos fiscales");
			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.PAGO);
		}

		@Test
		void elVendedorCapturaYCorrigeLosDatosFiscalesEnPago() {
			Prospecto prospecto = en(Etapa.PAGO);

			prospecto.capturarDatosFiscales(DatosFiscales.de("ABC010203XY9", "Autopartes del Bajío SA de CV",
					DatosFiscalesDePrueba.domicilioDeAna(), "601"));

			assertThat(prospecto.getDatosFiscales().getRfc()).isEqualTo("ABC010203XY9");
			assertThat(prospecto.getDatosFiscales().getRegimen()).isEqualTo(RegimenFiscal.GENERAL_PERSONAS_MORALES);
		}

		@Test
		void facturacionCorrigeLosDatosFiscalesSoloEnFacturacion() {
			Prospecto prospecto = en(Etapa.FACTURACION);
			DatosFiscales corregidos = DatosFiscales.de("ABC010203XY9", "Autopartes del Bajío SA de CV",
					DatosFiscalesDePrueba.domicilioDeAna(), "601");

			prospecto.corregirDatosFiscalesEnFacturacion(corregidos);

			assertThat(prospecto.getDatosFiscales().getRfc()).isEqualTo("ABC010203XY9");
			assertThatThrownBy(() -> en(Etapa.PAGO).corregirDatosFiscalesEnFacturacion(corregidos))
					.isInstanceOf(ConflictoException.class);
			assertThatThrownBy(() -> en(Etapa.ENTREGA).corregirDatosFiscalesEnFacturacion(corregidos))
					.isInstanceOf(ConflictoException.class);
		}

		@ParameterizedTest
		@EnumSource(value = Etapa.class, names = {"COTIZACION", "FACTURACION", "ENTREGA"})
		void losDatosFiscalesSoloSeCapturanEnPago(Etapa etapa) {
			Prospecto prospecto = en(etapa);

			assertThatThrownBy(() -> prospecto.capturarDatosFiscales(DatosFiscalesDePrueba.deAna()))
					.isInstanceOf(ConflictoException.class);
		}

		@Test
		void soloSeValidaElPagoDeUnProspectoEnPago() {
			Prospecto prospecto = en(Etapa.COTIZACION);

			assertThatThrownBy(() -> prospecto.validarPago(CONTADOR, DESPUES)).isInstanceOf(ConflictoException.class);
		}

		@Test
		void facturacionEmiteLaFacturaYPasaAEntregaDesdeElErp() {
			Prospecto prospecto = en(Etapa.FACTURACION);

			prospecto.confirmarFacturacion(FACTURISTA, DESPUES);

			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.ENTREGA);
			assertThat(prospecto.getHistorial()).last().extracting(HistorialEtapa::getOrigen)
					.isEqualTo(OrigenCambio.ERP);
		}

		@Test
		void laFacturaEmitidaAutorizaLaEntregaSinUsuarioYConLaHoraDeLaEmision() {
			Prospecto prospecto = en(Etapa.FACTURACION);

			assertThat(prospecto.autorizarEntregaPorFactura(DESPUES)).isTrue();

			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.ENTREGA);
			assertThat(prospecto.getEtapaActualizadaEn()).isEqualTo(DESPUES);
			assertThat(prospecto.getHistorial()).last().satisfies(movimiento -> {
				assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.ERP);
				assertThat(movimiento.getUsuarioId()).isNull();
			});
		}

		@ParameterizedTest
		@EnumSource(value = Etapa.class, names = {"PAGO", "ENTREGA", "FINALIZADO", "CERRADO"})
		void fueraDeFacturacionLaFacturaNoCambiaNada(Etapa etapa) {
			Prospecto prospecto = en(etapa);
			int movimientos = prospecto.getHistorial().size();

			assertThat(prospecto.autorizarEntregaPorFactura(DESPUES)).isFalse();

			assertThat(prospecto.getEtapa()).isEqualTo(etapa);
			assertThat(prospecto.getHistorial()).hasSize(movimientos);
		}

		@Test
		void soloSeFacturaUnProspectoEnFacturacion() {
			Prospecto prospecto = en(Etapa.PAGO);

			assertThatThrownBy(() -> prospecto.confirmarFacturacion(FACTURISTA, DESPUES))
					.isInstanceOf(ConflictoException.class);
		}
	}

	@Nested
	class EntregaYFinal {

		@Test
		void elVendedorFinalizaAlEntregarLaUnidad() {
			Prospecto prospecto = en(Etapa.ENTREGA);

			prospecto.moverPorVendedor(Etapa.FINALIZADO, null, VENDEDOR, DESPUES);

			assertThat(prospecto.getEtapa()).isEqualTo(Etapa.FINALIZADO);
		}

		@Test
		void desdeEntregaSoloPuedeFinalizar() {
			Prospecto prospecto = en(Etapa.ENTREGA);

			assertThatThrownBy(() -> prospecto.moverPorVendedor(Etapa.VISITA, null, VENDEDOR, DESPUES))
					.isInstanceOf(ConflictoException.class)
					.hasMessage("Desde Entrega solo puedes finalizar el prospecto");
		}

		@ParameterizedTest
		@EnumSource(value = Etapa.class, names = {"FINALIZADO", "CERRADO"})
		void unProspectoFinalizadoOCerradoYaNoCambia(Etapa etapa) {
			Prospecto prospecto = en(etapa);

			assertThatThrownBy(() -> prospecto.moverPorVendedor(Etapa.PRIMER_CONTACTO, null, VENDEDOR, DESPUES))
					.isInstanceOf(ConflictoException.class);
		}
	}

	@Test
	void elPrecioTotalEsMayorACeroYConHastaDosDecimales() {
		Prospecto prospecto = en(Etapa.PAGO);

		prospecto.fijarPrecioTotal(new BigDecimal("489900.5"));
		assertThat(prospecto.getPrecioTotal()).isEqualByComparingTo("489900.50");

		for (String precio : new String[]{"0", "-1", "10.555"}) {
			assertThatThrownBy(() -> prospecto.fijarPrecioTotal(new BigDecimal(precio)))
					.isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> prospecto.fijarPrecioTotal(null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Captura el precio total de la venta");
	}

	@Test
	void cadaMotivoTieneElNombreQueDecidioElPo() {
		assertThat(MotivoCierre.SE_FUE_POR_OTRA_MARCA.etiqueta()).isEqualTo("Se fue por otra marca");
		assertThat(MotivoCierre.PERDIO_EL_INTERES.etiqueta()).isEqualTo("Perdió el interés");
		assertThat(MotivoCierre.RECHAZO_DE_FINANCIAMIENTO.etiqueta()).isEqualTo("Rechazo de financiamiento");
		assertThat(MotivoCierre.SIN_CATEGORIA.etiqueta()).isEqualTo("Sin categoría");
	}

	@Test
	void convierteElTextoDeUnaPeticionEnEtapa() {
		assertThat(Etapa.desde("FINALIZADO")).isEqualTo(Etapa.FINALIZADO);
		assertThatThrownBy(() -> Etapa.desde("GANADO")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Etapa no válida: GANADO");
	}
}
