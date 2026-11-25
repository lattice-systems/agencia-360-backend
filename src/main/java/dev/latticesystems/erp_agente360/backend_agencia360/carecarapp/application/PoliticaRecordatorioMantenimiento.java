package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.Optional;

/**
 * Cuándo se le recuerda al cliente su próximo servicio (V2-M05.05): a
 * {@code kmAntes} kilómetros o {@code diasAntes} días, lo que llegue primero.
 * Es pura: no toca la base ni el reloj.
 */
final class PoliticaRecordatorioMantenimiento {

	static final String POR_KM = "km";
	static final String POR_TIEMPO = "tiempo";

	private final int kmAntes;
	private final int diasAntes;

	PoliticaRecordatorioMantenimiento(int kmAntes, int diasAntes) {
		if (kmAntes < 0 || diasAntes < 0) {
			throw new IllegalArgumentException("Los umbrales no pueden ser negativos");
		}
		this.kmAntes = kmAntes;
		this.diasAntes = diasAntes;
	}

	/**
	 * @param proximo
	 *            el próximo servicio del vehículo, o {@code null} si no hay
	 * @return {@code km} o {@code tiempo} si toca avisar; vacío si todavía no. Con
	 *         los dos criterios cumplidos gana el km, que es el más preciso.
	 */
	Optional<String> motivo(ProximoServicioApp proximo) {
		if (proximo == null) {
			return Optional.empty();
		}
		if (proximo.faltanKm() <= kmAntes) {
			return Optional.of(POR_KM);
		}
		if (proximo.faltanDias() <= diasAntes) {
			return Optional.of(POR_TIEMPO);
		}
		return Optional.empty();
	}
}
