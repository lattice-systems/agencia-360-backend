package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Timbrador.Timbre;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante.Concepto;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FacturaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MedioPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PerfilFiscal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoTarjeta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FacturaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PagoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PerfilFiscalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.DetalleDelServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.RenglonDelServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CamposNoProcesablesException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facturas simuladas de los pagos de la app (V2-M08.06, ADR 0009). Se timbra
 * sola al confirmarse un pago que trae datos fiscales, o cuando el cliente
 * factura un pago que se hizo sin ellos. Un pago se factura una sola vez: los
 * dos caminos toman el candado del pago y la tabla tiene un índice único. El
 * XML y el PDF se guardan en {@code archivos}. Lo ajeno responde 404.
 */
@Service
public class FacturasAppService {

	static final String EVENTO = "factura.timbrada";
	static final String SERIE = "A";
	static final String CARPETA = "facturas";
	static final String TIPO_PDF = "application/pdf";
	static final String TIPO_XML = "application/xml";

	private static final Logger LOG = LoggerFactory.getLogger(FacturasAppService.class);

	/** Separa las claves de bloqueo de la factura de un pago. */
	private static final long PREFIJO_FACTURA = 0x4641_4354_0000_0000L;

	private final PagoAppRepository pagos;
	private final PerfilFiscalRepository perfiles;
	private final FacturaAppRepository facturas;
	private final Timbrador timbrador;
	private final FacturaAppPdf pdfs;
	private final AlmacenArchivos archivos;
	private final HistorialDeServicios historial;
	private final TiendaAppService tienda;
	private final ApplicationEventPublisher eventos;
	private final Comprobante.Emisor emisor;
	private final Clock reloj;
	private final ZoneId zona;

	@Autowired
	public FacturasAppService(PagoAppRepository pagos, PerfilFiscalRepository perfiles, FacturaAppRepository facturas,
			Timbrador timbrador, FacturaAppPdf pdfs, AlmacenArchivos archivos, HistorialDeServicios historial,
			TiendaAppService tienda, ApplicationEventPublisher eventos,
			@Value("${app.carecarapp.factura.emisor.rfc}") String rfc,
			@Value("${app.carecarapp.factura.emisor.nombre}") String nombre,
			@Value("${app.carecarapp.factura.emisor.regimen-fiscal}") String regimenFiscal,
			@Value("${app.carecarapp.factura.emisor.codigo-postal}") String codigoPostal) {
		this(pagos, perfiles, facturas, timbrador, pdfs, archivos, historial, tienda, eventos,
				new Comprobante.Emisor(rfc, nombre, regimenFiscal, codigoPostal), Clock.systemUTC(),
				ZoneId.of("America/Mexico_City"));
	}

	FacturasAppService(PagoAppRepository pagos, PerfilFiscalRepository perfiles, FacturaAppRepository facturas,
			Timbrador timbrador, FacturaAppPdf pdfs, AlmacenArchivos archivos, HistorialDeServicios historial,
			TiendaAppService tienda, ApplicationEventPublisher eventos, Comprobante.Emisor emisor, Clock reloj,
			ZoneId zona) {
		this.pagos = pagos;
		this.perfiles = perfiles;
		this.facturas = facturas;
		this.timbrador = timbrador;
		this.pdfs = pdfs;
		this.archivos = archivos;
		this.historial = historial;
		this.tienda = tienda;
		this.eventos = eventos;
		this.emisor = emisor;
		this.reloj = reloj;
		this.zona = zona;
	}

	/**
	 * Un archivo de la factura para descargar.
	 *
	 * @param nombre
	 *            el nombre con que se guarda: {@code factura-A-000124.pdf}
	 */
	public record Descarga(String nombre, String tipoContenido, byte[] contenido) {
	}

	/**
	 * Factura un pago con los datos fiscales que elige el cliente.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el pago o el perfil fiscal no existen o son de otro cliente
	 *             (404)
	 * @throws ConflictoException
	 *             con {@code YA_FACTURADO} o {@code PAGO_NO_FACTURABLE} (409)
	 * @throws CamposNoProcesablesException
	 *             con {@code TIMBRADO_RECHAZADO} y qué corregir del perfil (422)
	 */
	@Transactional
	public FacturaVista facturar(Long clienteId, Long pagoId, Long perfilFiscalId) {
		if (perfilFiscalId == null) {
			throw new IllegalArgumentException("Elige los datos fiscales con que se factura");
		}
		if (pagos.findByIdAndClienteId(pagoId, clienteId).isEmpty()) {
			throw new RecursoNoEncontradoException("No existe el pago " + pagoId);
		}
		pagos.bloquear(PREFIJO_FACTURA | pagoId);
		PagoApp pago = pagos.findByIdAndClienteId(pagoId, clienteId).orElseThrow();
		PerfilFiscal perfil = perfiles.findByIdAndClienteId(perfilFiscalId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el perfil fiscal " + perfilFiscalId));
		if (facturas.buscarPorPago(pagoId).isPresent()) {
			throw new ConflictoException("El pago ya tiene factura", CodigosError.YA_FACTURADO);
		}
		if (pago.getEstado() != EstadoPago.PAGADO) {
			throw new ConflictoException("Solo se factura un pago pagado", CodigosError.PAGO_NO_FACTURABLE);
		}
		if (pago.getMedio() == MedioPago.CAJA) {
			throw new ConflictoException("Un pago de caja se factura en la agencia", CodigosError.PAGO_NO_FACTURABLE);
		}
		return FacturaVista.de(timbrar(pago, perfil));
	}

	/**
	 * Al confirmarse un pago que trae datos fiscales, timbra su factura. Si el
	 * timbrado la rechaza, el pago queda sin factura y la app ofrece facturarlo.
	 */
	@ApplicationModuleListener
	void alConfirmarPago(PagoConfirmado confirmado) {
		pagos.bloquear(PREFIJO_FACTURA | confirmado.pagoId());
		Optional<PagoApp> encontrado = pagos.findById(confirmado.pagoId());
		if (encontrado.isEmpty()) {
			return;
		}
		PagoApp pago = encontrado.get();
		if (pago.getPerfilFiscalId() == null || pago.getEstado() != EstadoPago.PAGADO
				|| pago.getMedio() == MedioPago.CAJA || facturas.buscarPorPago(pago.getId()).isPresent()) {
			return;
		}
		Optional<PerfilFiscal> perfil = perfiles.findByIdAndClienteId(pago.getPerfilFiscalId(), pago.getClienteId());
		if (perfil.isEmpty()) {
			return;
		}
		try {
			timbrar(pago, perfil.get());
		} catch (CamposNoProcesablesException e) {
			LOG.info("El timbrado rechazó la factura del pago {}: {}", pago.getId(), e.getCampos().keySet());
		}
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la factura no existe o es de otro cliente (404)
	 */
	@Transactional(readOnly = true)
	public Descarga pdf(Long clienteId, Long facturaId) {
		FacturaApp factura = propia(clienteId, facturaId);
		return descarga(factura, factura.getPdfArchivoId(), ".pdf");
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la factura no existe o es de otro cliente (404)
	 */
	@Transactional(readOnly = true)
	public Descarga xml(Long clienteId, Long facturaId) {
		FacturaApp factura = propia(clienteId, facturaId);
		return descarga(factura, factura.getXmlArchivoId(), ".xml");
	}

	/** Timbra, guarda el XML y el PDF y avisa; el pago ya está bajo su candado. */
	private FacturaApp timbrar(PagoApp pago, PerfilFiscal perfil) {
		Instant ahora = reloj.instant();
		Comprobante comprobante = new Comprobante(SERIE, facturas.siguienteFolio(),
				LocalDateTime.ofInstant(ahora, zona).truncatedTo(ChronoUnit.SECONDS), emisor, receptor(perfil),
				conceptos(pago), formaPago(pago));
		if (comprobante.total().compareTo(pago.getMonto()) != 0) {
			throw new IllegalStateException("Los conceptos del pago " + pago.getId() + " suman " + comprobante.total()
					+ " y se pagaron " + pago.getMonto());
		}
		Timbre timbre = timbrador.timbrar(comprobante);
		byte[] pdf = pdfs.armar(comprobante, timbre);
		String clavePdf = archivos.guardar(CARPETA, TIPO_PDF, pdf).clave();
		String claveXml = archivos.guardar(CARPETA, TIPO_XML, timbre.xml()).clave();
		pago.elegirPerfilFiscal(perfil.getId(), ahora);
		pagos.saveAndFlush(pago);
		FacturaApp factura = facturas.saveAndFlush(FacturaApp.timbrada(pago, perfil.getId(), comprobante, timbre.uuid(),
				timbre.timbradoEn(), claveXml, clavePdf));
		eventos.publishEvent(new AvisoAlCliente(pago.getClienteId(), EVENTO,
				Map.of("facturaId", factura.getId(), "pagoId", pago.getId())));
		return factura;
	}

	/** Un concepto por renglón del ticket de la orden o del pedido. */
	private List<Concepto> conceptos(PagoApp pago) {
		List<Concepto> conceptos = new ArrayList<>();
		if (pago.getOrdenId() != null) {
			DetalleDelServicio detalle = historial.detalle(pago.getOrdenId()).orElseThrow(
					() -> new IllegalStateException("No existe la orden " + pago.getOrdenId() + " del pago"));
			for (RenglonDelServicio renglon : detalle.manoDeObra()) {
				conceptos.add(Concepto.manoDeObra(renglon.concepto(), renglon.cantidad(), renglon.importe()));
			}
			for (RenglonDelServicio renglon : detalle.refacciones()) {
				conceptos.add(Concepto.refaccion(renglon.concepto(), renglon.cantidad(), renglon.importe()));
			}
		} else {
			PedidoVista pedido = tienda.ver(pago.getClienteId(), pago.getPedidoId());
			for (PedidoVista.Renglon renglon : pedido.renglones()) {
				conceptos.add(Concepto.refaccion(renglon.nombre() + " (" + renglon.codigo() + ")", renglon.cantidad(),
						renglon.importe()));
			}
		}
		return conceptos;
	}

	private static Comprobante.Receptor receptor(PerfilFiscal perfil) {
		return new Comprobante.Receptor(perfil.getRfc(), perfil.getRazonSocial(), perfil.getCodigoPostal(),
				perfil.getRegimenFiscal(), perfil.getUsoCfdi());
	}

	/** Débito es la forma 28; crédito y las billeteras, la 04 (ADR 0009). */
	private static String formaPago(PagoApp pago) {
		return pago.getTipoTarjeta() == TipoTarjeta.DEBITO ? Comprobante.FORMA_DEBITO : Comprobante.FORMA_CREDITO;
	}

	private FacturaApp propia(Long clienteId, Long facturaId) {
		return facturas.findByIdAndClienteId(facturaId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la factura " + facturaId));
	}

	private Descarga descarga(FacturaApp factura, String clave, String extension) {
		AlmacenArchivos.Archivo archivo = archivos.leer(clave).orElseThrow(
				() -> new RecursoNoEncontradoException("No se encontró el archivo de la factura " + factura.getId()));
		return new Descarga("factura-" + factura.getFolio() + extension, archivo.tipoContenido(), archivo.contenido());
	}
}
