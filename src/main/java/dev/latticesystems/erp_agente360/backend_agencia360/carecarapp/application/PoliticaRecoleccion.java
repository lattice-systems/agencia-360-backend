package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Zona de cobertura, tarifa y ventanas de la recolección a domicilio (V2-M17),
 * tomadas de {@code app.recoleccion.*}. La cobertura es un círculo (centro y
 * radio en km) y la distancia es la línea recta entre el pin y el centro
 * (haversine).
 */
@Component
public class PoliticaRecoleccion {

	/** Una ventana de horas dentro de un día. */
	public record Ventana(LocalTime inicio, LocalTime fin) {
	}

	private static final double RADIO_TIERRA_KM = 6371.0088;

	private final double centroLat;
	private final double centroLng;
	private final double radioKm;
	private final BigDecimal tarifaBase;
	private final BigDecimal tarifaPorKm;
	private final Ventana recoleccion;
	private final Ventana entrega;

	public PoliticaRecoleccion(@Value("${app.recoleccion.cobertura.centro-lat:21.1250}") double centroLat,
			@Value("${app.recoleccion.cobertura.centro-lng:-101.6860}") double centroLng,
			@Value("${app.recoleccion.cobertura.radio-km:15}") double radioKm,
			@Value("${app.recoleccion.tarifa.base:80}") BigDecimal tarifaBase,
			@Value("${app.recoleccion.tarifa.por-km:8}") BigDecimal tarifaPorKm,
			@Value("${app.recoleccion.ventana-recoleccion.inicio:07:00}") LocalTime recoleccionInicio,
			@Value("${app.recoleccion.ventana-recoleccion.fin:09:00}") LocalTime recoleccionFin,
			@Value("${app.recoleccion.ventana-entrega.inicio:17:00}") LocalTime entregaInicio,
			@Value("${app.recoleccion.ventana-entrega.fin:19:00}") LocalTime entregaFin) {
		if (Math.abs(centroLat) > 90 || Math.abs(centroLng) > 180 || radioKm <= 0 || tarifaBase.signum() < 0
				|| tarifaPorKm.signum() < 0 || !recoleccionFin.isAfter(recoleccionInicio)
				|| !entregaFin.isAfter(entregaInicio)) {
			throw new IllegalStateException("La configuración app.recoleccion.* no es válida");
		}
		this.centroLat = centroLat;
		this.centroLng = centroLng;
		this.radioKm = radioKm;
		this.tarifaBase = tarifaBase;
		this.tarifaPorKm = tarifaPorKm;
		this.recoleccion = new Ventana(recoleccionInicio, recoleccionFin);
		this.entrega = new Ventana(entregaInicio, entregaFin);
	}

	/** Kilómetros en línea recta del punto al centro de la cobertura. */
	public double distanciaKm(BigDecimal lat, BigDecimal lng) {
		double fi1 = Math.toRadians(centroLat);
		double fi2 = Math.toRadians(lat.doubleValue());
		double dFi = fi2 - fi1;
		double dLambda = Math.toRadians(lng.doubleValue() - centroLng);
		double a = Math.pow(Math.sin(dFi / 2), 2) + Math.cos(fi1) * Math.cos(fi2) * Math.pow(Math.sin(dLambda / 2), 2);
		return 2 * RADIO_TIERRA_KM * Math.asin(Math.min(1, Math.sqrt(a)));
	}

	public boolean cubre(BigDecimal lat, BigDecimal lng) {
		return distanciaKm(lat, lng) <= radioKm;
	}

	public double radioKm() {
		return radioKm;
	}

	/** Costo de un traslado: tarifa base más tarifa por km, a 2 decimales. */
	public BigDecimal costoPorTraslado(BigDecimal lat, BigDecimal lng) {
		return tarifaBase.add(tarifaPorKm.multiply(BigDecimal.valueOf(distanciaKm(lat, lng)))).setScale(2,
				RoundingMode.HALF_UP);
	}

	public Ventana ventanaRecoleccion() {
		return recoleccion;
	}

	public Ventana ventanaEntrega() {
		return entrega;
	}
}
