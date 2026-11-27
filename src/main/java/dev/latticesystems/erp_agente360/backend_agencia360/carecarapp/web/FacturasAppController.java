package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.FacturaVista;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.FacturasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facturar un pago y descargar su factura en PDF y XML (V2-M08.06). El timbrado
 * es simulado (ADR 0009): la factura no tiene validez fiscal.
 */
@RestController
@RequestMapping("/api/v1/app")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
@Tag(name = "App CareCar - Pagos y facturas", description = "Datos fiscales, pagos y facturas simulados (ADR 0009)")
public class FacturasAppController {

	private final FacturasAppService facturas;

	public FacturasAppController(FacturasAppService facturas) {
		this.facturas = facturas;
	}

	@PostMapping("/pagos/{pagoId}/factura")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Facturar un pago", description = "Timbra (simulado) la factura de un pago PAGADO. 409 "
			+ "YA_FACTURADO o PAGO_NO_FACTURABLE; 422 TIMBRADO_RECHAZADO con qué corregir del perfil fiscal.")
	public FacturaVista facturar(@PathVariable Long pagoId, @Valid @RequestBody NuevaFacturaRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return facturas.facturar(cliente.id(), pagoId, request.perfilFiscalId());
	}

	@GetMapping("/facturas/{facturaId}/pdf")
	@Operation(summary = "Descargar el PDF de una factura", description = "PDF en el cuerpo. 404 si es ajena.")
	public ResponseEntity<byte[]> pdf(@PathVariable Long facturaId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return archivo(facturas.pdf(cliente.id(), facturaId));
	}

	@GetMapping("/facturas/{facturaId}/xml")
	@Operation(summary = "Descargar el XML de una factura", description = "XML con la estructura de CFDI 4.0 y "
			+ "timbre simulado. 404 si es ajena.")
	public ResponseEntity<byte[]> xml(@PathVariable Long facturaId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return archivo(facturas.xml(cliente.id(), facturaId));
	}

	private static ResponseEntity<byte[]> archivo(FacturasAppService.Descarga descarga) {
		ContentDisposition disposicion = ContentDisposition.attachment().filename(descarga.nombre()).build();
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(descarga.tipoContenido()))
				.cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
				.body(descarga.contenido());
	}
}
