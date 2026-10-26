package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.web;

import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application.ConsultaFacturasService;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application.ConsultaFacturasService.PdfDeFactura;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application.EmisionFacturaService;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application.FacturaVista;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application.ProspectoPorEmitir;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facturas de la venta de unidades (ERP-1.3): folio interno y PDF, sin
 * timbrado. Facturación emite; el Administrador también consulta.
 */
@RestController
@RequestMapping("/api/v1/facturas")
@Tag(name = "Facturas", description = "Emisión de la factura de la venta de una unidad, con folio interno y PDF")
public class FacturaController {

	private final EmisionFacturaService emisionFacturaService;
	private final ConsultaFacturasService consultaFacturasService;

	public FacturaController(EmisionFacturaService emisionFacturaService,
			ConsultaFacturasService consultaFacturasService) {
		this.emisionFacturaService = emisionFacturaService;
		this.consultaFacturasService = consultaFacturasService;
	}

	@GetMapping("/por-emitir")
	@PreAuthorize("hasRole('FACTURACION')")
	@Operation(summary = "Listar los prospectos por facturar", description = "Los prospectos en Facturación que "
			+ "aún no tienen factura, del más reciente al más antiguo, con su total, saldo y datos fiscales. "
			+ "bloqueo dice por qué todavía no se puede emitir (saldo, precio o datos fiscales); vacío si ya se puede.")
	public List<ProspectoPorEmitir> porEmitir() {
		return consultaFacturasService.porEmitir();
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('FACTURACION', 'ADMINISTRADOR')")
	@Operation(summary = "Listar las facturas emitidas", description = "De la más reciente a la más antigua.")
	public List<FacturaVista> listar() {
		return consultaFacturasService.emitidas();
	}

	@PostMapping
	@PreAuthorize("hasRole('FACTURACION')")
	@Operation(summary = "Emitir la factura de un prospecto", description = "En una sola transacción: la unidad pasa "
			+ "de Disponible a Vendido, se registra la venta, se asigna el folio y se guarda el PDF. Publica "
			+ "FacturaEmitida y el prospecto pasa a Entrega. Responde 400 si faltan datos, 404 si no existe el "
			+ "prospecto o la unidad, y 409 si el prospecto no está en Facturación, ya tiene factura, tiene saldo, le "
			+ "faltan datos fiscales o la unidad ya no está disponible.")
	public ResponseEntity<FacturaVista> emitir(@Valid @RequestBody EmitirFacturaRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		FacturaVista emitida = emisionFacturaService.emitir(request.prospectoId(), request.vehiculoId(), actor);
		return ResponseEntity.created(URI.create("/api/v1/facturas/" + emitida.id())).body(emitida);
	}

	@GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@PreAuthorize("hasAnyRole('FACTURACION', 'ADMINISTRADOR')")
	@Operation(summary = "Descargar el PDF de una factura", description = "El PDF tal como se emitió. Responde 404 "
			+ "si no existe la factura.")
	public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
		PdfDeFactura pdf = consultaFacturasService.pdf(id);
		ContentDisposition archivo = ContentDisposition.attachment().filename("factura-" + pdf.folio() + ".pdf")
				.build();
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, archivo.toString()).body(pdf.contenido());
	}
}
