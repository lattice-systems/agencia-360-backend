package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CanjeApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.LealtadAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.MovimientoPuntosApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.RecompensaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ReferidoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ReferidosAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ResumenLealtadApp;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lealtad y recompensas de la app (V2-M12.01 a V2-M12.05). */
@RestController
@RequestMapping("/api/v1/app/lealtad")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
@Tag(name = "App CareCar - Lealtad", description = "Puntos, nivel, recompensas, canjes y referidos")
public class LealtadAppController {

	private final LealtadAppService lealtad;
	private final ReferidosAppService referidos;

	public LealtadAppController(LealtadAppService lealtad, ReferidosAppService referidos) {
		this.lealtad = lealtad;
		this.referidos = referidos;
	}

	@GetMapping
	@Operation(summary = "Mis puntos y nivel", description = "Saldo (suma de movimientos), nivel por los puntos "
			+ "ganados en 12 meses, lo que falta para el siguiente y el código para referir.")
	public ResumenLealtadApp resumen(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return lealtad.resumen(cliente.id());
	}

	@GetMapping("/movimientos")
	@Operation(summary = "Mis movimientos de puntos", description = "Del más reciente al más viejo, paginados.")
	public Pagina<MovimientoPuntosApp> movimientos(@RequestParam(required = false) Integer pagina,
			@RequestParam(required = false) Integer tamano, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return lealtad.movimientos(cliente.id(), pagina, tamano);
	}

	@GetMapping("/recompensas")
	@Operation(summary = "Recompensas", description = "Las activas, de la más barata a la más cara.")
	public List<RecompensaApp> recompensas() {
		return lealtad.recompensas();
	}

	@PostMapping("/canjes")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Canjear una recompensa", description = "Descuenta los puntos y regresa un código vigente "
			+ "para mostrar como QR. Responde 404 si la recompensa no existe, 409 SALDO_INSUFICIENTE o "
			+ "RECOMPENSA_AGOTADA.")
	public CanjeApp canjear(@Valid @RequestBody CanjearRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return lealtad.canjear(cliente.id(), request.recompensaId());
	}

	@GetMapping("/canjes")
	@Operation(summary = "Mis canjes", description = "Del más nuevo al más viejo, con su código y estado.")
	public List<CanjeApp> canjes(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return lealtad.canjes(cliente.id());
	}

	@PostMapping("/referidos")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Referir a un amigo", description = "Pide nombre, teléfono o correo y la confirmación de "
			+ "que el amigo aceptó que lo contacten. Responde 400 si falta algo, 409 REFERIDO_DUPLICADO si ya es "
			+ "cliente o ya lo refirieron y 409 LIMITE_REFERIDOS si hay demasiados en seguimiento.")
	public ReferidoApp referir(@Valid @RequestBody ReferirRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return referidos.referir(cliente.id(), request.nombre(), request.telefono(), request.correo());
	}

	@GetMapping("/referidos")
	@Operation(summary = "Mis referidos", description = "Del más nuevo al más viejo, con su estado.")
	public List<ReferidoApp> referidos(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return referidos.referidos(cliente.id());
	}
}
