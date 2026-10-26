package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application;

import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.DatosFactura.Parte;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Datos fiscales de la agencia que emite las facturas. Son fijos y vienen de la
 * configuración ({@code app.facturacion.emisor.*}); en cada ambiente se
 * sobrescriben con variables de entorno.
 */
@Component
public class Emisor {

	private final Parte datos;

	public Emisor(@Value("${app.facturacion.emisor.rfc}") String rfc,
			@Value("${app.facturacion.emisor.nombre}") String nombre,
			@Value("${app.facturacion.emisor.domicilio-fiscal}") String domicilioFiscal,
			@Value("${app.facturacion.emisor.regimen-fiscal}") String regimenFiscal) {
		this.datos = new Parte(rfc, nombre, domicilioFiscal, regimenFiscal);
	}

	public Parte datos() {
		return datos;
	}
}
