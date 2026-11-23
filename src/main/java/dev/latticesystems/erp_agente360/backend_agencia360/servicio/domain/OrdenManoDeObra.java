package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "orden_mano_de_obra")
public class OrdenManoDeObra extends RenglonOrden {

	protected OrdenManoDeObra() {
	}

	OrdenManoDeObra(OrdenServicio ordenServicio, String concepto, int cantidad, BigDecimal precio) {
		super(ordenServicio, concepto, cantidad, precio);
	}

	@Override
	public TipoRenglon getTipo() {
		return TipoRenglon.MANO_DE_OBRA;
	}
}
