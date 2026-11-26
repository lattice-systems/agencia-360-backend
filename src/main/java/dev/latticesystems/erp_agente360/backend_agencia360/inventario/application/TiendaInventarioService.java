package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.ApartadoDeRefacciones;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.PublicacionTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** La tienda de la app vista desde el inventario (V2-M14). */
@Service
public class TiendaInventarioService implements CatalogoDeTienda, ApartadoDeRefacciones {

	private static final String SIN_REFACCION = "No existe el producto ";

	private final RefaccionRepository refacciones;
	private final MovimientoInventarioRepository movimientos;
	private final EntityManager entidades;
	private final Clock reloj;

	@Autowired
	public TiendaInventarioService(RefaccionRepository refacciones, MovimientoInventarioRepository movimientos,
			EntityManager entidades) {
		this(refacciones, movimientos, entidades, Clock.systemUTC());
	}

	TiendaInventarioService(RefaccionRepository refacciones, MovimientoInventarioRepository movimientos,
			EntityManager entidades, Clock reloj) {
		this.refacciones = refacciones;
		this.movimientos = movimientos;
		this.entidades = entidades;
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ProductoDeTienda> buscar(FiltroDeTienda filtro, Pageable pagina) {
		Auto auto = filtro.vehiculo();
		return refacciones
				.buscarEnTienda(auto == null, auto == null ? "" : auto.modelo().toLowerCase(Locale.ROOT),
						auto == null ? 0 : auto.anio(), filtro.categoria(), patron(filtro.texto()), pagina)
				.map(TiendaInventarioService::producto);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ProductoDeTienda> publicado(Long productoId) {
		return refacciones.findById(productoId).filter(Refaccion::isPublicadaEnApp)
				.map(TiendaInventarioService::producto);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProductoDeTienda> porIds(Collection<Long> productoIds) {
		return refacciones.findAllById(productoIds).stream().map(TiendaInventarioService::producto).toList();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public List<PiezaApartada> apartar(Long pedidoId, Map<Long, Integer> cantidades) {
		Instant ahora = Instant.now(reloj);
		List<PiezaApartada> apartadas = new ArrayList<>();
		// En orden de id para que dos pedidos a la vez no se bloqueen entre sí.
		new TreeMap<>(cantidades).forEach((productoId, cantidad) -> {
			Refaccion refaccion = bloquear(productoId);
			if (!refaccion.isPublicadaEnApp()) {
				throw new RecursoNoEncontradoException(SIN_REFACCION + productoId);
			}
			if (cantidad > refaccion.getExistencia()) {
				throw new ConflictoException("No alcanzan las piezas de " + refaccion.getDescripcion(),
						CodigosError.SIN_EXISTENCIA);
			}
			movimientos.save(refaccion.apartar(cantidad, pedidoId, ahora));
			apartadas.add(new PiezaApartada(productoId, cantidad, refaccion.getPrecio()));
		});
		return apartadas;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void liberar(Long pedidoId, Map<Long, Integer> cantidades) {
		Instant ahora = Instant.now(reloj);
		new TreeMap<>(cantidades).forEach((productoId, cantidad) -> {
			Refaccion refaccion = bloquear(productoId);
			MovimientoInventario entrada = refaccion.liberar(cantidad, pedidoId, ahora);
			movimientos.save(entrada);
		});
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la refacción no existe (404)
	 */
	@Transactional(readOnly = true)
	public PublicacionVista publicacion(Long refaccionId) {
		return refacciones.findById(refaccionId).map(PublicacionVista::de)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la refacción " + refaccionId));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la refacción no existe (404)
	 * @throws IllegalArgumentException
	 *             si la publicación no es válida (400)
	 */
	@Transactional
	public PublicacionVista publicar(Long refaccionId, PublicacionTienda publicacion) {
		Refaccion refaccion = refacciones.buscarParaActualizar(refaccionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la refacción " + refaccionId));
		refaccion.publicar(publicacion);
		return PublicacionVista.de(refaccion);
	}

	/**
	 * Bloquea la fila y la vuelve a leer. Sin el {@code refresh}, una refacción que
	 * la transacción ya hubiera leído conservaría la existencia anterior al bloqueo
	 * y dos apartados a la vez se pisarían.
	 */
	private Refaccion bloquear(Long productoId) {
		refacciones.bloquearFila(productoId)
				.orElseThrow(() -> new RecursoNoEncontradoException(SIN_REFACCION + productoId));
		Refaccion refaccion = refacciones.findById(productoId).orElseThrow();
		entidades.refresh(refaccion);
		return refaccion;
	}

	/** Patrón de LIKE con los comodines del texto escapados con {@code !}. */
	private static String patron(String texto) {
		if (texto == null || texto.isBlank()) {
			return "%";
		}
		String escapado = texto.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_",
				"!_");
		return "%" + escapado + "%";
	}

	static ProductoDeTienda producto(Refaccion refaccion) {
		return new ProductoDeTienda(refaccion.getId(), refaccion.getCodigo(), refaccion.getDescripcion(),
				refaccion.getDescripcionApp(), refaccion.getCategoriaApp(), refaccion.getPrecio(),
				refaccion.getFotoUrl(), refaccion.getExistencia(), refaccion.isUniversalApp(), compatibles(refaccion));
	}

	static List<ModeloCompatible> compatibles(Refaccion refaccion) {
		return refaccion.getCompatibilidades().stream()
				.map(modelo -> new ModeloCompatible(modelo.getModelo(), modelo.getAnioDesde(), modelo.getAnioHasta()))
				.toList();
	}
}
