package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de refacciones en el inventario (ERP-2.0). La existencia con la que
 * llega se registra como la primera recepción, a cargo de quien da de alta,
 * para que toda la existencia quede respaldada por movimientos.
 */
@Service
public class RegistroRefaccionService {

	private final RefaccionRepository refaccionRepository;
	private final MovimientoInventarioRepository movimientoRepository;
	private final Clock reloj;

	@Autowired
	public RegistroRefaccionService(RefaccionRepository refaccionRepository,
			MovimientoInventarioRepository movimientoRepository) {
		this(refaccionRepository, movimientoRepository, Clock.systemUTC());
	}

	RegistroRefaccionService(RefaccionRepository refaccionRepository,
			MovimientoInventarioRepository movimientoRepository, Clock reloj) {
		this.refaccionRepository = refaccionRepository;
		this.movimientoRepository = movimientoRepository;
		this.reloj = reloj;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si los datos no son válidos o la existencia es negativa (400)
	 * @throws ConflictoException
	 *             si ya existe una refacción con ese código (409)
	 */
	@Transactional
	public RefaccionVista registrar(NuevaRefaccion nueva, UsuarioAutenticado responsable) {
		if (nueva.existencia() < 0) {
			throw new IllegalArgumentException("La existencia no puede ser negativa");
		}
		Refaccion refaccion = Refaccion.registrar(nueva.codigo(), nueva.descripcion(), nueva.costo(), nueva.precio(),
				nueva.minimo());
		if (refaccionRepository.existsByCodigo(refaccion.getCodigo())) {
			throw new ConflictoException("Ya existe una refacción con el código " + refaccion.getCodigo());
		}
		Refaccion guardada = refaccionRepository.save(refaccion);
		if (nueva.existencia() > 0) {
			movimientoRepository.save(guardada.recibir(nueva.existencia(), responsable.id(), Instant.now(reloj)));
		}
		return RefaccionVista.de(guardada);
	}
}
