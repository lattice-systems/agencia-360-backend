package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MedioPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoFalloPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoTarjeta;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un pago como lo ve la app (V2-M08.02, V2-M08.07 y V2-M14.04). Trae la orden o
 * el pedido que se pagó, nunca los dos.
 *
 * @param requiereAutenticacion
 *            sigue pendiente porque el banco simulado pide confirmar con 3-D
 *            Secure
 * @param simulado
 *            lo cobró el procesador simulado; {@code false} si se pagó en caja
 */
public record PagoVista(Long id, Long ordenId, Long pedidoId, Long vehiculoId, BigDecimal monto, String moneda,
		MedioPago medio, String marca, String ultimos4, TipoTarjeta tipoTarjeta, EstadoPago estado,
		MotivoFalloPago motivoFallo, boolean requiereAutenticacion, String referencia, boolean simulado,
		Long metodoPagoId, Long perfilFiscalId, Instant pagadoEn, Instant creadoEn) {

	static final String PROCESADOR_SIMULADO = "SIMULADO";

	public static PagoVista de(PagoApp pago) {
		return new PagoVista(pago.getId(), pago.getOrdenId(), pago.getPedidoId(), pago.getVehiculoId(), pago.getMonto(),
				pago.getMoneda(), pago.getMedio(), pago.getMarca(), pago.getUltimos4(), pago.getTipoTarjeta(),
				pago.getEstado(), pago.getMotivoFallo(), pago.requiereAutenticacion(), pago.getReferencia(),
				PROCESADOR_SIMULADO.equals(pago.getProcesador()), pago.getMetodoPagoId(), pago.getPerfilFiscalId(),
				pago.getPagadoEn(), pago.getCreadoEn());
	}
}
