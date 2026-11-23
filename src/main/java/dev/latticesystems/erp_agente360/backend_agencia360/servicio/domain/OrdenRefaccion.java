package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "orden_refaccion")
public class OrdenRefaccion extends RenglonOrden {

	@Column(name = "refaccion_id")
	private Long refaccionId;

	protected OrdenRefaccion() {
	}

	OrdenRefaccion(OrdenServicio ordenServicio, String concepto, int cantidad, BigDecimal precio) {
		super(ordenServicio, concepto, cantidad, precio);
	}

	@Override
	public TipoRenglon getTipo() {
		return TipoRenglon.REFACCION;
	}

	public Long getRefaccionId() {
		return refaccionId;
	}
}
