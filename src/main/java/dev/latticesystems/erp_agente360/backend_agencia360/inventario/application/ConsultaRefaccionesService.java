package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de las refacciones del inventario (ERP-2.0). */
@Service
public class ConsultaRefaccionesService {

	private final RefaccionRepository refaccionRepository;

	public ConsultaRefaccionesService(RefaccionRepository refaccionRepository) {
		this.refaccionRepository = refaccionRepository;
	}

	/** Ordenadas por código, como un catálogo. */
	@Transactional(readOnly = true)
	public List<RefaccionVista> listar() {
		return refaccionRepository.findAllByOrderByCodigoAsc().stream().map(RefaccionVista::de).toList();
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la refacción no existe (404)
	 */
	@Transactional(readOnly = true)
	public RefaccionVista obtener(Long id) {
		return refaccionRepository.findById(id).map(RefaccionVista::de)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la refacción " + id));
	}
}
