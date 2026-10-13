package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import java.time.Instant;

/**
 * Datos de un prospecto que se muestran en el CRM.
 *
 * @param creadoEn
 *            fecha y hora de registro
 */
public record ProspectoVista(Long id, String nombre, String telefono, String correo, Etapa etapa, Long vendedorId,
		Instant creadoEn) {

	static ProspectoVista de(Prospecto prospecto) {
		return new ProspectoVista(prospecto.getId(), prospecto.getNombre(), prospecto.getTelefono(),
				prospecto.getCorreo(), prospecto.getEtapa(), prospecto.getVendedorId(), prospecto.getCreatedAt());
	}
}
