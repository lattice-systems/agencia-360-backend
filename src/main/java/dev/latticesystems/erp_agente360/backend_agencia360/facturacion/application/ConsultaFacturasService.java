package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application;

import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.Factura;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.infrastructure.FacturaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.ProspectoParaFacturar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de la pantalla Facturas del ERP (ERP-1.3): los prospectos que
 * esperan su factura y las facturas emitidas con su PDF.
 */
@Service
public class ConsultaFacturasService {

	private final FacturaRepository facturaRepository;
	private final ConsultaFacturacion consultaFacturacion;

	public ConsultaFacturasService(FacturaRepository facturaRepository, ConsultaFacturacion consultaFacturacion) {
		this.facturaRepository = facturaRepository;
		this.consultaFacturacion = consultaFacturacion;
	}

	/**
	 * Los prospectos en Facturación sin factura, del más reciente al más antiguo.
	 * Uno ya facturado sigue en Facturación hasta que ventascrm atiende
	 * FacturaEmitida; no se muestra aquí.
	 */
	@Transactional(readOnly = true)
	public List<ProspectoPorEmitir> porEmitir() {
		List<ProspectoParaFacturar> prospectos = consultaFacturacion.enFacturacion();
		Set<Long> facturados = new HashSet<>(facturaRepository
				.prospectosFacturados(prospectos.stream().map(ProspectoParaFacturar::prospectoId).toList()));
		return prospectos.stream().filter(p -> !facturados.contains(p.prospectoId()))
				.map(p -> new ProspectoPorEmitir(p.prospectoId(), p.nombre(), p.vendedorId(), p.vendedor(), p.total(),
						p.saldo(), p.enFacturacionDesde(), p.receptor(), ReglasDeEmision.bloqueo(p).orElse(null)))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<FacturaVista> emitidas() {
		return facturaRepository.findAllByOrderByEmitidaEnDescIdDesc().stream().map(FacturaVista::de).toList();
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si no existe la factura (404)
	 */
	@Transactional(readOnly = true)
	public PdfDeFactura pdf(Long facturaId) {
		Factura factura = facturaRepository.findById(facturaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la factura " + facturaId));
		return new PdfDeFactura(factura.getFolio(), factura.getPdf());
	}

	/** El PDF tal como se emitió, con el folio para nombrar el archivo. */
	public record PdfDeFactura(String folio, byte[] contenido) {
	}
}
