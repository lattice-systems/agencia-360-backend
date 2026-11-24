package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code solicitud_estreno} (V2-M16.01 a V2-M16.04). */
public interface SolicitudesEstreno {

	/** Lo que se guarda al pedir. Los campos que no aplican al tipo van nulos. */
	record NuevaSolicitud(long clienteId, long modeloCatalogoId, String tipo, LocalDate preferenciaFecha,
			String preferenciaFranja, BigDecimal enganche, Integer plazoMeses, BigDecimal mensualidad,
			BigDecimal tasaAnual, Long estimacionTomaCuentaId) {
	}

	/** Una solicitud guardada, con el texto de su modelo. */
	record Solicitud(long id, long modeloCatalogoId, String modelo, String tipo, LocalDate preferenciaFecha,
			String preferenciaFranja, BigDecimal enganche, Integer plazoMeses, BigDecimal mensualidad,
			BigDecimal tasaAnual, Long estimacionTomaCuentaId, Long prospectoId, Instant creadaEn) {
	}

	long guardar(NuevaSolicitud solicitud);

	void ligarProspecto(long solicitudId, long prospectoId);

	Solicitud buscar(long id);

	/** Las del cliente, más recientes primero. */
	List<Solicitud> delCliente(long clienteId, int pagina, int tamano);

	long contarDelCliente(long clienteId);

	/** Las del cliente con ese modelo y tipo, para buscar duplicadas. */
	List<Solicitud> iguales(long clienteId, long modeloCatalogoId, String tipo);
}
