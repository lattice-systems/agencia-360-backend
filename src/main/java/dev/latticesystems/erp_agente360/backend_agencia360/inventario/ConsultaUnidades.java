package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import java.util.Optional;

/**
 * Consulta que inventario ofrece a otros módulos para saber qué unidad se le
 * vendió a un prospecto (ERP-1.0), sin exponer sus entidades. La usan clientes
 * (CRM-1.3: asignar el vehículo a la cuenta del cliente) y ventascrm (CRM-1.4:
 * mostrar el vehículo en Finalizados).
 *
 * <p>
 * Inventario no depende de ventascrm, así que estos módulos pueden usarla sin
 * formar un ciclo.
 */
public interface ConsultaUnidades {

	/**
	 * @return la unidad que Facturación eligió para el prospecto, esté Vendida o ya
	 *         entregada; vacío si todavía no tiene una
	 */
	Optional<UnidadDelProspecto> unidadDelProspecto(Long prospectoId);

	/**
	 * Datos de la unidad. En pantalla, {@code modelo} se muestra como Tipo (Sentra)
	 * y {@code anio} como Modelo (2026), como en la factura (ERP-2.2).
	 *
	 * <p>
	 * {@code version} y {@code marca} se agregaron al final en el Sprint 3 para el
	 * expediente del vehículo (ERP-3.0), sin cambiar el orden de los demás.
	 *
	 * @param marca
	 *            la marca de la agencia ({@code app.agencia.marca}): el inventario
	 *            no la guarda por unidad porque la agencia vende una sola marca
	 */
	record UnidadDelProspecto(Long vehiculoId, String vin, String modelo, Integer anio, String version, String marca) {
	}
}
