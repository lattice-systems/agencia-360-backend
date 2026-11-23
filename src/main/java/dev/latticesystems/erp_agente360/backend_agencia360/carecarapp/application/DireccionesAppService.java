package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DireccionCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.DireccionClienteRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.TrasladoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoProcesableException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Direcciones guardadas del cliente (V2-M17.01). Solo se ven y cambian las
 * propias, y un pin fuera de la zona de cobertura no se guarda.
 */
@Service
public class DireccionesAppService {

	private final DireccionClienteRepository direcciones;
	private final TrasladoRepository traslados;
	private final PoliticaRecoleccion politica;

	public DireccionesAppService(DireccionClienteRepository direcciones, TrasladoRepository traslados,
			PoliticaRecoleccion politica) {
		this.direcciones = direcciones;
		this.traslados = traslados;
		this.politica = politica;
	}

	@Transactional(readOnly = true)
	public List<DireccionApp> listar(Long clienteId) {
		return direcciones.findByClienteIdOrderByIdAsc(clienteId).stream().map(DireccionApp::de).toList();
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta un dato o no es válido (400)
	 * @throws NoProcesableException
	 *             con {@code FUERA_DE_COBERTURA} si el pin queda fuera de la zona
	 *             (422)
	 */
	@Transactional
	public DireccionApp crear(Long clienteId, String alias, String calle, String colonia, String cp, String ciudad,
			BigDecimal lat, BigDecimal lng) {
		DireccionCliente nueva = DireccionCliente.crear(clienteId, alias, calle, colonia, cp, ciudad, lat, lng);
		exigirCobertura(nueva);
		return DireccionApp.de(direcciones.save(nueva));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la dirección no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si falta un dato o no es válido (400)
	 * @throws NoProcesableException
	 *             con {@code FUERA_DE_COBERTURA} si el pin queda fuera de la zona
	 *             (422)
	 */
	@Transactional
	public DireccionApp editar(Long clienteId, Long id, String alias, String calle, String colonia, String cp,
			String ciudad, BigDecimal lat, BigDecimal lng) {
		DireccionCliente direccion = propia(clienteId, id);
		if (lat != null && lng != null) {
			exigirCobertura(lat, lng);
		}
		direccion.corregir(alias, calle, colonia, cp, ciudad, lat, lng);
		return DireccionApp.de(direcciones.save(direccion));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la dirección no existe o no es del cliente (404)
	 * @throws ConflictoException
	 *             con {@code DIRECCION_EN_USO} si tiene traslados (409)
	 */
	@Transactional
	public void eliminar(Long clienteId, Long id) {
		DireccionCliente direccion = propia(clienteId, id);
		if (traslados.existsByDireccionClienteId(id)) {
			throw new ConflictoException("La dirección tiene traslados y no se puede borrar",
					CodigosError.DIRECCION_EN_USO);
		}
		direcciones.delete(direccion);
	}

	DireccionCliente propia(Long clienteId, Long id) {
		return direcciones.findByIdAndClienteId(id, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la dirección " + id));
	}

	void exigirCobertura(DireccionCliente direccion) {
		exigirCobertura(direccion.getLat(), direccion.getLng());
	}

	/** Se revisa antes de cambiar la dirección para no dejarla a medias. */
	private void exigirCobertura(BigDecimal lat, BigDecimal lng) {
		if (!politica.cubre(lat, lng)) {
			throw new NoProcesableException("La dirección queda fuera de la zona de cobertura",
					CodigosError.FUERA_DE_COBERTURA);
		}
	}
}
