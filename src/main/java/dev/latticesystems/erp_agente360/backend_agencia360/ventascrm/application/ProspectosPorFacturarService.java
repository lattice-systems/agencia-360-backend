package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista de trabajo de Facturación en el ERP (ERP-1.0): los prospectos en
 * Facturación, a los que les elige la unidad y después pasa a Entrega.
 */
@Service
public class ProspectosPorFacturarService {

	private final ProspectoRepository prospectoRepository;
	private final DirectorioUsuarios directorioUsuarios;

	public ProspectosPorFacturarService(ProspectoRepository prospectoRepository,
			DirectorioUsuarios directorioUsuarios) {
		this.prospectoRepository = prospectoRepository;
		this.directorioUsuarios = directorioUsuarios;
	}

	@Transactional(readOnly = true)
	public List<ProspectoPorFacturar> listar() {
		List<Prospecto> prospectos = prospectoRepository.findByEtapaOrderByCreatedAtDescIdDesc(Etapa.FACTURACION);
		Map<Long, String> vendedores = directorioUsuarios
				.nombresDe(prospectos.stream().map(Prospecto::getVendedorId).distinct().toList());
		return prospectos.stream().map(p -> new ProspectoPorFacturar(p.getId(), p.getNombre(), p.getTelefono(),
				p.getCorreo(), p.getVendedorId(), vendedores.get(p.getVendedorId()), p.getEtapaActualizadaEn()))
				.toList();
	}
}
