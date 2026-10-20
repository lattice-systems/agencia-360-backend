package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.MotivoCierre;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Datos de un prospecto que se muestran en el CRM.
 *
 * @param creadoEn
 *            fecha y hora de registro
 * @param ultimoCambio
 *            fecha y hora del último cambio de etapa
 * @param motivoCierre
 *            solo si el prospecto está cerrado; el CRM muestra su etiqueta
 * @param ultimaEtapa
 *            etapa en la que estaba al cerrarse; solo si está cerrado
 * @param cierrePendiente
 *            hay una solicitud de cierre esperando al Administrador
 * @param precioTotal
 *            precio total de la venta; lo captura el vendedor al pasar a Pago
 */
public record ProspectoVista(Long id, String nombre, String telefono, String correo, Etapa etapa, Long vendedorId,
		Instant creadoEn, Instant ultimoCambio, MotivoCierre motivoCierre, Etapa ultimaEtapa, boolean cierrePendiente,
		BigDecimal precioTotal) {

	static ProspectoVista de(Prospecto prospecto) {
		return new ProspectoVista(prospecto.getId(), prospecto.getNombre(), prospecto.getTelefono(),
				prospecto.getCorreo(), prospecto.getEtapa(), prospecto.getVendedorId(), prospecto.getCreatedAt(),
				prospecto.getEtapaActualizadaEn(), prospecto.getMotivoCierre(), prospecto.getUltimaEtapa(),
				prospecto.isCierrePendiente(), prospecto.getPrecioTotal());
	}
}
