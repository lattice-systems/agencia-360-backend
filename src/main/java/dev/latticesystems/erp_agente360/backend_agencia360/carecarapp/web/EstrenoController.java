package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CalculadoraMensualidad;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EstrenoService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Estreno (M16): prueba de manejo y cotización de auto nuevo. */
@RestController
@RequestMapping("/api/v1/app/estreno")
@Tag(name = "App CareCar · Estreno")
public class EstrenoController {

	private final EstrenoService estreno;

	public EstrenoController(EstrenoService estreno) {
		this.estreno = estreno;
	}

	/** Lo que pide el cálculo de mensualidad. */
	public record CalculoRequest(@NotNull Long modeloCatalogoId, @NotNull BigDecimal enganche,
			@NotNull Integer plazoMeses) {
	}

	/** Lo que pide la app para crear una solicitud. */
	public record SolicitudRequest(@NotNull String tipo, @NotNull Long modeloCatalogoId, LocalDate preferenciaFecha,
			String preferenciaFranja, BigDecimal enganche, Integer plazoMeses, Long estimacionTomaCuentaId) {
	}

	@PostMapping("/cotizaciones/calculo")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Calcular la mensualidad estimada", description = "V2-M16.02 · No guarda. 404 modelo; "
			+ "409 ESTRENO_SIN_PRECIO; 400 enganche bajo o plazo no permitido.")
	public CalculadoraMensualidad.Cotizacion calcular(@Valid @RequestBody CalculoRequest request) {
		return estreno.calcular(request.modeloCatalogoId(), request.enganche(), request.plazoMeses());
	}

	@PostMapping("/solicitudes")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Pedir prueba de manejo o cotización", description = "V2-M16.01, V2-M16.03 · El CRM "
			+ "recibe un prospecto con origen APP. 404 modelo o estimación ajena; 409 ESTRENO_DUPLICADA o "
			+ "ESTRENO_SIN_PRECIO; 400 datos que no corresponden al tipo.")
	public EstrenoService.SolicitudEstrenoApp pedir(@Valid @RequestBody SolicitudRequest r,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return estreno.pedir(cliente.id(), new EstrenoService.PedidoEstreno(r.tipo(), r.modeloCatalogoId(),
				r.preferenciaFecha(), r.preferenciaFranja(), r.enganche(), r.plazoMeses(), r.estimacionTomaCuentaId()));
	}

	@GetMapping("/solicitudes")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Mis solicitudes de estreno", description = "V2-M16.04 · Estado RECIBIDA, EN_ATENCION "
			+ "(con vendedor) o CERRADA.")
	public Pagina<EstrenoService.SolicitudEstrenoApp> solicitudes(@AuthenticationPrincipal UsuarioAutenticado cliente,
			@RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano) {
		return estreno.delCliente(cliente.id(), pagina, tamano);
	}
}
