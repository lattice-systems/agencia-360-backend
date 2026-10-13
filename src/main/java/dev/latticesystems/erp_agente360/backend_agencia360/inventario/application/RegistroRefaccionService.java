package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de refacciones en el inventario (ERP-2.0). Una refacción nueva entra con
 * existencia cero; la existencia solo crece con las recepciones (decisión del
 * PO del 13 de octubre).
 */
@Service
public class RegistroRefaccionService {

	private final RefaccionRepository refaccionRepository;

	public RegistroRefaccionService(RefaccionRepository refaccionRepository) {
		this.refaccionRepository = refaccionRepository;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si los datos no son válidos (400)
	 * @throws ConflictoException
	 *             si ya existe una refacción con ese código (409)
	 */
	@Transactional
	public RefaccionVista registrar(NuevaRefaccion nueva) {
		Refaccion refaccion = Refaccion.registrar(nueva.codigo(), nueva.descripcion(), nueva.costo(), nueva.precio(),
				nueva.minimo());
		if (refaccionRepository.existsByCodigo(refaccion.getCodigo())) {
			throw new ConflictoException("Ya existe una refacción con el código " + refaccion.getCodigo());
		}
		return RefaccionVista.de(refaccionRepository.save(refaccion));
	}
}
