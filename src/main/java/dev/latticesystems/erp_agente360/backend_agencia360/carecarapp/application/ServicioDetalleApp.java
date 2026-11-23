package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.DetalleDelServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.RenglonDelServicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ServicioDetalleApp(Long id, Long vehiculoId, LocalDate fecha, int kilometraje, EstatusServicio estatus,
		@JsonFormat(pattern = ServicioApp.FORMATO_FECHA_HORA) LocalDateTime fechaCierre, List<RenglonApp> manoDeObra,
		List<RenglonApp> refacciones, BigDecimal totalManoDeObra, BigDecimal totalRefacciones, BigDecimal subtotal,
		BigDecimal iva, BigDecimal total) {

	public record RenglonApp(String concepto, int cantidad, BigDecimal precio, BigDecimal importe) {

		static RenglonApp de(RenglonDelServicio renglon) {
			return new RenglonApp(renglon.concepto(), renglon.cantidad(), renglon.precio(), renglon.importe());
		}
	}

	static ServicioDetalleApp de(DetalleDelServicio detalle) {
		return new ServicioDetalleApp(detalle.id(), detalle.vehiculoId(), detalle.fecha(), detalle.kilometraje(),
				EstatusServicio.valueOf(detalle.estatus().name()), ServicioApp.enLaAgencia(detalle.fechaCierre()),
				detalle.manoDeObra().stream().map(RenglonApp::de).toList(),
				detalle.refacciones().stream().map(RenglonApp::de).toList(), detalle.totalManoDeObra(),
				detalle.totalRefacciones(), detalle.subtotal(), detalle.iva(), detalle.total());
	}
}
