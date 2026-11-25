package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.Map;

/**
 * Una llamada HTTP saliente. Existe para que las pruebas sustituyan a Google
 * sin red; la implementación real es {@link LlamadaHttpDelSistema}.
 */
interface LlamadaHttp {

	RespuestaHttp enviar(String metodo, String url, Map<String, String> cabeceras, String cuerpo);

	record RespuestaHttp(int codigo, String cuerpo) {
	}
}
