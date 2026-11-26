package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Las refacciones publicadas en la tienda de la app (V2-M14.01), sin exponer
 * las entidades del inventario ni el costo.
 */
public interface CatalogoDeTienda {

	/** Solo las publicadas, por nombre. */
	Page<ProductoDeTienda> buscar(FiltroDeTienda filtro, Pageable pagina);

	/** Vacío si no existe o no está publicada. */
	Optional<ProductoDeTienda> publicado(Long productoId);

	/** Estén publicados o no, para nombrar los renglones de un pedido ya hecho. */
	List<ProductoDeTienda> porIds(Collection<Long> productoIds);

	/**
	 * @param vehiculo
	 *            deja solo los universales y los que le quedan; nulo para no
	 *            filtrar
	 * @param categoria
	 *            nula para todas
	 * @param texto
	 *            parte del nombre o del código; nulo o vacío para no filtrar
	 */
	record FiltroDeTienda(Auto vehiculo, CategoriaTienda categoria, String texto) {
	}

	/** El modelo (el Tipo, como Sentra) y el año de un vehículo. */
	record Auto(String modelo, int anio) {
	}

	/** Un modelo y rango de años compatibles. */
	record ModeloCompatible(String modelo, int anioDesde, int anioHasta) {

		public boolean incluye(Auto auto) {
			return modelo.equalsIgnoreCase(auto.modelo()) && auto.anio() >= anioDesde && auto.anio() <= anioHasta;
		}
	}

	/**
	 * @param existencia
	 *            piezas disponibles; las apartadas ya no cuentan
	 */
	record ProductoDeTienda(Long id, String codigo, String nombre, String descripcion, CategoriaTienda categoria,
			BigDecimal precio, String fotoUrl, int existencia, boolean universal,
			List<ModeloCompatible> compatibilidad) {

		public boolean leQuedaA(Auto auto) {
			return universal || compatibilidad.stream().anyMatch(modelo -> modelo.incluye(auto));
		}
	}
}
