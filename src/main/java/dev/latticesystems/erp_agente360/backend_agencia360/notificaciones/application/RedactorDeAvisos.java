package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Arma el texto en español de cada aviso a partir del evento de dominio
 * (V2-M06.07).
 *
 * <p>
 * Es una función pura: el mismo evento da siempre el mismo texto. Los textos
 * son genéricos a propósito (V2-M06.02): no llevan nombre del cliente, VIN,
 * placa, montos ni direcciones, porque una notificación se ve en la pantalla de
 * bloqueo y pasa por servidores de terceros. Lo único que se cuela del evento
 * es el nombre del servicio o de la garantía, que es del catálogo de la
 * agencia.
 *
 * <p>
 * Un evento sin texto definido (o con un estado que no merece aviso) devuelve
 * vacío: no hay push ni fila en la bandeja.
 */
@Component
public class RedactorDeAvisos {

	static final String TALLER = "taller";
	static final String CITAS = "citas";
	static final String MANTENIMIENTO = "mantenimiento";
	static final String DOCUMENTOS = "documentos";
	static final String PROMOCIONES = "promociones";

	public Optional<ContenidoAviso> redactar(String evento, Map<String, Object> datos) {
		return switch (evento) {
			case "mantenimiento.recordatorio" -> Optional.of(recordatorio(datos));
			case "orden" -> orden(datos);
			case "asistencia.estado" -> Optional.of(asistencia(datos));
			case "traslado.estado" -> traslado(datos);
			case "campana.nueva" -> Optional.of(campana(datos));
			case "garantia.por_vencer" -> Optional.of(garantia(datos));
			case "pago.confirmado" -> pago(datos);
			case "pedido.estado" -> pedido(datos);
			case "puntos.movidos" -> puntos(datos);
			case "factura.timbrada" -> Optional.of(new ContenidoAviso(DOCUMENTOS, "Tu factura está lista",
					"Ya puedes verla y descargarla desde tus pagos.", "/pagos"));
			case "valuacion.oferta" -> Optional.of(new ContenidoAviso(PROMOCIONES, "Tienes una oferta por tu auto",
					"La agencia te hizo una oferta. Revísala antes de que venza.",
					"/valuaciones/" + texto(datos, "valuacionId", "")));
			case "arco.estado" -> Optional.of(new ContenidoAviso(DOCUMENTOS, "Actualizamos tu solicitud de privacidad",
					"Tu solicitud cambió de estado. Revísala en la app.", "/privacidad/mis-datos"));
			default -> Optional.empty();
		};
	}

	private ContenidoAviso recordatorio(Map<String, Object> datos) {
		String nombre = texto(datos, "nombre", "Tu próximo servicio");
		String detalle = switch (texto(datos, "motivo", "")) {
			case "km" -> "faltan unos " + miles(datos.get("faltanKm")) + " km";
			case "tiempo" -> dias(datos.get("faltanDias"), "faltan ", "falta ");
			default -> "se acerca la fecha";
		};
		return new ContenidoAviso(MANTENIMIENTO, "Se acerca tu próximo servicio",
				nombre + ": " + detalle + ". Agenda tu cita cuando quieras.",
				"/mantenimiento/" + texto(datos, "vehiculoId", ""));
	}

	private Optional<ContenidoAviso> orden(Map<String, Object> datos) {
		String destino = "/taller/" + texto(datos, "ordenId", "");
		String motivo = texto(datos, "motivo", "");
		String etapa = texto(datos, "etapa", "");
		return switch (motivo) {
			case "APERTURA" -> Optional.of(new ContenidoAviso(TALLER, "Tu auto ya está en el taller",
					"Abrimos tu orden. Puedes seguir el avance desde la app.", destino));
			case "ADICIONAL" -> Optional.of(new ContenidoAviso(TALLER, "Hay un trabajo adicional por aprobar",
					"El taller te propone un trabajo adicional. Revísalo y responde desde la app.", destino));
			case "ETAPA" -> Optional.of(etapa(etapa, destino));
			default -> Optional.empty();
		};
	}

	private ContenidoAviso etapa(String etapa, String destino) {
		return switch (etapa) {
			case "LISTO_PARA_ENTREGA" -> new ContenidoAviso(TALLER, "Tu auto está listo",
					"Ya puedes pasar por él. Abre la app para ver el horario de entrega.", destino);
			case "ENTREGADO" -> new ContenidoAviso(TALLER, "Tu auto fue entregado",
					"Gracias por confiar en nosotros. Tu servicio ya está en tu historial.", destino);
			case "DIAGNOSTICO" -> avance("en diagnóstico", destino);
			case "ESPERANDO_APROBACION" -> avance("esperando tu aprobación", destino);
			case "EN_REPARACION" -> avance("en reparación", destino);
			case "CONTROL_DE_CALIDAD" -> avance("en control de calidad", destino);
			case "LAVADO" -> avance("en lavado", destino);
			default -> avance("recibido", destino);
		};
	}

	private ContenidoAviso avance(String donde, String destino) {
		return new ContenidoAviso(TALLER, "Tu auto está " + donde, "Mira el avance de tu servicio en la app.", destino);
	}

	private ContenidoAviso asistencia(Map<String, Object> datos) {
		String destino = "/asistencia/guias";
		return switch (texto(datos, "estado", "")) {
			case "recibida" -> new ContenidoAviso(CITAS, "Recibimos tu solicitud de asistencia",
					"Estamos asignando a quien te ayude.", destino);
			case "en_camino" -> new ContenidoAviso(CITAS, "Tu asistencia va en camino",
					"Ya hay alguien en camino. Abre la app para ver el avance.", destino);
			case "atendida" ->
				new ContenidoAviso(CITAS, "Asistencia atendida", "Tu solicitud de asistencia quedó atendida.", destino);
			case "cancelada" ->
				new ContenidoAviso(CITAS, "Asistencia cancelada", "Tu solicitud de asistencia fue cancelada.", destino);
			default -> new ContenidoAviso(CITAS, "Actualizamos tu solicitud de asistencia",
					"Abre la app para ver los detalles.", destino);
		};
	}

	private Optional<ContenidoAviso> traslado(Map<String, Object> datos) {
		String destino = "/recoleccion/traslados/" + texto(datos, "trasladoId", "");
		return switch (texto(datos, "estado", "")) {
			case "programado" -> Optional.of(new ContenidoAviso(CITAS, "Tu traslado quedó programado",
					"Te avisaremos cuando el chofer salga.", destino));
			case "en_camino" -> Optional.of(new ContenidoAviso(CITAS, "Tu chofer va en camino",
					"Puedes seguirlo en el mapa desde la app.", destino));
			case "recogido" ->
				Optional.of(new ContenidoAviso(CITAS, "Recogimos tu auto", "Va camino al taller.", destino));
			case "en_taller" -> Optional
					.of(new ContenidoAviso(CITAS, "Tu auto llegó al taller", "Ya empezamos con tu servicio.", destino));
			case "de_regreso" -> Optional.of(new ContenidoAviso(CITAS, "Tu auto va de regreso",
					"Puedes seguir al chofer en el mapa desde la app.", destino));
			case "entregado" -> Optional.of(
					new ContenidoAviso(CITAS, "Tu auto fue entregado", "Gracias por confiar en nosotros.", destino));
			case "cancelado" -> Optional.of(new ContenidoAviso(CITAS, "Traslado cancelado",
					"Tu traslado fue cancelado. Abre la app si necesitas reprogramarlo.", destino));
			default -> Optional.empty();
		};
	}

	private ContenidoAviso campana(Map<String, Object> datos) {
		String destino = "/garantia/" + texto(datos, "vehiculoId", "") + "/campanas/" + texto(datos, "campanaId", "");
		if (Boolean.TRUE.equals(datos.get("critica"))) {
			return new ContenidoAviso(MANTENIMIENTO, "Campaña de seguridad urgente",
					"Hay una campaña de seguridad importante para tu auto. Agenda tu revisión cuanto antes.", destino);
		}
		return new ContenidoAviso(MANTENIMIENTO, "Nueva campaña para tu auto",
				"Hay una campaña de revisión para tu auto. Consulta los detalles en la app.", destino);
	}

	private ContenidoAviso garantia(Map<String, Object> datos) {
		String componente = texto(datos, "componente", "tu auto");
		String cuerpo = "km_1000".equals(texto(datos, "motivo", ""))
				? "A la garantía de " + componente + " le quedan unos " + miles(datos.get("kmRestantes")) + " km."
				: "La garantía de " + componente + " vence en " + dias(datos.get("diasRestantes"), "", "") + ".";
		return new ContenidoAviso(MANTENIMIENTO, "Tu garantía está por vencer", cuerpo,
				"/garantia/" + texto(datos, "vehiculoId", ""));
	}

	private Optional<ContenidoAviso> pago(Map<String, Object> datos) {
		return switch (texto(datos, "estado", "")) {
			case "PAGADO" ->
				Optional.of(new ContenidoAviso(DOCUMENTOS, "Pago confirmado", "Recibimos tu pago. Gracias.", "/pagos"));
			case "FALLIDO" -> Optional.of(new ContenidoAviso(DOCUMENTOS, "No pudimos procesar tu pago",
					"Revisa tu método de pago e inténtalo de nuevo.", "/pagos"));
			default -> Optional.empty();
		};
	}

	private Optional<ContenidoAviso> pedido(Map<String, Object> datos) {
		String destino = "/tienda/pedidos/" + texto(datos, "pedidoId", "");
		return switch (texto(datos, "estado", "")) {
			case "listo" -> Optional.of(new ContenidoAviso(PROMOCIONES, "Tu pedido está listo",
					"Ya puedes recogerlo en la agencia.", destino));
			case "cancelado" -> Optional.of(new ContenidoAviso(PROMOCIONES, "Tu pedido fue cancelado",
					"Revisa los detalles en la app.", destino));
			default -> Optional.empty();
		};
	}

	private Optional<ContenidoAviso> puntos(Map<String, Object> datos) {
		long puntos = datos.get("puntos") instanceof Number numero ? numero.longValue() : 0;
		if (puntos <= 0) {
			return Optional.empty();
		}
		return Optional.of(new ContenidoAviso(PROMOCIONES, "Ganaste puntos",
				"Sumaste " + miles(puntos) + " puntos CareCar.", "/lealtad/movimientos"));
	}

	private String texto(Map<String, Object> datos, String clave, String porDefecto) {
		Object valor = datos.get(clave);
		return valor == null ? porDefecto : valor.toString();
	}

	private String miles(Object valor) {
		long numero = valor instanceof Number n ? n.longValue() : 0;
		return String.format(Locale.US, "%,d", numero);
	}

	private String dias(Object valor, String plural, String singular) {
		long numero = valor instanceof Number n ? n.longValue() : 0;
		return numero == 1 ? singular + "1 día" : plural + numero + " días";
	}
}
