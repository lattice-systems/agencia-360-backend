package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DocumentoVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.OrigenDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoDocumento;
import java.time.LocalDate;

/**
 * Documento del vehículo ({@code Documento} del contrato de la app). No expone
 * la dirección del archivo: solo si lo tiene; se descargará con URL firmada.
 *
 * @param estado
 *            calculado con la fecha de la agencia
 * @param diasRestantes
 *            negativo si ya venció; {@code null} sin fecha de vencimiento
 */
public record DocumentoApp(Long id, Long vehiculoId, TipoDocumento tipo, String titulo, String numero,
		LocalDate emitidoEn, LocalDate venceEn, EstadoDocumento estado, Integer diasRestantes, OrigenDocumento origen,
		boolean tieneArchivo, String aseguradora, String telefonoAseguradora) {

	static DocumentoApp de(DocumentoVehiculo documento, LocalDate hoy) {
		return new DocumentoApp(documento.getId(), documento.getVehiculoClienteId(), documento.getTipo(),
				documento.getTitulo(), documento.getNumero(), documento.getEmitidoEn(), documento.getVenceEn(),
				documento.estadoEn(hoy), documento.diasRestantesEn(hoy), documento.getOrigen(),
				documento.getArchivoUrl() != null, documento.getAseguradora(), documento.getTelefonoAseguradora());
	}
}
