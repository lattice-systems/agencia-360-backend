package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

import java.util.Optional;

/**
 * Lo que otros módulos pueden saber de un prospecto sin leer sus tablas
 * (V2-M16.04: estado de las solicitudes de estreno en la app).
 */
public interface ConsultaProspectos {

	/** Etapa y vendedor de un prospecto, si existe. */
	Optional<EstadoProspecto> estado(Long prospectoId);

	/**
	 * Ids de los prospectos que atiende un vendedor, para que otro módulo le
	 * muestre solo lo suyo (V2-M26.04).
	 */
	java.util.List<Long> delVendedor(Long vendedorId);

	/**
	 * @param terminado
	 *            {@code true} si está Finalizado o Cerrado
	 */
	record EstadoProspecto(String etapa, boolean terminado, Long vendedorId) {
	}
}
