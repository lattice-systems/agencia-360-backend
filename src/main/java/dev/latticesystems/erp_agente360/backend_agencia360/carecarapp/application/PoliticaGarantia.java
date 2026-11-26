package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.AvisoGarantia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Garantia;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Cómo va una cobertura y cuándo avisar que está por vencer (V2-M10.01 y
 * V2-M10.05): por vencer a 60 días o a 1 000 km del límite; avisos a 60 y 15
 * días y a 1 000 km. Es pura: no toca la base ni el reloj.
 */
final class PoliticaGarantia {

	static final int DIAS_POR_VENCER = 60;
	static final int DIAS_AVISO_FINAL = 15;
	static final int KM_POR_VENCER = 1_000;
	static final String POR_FECHA = "fecha";
	static final String POR_KM = "km";

	private static final int CIEN = 100;

	private PoliticaGarantia() {
	}

	static CoberturaApp evaluar(Garantia garantia, Integer kmActual, LocalDate hoy) {
		Integer kmLimite = garantia.getKmLimite();
		boolean vencidaPorFecha = hoy.isAfter(garantia.getVenceEn());
		boolean vencidaPorKm = kmLimite != null && kmActual != null && kmActual >= kmLimite;
		long dias = Math.max(0, ChronoUnit.DAYS.between(hoy, garantia.getVenceEn()));
		Integer kmRestantes = kmLimite == null || kmActual == null ? null : Math.max(0, kmLimite - kmActual);
		int porTiempo = consumoDelTiempo(garantia, hoy);
		int porKm = kmLimite == null || kmActual == null ? 0 : acotar((long) kmActual * CIEN / kmLimite);
		EstadoCobertura estado;
		String limite;
		if (vencidaPorFecha || vencidaPorKm) {
			estado = EstadoCobertura.VENCIDA;
			limite = vencidaPorKm && !vencidaPorFecha ? POR_KM : POR_FECHA;
		} else {
			boolean cerca = dias <= DIAS_POR_VENCER || kmRestantes != null && kmRestantes <= KM_POR_VENCER;
			estado = cerca ? EstadoCobertura.POR_VENCER : EstadoCobertura.VIGENTE;
			limite = porKm > porTiempo ? POR_KM : POR_FECHA;
		}
		return new CoberturaApp(garantia.getId(), garantia.getComponente(), garantia.getDescripcion(),
				garantia.getIniciaEn(), garantia.getVenceEn(), kmLimite, dias, kmRestantes, Math.max(porTiempo, porKm),
				estado, limite);
	}

	/**
	 * @return el aviso que toca mandar hoy, el más urgente primero (15 días, 1 000
	 *         km, 60 días); vacío si ninguno o si ya venció
	 */
	static Optional<AvisoGarantia> avisoPendiente(Garantia garantia, Integer kmActual, LocalDate hoy) {
		CoberturaApp cobertura = evaluar(garantia, kmActual, hoy);
		if (cobertura.estado() == EstadoCobertura.VENCIDA) {
			return Optional.empty();
		}
		if (cobertura.diasRestantes() <= DIAS_AVISO_FINAL && !garantia.avisado(AvisoGarantia.DIAS_15)) {
			return Optional.of(AvisoGarantia.DIAS_15);
		}
		if (cobertura.kmRestantes() != null && cobertura.kmRestantes() <= KM_POR_VENCER
				&& !garantia.avisado(AvisoGarantia.KM_1000)) {
			return Optional.of(AvisoGarantia.KM_1000);
		}
		if (cobertura.diasRestantes() <= DIAS_POR_VENCER && !garantia.avisado(AvisoGarantia.DIAS_60)) {
			return Optional.of(AvisoGarantia.DIAS_60);
		}
		return Optional.empty();
	}

	private static int consumoDelTiempo(Garantia garantia, LocalDate hoy) {
		long total = ChronoUnit.DAYS.between(garantia.getIniciaEn(), garantia.getVenceEn());
		if (total <= 0) {
			return hoy.isBefore(garantia.getIniciaEn()) ? 0 : CIEN;
		}
		return acotar(ChronoUnit.DAYS.between(garantia.getIniciaEn(), hoy) * CIEN / total);
	}

	private static int acotar(long porcentaje) {
		return (int) Math.max(0, Math.min(CIEN, porcentaje));
	}
}
