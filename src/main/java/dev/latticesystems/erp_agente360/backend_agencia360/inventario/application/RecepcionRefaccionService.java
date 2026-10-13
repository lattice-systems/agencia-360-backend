package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recepción de refacciones (ERP-2.0): guarda la fecha, la cantidad y el
 * responsable, y suma la cantidad a la existencia. La fila de la refacción se
 * bloquea durante la transacción para que dos recepciones simultáneas se sumen
 * las dos.
 */
@Service
public class RecepcionRefaccionService {

	private final RefaccionRepository refaccionRepository;
	private final MovimientoInventarioRepository movimientoRepository;
	private final Clock reloj;

	@Autowired
	public RecepcionRefaccionService(RefaccionRepository refaccionRepository,
			MovimientoInventarioRepository movimientoRepository) {
		this(refaccionRepository, movimientoRepository, Clock.systemUTC());
	}

	RecepcionRefaccionService(RefaccionRepository refaccionRepository,
			MovimientoInventarioRepository movimientoRepository, Clock reloj) {
		this.refaccionRepository = refaccionRepository;
		this.movimientoRepository = movimientoRepository;
		this.reloj = reloj;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la refacción no existe (404)
	 * @throws IllegalArgumentException
	 *             si la cantidad no es mayor a cero (400)
	 */
	@Transactional
	public RecepcionRegistrada recibir(Long refaccionId, int cantidad, UsuarioAutenticado responsable) {
		Refaccion refaccion = refaccionRepository.buscarParaActualizar(refaccionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la refacción " + refaccionId));
		MovimientoInventario movimiento = movimientoRepository
				.save(refaccion.recibir(cantidad, responsable.id(), Instant.now(reloj)));
		return RecepcionRegistrada.de(movimiento, refaccion);
	}
}
