package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FichaTecnicaModelo;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ficha técnica del vehículo del cliente (V2-M02.04): especificaciones del
 * modelo y año agrupadas en secciones. {@code secciones} viene vacía si la
 * agencia aún no carga ese modelo.
 */
public record FichaTecnicaApp(Long vehiculoId, String marca, String modelo, int anio, String version,
		List<Seccion> secciones) {

	public record Seccion(String nombre, List<Especificacion> especificaciones) {
	}

	public record Especificacion(String nombre, String valor) {
	}

	/** Agrupa por sección en el orden en que llegan las filas. */
	static FichaTecnicaApp de(VehiculoDeCliente vehiculo, List<FichaTecnicaModelo> filas) {
		Map<String, List<Especificacion>> porSeccion = new LinkedHashMap<>();
		for (FichaTecnicaModelo fila : filas) {
			porSeccion.computeIfAbsent(fila.getSeccion(), seccion -> new ArrayList<>())
					.add(new Especificacion(fila.getNombre(), fila.getValor()));
		}
		List<Seccion> secciones = porSeccion.entrySet().stream()
				.map(entrada -> new Seccion(entrada.getKey(), entrada.getValue())).toList();
		return new FichaTecnicaApp(vehiculo.id(), vehiculo.marca(), vehiculo.modelo(), vehiculo.anio(),
				vehiculo.version(), secciones);
	}
}
