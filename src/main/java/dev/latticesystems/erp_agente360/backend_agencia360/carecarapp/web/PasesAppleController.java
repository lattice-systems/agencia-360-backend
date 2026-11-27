package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PasesAppleService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PasesAppleService.RespuestaPase;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PasesAppleService.ResultadoRegistro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Servicio web que Apple Wallet exige para actualizar el pase de una cita
 * (V2-M25.06), según el PassKit Web Service. Vive en {@code /v1}, fuera de
 * {@code /api/v1/app}, porque Apple fija la ruta; no usa el JWT de la app: cada
 * llamada trae el {@code authenticationToken} del pase como
 * {@code Authorization: ApplePass <token>}. Solo responde si hay credenciales
 * de Apple y una URL pública HTTPS configurada.
 */
@RestController
@RequestMapping("/v1")
@Tag(name = "Apple Wallet", description = "Servicio web de pases de Apple (lo llama Wallet, no la app)")
public class PasesAppleController {

	static final String TIPO_PKPASS = "application/vnd.apple.pkpass";
	private static final String PREFIJO_AUTORIZACION = "ApplePass ";

	private final PasesAppleService servicio;

	public PasesAppleController(PasesAppleService servicio) {
		this.servicio = servicio;
	}

	/** Cuerpo del registro: el push token del dispositivo para este pase. */
	public record RegistroPase(String pushToken) {
	}

	/** Errores que Wallet reporta desde el dispositivo. */
	public record RegistroDeLogs(List<String> logs) {
	}

	@PostMapping("/devices/{dispositivo}/registrations/{passTypeId}/{serial}")
	@Operation(summary = "Registrar un dispositivo para un pase", description = "201 si es nuevo, 200 si ya estaba "
			+ "registrado, 401 con un token que no es del pase y 404 si el servicio está apagado.")
	public ResponseEntity<Void> registrar(@PathVariable String dispositivo, @PathVariable String passTypeId,
			@PathVariable String serial,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion,
			@RequestBody RegistroPase cuerpo) {
		ResultadoRegistro resultado = servicio.registrar(dispositivo, passTypeId, serial, token(autorizacion),
				cuerpo == null ? null : cuerpo.pushToken());
		return respuesta(resultado);
	}

	@DeleteMapping("/devices/{dispositivo}/registrations/{passTypeId}/{serial}")
	@Operation(summary = "Dar de baja un dispositivo", description = "200 aunque no estuviera registrado.")
	public ResponseEntity<Void> darDeBaja(@PathVariable String dispositivo, @PathVariable String passTypeId,
			@PathVariable String serial,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {
		return respuesta(servicio.darDeBaja(dispositivo, passTypeId, serial, token(autorizacion)));
	}

	@GetMapping("/devices/{dispositivo}/registrations/{passTypeId}")
	@Operation(summary = "Pases del dispositivo que cambiaron", description = "Wallet no manda token en esta "
			+ "llamada. 200 con serialNumbers y lastUpdated, o 204 si no hay cambios.")
	public ResponseEntity<PasesActualizados> pasesActualizados(@PathVariable String dispositivo,
			@PathVariable String passTypeId, @RequestParam(required = false) String passesUpdatedSince) {
		return servicio.serialesActualizados(dispositivo, passTypeId, passesUpdatedSince)
				.map(cambios -> ResponseEntity.ok(new PasesActualizados(cambios.serialNumbers(), cambios.etiqueta())))
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/** Respuesta con el formato que Apple define. */
	public record PasesActualizados(List<String> serialNumbers, String lastUpdated) {
	}

	@GetMapping("/passes/{passTypeId}/{serial}")
	@Operation(summary = "Entregar el pase vigente", description = "El .pkpass con voided si la cita se canceló. "
			+ "304 con If-Modified-Since al día, 401 con un token que no es del pase.")
	public ResponseEntity<byte[]> pase(@PathVariable String passTypeId, @PathVariable String serial,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion,
			@RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String modificadoDesde) {
		RespuestaPase respuesta = servicio.pase(passTypeId, serial, token(autorizacion), fecha(modificadoDesde));
		if (respuesta instanceof RespuestaPase.Entrega entrega) {
			return ResponseEntity.ok().contentType(MediaType.parseMediaType(TIPO_PKPASS))
					.header(HttpHeaders.LAST_MODIFIED,
							DateTimeFormatter.RFC_1123_DATE_TIME.format(entrega.modificado().atZone(ZoneOffset.UTC)))
					.body(entrega.pkpass());
		}
		return switch ((RespuestaPase.Sin) respuesta) {
			case NO_AUTORIZADO -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			case NO_MODIFICADO -> ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
			case NO_ENCONTRADO -> ResponseEntity.notFound().build();
		};
	}

	@PostMapping("/log")
	@Operation(summary = "Recibir los errores que reporta Wallet", description = "Siempre 200.")
	public ResponseEntity<Void> log(@RequestBody(required = false) RegistroDeLogs cuerpo) {
		servicio.registrarLog(cuerpo == null ? null : cuerpo.logs());
		return ResponseEntity.ok().build();
	}

	private static ResponseEntity<Void> respuesta(ResultadoRegistro resultado) {
		return switch (resultado) {
			case CREADO -> ResponseEntity.status(HttpStatus.CREATED).build();
			case ACTUALIZADO -> ResponseEntity.ok().build();
			case NO_AUTORIZADO -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			case NO_ENCONTRADO -> ResponseEntity.notFound().build();
		};
	}

	private static String token(String autorizacion) {
		return autorizacion != null && autorizacion.startsWith(PREFIJO_AUTORIZACION)
				? autorizacion.substring(PREFIJO_AUTORIZACION.length()).trim()
				: null;
	}

	private static Instant fecha(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			return ZonedDateTime.parse(valor, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
		} catch (DateTimeParseException ilegible) {
			return null;
		}
	}
}
