package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PagoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PagosAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * La caja marca pagada una orden que se cobró en el mostrador, para que nadie la
 * pague otra vez en la app (V2-M08.02). Mientras el ERP siga congelado se opera
 * con Swagger UI en local o con un cliente HTTP.
 */
@RestController
@RequestMapping("/api/v1/caja/ordenes")
@PreAuthorize("hasAnyRole('CAJA', 'ADMINISTRADOR')")
@Tag(name = "Caja - Pagos de la app", description = "Órdenes cobradas en el mostrador")
public class PagosCajaController {

	private final PagosAppService pagos;

	public PagosCajaController(PagosAppService pagos) {
		this.pagos = pagos;
	}

	@PostMapping("/{ordenId}/pagada-en-caja")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Marcar una orden pagada en caja", description = "201 con el pago. 409 ORDEN_NO_PAGABLE si "
			+ "aún no está lista, no tiene total o ya está pagada.")
	public PagoVista pagadaEnCaja(@PathVariable Long ordenId,
			@Valid @RequestBody(required = false) PagoEnCajaRequest request,
			@AuthenticationPrincipal UsuarioAutenticado personal) {
		return pagos.pagarEnCaja(ordenId, personal.id(), request == null ? null : request.referencia());
	}
}
