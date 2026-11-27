package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.infrastructure;

import java.util.Map;

/**
 * Una llamada HTTP saliente. Existe para que las pruebas sustituyan a Google
 * sin red; la implementación real es {@link LlamadaHttpDelSistema}.
 *
 * <p>
 * Es una copia mínima de la de {@code carecarapp}, que es interna de ese
 * módulo; si un tercer módulo la necesita conviene subirla a {@code shared}.
 */
interface LlamadaHttp {

	RespuestaHttp enviar(String metodo, String url, Map<String, String> cabeceras, String cuerpo);

	record RespuestaHttp(int codigo, String cuerpo) {
	}
}
