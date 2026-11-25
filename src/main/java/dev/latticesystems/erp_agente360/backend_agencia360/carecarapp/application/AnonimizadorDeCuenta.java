package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Borra o anonimiza los datos personales de una cuenta de la app que ninguna
 * ley obliga a conservar (cancelación ARCO, V2-M22.03).
 *
 * <p>
 * No toca lo fiscal y comercial (ventas, pagos, facturas, prospectos) ni el
 * historial de servicio de los vehículos: son de otros módulos o se conservan
 * por obligación. La tabla de qué pasa con cada dato está en
 * {@code plan/historias-backend.md}. Debe correr dentro de la transacción de
 * quien la pide: todo o nada.
 */
public interface AnonimizadorDeCuenta {

	void anonimizar(long clienteId);
}
