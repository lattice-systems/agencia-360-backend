package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Etapas del prospecto, en el orden del embudo de venta. Decisiones del PO del
 * 9 y 12 de octubre:
 * <ul>
 * <li>Primer contacto, Visita y Cotización: las mueve el vendedor, incluso
 * hacia atrás o saltándose alguna.</li>
 * <li>Pago: la pone el vendedor y la avanza Contabilidad al validar el
 * pago.</li>
 * <li>Facturación: la avanza Facturación al emitir la factura.</li>
 * <li>Entrega: vuelve al vendedor, que la marca como {@link #FINALIZADO} al
 * entregar la unidad.</li>
 * <li>{@link #CERRADO}: venta no concretada.</li>
 * </ul>
 */
public enum Etapa {
	PRIMER_CONTACTO, VISITA, COTIZACION, PAGO, FACTURACION, ENTREGA, FINALIZADO, CERRADO;

	private static final Set<Etapa> INICIALES = EnumSet.of(PRIMER_CONTACTO, VISITA, COTIZACION);
	private static final Set<Etapa> EN_PROCESO_DE_VENTA = EnumSet.of(PAGO, FACTURACION, ENTREGA);

	/** Primer contacto, Visita o Cotización: el vendedor la mueve libremente. */
	public boolean esInicial() {
		return INICIALES.contains(this);
	}

	/**
	 * Pago, Facturación o Entrega: cerrarlo requiere la autorización del
	 * Administrador.
	 */
	public boolean estaEnProcesoDeVenta() {
		return EN_PROCESO_DE_VENTA.contains(this);
	}

	/** Finalizado o Cerrado: el prospecto ya no cambia de etapa. */
	public boolean esFinal() {
		return this == FINALIZADO || this == CERRADO;
	}

	/**
	 * Convierte el valor recibido en una petición.
	 *
	 * @throws IllegalArgumentException
	 *             si el valor no corresponde a ninguna etapa
	 */
	public static Etapa desde(String valor) {
		for (Etapa etapa : values()) {
			if (etapa.name().equals(valor)) {
				return etapa;
			}
		}
		throw new IllegalArgumentException("Etapa no válida: " + valor);
	}
}
