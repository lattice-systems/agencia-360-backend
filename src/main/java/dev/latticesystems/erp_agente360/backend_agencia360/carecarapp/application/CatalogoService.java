package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ModeloCatalogo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.EspecificacionModeloCatalogoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ImagenModeloCatalogoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ModeloCatalogoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de vehículos nuevos (APP-4.1): la app ve los activos. */
@Service
public class CatalogoService {

	private final ModeloCatalogoRepository repository;
	private final ImagenModeloCatalogoRepository imagenes;
	private final EspecificacionModeloCatalogoRepository especificaciones;

	public CatalogoService(ModeloCatalogoRepository repository, ImagenModeloCatalogoRepository imagenes,
			EspecificacionModeloCatalogoRepository especificaciones) {
		this.repository = repository;
		this.imagenes = imagenes;
		this.especificaciones = especificaciones;
	}

	/**
	 * El modelo con su galería, sus versiones y su ficha resumida (V2-M03.02).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si no existe o está retirado, que se trata igual para no filtrar
	 *             el catálogo viejo (404)
	 */
	@Transactional(readOnly = true)
	public DetalleModeloCatalogoApp detalle(Long id) {
		ModeloCatalogo modelo = repository.findById(id).filter(ModeloCatalogo::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el modelo " + id));
		List<DetalleModeloCatalogoApp.Imagen> fotos = imagenes.findByModeloCatalogoIdOrderByOrdenAsc(id).stream()
				.map(i -> new DetalleModeloCatalogoApp.Imagen(i.getId(), i.getUrl(), i.getColor(), i.getDescripcion(),
						i.getOrden()))
				.toList();
		List<DetalleModeloCatalogoApp.Version> versiones = repository
				.versionesActivas(modelo.getMarca(), modelo.getModelo(), modelo.getAnio()).stream()
				.map(v -> new DetalleModeloCatalogoApp.Version(v.getId(),
						v.getVersion() != null ? v.getVersion() : v.getModelo(), v.getPrecioDesde()))
				.toList();
		List<DetalleModeloCatalogoApp.Especificacion> ficha = especificaciones.findByModeloCatalogoIdOrderByOrdenAsc(id)
				.stream()
				.map(e -> new DetalleModeloCatalogoApp.Especificacion(e.getEtiqueta(), e.getValor(), e.getOrden()))
				.toList();
		return new DetalleModeloCatalogoApp(modelo.getId(), modelo.getMarca(), modelo.getModelo(), modelo.getAnio(),
				modelo.getVersion(), modelo.getPrecioDesde(), modelo.getImagenUrl(), modelo.getDescripcion(),
				modelo.isActivo(), fotos, versiones, ficha);
	}

	/** Los modelos activos por modelo, del año más nuevo al más viejo. */
	@Transactional(readOnly = true)
	public List<ModeloCatalogoApp> vigentes() {
		return repository.findByActivoTrueOrderByModeloAscAnioDescVersionAsc().stream().map(ModeloCatalogoApp::de)
				.toList();
	}

	/** Todos, también los retirados, para el ERP. */
	@Transactional(readOnly = true)
	public List<ModeloCatalogoApp> todos() {
		return repository.findAllByOrderByModeloAscAnioDescVersionAsc().stream().map(ModeloCatalogoApp::de).toList();
	}

	/**
	 * @throws IllegalArgumentException
	 *             si un dato no es válido (400)
	 * @throws ConflictoException
	 *             si ya existe la misma marca, modelo, año y versión (409)
	 */
	@Transactional
	public ModeloCatalogoApp agregar(String marca, String modelo, int anio, String version, BigDecimal precioDesde,
			String imagenUrl, String descripcion) {
		ModeloCatalogo nuevo = ModeloCatalogo.nuevo(marca, modelo, anio, version, precioDesde, imagenUrl, descripcion);
		if (repository.existe(nuevo.getMarca(), nuevo.getModelo(), nuevo.getAnio(), nuevo.getVersion())) {
			throw new ConflictoException("Ese modelo ya está en el catálogo");
		}
		return ModeloCatalogoApp.de(repository.save(nuevo));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si no existe (404)
	 */
	@Transactional
	public ModeloCatalogoApp retirar(Long id) {
		ModeloCatalogo modelo = repository.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el modelo " + id));
		modelo.retirar();
		return ModeloCatalogoApp.de(modelo);
	}
}
