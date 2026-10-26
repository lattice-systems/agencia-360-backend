package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consulta que ventascrm ofrece a facturacion (ERP-1.3) para saber qué
 * prospectos esperan su factura y con qué datos se emite, sin exponer sus
 * entidades. Contrato acordado entre Coper y Pablito el día 1 del Sprint 3.
 *
 * <p>
 * Ventascrm no depende de facturacion: se entera de la emisión por el evento
 * {@code shared.eventos.FacturaEmitida}, así que no se forma un ciclo.
 */
public interface ConsultaFacturacion {

	/**
	 * @return los prospectos en la etapa Facturación, del más reciente al más
	 *         antiguo
	 */
	List<ProspectoParaFacturar> enFacturacion();

	/**
	 * @return el prospecto con los datos para facturar; vacío si no existe o no
	 *         está en la etapa Facturación
	 */
	Optional<ProspectoParaFacturar> paraFacturar(Long prospectoId);

	/**
	 * Prospecto que espera su factura.
	 *
	 * @param vendedor
	 *            nombre del vendedor a cargo
	 * @param total
	 *            precio total de la venta, con IVA incluido (ERP-1.1); vacío si
	 *            nadie lo capturó
	 * @param saldo
	 *            lo que falta por pagar: el total menos los pagos no anulados,
	 *            nunca negativo. No se factura si es mayor a 0
	 * @param enFacturacionDesde
	 *            cuando Contabilidad validó los pagos y pasó a Facturación
	 * @param receptor
	 *            datos fiscales del cliente que captura el vendedor al pasar a
	 *            Pagos; vacío mientras no se capturen
	 * @param cierrePendiente
	 *            hay una solicitud de cierre esperando al Administrador; no se
	 *            factura hasta que la resuelva (decisión del PO del 26 de octubre)
	 */
	record ProspectoParaFacturar(Long prospectoId, String nombre, Long vendedorId, String vendedor, BigDecimal total,
			BigDecimal saldo, Instant enFacturacionDesde, DatosFiscales receptor, boolean cierrePendiente) {
	}

	/** Datos fiscales del receptor de la factura. */
	record DatosFiscales(String rfc, String nombreCompleto, String domicilioFiscal, String regimenFiscal) {

		/** Con todos los datos se puede emitir la factura. */
		public boolean completos() {
			return conValor(rfc) && conValor(nombreCompleto) && conValor(domicilioFiscal) && conValor(regimenFiscal);
		}

		private static boolean conValor(String valor) {
			return valor != null && !valor.isBlank();
		}
	}
}
