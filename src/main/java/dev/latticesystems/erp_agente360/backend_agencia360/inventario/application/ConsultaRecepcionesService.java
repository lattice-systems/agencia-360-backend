package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.TipoMovimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historial de recepciones de una refacción, de la más reciente a la más
 * antigua. Muestra las dos fechas para que el Administrador vea las entregas
 * que se registraron después de llegar.
 */
@Service
public class ConsultaRecepcionesService {

	private final RefaccionRepository refaccionRepository;
	private final MovimientoInventarioRepository movimientoRepository;

	public ConsultaRecepcionesService(RefaccionRepository refaccionRepository,
			MovimientoInventarioRepository movimientoRepository) {
		this.refaccionRepository = refaccionRepository;
		this.movimientoRepository = movimientoRepository;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la refacción no existe (404)
	 */
	@Transactional(readOnly = true)
	public List<RecepcionVista> listar(Long refaccionId) {
		if (!refaccionRepository.existsById(refaccionId)) {
			throw new RecursoNoEncontradoException("No existe la refacción " + refaccionId);
		}
		return movimientoRepository
				.findByRefaccionIdAndTipoOrderByFechaRegistroDescIdDesc(refaccionId, TipoMovimiento.ENTRADA).stream()
				.map(RecepcionVista::de).toList();
	}
}
