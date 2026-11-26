package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.NivelLealtad;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Las reglas de lealtad (V2-M12), en la configuración {@code app.lealtad.*}
 * para que el PO las cambie sin tocar código. Los valores por omisión son los
 * propuestos en el plan del módulo M12.
 */
@Component
public class ReglasLealtad {

	private final int pesosPorPunto;
	private final int puntosPlata;
	private final int puntosOro;
	private final Duration vigenciaCanje;
	private final int puntosPorReferido;
	private final int referidosAbiertos;

	public ReglasLealtad(@Value("${app.lealtad.pesos-por-punto:10}") int pesosPorPunto,
			@Value("${app.lealtad.nivel.plata:1500}") int puntosPlata,
			@Value("${app.lealtad.nivel.oro:4000}") int puntosOro,
			@Value("${app.lealtad.canje.dias-vigencia:30}") int diasVigenciaCanje,
			@Value("${app.lealtad.referido.puntos:500}") int puntosPorReferido,
			@Value("${app.lealtad.referido.abiertos:10}") int referidosAbiertos) {
		if (pesosPorPunto <= 0 || puntosPlata <= 0 || puntosOro <= puntosPlata || diasVigenciaCanje <= 0
				|| puntosPorReferido <= 0 || referidosAbiertos <= 0) {
			throw new IllegalArgumentException("Reglas de lealtad inválidas");
		}
		this.pesosPorPunto = pesosPorPunto;
		this.puntosPlata = puntosPlata;
		this.puntosOro = puntosOro;
		this.vigenciaCanje = Duration.ofDays(diasVigenciaCanje);
		this.puntosPorReferido = puntosPorReferido;
		this.referidosAbiertos = referidosAbiertos;
	}

	/** Las de omisión, para las pruebas. */
	public static ReglasLealtad porOmision() {
		return new ReglasLealtad(10, 1500, 4000, 30, 500, 10);
	}

	/** Puntos de un servicio: un punto por cada {@code pesosPorPunto}, truncado. */
	public int puntosPorServicio(BigDecimal total) {
		if (total == null || total.signum() <= 0) {
			return 0;
		}
		return total.divide(BigDecimal.valueOf(pesosPorPunto), 0, RoundingMode.DOWN).intValueExact();
	}

	public NivelLealtad nivel(long ganados) {
		if (ganados >= puntosOro) {
			return NivelLealtad.ORO;
		}
		return ganados >= puntosPlata ? NivelLealtad.PLATA : NivelLealtad.BRONCE;
	}

	/** El nivel que sigue, o {@code null} si ya está en el último. */
	public NivelLealtad siguiente(NivelLealtad nivel) {
		if (nivel == NivelLealtad.BRONCE) {
			return NivelLealtad.PLATA;
		}
		return nivel == NivelLealtad.PLATA ? NivelLealtad.ORO : null;
	}

	/** Los puntos ganados que pide un nivel. */
	public int umbral(NivelLealtad nivel) {
		if (nivel == NivelLealtad.ORO) {
			return puntosOro;
		}
		return nivel == NivelLealtad.PLATA ? puntosPlata : 0;
	}

	public Duration vigenciaCanje() {
		return vigenciaCanje;
	}

	public int puntosPorReferido() {
		return puntosPorReferido;
	}

	public int referidosAbiertos() {
		return referidosAbiertos;
	}
}
