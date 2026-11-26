package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoPuntos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MovimientoPuntos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.MovimientoPuntosRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mueve los puntos de un cliente (V2-M12): guarda el movimiento y avisa en vivo
 * con {@code puntos.movidos}. Un mismo motivo con la misma referencia solo
 * cuenta una vez; repetirlo no hace nada.
 */
@Service
public class CuentaPuntosService {

	static final String EVENTO = "puntos.movidos";

	/** Separa las claves de bloqueo del saldo de las de la agenda. */
	private static final long PREFIJO_BLOQUEO = 0x4C45_414C_0000_0000L;

	private final MovimientoPuntosRepository movimientos;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;

	@Autowired
	public CuentaPuntosService(MovimientoPuntosRepository movimientos, ApplicationEventPublisher eventos) {
		this(movimientos, eventos, Clock.systemUTC());
	}

	CuentaPuntosService(MovimientoPuntosRepository movimientos, ApplicationEventPublisher eventos, Clock reloj) {
		this.movimientos = movimientos;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/** Bloquea el saldo del cliente hasta que termine la transacción en curso. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void bloquear(Long clienteId) {
		movimientos.bloquearSaldo(PREFIJO_BLOQUEO | clienteId);
	}

	/**
	 * @return el movimiento guardado, o {@code null} si esa referencia ya se había
	 *         contado
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public MovimientoPuntos mover(Long clienteId, int puntos, MotivoPuntos motivo, String referenciaTipo,
			Long referenciaId) {
		if (referenciaTipo != null
				&& movimientos.existsByMotivoAndReferenciaTipoAndReferenciaId(motivo, referenciaTipo, referenciaId)) {
			return null;
		}
		MovimientoPuntos guardado = movimientos
				.save(new MovimientoPuntos(clienteId, puntos, motivo, referenciaTipo, referenciaId, reloj.instant()));
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("puntos", puntos);
		datos.put("motivo", motivo.name());
		datos.put("saldo", movimientos.saldo(clienteId));
		eventos.publishEvent(new AvisoAlCliente(clienteId, EVENTO, datos));
		return guardado;
	}

	public long saldo(Long clienteId) {
		return movimientos.saldo(clienteId);
	}
}
