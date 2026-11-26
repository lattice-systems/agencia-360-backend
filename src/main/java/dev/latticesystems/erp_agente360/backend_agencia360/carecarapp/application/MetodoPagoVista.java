package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MetodoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoTarjeta;
import java.time.Instant;
import java.time.YearMonth;

/** Una tarjeta guardada como la ve la app (V2-M08.04): sin token ni número. */
public record MetodoPagoVista(Long id, String marca, String ultimos4, TipoTarjeta tipo, int mesVence, int anioVence,
		boolean vencida, boolean predeterminado, Instant creadoEn) {

	static MetodoPagoVista de(MetodoPago metodo, YearMonth hoy) {
		return new MetodoPagoVista(metodo.getId(), metodo.getMarca(), metodo.getUltimos4(), metodo.getTipo(),
				metodo.getMesVence(), metodo.getAnioVence(), metodo.vencida(hoy), metodo.isPredeterminado(),
				metodo.getCreadoEn());
	}
}
