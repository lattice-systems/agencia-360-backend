package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.BitacoraAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.GastoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bitácora del conductor: gastos del vehículo (V2-M18.01 y V2-M18.04). */
@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App CareCar - Bitácora", description = "Gastos del vehículo: gasolina, estacionamiento, casetas y más")
public class BitacoraAppController {

	private final BitacoraAppService bitacoraAppService;

	public BitacoraAppController(BitacoraAppService bitacoraAppService) {
		this.bitacoraAppService = bitacoraAppService;
	}

	@GetMapping("/vehiculos/{vehiculoId}/gastos")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Gastos del vehículo", description = "Más recientes primero, paginados. Con mes=AAAA-MM "
			+ "solo los de ese mes. Responde 404 si el vehículo no es del cliente.")
	public Pagina<GastoApp> listar(@PathVariable Long vehiculoId, @RequestParam(required = false) String mes,
			@RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return bitacoraAppService.listar(cliente.id(), vehiculoId, mes, pagina, tamano);
	}

	@PostMapping("/vehiculos/{vehiculoId}/gastos")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Registrar un gasto", description = "La gasolina exige litros y km y crea una lectura de "
			+ "km del cliente; otro exige descripción. Responde 404 si el vehículo no es del cliente y 422 con "
			+ "KM_INVALIDO si el km es menor a la última lectura.")
	public GastoApp registrar(@PathVariable Long vehiculoId, @Valid @RequestBody NuevoGastoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return bitacoraAppService.registrar(cliente.id(), vehiculoId, request.tipo(), request.monto(), request.litros(),
				request.km(), request.fecha(), request.descripcion());
	}

	@PutMapping("/gastos/{gastoId}")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Corregir un gasto", description = "Reemplaza el gasto con las mismas reglas que al "
			+ "crearlo. Responde 404 si no es del cliente.")
	public GastoApp corregir(@PathVariable Long gastoId, @Valid @RequestBody NuevoGastoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return bitacoraAppService.corregir(cliente.id(), gastoId, request.tipo(), request.monto(), request.litros(),
				request.km(), request.fecha(), request.descripcion());
	}

	@DeleteMapping("/gastos/{gastoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Borrar un gasto", description = "La lectura de km que creó una carga de gasolina se "
			+ "conserva. Responde 404 si no es del cliente.")
	public void borrar(@PathVariable Long gastoId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		bitacoraAppService.borrar(cliente.id(), gastoId);
	}
}
