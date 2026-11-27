package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos.Archivo;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos.ArchivoGuardado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DatosFiscales;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FacturaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MedioPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ObjetoFacturado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp.Cobrable;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PerfilFiscal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TarjetaDePrueba;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FacturaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PagoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PerfilFiscalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.TimbradorSimulado;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.DetalleDelServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.RenglonDelServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CamposNoProcesablesException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Facturar un pago de la app (V2-M08.06) con el timbrador simulado y el PDF
 * reales, y lo demás de mentira.
 */
class FacturasAppServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T18:00:00Z");
	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
	private static final Long CLIENTE = 3L;
	private static final Long ORDEN = 41L;
	private static final Long PEDIDO = 15L;

	private final PagoAppRepository pagos = mock(PagoAppRepository.class);
	private final PerfilFiscalRepository perfiles = mock(PerfilFiscalRepository.class);
	private final FacturaAppRepository facturas = mock(FacturaAppRepository.class);
	private final AlmacenArchivos archivos = mock(AlmacenArchivos.class);
	private final HistorialDeServicios historial = mock(HistorialDeServicios.class);
	private final TiendaAppService tienda = mock(TiendaAppService.class);
	private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
	private final List<Object> publicados = new ArrayList<>();
	private final List<String> guardados = new ArrayList<>();
	private final AtomicLong ids = new AtomicLong(100);

	private FacturasAppService servicio;

	@BeforeEach
	void preparar() {
		Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
		servicio = new FacturasAppService(pagos, perfiles, facturas, new TimbradorSimulado(reloj, ZONA),
				new FacturaAppPdf(), archivos, historial, tienda, eventos,
				new Comprobante.Emisor("EKU9003173C9", "ESCUELA KEMPER URGATE", "601", "42501"), reloj, ZONA);
		when(facturas.siguienteFolio()).thenReturn(124L);
		when(facturas.buscarPorPago(anyLong())).thenReturn(Optional.empty());
		when(facturas.saveAndFlush(any(FacturaApp.class))).thenAnswer(invocacion -> conId(invocacion.getArgument(0)));
		when(pagos.saveAndFlush(any(PagoApp.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		when(archivos.guardar(eq("facturas"), anyString(), any(byte[].class))).thenAnswer(invocacion -> {
			String clave = "facturas/" + invocacion.getArgument(1, String.class).replace('/', '-');
			guardados.add(invocacion.getArgument(1));
			return new ArchivoGuardado(clave, invocacion.getArgument(1), 1);
		});
		when(historial.detalle(ORDEN)).thenReturn(Optional.of(new DetalleDelServicio(ORDEN, 9L,
				LocalDate.of(2026, 11, 26), 15200, EstatusOrden.TERMINADO, AHORA,
				List.of(new RenglonDelServicio("Afinación", 1, new BigDecimal("1200.00"), new BigDecimal("1200.00"))),
				List.of(new RenglonDelServicio("Filtro de aceite", 3, new BigDecimal("150.00"),
						new BigDecimal("450.00"))),
				new BigDecimal("1200.00"), new BigDecimal("450.00"), new BigDecimal("1422.41"),
				new BigDecimal("227.59"), new BigDecimal("1650.00"))));
		doAnswer(invocacion -> publicados.add(invocacion.getArgument(0))).when(eventos).publishEvent(any(Object.class));
	}

	@Test
	void facturaUnPagoConLosDatosQueEligeYAvisa() {
		PagoApp pago = pagado(null);
		PerfilFiscal perfil = perfil(10L, "GODE561231GR8", "Dolores Gómez", "612", "37000");

		FacturaVista factura = servicio.facturar(CLIENTE, pago.getId(), 10L);

		assertThat(factura.folio()).isEqualTo("A-000124");
		assertThat(factura.total()).isEqualByComparingTo("1650.00");
		assertThat(factura.rfcReceptor()).isEqualTo(perfil.getRfc());
		assertThat(factura.razonSocialReceptor()).isEqualTo("DOLORES GÓMEZ");
		assertThat(factura.usoCfdi()).isEqualTo("G03");
		assertThat(factura.simulada()).isTrue();
		assertThat(factura.uuid()).isNotNull();
		assertThat(guardados).containsExactly("application/pdf", "application/xml");
		assertThat(pago.getPerfilFiscalId()).isEqualTo(10L);
		assertThat(pago.facturable()).isFalse();
		assertThat(pago.getFactura().getTipo()).isEqualTo(ObjetoFacturado.SERVICIO);
		AvisoAlCliente aviso = (AvisoAlCliente) publicados.getFirst();
		assertThat(aviso.evento()).isEqualTo("factura.timbrada");
		assertThat(aviso.datos()).containsEntry("facturaId", factura.id()).containsEntry("pagoId", pago.getId());
		verify(pagos).bloquear(anyLong());
	}

	@Test
	void unPagoSeFacturaUnaVezYSoloSiEstaPagadoYNoEsDeCaja() {
		PagoApp pago = pagado(null);
		perfil(10L, "GODE561231GR8", "Dolores Gómez", "612", "37000");
		FacturaApp previa = mock(FacturaApp.class);
		when(facturas.buscarPorPago(pago.getId())).thenReturn(Optional.of(previa));
		assertThatThrownBy(() -> servicio.facturar(CLIENTE, pago.getId(), 10L)).isInstanceOfSatisfying(
				ConflictoException.class, e -> assertThat(e.getCodigo()).isEqualTo(CodigosError.YA_FACTURADO));

		PagoApp pendiente = conId(PagoApp.conTarjeta(new Cobrable(ORDEN, 9L, CLIENTE, new BigDecimal("1650.00")),
				"SIMULADO", "sim_pi_2", "llave-2", TarjetaDePrueba.deToken("tok_sim_3ds").orElseThrow(),
				YearMonth.of(2028, 12), null, false, null, AHORA));
		when(pagos.findByIdAndClienteId(pendiente.getId(), CLIENTE)).thenReturn(Optional.of(pendiente));
		assertThatThrownBy(() -> servicio.facturar(CLIENTE, pendiente.getId(), 10L)).isInstanceOfSatisfying(
				ConflictoException.class, e -> assertThat(e.getCodigo()).isEqualTo(CodigosError.PAGO_NO_FACTURABLE));

		PagoApp enCaja = conId(
				PagoApp.enCaja(new Cobrable(ORDEN, 9L, CLIENTE, new BigDecimal("1650.00")), "caja_1", null, 7L, AHORA));
		when(pagos.findByIdAndClienteId(enCaja.getId(), CLIENTE)).thenReturn(Optional.of(enCaja));
		assertThatThrownBy(() -> servicio.facturar(CLIENTE, enCaja.getId(), 10L)).isInstanceOfSatisfying(
				ConflictoException.class, e -> assertThat(e.getCodigo()).isEqualTo(CodigosError.PAGO_NO_FACTURABLE));
		verify(archivos, never()).guardar(anyString(), anyString(), any(byte[].class));
	}

	@Test
	void loAjenoNoExisteYSinPerfilNoSeFactura() {
		PagoApp pago = pagado(null);
		assertThatThrownBy(() -> servicio.facturar(CLIENTE, pago.getId(), 99L))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.facturar(4L, pago.getId(), 10L))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.facturar(CLIENTE, pago.getId(), null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servicio.pdf(CLIENTE, 77L)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void unRechazoDelTimbradoDiceQueCorregirYNoGuardaNada() {
		PagoApp pago = pagado(null);
		perfil(10L, "EKU9003173C9", "Escuela Kemper Urgate SA de CV", "601", "42501");

		assertThatThrownBy(() -> servicio.facturar(CLIENTE, pago.getId(), 10L))
				.isInstanceOfSatisfying(CamposNoProcesablesException.class, e -> {
					assertThat(e.getCodigo()).isEqualTo(CodigosError.TIMBRADO_RECHAZADO);
					assertThat(e.getCampos()).containsOnlyKeys("razonSocial");
				});
		verify(archivos, never()).guardar(anyString(), anyString(), any(byte[].class));
		verify(facturas, never()).saveAndFlush(any(FacturaApp.class));
		assertThat(pago.facturable()).isTrue();
	}

	@Test
	void alConfirmarseUnPagoConDatosFiscalesSeTimbraSola() {
		PagoApp pago = pagado(10L);
		perfil(10L, "GODE561231GR8", "Dolores Gómez", "612", "37000");
		when(pagos.findById(pago.getId())).thenReturn(Optional.of(pago));

		servicio.alConfirmarPago(new PagoConfirmado(pago.getId(), ORDEN, CLIENTE, pago.getMonto(), null));

		verify(facturas).saveAndFlush(any(FacturaApp.class));
		assertThat(pago.getFactura()).isNotNull();
	}

	@Test
	void alConfirmarseSinDatosFiscalesOConUnRechazoNoPasaNada() {
		PagoApp sinPerfil = pagado(null);
		when(pagos.findById(sinPerfil.getId())).thenReturn(Optional.of(sinPerfil));
		servicio.alConfirmarPago(new PagoConfirmado(sinPerfil.getId(), ORDEN, CLIENTE, sinPerfil.getMonto(), null));

		PagoApp conRechazo = pagado(11L);
		perfil(11L, "EKU9003173C9", "Escuela Kemper Urgate, S.A. de C.V.", "601", "42501");
		when(pagos.findById(conRechazo.getId())).thenReturn(Optional.of(conRechazo));
		servicio.alConfirmarPago(new PagoConfirmado(conRechazo.getId(), ORDEN, CLIENTE, conRechazo.getMonto(), null));

		verify(facturas, never()).saveAndFlush(any(FacturaApp.class));
		assertThat(conRechazo.facturable()).isTrue();
	}

	@Test
	void elPagoDeUnPedidoSeFacturaConSusRefaccionesYConDebito() {
		PagoApp pago = conId(PagoApp.conTarjeta(Cobrable.dePedido(PEDIDO, CLIENTE, new BigDecimal("402.00")),
				"SIMULADO", "sim_pi_3", "llave-3", TarjetaDePrueba.deToken("tok_sim_visa_debito").orElseThrow(),
				YearMonth.of(2028, 12), null, false, null, AHORA));
		pago.aprobar(AHORA);
		when(pagos.findByIdAndClienteId(pago.getId(), CLIENTE)).thenReturn(Optional.of(pago));
		perfil(10L, "GODE561231GR8", "Dolores Gómez", "612", "37000");
		when(tienda.ver(CLIENTE, PEDIDO)).thenReturn(new PedidoVista(PEDIDO, "apartado", "mostrador",
				new BigDecimal("402.00"), AHORA, null, null, null, null, null, null, List.of(new PedidoVista.Renglon(5L,
						"FIL-001", "Filtro de aire", 2, new BigDecimal("201.00"), new BigDecimal("402.00"))),
				null, false, null, AHORA));

		FacturaVista factura = servicio.facturar(CLIENTE, pago.getId(), 10L);

		assertThat(factura.total()).isEqualByComparingTo("402.00");
		assertThat(pago.getFactura().getTipo()).isEqualTo(ObjetoFacturado.PEDIDO_APP);
		assertThat(pago.getFactura().getPedidoId()).isEqualTo(PEDIDO);
		assertThat(pago.getFactura().getFormaPago()).isEqualTo("28");
	}

	@Test
	void descargaElPdfYElXmlConElFolioEnElNombre() {
		FacturaApp factura = mock(FacturaApp.class);
		when(factura.getId()).thenReturn(5L);
		when(factura.getFolio()).thenReturn("A-000124");
		when(factura.getPdfArchivoId()).thenReturn("facturas/pdf");
		when(factura.getXmlArchivoId()).thenReturn("facturas/xml");
		when(facturas.findByIdAndClienteId(5L, CLIENTE)).thenReturn(Optional.of(factura));
		when(archivos.leer("facturas/pdf"))
				.thenReturn(Optional.of(new Archivo("facturas/pdf", "application/pdf", new byte[]{1})));
		when(archivos.leer("facturas/xml"))
				.thenReturn(Optional.of(new Archivo("facturas/xml", "application/xml", new byte[]{2})));

		FacturasAppService.Descarga pdf = servicio.pdf(CLIENTE, 5L);
		FacturasAppService.Descarga xml = servicio.xml(CLIENTE, 5L);

		assertThat(pdf.nombre()).isEqualTo("factura-A-000124.pdf");
		assertThat(pdf.tipoContenido()).isEqualTo("application/pdf");
		assertThat(xml.nombre()).isEqualTo("factura-A-000124.xml");
		assertThat(xml.contenido()).containsExactly(2);
	}

	@Test
	void conUnaBilleteraLaFormaDePagoEsCredito() {
		PagoApp pago = conId(PagoApp.conBilletera(new Cobrable(ORDEN, 9L, CLIENTE, new BigDecimal("1650.00")),
				"SIMULADO", "sim_pi_4", "llave-4", MedioPago.APPLE_PAY,
				TarjetaDePrueba.deBilletera(MedioPago.APPLE_PAY), null, AHORA));
		pago.aprobar(AHORA);
		when(pagos.findByIdAndClienteId(pago.getId(), CLIENTE)).thenReturn(Optional.of(pago));
		perfil(10L, "GODE561231GR8", "Dolores Gómez", "612", "37000");

		servicio.facturar(CLIENTE, pago.getId(), 10L);

		assertThat(pago.getFactura().getFormaPago()).isEqualTo("04");
	}

	private PagoApp pagado(Long perfilFiscalId) {
		PagoApp pago = conId(PagoApp.conTarjeta(new Cobrable(ORDEN, 9L, CLIENTE, new BigDecimal("1650.00")), "SIMULADO",
				"sim_pi_" + ids.get(), "llave-" + ids.get(), TarjetaDePrueba.deToken("tok_sim_visa").orElseThrow(),
				YearMonth.of(2028, 12), null, false, perfilFiscalId, AHORA));
		pago.aprobar(AHORA);
		when(pagos.findByIdAndClienteId(pago.getId(), CLIENTE)).thenReturn(Optional.of(pago));
		return pago;
	}

	private PerfilFiscal perfil(Long id, String rfc, String razonSocial, String regimen, String codigoPostal) {
		PerfilFiscal perfil = new PerfilFiscal(CLIENTE,
				DatosFiscales.validar(rfc, razonSocial, regimen, codigoPostal, "G03", null), AHORA);
		ReflectionTestUtils.setField(perfil, "id", id);
		when(perfiles.findByIdAndClienteId(id, CLIENTE)).thenReturn(Optional.of(perfil));
		return perfil;
	}

	private <T> T conId(T entidad) {
		if (ReflectionTestUtils.getField(entidad, "id") == null) {
			ReflectionTestUtils.setField(entidad, "id", ids.incrementAndGet());
		}
		return entidad;
	}
}
