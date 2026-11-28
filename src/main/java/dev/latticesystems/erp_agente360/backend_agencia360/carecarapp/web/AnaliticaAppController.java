package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import com.fasterxml.jackson.databind.JsonNode;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.AnaliticaAppJdbc;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnaliticaAppController {

	private static final Pattern UUID_CANONICO = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");

	private final AnaliticaAppJdbc datos;

	public AnaliticaAppController(AnaliticaAppJdbc datos) {
		this.datos = datos;
	}

	@PostMapping("/api/v1/app/analitica/eventos")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void registrar(@RequestBody JsonNode evento, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		String tipo = evento.path("tipo").asText();
		switch (tipo) {
			case "DISPONIBILIDAD", "FRANJA" -> {
				estructura(evento, 2);
				datos.registrarPaso(intento(evento), "FRANJA".equals(tipo) ? 2 : 1, null, cliente.id());
			}
			case "CONFIRMADA" -> {
				estructura(evento, 3);
				datos.registrarPaso(intento(evento), 3, entero(evento, "citaId", 1, Long.MAX_VALUE), cliente.id());
			}
			case "ERROR_FLUTTER", "ERROR_ASINCRONO", "CRASH_ANDROID" -> {
				estructura(evento, 1);
				datos.sumarDiagnostico("ERROR_FLUTTER".equals(tipo) ? 1 : 0, "ERROR_ASINCRONO".equals(tipo) ? 1 : 0,
						"CRASH_ANDROID".equals(tipo) ? 1 : 0, 0, 0, 0, 0, 0);
			}
			case "RENDIMIENTO" -> {
				estructura(evento, 5);
				datos.sumarDiagnostico(0, 0, 0, 1, (int) entero(evento, "arranqueMs", 0, 600000),
						(int) entero(evento, "primeraPantallaMs", 0, 600000),
						(int) entero(evento, "cuadrosMedidos", 1, 120),
						(int) entero(evento, "cuadrosPerdidos", 0, 60000));
			}
			default -> throw new IllegalArgumentException("Tipo de evento desconocido");
		}
	}

	private static void estructura(JsonNode evento, int campos) {
		if (!evento.isObject() || evento.size() != campos) {
			throw new IllegalArgumentException("Propiedades de evento inválidas");
		}
	}

	private static long entero(JsonNode evento, String campo, long minimo, long maximo) {
		JsonNode valor = evento.path(campo);
		if (!valor.isIntegralNumber() || !valor.canConvertToLong() || valor.longValue() < minimo
				|| valor.longValue() > maximo) {
			throw new IllegalArgumentException("Contador o referencia inválida");
		}
		return valor.longValue();
	}

	private static UUID intento(JsonNode evento) {
		String valor = evento.path("intento").asText();
		if (!UUID_CANONICO.matcher(valor).matches()) {
			throw new IllegalArgumentException("Identificador de intento inválido");
		}
		return UUID.fromString(valor);
	}
}
