package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrección de los datos de una refacción (decisión del PO del 13 de octubre:
 * se edita, no se elimina). La fila se bloquea como en la recepción, para que
 * guardar la edición no pise la existencia que sume una recepción al mismo
 * tiempo.
 */
@Service
public class EdicionRefaccionService {

	private final RefaccionRepository refaccionRepository;

	public EdicionRefaccionService(RefaccionRepository refaccionRepository) {
		this.refaccionRepository = refaccionRepository;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la refacción no existe (404)
	 * @throws IllegalArgumentException
	 *             si un dato no es válido (400)
	 * @throws ConflictoException
	 *             si otra refacción ya tiene ese código (409)
	 */
	@Transactional
	public RefaccionVista editar(Long id, RefaccionEditada datos) {
		Refaccion refaccion = refaccionRepository.buscarParaActualizar(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la refacción " + id));
		// Se revisa antes de modificar la entidad: la consulta forzaría a guardar el
		// cambio y el choque llegaría como error de la base en lugar de este mensaje.
		String codigo = Refaccion.normalizarCodigo(datos.codigo());
		if (refaccionRepository.existsByCodigoAndIdNot(codigo, id)) {
			throw new ConflictoException("Ya existe una refacción con el código " + codigo);
		}
		refaccion.editar(codigo, datos.descripcion(), datos.costo(), datos.precio(), datos.minimo());
		return RefaccionVista.de(refaccion);
	}
}
