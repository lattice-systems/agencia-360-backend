package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * Valores del campo {@code codigo} de {@link ApiError} que comparte la API
 * (contrato de la app CareCar).
 */
public final class CodigosError {

	public static final String CREDENCIALES_INVALIDAS = "CREDENCIALES_INVALIDAS";
	public static final String CUENTA_DESACTIVADA = "CUENTA_DESACTIVADA";
	public static final String DEMASIADOS_INTENTOS = "DEMASIADOS_INTENTOS";
	public static final String CODIGO_INVALIDO = "CODIGO_INVALIDO";
	public static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";
	public static final String FRANJA_SIN_CUPO = "FRANJA_SIN_CUPO";
	public static final String FUERA_DE_PLAZO = "FUERA_DE_PLAZO";
	public static final String CITA_NO_CANCELABLE = "CITA_NO_CANCELABLE";
	public static final String CITA_CANCELADA = "CITA_CANCELADA";
	public static final String CITA_DUPLICADA = "CITA_DUPLICADA";
	public static final String CITA_NO_REPROGRAMABLE = "CITA_NO_REPROGRAMABLE";
	public static final String WALLET_NO_CONFIGURADO = "WALLET_NO_CONFIGURADO";
	public static final String ASISTENTE_SIN_MANUAL = "ASISTENTE_SIN_MANUAL";
	public static final String ASISTENTE_LIMITE = "ASISTENTE_LIMITE";
	/** V2-M16.02: el modelo del catálogo no tiene precio publicado. */
	public static final String ESTRENO_SIN_PRECIO = "ESTRENO_SIN_PRECIO";
	/** V2-M16.01: ya hay una solicitud abierta del mismo modelo y tipo. */
	public static final String ESTRENO_DUPLICADA = "ESTRENO_DUPLICADA";
	/**
	 * V2-M22.01: se quiso aceptar un aviso de privacidad que ya no es el vigente.
	 */
	public static final String AVISO_NO_VIGENTE = "AVISO_NO_VIGENTE";
	public static final String ASISTENTE_NO_DISPONIBLE = "ASISTENTE_NO_DISPONIBLE";
	public static final String SIN_PARAMETROS_DE_VALUACION = "SIN_PARAMETROS_DE_VALUACION";
	public static final String SOLICITUD_ABIERTA = "SOLICITUD_ABIERTA";
	public static final String FUERA_DE_COBERTURA = "FUERA_DE_COBERTURA";
	public static final String DIRECCION_EN_USO = "DIRECCION_EN_USO";
	public static final String ORDEN_ABIERTA = "ORDEN_ABIERTA";
	public static final String ORDEN_CERRADA = "ORDEN_CERRADA";
	public static final String ADICIONAL_YA_RESPONDIDO = "ADICIONAL_YA_RESPONDIDO";
	public static final String KM_INVALIDO = "KM_INVALIDO";
	public static final String TRANSICION_INVALIDA = "TRANSICION_INVALIDA";
	public static final String CAMPANA_NO_AGENDABLE = "CAMPANA_NO_AGENDABLE";
	/** V2-M12.03: el saldo de puntos no alcanza para la recompensa. */
	public static final String SALDO_INSUFICIENTE = "SALDO_INSUFICIENTE";
	/** V2-M12.03: la recompensa ya no tiene existencia. */
	public static final String RECOMPENSA_AGOTADA = "RECOMPENSA_AGOTADA";
	/** V2-M12.03: el canje ya se usó o ya venció. */
	public static final String CANJE_NO_VIGENTE = "CANJE_NO_VIGENTE";
	/** V2-M12.04: el amigo ya es cliente o ya lo refirieron. */
	public static final String REFERIDO_DUPLICADO = "REFERIDO_DUPLICADO";
	/** V2-M12.04: el cliente ya tiene el máximo de referidos abiertos. */
	public static final String LIMITE_REFERIDOS = "LIMITE_REFERIDOS";
	/** V2-M26.01: la estimación se hizo sin un vehículo registrado. */
	public static final String VALUACION_SIN_VEHICULO = "VALUACION_SIN_VEHICULO";
	/** V2-M26.02: la valuación ya no admite fotos. */
	public static final String VALUACION_CERRADA = "VALUACION_CERRADA";
	/** V2-M26.03: la valuación ya tiene cita o ya no se puede agendar. */
	public static final String VALUACION_NO_AGENDABLE = "VALUACION_NO_AGENDABLE";
	/** V2-M26.04: la vigencia de la oferta ya pasó. */
	public static final String OFERTA_VENCIDA = "OFERTA_VENCIDA";
	/** V2-M26.04: no hay una oferta que responder así. */
	public static final String OFERTA_NO_DISPONIBLE = "OFERTA_NO_DISPONIBLE";

	/** V2-M14.02: no alcanzan las piezas. */
	public static final String SIN_EXISTENCIA = "SIN_EXISTENCIA";
	/** V2-M14.03: la cita está cancelada o ya pasó. */
	public static final String CITA_NO_DISPONIBLE = "CITA_NO_DISPONIBLE";
	/** V2-M14.02: el cliente ya tiene el máximo de pedidos sin entregar. */
	public static final String LIMITE_APARTADOS = "LIMITE_APARTADOS";
	/** V2-M14.02: el pedido ya se entregó. */
	public static final String PEDIDO_NO_CANCELABLE = "PEDIDO_NO_CANCELABLE";
	/** V2-M14.03: el pedido ya se entregó o se canceló. */
	public static final String PEDIDO_NO_MODIFICABLE = "PEDIDO_NO_MODIFICABLE";
	/** V2-M14.03: un producto no le queda al vehículo de la cita. */
	public static final String PRODUCTO_INCOMPATIBLE = "PRODUCTO_INCOMPATIBLE";
	/**
	 * V2-M08.05: un dato del perfil fiscal no cumple las reglas del SAT; el error
	 * trae {@code campos} con qué corregir en cada uno.
	 */
	public static final String DATOS_FISCALES_INVALIDOS = "DATOS_FISCALES_INVALIDOS";

	private CodigosError() {
	}
}
