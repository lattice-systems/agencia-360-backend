package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DatosFiscales;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PerfilFiscal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PerfilFiscalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CamposInvalidosException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos fiscales del cliente (V2-M08.05). Solo se ven y cambian los propios.
 * Siempre hay un predeterminado si hay perfiles: el primero lo es solo, otro lo
 * toma al pedirlo y, si se borra el predeterminado, lo toma el más viejo de los
 * que quedan. Los cambios de un cliente se hacen uno a la vez.
 */
@Service
public class PerfilesFiscalesAppService {

	/**
	 * Prefijo del candado por cliente ("PERF" en ASCII), distinto al de lealtad.
	 */
	static final long PREFIJO_BLOQUEO = 0x5045_5246_0000_0000L;

	private final PerfilFiscalRepository perfiles;
	private final Clock reloj;

	@Autowired
	public PerfilesFiscalesAppService(PerfilFiscalRepository perfiles) {
		this(perfiles, Clock.systemUTC());
	}

	PerfilesFiscalesAppService(PerfilFiscalRepository perfiles, Clock reloj) {
		this.perfiles = perfiles;
		this.reloj = reloj;
	}

	/** El predeterminado primero y luego del más viejo al más nuevo. */
	@Transactional(readOnly = true)
	public List<PerfilFiscalApp> listar(Long clienteId) {
		return perfiles.findByClienteIdOrderByPredeterminadoDescIdAsc(clienteId).stream().map(PerfilFiscalApp::de)
				.toList();
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si no existe o no es del cliente (404)
	 */
	@Transactional(readOnly = true)
	public PerfilFiscalApp uno(Long clienteId, Long id) {
		return PerfilFiscalApp.de(propio(clienteId, id));
	}

	/**
	 * Agrega un perfil. El primero del cliente es siempre el predeterminado; otro
	 * lo es si la entrada lo pide.
	 *
	 * @throws CamposInvalidosException
	 *             con {@code DATOS_FISCALES_INVALIDOS} y lo que falla en cada campo
	 *             (400)
	 */
	@Transactional
	public PerfilFiscalApp crear(Long clienteId, EntradaPerfilFiscal entrada) {
		DatosFiscales datos = entrada.validar();
		bloquear(clienteId);
		List<PerfilFiscal> actuales = perfiles.findByClienteIdOrderByPredeterminadoDescIdAsc(clienteId);
		PerfilFiscal nuevo = new PerfilFiscal(clienteId, datos, reloj.instant());
		if (actuales.isEmpty() || entrada.pideSerPredeterminado()) {
			quitarPredeterminado(actuales);
			nuevo.marcarPredeterminado(true);
		}
		return PerfilFiscalApp.de(perfiles.save(nuevo));
	}

	/**
	 * Reemplaza los datos. Con {@code predeterminado} en {@code true} lo vuelve el
	 * predeterminado; en {@code false} o sin él no cambia cuál es.
	 *
	 * @throws CamposInvalidosException
	 *             con {@code DATOS_FISCALES_INVALIDOS} (400)
	 * @throws RecursoNoEncontradoException
	 *             si no existe o no es del cliente (404)
	 */
	@Transactional
	public PerfilFiscalApp editar(Long clienteId, Long id, EntradaPerfilFiscal entrada) {
		DatosFiscales datos = entrada.validar();
		bloquear(clienteId);
		PerfilFiscal perfil = propio(clienteId, id);
		perfil.corregir(datos, reloj.instant());
		if (entrada.pideSerPredeterminado() && !perfil.isPredeterminado()) {
			quitarPredeterminado(perfiles.findByClienteIdOrderByPredeterminadoDescIdAsc(clienteId));
			perfil.marcarPredeterminado(true);
		}
		return PerfilFiscalApp.de(perfiles.save(perfil));
	}

	/**
	 * Borra el perfil. Si era el predeterminado, lo toma el más viejo de los que
	 * quedan. Las facturas guardan su propia copia del receptor, así que borrar un
	 * perfil nunca cambia una factura.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si no existe o no es del cliente (404)
	 */
	@Transactional
	public void borrar(Long clienteId, Long id) {
		bloquear(clienteId);
		PerfilFiscal perfil = propio(clienteId, id);
		boolean eraPredeterminado = perfil.isPredeterminado();
		perfiles.delete(perfil);
		perfiles.flush();
		if (eraPredeterminado) {
			perfiles.findByClienteIdOrderByPredeterminadoDescIdAsc(clienteId).stream().findFirst()
					.ifPresent(siguiente -> {
						siguiente.marcarPredeterminado(true);
						perfiles.save(siguiente);
					});
		}
	}

	private PerfilFiscal propio(Long clienteId, Long id) {
		return perfiles.findByIdAndClienteId(id, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el perfil fiscal " + id));
	}

	private void bloquear(Long clienteId) {
		perfiles.bloquearDelCliente(PREFIJO_BLOQUEO | clienteId);
	}

	/** Se escribe antes de marcar el nuevo para no chocar con el índice único. */
	private void quitarPredeterminado(List<PerfilFiscal> actuales) {
		for (PerfilFiscal actual : actuales) {
			if (actual.isPredeterminado()) {
				actual.marcarPredeterminado(false);
				perfiles.saveAndFlush(actual);
			}
		}
	}
}
