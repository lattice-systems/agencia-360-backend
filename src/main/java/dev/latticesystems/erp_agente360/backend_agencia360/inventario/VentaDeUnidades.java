package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Venta de una unidad del inventario, que inventario ofrece a facturacion
 * (ERP-1.3) para registrarla en la misma transacción de la emisión de la
 * factura. Si la factura no se guarda, la unidad no queda vendida.
 *
 * <p>
 * Inventario no depende de facturacion, así que no se forma un ciclo.
 */
public interface VentaDeUnidades {

	/**
	 * Bloquea la fila de la unidad ({@code SELECT ... FOR UPDATE}), revisa que siga
	 * Disponible, la pasa a Vendido y registra la venta del prospecto. Se llama
	 * dentro de la transacción de quien emite.
	 *
	 * @param usuarioId
	 *            quién de Facturación emite la factura
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException
	 *             si no existe la unidad (404)
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException
	 *             si la unidad ya no está disponible o el prospecto ya tiene una
	 *             venta (409)
	 */
	UnidadVendida vender(Long prospectoId, Long vehiculoId, Long usuarioId, Instant ahora);

	/**
	 * Datos de la unidad vendida para la factura. {@code modelo} es el Tipo
	 * (Sentra) y {@code anio} el Modelo (2026), como en la factura (ERP-2.2).
	 *
	 * @param ventaId
	 *            la venta que se registró
	 * @param precio
	 *            precio de lista de la unidad
	 */
	record UnidadVendida(Long ventaId, Long vehiculoId, String vin, String marca, String modelo, String version,
			Integer anio, String color, BigDecimal precio) {
	}
}
